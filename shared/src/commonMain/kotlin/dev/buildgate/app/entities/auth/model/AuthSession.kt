package dev.buildgate.app.entities.auth.model

import dev.buildgate.app.entities.testerprofile.model.TesterProfile
import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String,
    val email: String,
    val isGoogleLinked: Boolean = false
)

@Serializable
data class AuthSession(
    val accessToken: String? = null,
    val user: AuthUser? = null,
    val profile: TesterProfile? = null,
    val isLoggedIn: Boolean = false,
    val isDemoMode: Boolean = true
)
