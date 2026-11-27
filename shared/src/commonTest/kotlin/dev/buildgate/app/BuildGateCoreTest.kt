package dev.buildgate.app

import dev.buildgate.app.app.di.appModule
import dev.buildgate.app.app.navigation.BuildGateRoute
import dev.buildgate.app.app.navigation.DeepLinkRouter
import dev.buildgate.app.app.navigation.IncomingDeepLink
import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.dailyverification.model.VerificationMethod
import dev.buildgate.app.entities.enrollment.domain.StreakAndKarmaCalculator
import dev.buildgate.app.entities.enrollment.model.Enrollment
import dev.buildgate.app.entities.enrollment.model.EnrollmentStatus
import dev.buildgate.app.entities.enrollment.repository.EnrollmentRepository
import dev.buildgate.app.entities.feedbackitem.model.FeedbackCategory
import dev.buildgate.app.entities.feedbackitem.model.FeedbackStatus
import dev.buildgate.app.entities.feedbackitem.repository.DemoFeedbackRepository
import dev.buildgate.app.entities.feedbackitem.repository.FeedbackRepository
import dev.buildgate.app.features.auth.AuthViewModel
import dev.buildgate.app.features.managemissions.ManageMissionsViewModel
import dev.buildgate.app.features.publishapp.PublishAppViewModel
import dev.buildgate.app.features.submitfeedback.SubmitFeedbackViewModel
import dev.buildgate.app.features.triagefeedback.TriageFeedbackViewModel
import dev.buildgate.app.features.verifysession.VerifySessionViewModel
import dev.buildgate.app.shared.platform.DeviceMetadata
import dev.buildgate.app.shared.session.QaSessionCoordinator
import dev.buildgate.app.shared.session.QaSessionPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        val campaignLink = DeepLinkRouter.parseUri("https://buildgatehub.com/app/io.focuspulse.habittracker")
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

    @Test
    fun koinAppModule_resolvesAllRepositoriesAndViewModelsInDemoMode() {
        val demoOverride = org.koin.dsl.module {
            single { dev.buildgate.app.shared.network.SupabaseConfig(supabaseUrl = "", publishableKey = "") }
        }
        val app = koinApplication {
            allowOverride(true)
            modules(appModule, demoOverride)
        }
        val koin = app.koin
        assertNotNull(koin.get<AuthRepository>())
        assertNotNull(koin.get<AppListingRepository>())
        assertNotNull(koin.get<EnrollmentRepository>())
        assertNotNull(koin.get<FeedbackRepository>())
        assertNotNull(koin.get<AuthViewModel>())
        assertNotNull(koin.get<VerifySessionViewModel>())
        assertNotNull(koin.get<TriageFeedbackViewModel>())
        assertNotNull(koin.get<SubmitFeedbackViewModel>())
        assertNotNull(koin.get<ManageMissionsViewModel>())
        assertNotNull(koin.get<PublishAppViewModel>())
        app.close()
    }

    @Test
    fun qaSessionCoordinator_pausesWhenTargetBackgrounded_andAdvancesDirectlyToNextApp() {
        var fakeNowMs = 1_000L
        val coordinator = QaSessionCoordinator { fakeNowMs }

        val app1 = sampleApp("app_1", "com.mkdinteractive.shatterhorde")
        val app2 = sampleApp("app_2", "com.cozybento.bento_puzzle")
        coordinator.startQueue(listOf(app1, app2), testerId = "tester_1")
        coordinator.startAppSession(
            app = app1,
            enrollmentId = "enr_1",
            testerId = "tester_1",
            sessionToken = "bg_nonce_1",
            targetDurationMs = 60_000L
        )

        // 20s in foreground
        fakeNowMs = 21_000L
        assertEquals(40, coordinator.state.value.secondsRemaining(fakeNowMs))

        // User backgrounds target app -> timer freezes!
        coordinator.onTargetForegroundChanged(isForeground = false)
        assertEquals(QaSessionPhase.PAUSED_TARGET_BACKGROUNDED, coordinator.state.value.phase)
        fakeNowMs = 121_000L // 100s spent on Home screen
        assertEquals(40, coordinator.state.value.secondsRemaining(fakeNowMs))

        // User returns to target app -> finishes remaining 40s
        coordinator.onTargetForegroundChanged(isForeground = true)
        assertEquals(QaSessionPhase.RUNNING_IN_TARGET_APP, coordinator.state.value.phase)
        fakeNowMs = 162_000L
        assertEquals(QaSessionPhase.READY_TO_VERIFY, coordinator.evaluateProgress().phase)

        // Direct overlay launch of next queued app
        val next = coordinator.advanceAndStartNextQueuedApp(sessionToken = "bg_nonce_2")
        assertEquals("app_2", next?.id)
        assertEquals(QaSessionPhase.RUNNING_IN_TARGET_APP, coordinator.state.value.phase)
        assertEquals(1, coordinator.state.value.currentQueueIndex)
    }

    @Test
    fun qrCodeEncoder_generatesValidIsoMatrixForDeepLink() {
        val matrix = dev.buildgate.app.widgets.qr.QrCodeEncoder.encode("buildgate://app/com.mkdinteractive.shatterhorde")
        assertTrue(matrix.size >= 29) // Version 3+ for 47-byte payload at EC-M
        assertEquals(matrix.size * matrix.size, matrix.modules.size)
        // Top-left finder pattern center & outer ring must be dark
        assertTrue(matrix.get(0, 0))
        assertTrue(matrix.get(3, 3))
        assertFalse(matrix.get(1, 1))
        // Mandatory dark module at (size - 8, 8)
        assertTrue(matrix.get(matrix.size - 8, 8))
    }

    @Test
    fun publishAppUiState_generatesUnifiedSchemeAndValidSnippetsForAllSupportedAndroidStacks() {
        val unifiedState = dev.buildgate.app.features.publishapp.PublishAppUiState(
            packageName = "com.mkdinteractive.shatterhorde",
            enableDeepLinkHandshake = true
        )
        assertEquals("buildgate.com.mkdinteractive.shatterhorde", unifiedState.unifiedDefaultScheme)
        assertEquals("buildgate.com.mkdinteractive.shatterhorde", unifiedState.resolvedDeepLinkScheme)
        assertTrue(unifiedState.adbTestCommand.contains("buildgate.com.mkdinteractive.shatterhorde://buildgate-verify"))

        dev.buildgate.app.features.publishapp.DeepLinkSnippetLanguage.entries.forEach { lang ->
            val split = unifiedState.splitSnippetFor(lang)
            assertTrue(split.manifestPath.contains("AndroidManifest.xml"), "Missing manifest path for $lang")
            assertTrue(split.manifestXml.contains("<intent-filter>"), "Missing full manifest intent-filter in $lang")
            assertTrue(split.manifestXml.contains("buildgate.com.mkdinteractive.shatterhorde"), "Missing unified scheme in $lang manifest")
            assertTrue(split.handlerPath.isNotBlank(), "Missing handler path for $lang")
            assertTrue(split.handlerCode.contains("buildgate.com.mkdinteractive.shatterhorde"), "Missing unified scheme in $lang handler")
            assertTrue(split.handlerCode.contains("com.mkdinteractive.shatterhorde"), "Missing package in $lang handler")
            assertTrue(split.handlerCode.contains("verify-deeplink-heartbeat"), "Missing endpoint in $lang handler")
        }
    }

    @Test
    fun lenientJson_decodesPostgresBigintIdsIntoAppListingModel() {
        val lenientJson = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val postgrestPayload = """
            {
              "id": 42,
              "owner_id": "11111111-2222-3333-4444-555555555555",
              "package_name": "com.mkdinteractive.shatterhorde",
              "title": "Shatter Horde: Arena Survivor",
              "short_summary": "Arena survivor",
              "play_store_opt_in_url": "https://play.google.com/apps/testing/com.mkdinteractive.shatterhorde",
              "google_group_url": "https://groups.google.com/g/mkd-interactive",
              "deep_link_scheme": null,
              "current_build_version": "1.6.0",
              "min_version_code": 7,
              "target_days": 14,
              "target_testers": 16,
              "category": "Action",
              "is_active": true,
              "created_at": "2026-11-26T06:00:00Z"
            }
        """.trimIndent()
        val decoded = lenientJson.decodeFromString(AppListing.serializer(), postgrestPayload)
        assertEquals("42", decoded.id)
        assertEquals("com.mkdinteractive.shatterhorde", decoded.packageName)

        val feedbackPayload = """
            {
              "id": 1,
              "app_id": 1,
              "author_id": "8f611d9d-b3c7-4fda-9385-259dfa220b68",
              "session_id": null,
              "build_version": "v1.3.0",
              "category": "BUG",
              "status": "OPEN",
              "title": "movement issue",
              "description": "its hard to tell what axis I'm moving against",
              "device_metadata": {"model": "samsung SM-F971U", "osVersion": "Android 17 (API 37)", "aspectRatio": "312:493", "platformName": "Android Mobile", "screenResolution": "1248x1972"},
              "screenshot_path": "https://lrqwkdqheumyigrpqevw.supabase.co/storage/v1/object/public/feedback-media/u/1/shot.webp",
              "video_clip_path": null,
              "media_duration_ms": null,
              "upvotes": 1,
              "bounty_awarded": false,
              "created_at": "2026-11-26 06:13:44.037952+00"
            }
        """.trimIndent()
        val decodedFb = lenientJson.decodeFromString(
            dev.buildgate.app.entities.feedbackitem.model.FeedbackItem.serializer(),
            feedbackPayload
        )
        assertEquals("1", decodedFb.id)
        assertEquals("1", decodedFb.appId)
        assertTrue(decodedFb.screenshotUrl?.endsWith("shot.webp") == true)
    }

    @Test
    fun triageViewModel_enforcesOwnerOnlyTriage_andScopesOwnedAndEnrolledApps() = runTest {
        val listingRepo = dev.buildgate.app.entities.applisting.repository.DemoAppListingRepository()
        val enrollmentRepo = dev.buildgate.app.entities.enrollment.repository.DemoEnrollmentRepository()
        val feedbackRepo = DemoFeedbackRepository()
        val authRepo = dev.buildgate.app.entities.auth.repository.DemoAuthRepository()

        val vm = TriageFeedbackViewModel(
            feedbackRepository = feedbackRepo,
            appListingRepository = listingRepo,
            enrollmentRepository = enrollmentRepo,
            authRepository = authRepo
        )

        // 1. Signed out on top-level tab (empty appId) -> no app selected, canTriage is false
        vm.setUserContext(userId = "", isSignedIn = false)
        vm.selectApp("")
        val signedOutState = vm.uiState.first { !it.isSignedIn }
        assertFalse(signedOutState.canTriage)
        assertNull(signedOutState.selectedApp)

        // 2. Signed out viewing a specific app from Discover -> read-only (canTriage = false)
        vm.selectApp("app_1")
        val guestReadOnly = vm.uiState.first { it.selectedApp?.id == "app_1" }
        assertFalse(guestReadOnly.canTriage)

        // 3. Signed in as non-owner tester ("tester_99") -> still read-only (canTriage = false)
        vm.setUserContext(userId = "tester_99", isSignedIn = true)
        val nonOwnerState = vm.uiState.first { it.currentUserId == "tester_99" }
        assertFalse(nonOwnerState.canTriage)

        // 4. Signed in as owner ("tester_1") -> canTriage = true and ownedApps populated
        vm.setUserContext(userId = "tester_1", isSignedIn = true)
        val ownerState = vm.uiState.first { it.currentUserId == "tester_1" && it.canTriage }
        assertTrue(ownerState.canTriage)
        assertTrue(ownerState.ownedApps.any { it.id == "app_1" })
    }

    @Test
    fun qaSessionCoordinator_tracksMissionsAcrossQueue_andMarksPassedInOverlay() {
        var nowMs = 1_000L
        val coordinator = QaSessionCoordinator { nowMs }
        val app1 = sampleApp("app_1", "com.mkdinteractive.shatterhorde")
        val app2 = sampleApp("app_2", "com.cozybento.bento_puzzle")
        val m1 = dev.buildgate.app.entities.testmission.model.TestMission(
            id = "m1", appId = "app_1", buildVersion = "v1.6.0",
            title = "Survive Wave 3", instructions = "Test upgrades"
        )
        val m2 = dev.buildgate.app.entities.testmission.model.TestMission(
            id = "m2", appId = "app_2", buildVersion = "v1.3.1",
            title = "Solve 2 Bento Puzzles", instructions = "Check edge snap"
        )

        coordinator.startQueue(
            apps = listOf(app1, app2),
            testerId = "tester_1",
            missionsByAppId = mapOf("app_1" to listOf(m1), "app_2" to listOf(m2))
        )
        coordinator.startAppSession(app1, "enr_1", "tester_1", "bg_1")
        assertEquals(1, coordinator.state.value.activeAppMissions.size)
        assertFalse(coordinator.state.value.activeAppMissions.first().isCompleted)

        coordinator.markMissionCompleted("m1", completed = true)
        assertTrue(coordinator.state.value.activeAppMissions.first().isCompleted)

        // Advancing directly to app_2 swaps activeAppMissions to app_2's missions
        coordinator.advanceAndStartNextQueuedApp("bg_2")
        assertEquals("m2", coordinator.state.value.activeAppMissions.first().id)
        assertFalse(coordinator.state.value.activeAppMissions.first().isCompleted)
    }

    @Test
    fun appListingRepository_updatesBuildVersionAndUpgradesToDeepLinkHandshake() = runTest {
        val repo = dev.buildgate.app.entities.applisting.repository.DemoAppListingRepository()
        val original = repo.getListingById("app_1")!!
        assertEquals("v1.6.0", original.currentBuildVersion)
        assertNull(original.deepLinkScheme)

        val updated = repo.updateListing(
            original.copy(
                currentBuildVersion = "v1.6.1",
                minVersionCode = 8L,
                deepLinkScheme = "buildgate.com.mkdinteractive.shatterhorde"
            )
        )
        assertEquals("v1.6.1", updated.currentBuildVersion)
        assertEquals(8L, updated.minVersionCode)
        assertTrue(updated.hasDeepLinkHandshake)

        val fetched = repo.getListingById("app_1")!!
        assertEquals("v1.6.1", fetched.currentBuildVersion)
        assertEquals("buildgate.com.mkdinteractive.shatterhorde", fetched.deepLinkScheme)
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
