package dev.buildgate.app.entities.auth.repository

import dev.buildgate.app.entities.auth.model.AuthSession
import dev.buildgate.app.entities.auth.model.AuthUser
import dev.buildgate.app.entities.testerprofile.model.KarmaEntry
import dev.buildgate.app.entities.testerprofile.model.KarmaReason
import dev.buildgate.app.entities.testerprofile.model.TesterProfile
import dev.buildgate.app.shared.platform.DeviceMetadata
import dev.buildgate.app.shared.time.BuildGateClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

interface AuthRepository {
    fun getAuthSession(): Flow<AuthSession>
    fun getKarmaLedger(userId: String): Flow<List<KarmaEntry>>
    suspend fun signInWithGoogle(): Result<AuthSession>
    suspend fun signInWithEmail(email: String, pass: String): Result<AuthSession>
    suspend fun signUpWithEmail(email: String, pass: String, username: String): Result<AuthSession>
    suspend fun registerCurrentDevice(device: DeviceMetadata)
    suspend fun awardKarma(userId: String, delta: Int, reason: KarmaReason, appId: String? = null, note: String = "")
    suspend fun handleAuthCallbackUri(uri: String): Boolean
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
}

class DemoAuthRepository : AuthRepository {

    private val defaultProfile = TesterProfile(
        id = "tester_1",
        username = "AlexDeveloper",
        googleEmail = "alex.dev@gmail.com",
        karmaPoints = 340,
        trustedScore = 4.95,
        completedCampaignsCount = 2,
        registeredDevices = listOf(
            DeviceMetadata("Google Pixel 8 Pro", "Android 15 (API 35)", "1344x2992", "20:9", "Android Mobile")
        )
    )

    private val profilesById = MutableStateFlow(
        mapOf("tester_1" to defaultProfile)
    )

    private val ledgerState = MutableStateFlow(
        listOf(
            KarmaEntry("k_1", "tester_1", 50, KarmaReason.SIGNUP_WELCOME, note = "Welcome to BuildGate", createdAtIso = "2026-11-10T09:00:00Z"),
            KarmaEntry("k_2", "tester_1", 100, KarmaReason.DAY_14_COMPLETION_BONUS, appId = "app_3", note = "Completed 14-day test for Spacefold", createdAtIso = "2026-11-20T18:00:00Z"),
            KarmaEntry("k_3", "tester_1", 25, KarmaReason.TRIAGED_BUG_BOUNTY, appId = "app_1", note = "Verified Wave 3 frame dip on Shatter Horde", createdAtIso = "2026-11-22T14:15:00Z")
        )
    )

    private val sessionState = MutableStateFlow(
        AuthSession(
            accessToken = "demo_local_session_token",
            user = AuthUser(id = "tester_1", email = "alex.dev@gmail.com", isGoogleLinked = true),
            profile = defaultProfile,
            isLoggedIn = true,
            isDemoMode = true
        )
    )

    override fun getAuthSession(): Flow<AuthSession> = sessionState.asStateFlow()

    override fun getKarmaLedger(userId: String): Flow<List<KarmaEntry>> =
        ledgerState.map { list -> list.filter { it.userId == userId } }

    override suspend fun signInWithGoogle(): Result<AuthSession> {
        val profile = profilesById.value["tester_1"] ?: defaultProfile
        val session = AuthSession(
            accessToken = "demo_google_oauth_token",
            user = AuthUser(id = profile.id, email = profile.googleEmail.ifBlank { "alex.dev@gmail.com" }, isGoogleLinked = true),
            profile = profile,
            isLoggedIn = true,
            isDemoMode = true
        )
        sessionState.value = session
        return Result.success(session)
    }

    override suspend fun signInWithEmail(email: String, pass: String): Result<AuthSession> {
        if (email.isBlank() || pass.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password are required"))
        }
        val cleanEmail = email.trim()
        val id = if (cleanEmail.equals("alex.dev@gmail.com", ignoreCase = true) || cleanEmail.equals("alex@playdeveloper.com", ignoreCase = true)) {
            "tester_1"
        } else {
            "user_${cleanEmail.lowercase().hashCode().toUInt()}"
        }
        val existing = profilesById.value[id] ?: TesterProfile(
            id = id,
            username = cleanEmail.substringBefore("@"),
            googleEmail = cleanEmail,
            karmaPoints = 50
        )
        profilesById.update { it + (id to existing) }
        val session = AuthSession(
            accessToken = "demo_email_token",
            user = AuthUser(id = id, email = cleanEmail, isGoogleLinked = cleanEmail.endsWith("@gmail.com")),
            profile = existing,
            isLoggedIn = true,
            isDemoMode = true
        )
        sessionState.value = session
        return Result.success(session)
    }

    override suspend fun signUpWithEmail(email: String, pass: String, username: String): Result<AuthSession> {
        if (email.isBlank() || pass.length < 6 || username.isBlank()) {
            return Result.failure(IllegalArgumentException("Provide valid email, username, and 6+ char password"))
        }
        val cleanEmail = email.trim()
        val id = "user_${cleanEmail.lowercase().hashCode().toUInt()}"
        val profile = TesterProfile(
            id = id,
            username = username.trim(),
            googleEmail = cleanEmail,
            karmaPoints = 50
        )
        profilesById.update { it + (id to profile) }
        ledgerState.update {
            listOf(
                KarmaEntry(
                    id = "k_${it.size + 1}",
                    userId = id,
                    delta = 50,
                    reason = KarmaReason.SIGNUP_WELCOME,
                    note = "Starter Karma credit",
                    createdAtIso = BuildGateClock.nowIso()
                )
            ) + it
        }
        val session = AuthSession(
            accessToken = "demo_signup_token",
            user = AuthUser(id = id, email = cleanEmail, isGoogleLinked = cleanEmail.endsWith("@gmail.com")),
            profile = profile,
            isLoggedIn = true,
            isDemoMode = true
        )
        sessionState.value = session
        return Result.success(session)
    }

    override suspend fun registerCurrentDevice(device: DeviceMetadata) {
        val current = sessionState.value
        val profile = current.profile ?: return
        if (profile.registeredDevices.any { it.model == device.model && it.osVersion == device.osVersion }) return
        val updatedProfile = profile.copy(registeredDevices = profile.registeredDevices + device)
        profilesById.update { it + (profile.id to updatedProfile) }
        sessionState.value = current.copy(profile = updatedProfile)
    }

    override suspend fun awardKarma(userId: String, delta: Int, reason: KarmaReason, appId: String?, note: String) {
        val entry = KarmaEntry(
            id = "k_${ledgerState.value.size + 1}",
            userId = userId,
            delta = delta,
            reason = reason,
            appId = appId,
            note = note,
            createdAtIso = BuildGateClock.nowIso()
        )
        ledgerState.update { listOf(entry) + it }

        val targetProfile = profilesById.value[userId]
        if (targetProfile != null) {
            val completedBump = if (reason == KarmaReason.DAY_14_COMPLETION_BONUS) 1 else 0
            val updated = targetProfile.copy(
                karmaPoints = (targetProfile.karmaPoints + delta).coerceAtLeast(0),
                completedCampaignsCount = targetProfile.completedCampaignsCount + completedBump
            )
            profilesById.update { it + (userId to updated) }
            if (sessionState.value.user?.id == userId) {
                sessionState.update { it.copy(profile = updated) }
            }
        }
    }

    override suspend fun handleAuthCallbackUri(uri: String): Boolean = false

    override suspend fun signOut() {
        sessionState.value = AuthSession(isLoggedIn = false, isDemoMode = true)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val uid = sessionState.value.user?.id
        if (uid != null) {
            profilesById.update { it - uid }
            ledgerState.update { list -> list.filterNot { it.userId == uid } }
        }
        sessionState.value = AuthSession(isLoggedIn = false, isDemoMode = true)
        return Result.success(Unit)
    }
}
