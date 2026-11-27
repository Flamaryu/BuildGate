package dev.buildgate.app.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class TopLevelTab(
    val title: String,
    val icon: String
) {
    DISCOVERY("Discover", "🔍"),
    DAILY_QUEUE("Daily Queue", "▶️"),
    TRIAGE_BOARD("QA & Triage", "📊"),
    PUBLISH_APP("Publish App", "🚀"),
    ACCOUNT("Account", "👤")
}

@Serializable
sealed interface BuildGateRoute {
    @Serializable
    data object Discovery : BuildGateRoute

    @Serializable
    data object DailyQueue : BuildGateRoute

    @Serializable
    data class AppCampaign(val appIdOrPackage: String) : BuildGateRoute

    @Serializable
    data class TriageBoard(val appId: String = "app_1") : BuildGateRoute

    @Serializable
    data class ProductionPacket(val appId: String) : BuildGateRoute

    @Serializable
    data object PublishApp : BuildGateRoute

    @Serializable
    data class EditApp(val appId: String) : BuildGateRoute

    @Serializable
    data object Account : BuildGateRoute
}

sealed interface IncomingDeepLink {
    data class Navigate(val route: BuildGateRoute) : IncomingDeepLink
    data class VerificationCallback(
        val sessionToken: String,
        val proof: String?
    ) : IncomingDeepLink
    data class AuthCallback(val rawUri: String) : IncomingDeepLink
}

object DeepLinkRouter {
    private val _incomingLinks = MutableStateFlow<IncomingDeepLink?>(null)
    val incomingLinks: StateFlow<IncomingDeepLink?> = _incomingLinks.asStateFlow()

    fun dispatchUri(rawUri: String) {
        val parsed = parseUri(rawUri) ?: return
        _incomingLinks.value = parsed
    }

    fun consume() {
        _incomingLinks.value = null
    }

    fun buildShareableCampaignUrl(packageName: String): String =
        "https://buildgatehub.com/app/$packageName"

    fun buildAndroidDeepLinkUrl(packageName: String): String =
        "buildgate://app/$packageName"

    fun toWebHash(route: BuildGateRoute): String = when (route) {
        BuildGateRoute.Discovery -> "#/"
        BuildGateRoute.DailyQueue -> "#/queue"
        is BuildGateRoute.AppCampaign -> "#/app/${route.appIdOrPackage}"
        is BuildGateRoute.TriageBoard -> "#/triage/${route.appId}"
        is BuildGateRoute.ProductionPacket -> "#/packet/${route.appId}"
        BuildGateRoute.PublishApp -> "#/publish"
        is BuildGateRoute.EditApp -> "#/edit/${route.appId}"
        BuildGateRoute.Account -> "#/account"
    }

    fun parseUri(rawUri: String): IncomingDeepLink? {
        val trimmed = rawUri.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.contains("auth/callback") || trimmed.contains("code=") || trimmed.contains("access_token=")) {
            return IncomingDeepLink.AuthCallback(trimmed)
        }

        val normalized = trimmed
            .removePrefix("https://buildgatehub.com")
            .removePrefix("http://buildgatehub.com")
            .removePrefix("buildgate://")
            .let { if (it.contains("#")) it.substringAfter("#") else it }
            .trimStart('/')

        val pathPart = normalized.substringBefore("?")
        val queryPart = normalized.substringAfter("?", "")
        val queryParams = queryPart
            .split("&")
            .filter { it.contains("=") }
            .associate {
                val k = it.substringBefore("=")
                val v = it.substringAfter("=")
                k to v
            }

        if (pathPart.startsWith("verify-callback")) {
            val token = queryParams["nonce"] ?: queryParams["token"] ?: return null
            return IncomingDeepLink.VerificationCallback(
                sessionToken = token,
                proof = queryParams["proof"]
            )
        }

        val segments = pathPart.split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) return IncomingDeepLink.Navigate(BuildGateRoute.Discovery)

        return when (segments[0]) {
            "queue" -> IncomingDeepLink.Navigate(BuildGateRoute.DailyQueue)
            "publish" -> IncomingDeepLink.Navigate(BuildGateRoute.PublishApp)
            "edit" -> segments.getOrNull(1)?.let {
                IncomingDeepLink.Navigate(BuildGateRoute.EditApp(it))
            }
            "account" -> IncomingDeepLink.Navigate(BuildGateRoute.Account)
            "app" -> segments.getOrNull(1)?.let {
                IncomingDeepLink.Navigate(BuildGateRoute.AppCampaign(it))
            }
            "triage" -> IncomingDeepLink.Navigate(
                BuildGateRoute.TriageBoard(segments.getOrNull(1) ?: "app_1")
            )
            "packet" -> segments.getOrNull(1)?.let {
                IncomingDeepLink.Navigate(BuildGateRoute.ProductionPacket(it))
            }
            else -> null
        }
    }
}
