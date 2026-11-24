package dev.buildgate.app.entities.auth.repository

import dev.buildgate.app.entities.auth.model.AuthSession
import dev.buildgate.app.entities.auth.model.AuthUser
import dev.buildgate.app.entities.testerprofile.model.TesterProfile
import dev.buildgate.app.shared.platform.DeviceMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AuthRepository {
    fun getAuthSession(): Flow<AuthSession>
    suspend fun signInWithEmail(email: String, pass: String): AuthSession
    suspend fun signUpWithEmail(email: String, pass: String, username: String): AuthSession
    suspend fun switchUserRole(role: String)
    suspend fun signOut()
}

class InMemoryAuthRepository : AuthRepository {

    private val defaultProfile = TesterProfile(
        id = "tester_1",
        username = "AlexDeveloper",
        karmaPoints = 340,
        trustedScore = 4.9,
        registeredDevices = listOf(
            DeviceMetadata("Google Pixel 8 Pro", "Android 14", "1344x2992", "20:9", "Android Mobile")
        )
    )

    private val sessionState = MutableStateFlow(
        AuthSession(
            accessToken = "eyJhbGciOiJIUzI1Ni...mock_supabase_token",
            user = AuthUser(id = "tester_1", email = "alex@playdeveloper.com", role = "tester"),
            profile = defaultProfile,
            isLoggedIn = true
        )
    )

    override fun getAuthSession(): Flow<AuthSession> = sessionState.asStateFlow()

    override suspend fun signInWithEmail(email: String, pass: String): AuthSession {
        val user = AuthUser(id = "tester_1", email = email, role = "tester")
        val session = AuthSession(
            accessToken = "eyJhbGciOiJIUzI1Ni...mock_token",
            user = user,
            profile = defaultProfile.copy(username = email.substringBefore("@")),
            isLoggedIn = true
        )
        sessionState.value = session
        return session
    }

    override suspend fun signUpWithEmail(email: String, pass: String, username: String): AuthSession {
        val user = AuthUser(id = "user_${email.hashCode()}", email = email, role = "tester")
        val profile = TesterProfile(id = user.id, username = username)
        val session = AuthSession(
            accessToken = "eyJhbGciOiJIUzI1...new_token",
            user = user,
            profile = profile,
            isLoggedIn = true
        )
        sessionState.value = session
        return session
    }

    override suspend fun switchUserRole(role: String) {
        val current = sessionState.value
        if (current.user != null) {
            sessionState.value = current.copy(
                user = current.user.copy(role = role)
            )
        }
    }

    override suspend fun signOut() {
        sessionState.value = AuthSession(isLoggedIn = false)
    }
}
