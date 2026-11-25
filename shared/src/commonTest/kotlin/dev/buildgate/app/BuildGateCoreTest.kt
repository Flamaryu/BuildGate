package dev.buildgate.app

import dev.buildgate.app.app.navigation.BuildGateRoute
import dev.buildgate.app.app.navigation.DeepLinkRouter
import dev.buildgate.app.app.navigation.IncomingDeepLink
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.domain.StreakAndKarmaCalculator
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.repository.DemoFeedbackRepository
import dev.buildgate.app.shared.platform.DeviceMetadata
import dev.buildgate.app.shared.session.QaSessionCoordinator
import dev.buildgate.app.shared.session.QaSessionPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildGateCoreTest {

    @Test
    fun streakAndKarma_handlesConsecutiveDays_graceBuffer_streakReset_andDay14Bonus() {
        var enrollment = Enrollment(
            id = "enr_test",
            appId = "app_1",
            testerId = "tester_1",
            status = EnrollmentStatus.ACTIVE
        )

        // Day 1 check-in
        val d1 = StreakAndKarmaCalculator.applyDailyVerification(
            enrollment = enrollment,
            todayUtcDate = "2026-11-01"
        )
        assertEquals(1, d1.dayNumber)
        assertEquals(10, d1.karmaEarned)
        assertFalse(d1.alreadyVerifiedToday)
        enrollment = d1.updatedEnrollment

        // Same-day duplicate check-in is idempotent
        val duplicate = StreakAndKarmaCalculator.applyDailyVerification(
            enrollment = enrollment,
            todayUtcDate = "2026-11-01"
        )
        assertTrue(duplicate.alreadyVerifiedToday)
        assertEquals(0, duplicate.karmaEarned)

        // Skip 2026-11-02 -> check in on 2026-11-03 uses the 1-day grace buffer!
        val graceCheckIn = StreakAndKarmaCalculator.applyDailyVerification(
            enrollment = enrollment,
            todayUtcDate = "2026-11-03",
            method = VerificationMethod.VERIFIED_DEEPLINK_HANDSHAKE
        )
        assertTrue(graceCheckIn.usedGraceDay)
        assertFalse(graceCheckIn.streakResetOccurred)
        assertEquals(2, graceCheckIn.dayNumber)
        assertEquals(15, graceCheckIn.karmaEarned) // 10 base + 5 deep-link bonus
        enrollment = graceCheckIn.updatedEnrollment

        // Skip another day after grace is exhausted -> streak resets to Day 1
        val resetCheckIn = StreakAndKarmaCalculator.applyDailyVerification(
            enrollment = enrollment,
            todayUtcDate = "2026-11-05"
        )
        assertTrue(resetCheckIn.streakResetOccurred)
        assertEquals(1, resetCheckIn.dayNumber)
        enrollment = resetCheckIn.updatedEnrollment

        // Fast-forward to Day 13 -> Day 14 completion awards +100 bonus and COMPLETED status
        enrollment = enrollment.copy(currentStreak = 13, lastVerifiedUtcDate = "2026-11-17")
        val day14 = StreakAndKarmaCalculator.applyDailyVerification(
            enrollment = enrollment,
            todayUtcDate = "2026-11-18",
            targetDays = 14
        )
        assertTrue(day14.campaignJustCompleted)
        assertEquals(14, day14.dayNumber)
        assertEquals(110, day14.karmaEarned)
        assertEquals(EnrollmentStatus.COMPLETED, day14.updatedEnrollment.status)

        // Completed campaigns are not penalized if the tester uninstalls after Day 14
        val postUninstall = StreakAndKarmaCalculator.applyUninstallCheck(
            enrollment = day14.updatedEnrollment,
            isInstalled = false
        )
        assertEquals(EnrollmentStatus.COMPLETED, postUninstall.status)
    }

    @Test
    fun qaSessionCoordinator_freezesTimerAndHoldsQueueWhilePausedForFeedback() {
        var fakeNowMs = 1_000L
        val coordinator = QaSessionCoordinator { fakeNowMs }

        val app1 = sampleApp("app_1", "io.focuspulse.app")
        val app2 = sampleApp("app_2", "io.vaultpay.app")
        coordinator.startQueue(listOf(app1, app2), testerId = "tester_1")
        coordinator.startAppSession(
            app = app1,
            enrollmentId = "enr_1",
            testerId = "tester_1",
            sessionToken = "bg_nonce_1",
            targetDurationMs = 60_000L
        )

        // Run for 25 seconds inside target app
        fakeNowMs = 26_000L
        assertEquals(35, coordinator.state.value.secondsRemaining(fakeNowMs))

        // Tester taps Floating QA Pill -> Pause & Log Bug
        coordinator.pauseForFeedback("Capturing screenshot & filing bug")
        assertEquals(QaSessionPhase.PAUSED_FOR_FEEDBACK, coordinator.state.value.phase)

        // Spend 3 minutes typing bug report: timer must NOT advance and queue must NOT skip!
        fakeNowMs = 206_000L
        assertEquals(35, coordinator.state.value.secondsRemaining(fakeNowMs))
        assertNull(coordinator.advanceToNextQueuedApp())

        // Submit bug & resume session -> finish remaining 35 seconds
        coordinator.resumeFromFeedback()
        assertEquals(QaSessionPhase.RUNNING_IN_TARGET_APP, coordinator.state.value.phase)

        fakeNowMs = 241_500L
        val evaluated = coordinator.evaluateProgress()
        assertEquals(QaSessionPhase.READY_TO_VERIFY, evaluated.phase)
        assertEquals(0, evaluated.secondsRemaining(fakeNowMs))

        // Queue can now advance cleanly to app_2
        val next = coordinator.advanceToNextQueuedApp()
        assertEquals("app_2", next?.id)
    }

    @Test
    fun deepLinkRouter_parsesAppLinks_customSchemeCallbacks_webHashes_andAuthCodes() {
        val campaignLink = DeepLinkRouter.parseUri("https://buildgate.dev/app/io.focuspulse.habittracker")
        assertEquals(
            IncomingDeepLink.Navigate(BuildGateRoute.AppCampaign("io.focuspulse.habittracker")),
            campaignLink
        )

        val verifyCallback = DeepLinkRouter.parseUri("buildgate://verify-callback?nonce=bg_998877&proof=server_verified")
        assertEquals(
            IncomingDeepLink.VerificationCallback(sessionToken = "bg_998877", proof = "server_verified"),
            verifyCallback
        )

        val webHash = DeepLinkRouter.parseUri("#/packet/app_1")
        assertEquals(
            IncomingDeepLink.Navigate(BuildGateRoute.ProductionPacket("app_1")),
            webHash
        )

        val oauthCallback = DeepLinkRouter.parseUri("buildgate://auth/callback?code=pkce_auth_code_123")
        assertTrue(oauthCallback is IncomingDeepLink.AuthCallback)
    }

    @Test
    fun demoFeedbackRepository_emitsLiveReactiveUpdates_andAwardsSingleBounty() = runTest {
        val repo = DemoFeedbackRepository()
        val initialCount = repo.getFeedbackForApp("app_1").first().size

        val created = repo.submitFeedback(
            appId = "app_1",
            authorId = "tester_99",
            authorUsername = "ReactiveTester",
            buildVersion = "v1.0.5",
            category = FeedbackCategory.BUG,
            title = "Overlay screenshot test bug",
            description = "Verified reactive StateFlow.map update",
            deviceMetadata = DeviceMetadata("Pixel 9", "Android 16", "1080x2424", "20:9", "Android Mobile")
        )

        val updatedList = repo.getFeedbackForApp("app_1").first()
        assertEquals(initialCount + 1, updatedList.size)

        // First transition to IN_PROGRESS awards bounty
        val firstTriage = repo.updateFeedbackStatus(created.id, FeedbackStatus.IN_PROGRESS)
        assertEquals("tester_99", firstTriage.newlyAwardedBountyToAuthorId)

        // Subsequent transition to FIXED does not double-award bounty
        val secondTriage = repo.updateFeedbackStatus(created.id, FeedbackStatus.FIXED)
        assertNull(secondTriage.newlyAwardedBountyToAuthorId)
    }

    private fun sampleApp(id: String, pkg: String) = AppListing(
        id = id,
        ownerId = "owner_1",
        packageName = pkg,
        title = "Sample $id",
        playStoreOptInUrl = "https://play.google.com/apps/testing/$pkg",
        googleGroupUrl = "https://groups.google.com/g/$id"
    )
}
