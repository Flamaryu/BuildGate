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
    suspend fun updateListing(listing: AppListing): AppListing
    fun getMissionsForApp(appId: String): Flow<List<TestMission>>
    suspend fun createMission(mission: TestMission)
    suspend fun toggleMissionCompletion(missionId: String)
}

class DemoAppListingRepository : AppListingRepository {

    private val sampleListings = listOf(
        AppListing(
            id = "app_1",
            ownerId = "tester_1",
            packageName = "com.mkdinteractive.shatterhorde",
            title = "Shatter Horde: Arena Survivor",
            shortSummary = "Fast-paced top-down arena survivor action game with escalating waves, build synergies, and boss encounters.",
            playStoreOptInUrl = "https://play.google.com/apps/testing/com.mkdinteractive.shatterhorde",
            googleGroupUrl = "https://groups.google.com/g/mkd-interactive",
            deepLinkScheme = null,
            currentBuildVersion = "v1.6.0",
            minVersionCode = 7L,
            targetDays = 14,
            targetTesters = 16,
            activeTestersCount = 14,
            completedTestersCount = 4,
            ownerKarmaBalance = 340,
            category = "Action · Arena Survivor",
            createdAtIso = "2026-11-12T10:00:00Z"
        ),
        AppListing(
            id = "app_2",
            ownerId = "tester_1",
            packageName = "com.cozybento.bento_puzzle",
            title = "Bento Neko",
            shortSummary = "Cozy cat-themed bento box puzzle game where you arrange adorable dishes to fit every compartment.",
            playStoreOptInUrl = "https://play.google.com/apps/testing/com.cozybento.bento_puzzle",
            googleGroupUrl = "https://groups.google.com/g/mkd-interactive",
            deepLinkScheme = null,
            currentBuildVersion = "v1.3.1",
            minVersionCode = 7L,
            targetDays = 14,
            targetTesters = 16,
            activeTestersCount = 12,
            completedTestersCount = 3,
            ownerKarmaBalance = 340,
            category = "Puzzle · Cozy",
            createdAtIso = "2026-11-14T10:00:00Z"
        ),
        AppListing(
            id = "app_3",
            ownerId = "tester_1",
            packageName = "com.spacefold.game",
            title = "Spacefold",
            shortSummary = "Sci-fi spatial game built around folding space, navigating cosmic obstacles, and mastering precision movement.",
            playStoreOptInUrl = "https://play.google.com/apps/testing/com.spacefold.game",
            googleGroupUrl = "https://groups.google.com/g/mkd-interactive",
            deepLinkScheme = null,
            currentBuildVersion = "v1.3.0",
            minVersionCode = 8L,
            targetDays = 14,
            targetTesters = 16,
            activeTestersCount = 11,
            completedTestersCount = 2,
            ownerKarmaBalance = 340,
            category = "Arcade · Sci-Fi",
            createdAtIso = "2026-11-15T10:00:00Z"
        )
    )

    private val sampleMissions = MutableStateFlow(
        listOf(
            TestMission(
                id = "m1",
                appId = "app_1",
                buildVersion = "v1.6.0",
                title = "Survive 3 Waves & Test Upgrades",
                instructions = "Start an arena run, level up at least twice, select weapon/perk upgrades, and verify HUD & frame rate stay smooth.",
                isRequired = true,
                isCompleted = false
            ),
            TestMission(
                id = "m2",
                appId = "app_1",
                buildVersion = "v1.6.0",
                title = "Pause, Resume & Audio Recovery",
                instructions = "Pause mid-wave, open the floating QA pill briefly, then resume gameplay and verify audio and wave timers recover cleanly.",
                isRequired = false,
                isCompleted = false
            ),
            TestMission(
                id = "m3",
                appId = "app_2",
                buildVersion = "v1.3.1",
                title = "Complete 2 Bento Puzzles",
                instructions = "Drag, rotate, and snap bento dishes into place across two stages and verify touch accuracy near screen edges.",
                isRequired = true,
                isCompleted = false
            ),
            TestMission(
                id = "m4",
                appId = "app_2",
                buildVersion = "v1.3.1",
                title = "Test Undo & Stage Reset",
                instructions = "Trigger undo and reset mid-puzzle to confirm pieces and animations clear without overlapping.",
                isRequired = false,
                isCompleted = false
            ),
            TestMission(
                id = "m5",
                appId = "app_3",
                buildVersion = "v1.3.0",
                title = "Play a 60s Run & Verify Controls",
                instructions = "Start a Spacefold run, test spatial folding/movement controls for at least 60 seconds, and check collision responsiveness.",
                isRequired = true,
                isCompleted = false
            ),
            TestMission(
                id = "m6",
                appId = "app_3",
                buildVersion = "v1.3.0",
                title = "Verify Menu & Retry Transitions",
                instructions = "Cycle between main menu, active run, and retry screen to ensure no UI clipping or stutter.",
                isRequired = false,
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

    override suspend fun updateListing(listing: AppListing): AppListing {
        listingsState.update { list ->
            list.map { existing ->
                if (existing.id == listing.id || existing.packageName == listing.packageName) {
                    existing.copy(
                        title = listing.title,
                        shortSummary = listing.shortSummary,
                        playStoreOptInUrl = listing.playStoreOptInUrl,
                        googleGroupUrl = listing.googleGroupUrl,
                        deepLinkScheme = listing.deepLinkScheme,
                        currentBuildVersion = listing.currentBuildVersion,
                        minVersionCode = listing.minVersionCode,
                        targetDays = listing.targetDays,
                        targetTesters = listing.targetTesters,
                        category = listing.category
                    )
                } else existing
            }
        }
        return listingsState.value.find { it.id == listing.id || it.packageName == listing.packageName } ?: listing
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
