package dev.buildgate.app.entities.applisting.repository

import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.testmission.model.TestMission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppListingRepository {
    fun getListings(): Flow<List<AppListing>>
    suspend fun getListingById(id: String): AppListing?
    fun getMissionsForApp(appId: String): Flow<List<TestMission>>
    suspend fun createMission(mission: TestMission)
    suspend fun toggleMissionCompletion(missionId: String)
}

class InMemoryAppListingRepository : AppListingRepository {

    private val sampleListings = listOf(
        AppListing(
            id = "app_1",
            ownerId = "owner_1",
            packageName = "io.focuspulse.habittracker",
            title = "FocusPulse Pro - Habit Tracker",
            playStoreOptInUrl = "https://play.google.com/apps/testing/io.focuspulse.habittracker",
            googleGroupUrl = "https://groups.google.com/g/focuspulse-beta-testers",
            targetDays = 14,
            activeTestersCount = 18,
            category = "Productivity & Habits"
        ),
        AppListing(
            id = "app_2",
            ownerId = "owner_2",
            packageName = "com.devhub.cryptowallet.beta",
            title = "VaultPay Multi-Chain Wallet",
            playStoreOptInUrl = "https://play.google.com/apps/testing/com.devhub.cryptowallet.beta",
            googleGroupUrl = "https://groups.google.com/g/vaultpay-testers",
            targetDays = 14,
            activeTestersCount = 23,
            category = "Finance"
        ),
        AppListing(
            id = "app_3",
            ownerId = "owner_1",
            packageName = "com.soundscape.audio.studio",
            title = "SoundScape AI Audio Editor",
            playStoreOptInUrl = "https://play.google.com/apps/testing/com.soundscape.audio.studio",
            googleGroupUrl = "https://groups.google.com/g/soundscape-early-access",
            targetDays = 14,
            activeTestersCount = 12,
            category = "Music & Audio"
        )
    )

    private val sampleMissions = MutableStateFlow(
        listOf(
            TestMission(
                id = "m1",
                appId = "app_1",
                buildVersion = "v1.0.4-rc1",
                title = "Create 1 Focus Habit",
                instructions = "Tap '+' icon, select 25-min Pomodoro habit, and start timer for at least 1 min.",
                isRequired = true,
                isCompleted = false
            ),
            TestMission(
                id = "m2",
                appId = "app_1",
                buildVersion = "v1.0.4-rc1",
                title = "Export Weekly Analytics PDF",
                instructions = "Go to Profile -> Analytics -> Export Report as PDF.",
                isRequired = false,
                isCompleted = false
            ),
            TestMission(
                id = "m3",
                appId = "app_2",
                buildVersion = "v2.1.0-beta",
                title = "Create Biometric Security Backup",
                instructions = "Enable Fingerprint unlock and export secret recovery QR.",
                isRequired = true,
                isCompleted = false
            )
        )
    )

    private val listingsState = MutableStateFlow(sampleListings)

    override fun getListings(): Flow<List<AppListing>> = listingsState.asStateFlow()

    override suspend fun getListingById(id: String): AppListing? {
        return listingsState.value.find { it.id == id }
    }

    override fun getMissionsForApp(appId: String): Flow<List<TestMission>> {
        val flow = MutableStateFlow(sampleMissions.value.filter { it.appId == appId })
        return flow.asStateFlow()
    }

    override suspend fun createMission(mission: TestMission) {
        sampleMissions.value = sampleMissions.value + mission
    }

    override suspend fun toggleMissionCompletion(missionId: String) {
        sampleMissions.value = sampleMissions.value.map {
            if (it.id == missionId) it.copy(isCompleted = !it.isCompleted) else it
        }
    }
}
