package dev.buildgate.app.entities.applisting.repository

import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.testmission.model.TestMission
import dev.buildgate.app.shared.time.BuildGateClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface AppListingRepository {
    fun getListings(): Flow<List<AppListing>>
    fun observeListingByIdOrPackage(idOrPackage: String): Flow<AppListing?>
    suspend fun getListingById(id: String): AppListing?
    suspend fun getListingByPackage(packageName: String): AppListing?
    suspend fun createListing(listing: AppListing): AppListing
    fun getMissionsForApp(appId: String): Flow<List<TestMission>>
    suspend fun createMission(mission: TestMission)
    suspend fun toggleMissionCompletion(missionId: String)
}

class DemoAppListingRepository : AppListingRepository {

    private val sampleListings = listOf(
        AppListing(
            id = "app_1",
            ownerId = "tester_1",
            packageName = "io.focuspulse.habittracker",
            title = "FocusPulse Pro - Habit Tracker",
            shortSummary = "ADHD-friendly Pomodoro timer & daily streak tracker needing 14-day closed testers before Play launch.",
            playStoreOptInUrl = "https://play.google.com/apps/testing/io.focuspulse.habittracker",
            googleGroupUrl = "https://groups.google.com/g/focuspulse-beta-testers",
            deepLinkScheme = "focuspulse",
            currentBuildVersion = "v1.0.4-rc1",
            minVersionCode = 14L,
            targetDays = 14,
            targetTesters = 16,
            activeTestersCount = 18,
            completedTestersCount = 6,
            ownerKarmaBalance = 340,
            category = "Productivity & Habits",
            createdAtIso = "2026-11-12T10:00:00Z"
        ),
        AppListing(
            id = "app_2",
            ownerId = "owner_2",
            packageName = "com.devhub.cryptowallet.beta",
            title = "VaultPay Multi-Chain Wallet",
            shortSummary = "Hardware-backed passkey wallet with instant QR swaps and biometric recovery.",
            playStoreOptInUrl = "https://play.google.com/apps/testing/com.devhub.cryptowallet.beta",
            googleGroupUrl = "https://groups.google.com/g/vaultpay-testers",
            deepLinkScheme = null,
            currentBuildVersion = "v2.1.0-beta",
            minVersionCode = 21L,
            targetDays = 14,
            targetTesters = 20,
            activeTestersCount = 23,
            completedTestersCount = 11,
            ownerKarmaBalance = 490,
            category = "Finance",
            createdAtIso = "2026-11-08T10:00:00Z"
        ),
        AppListing(
            id = "app_3",
            ownerId = "owner_3",
            packageName = "com.soundscape.audio.studio",
            title = "SoundScape AI Audio Editor",
            shortSummary = "Multi-track stem splitter and mobile podcast noise remover.",
            playStoreOptInUrl = "https://play.google.com/apps/testing/com.soundscape.audio.studio",
            googleGroupUrl = "https://groups.google.com/g/soundscape-early-access",
            deepLinkScheme = "soundscape",
            currentBuildVersion = "v0.9.8-beta",
            minVersionCode = 9L,
            targetDays = 14,
            targetTesters = 16,
            activeTestersCount = 14,
            completedTestersCount = 4,
            ownerKarmaBalance = 215,
            category = "Music & Audio",
            createdAtIso = "2026-11-15T10:00:00Z"
        )
    )

    private val sampleMissions = MutableStateFlow(
        listOf(
            TestMission(
                id = "m1",
                appId = "app_1",
                buildVersion = "v1.0.4-rc1",
                title = "Create 1 Focus Habit",
                instructions = "Tap '+' icon, select 25-min Pomodoro habit, and run timer for at least 60s.",
                isRequired = true,
                isCompleted = false
            ),
            TestMission(
                id = "m2",
                appId = "app_1",
                buildVersion = "v1.0.4-rc1",
                title = "Export Weekly Analytics PDF",
                instructions = "Go to Profile -> Analytics -> Export Report as PDF and verify share sheet opens.",
                isRequired = false,
                isCompleted = false
            ),
            TestMission(
                id = "m3",
                appId = "app_2",
                buildVersion = "v2.1.0-beta",
                title = "Create Biometric Security Backup",
                instructions = "Enable Fingerprint unlock and verify secret recovery QR renders cleanly.",
                isRequired = true,
                isCompleted = false
            ),
            TestMission(
                id = "m4",
                appId = "app_3",
                buildVersion = "v0.9.8-beta",
                title = "Trim a 10-second Voice Memo",
                instructions = "Record or import a short clip, apply Noise Cleanse, and preview playback.",
                isRequired = true,
                isCompleted = false
            )
        )
    )

    private val listingsState = MutableStateFlow(sampleListings)

    override fun getListings(): Flow<List<AppListing>> =
        listingsState.map { list -> list.sortedByDescending { it.discoveryScore } }

    override fun observeListingByIdOrPackage(idOrPackage: String): Flow<AppListing?> =
        listingsState.map { list ->
            list.find { it.id == idOrPackage || it.packageName == idOrPackage }
        }

    override suspend fun getListingById(id: String): AppListing? =
        listingsState.value.find { it.id == id || it.packageName == id }

    override suspend fun getListingByPackage(packageName: String): AppListing? =
        listingsState.value.find { it.packageName == packageName }

    override suspend fun createListing(listing: AppListing): AppListing {
        val created = listing.copy(
            id = if (listing.id.isBlank()) "app_${listingsState.value.size + 1}" else listing.id,
            createdAtIso = listing.createdAtIso.ifBlank { BuildGateClock.nowIso() }
        )
        listingsState.update { listOf(created) + it }
        return created
    }

    override fun getMissionsForApp(appId: String): Flow<List<TestMission>> =
        sampleMissions.map { list -> list.filter { it.appId == appId } }

    override suspend fun createMission(mission: TestMission) {
        sampleMissions.update { it + mission }
    }

    override suspend fun toggleMissionCompletion(missionId: String) {
        sampleMissions.update { list ->
            list.map { if (it.id == missionId) it.copy(isCompleted = !it.isCompleted) else it }
        }
    }
}
