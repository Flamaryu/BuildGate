package dev.buildgate.app.entities.applisting.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AppBoostType(
    val title: String,
    val costKarma: Int,
    val description: String,
    val icon: String
) {
    FEATURED_SPOTLIGHT(
        title = "48h Featured Spotlight",
        costKarma = 100,
        description = "Pins your app (+500 Discover score) for 48 hours to fill your roster faster.",
        icon = "🚀"
    ),
    PRIORITY_BOUNTY_POOL(
        title = "2× Priority Bug Bounty Pool",
        costKarma = 50,
        description = "Doubles bug bounties from +25⚡ to +50⚡ for the next 2 accepted reports.",
        icon = "🎯"
    ),
    BUILD_RETEST_PING(
        title = "New Build Re-Test Ping",
        costKarma = 30,
        description = "Alerts enrolled testers for 24h that a new build is ready (+5⚡ Early Upgrader bonus).",
        icon = "📲"
    ),
    VETERAN_WAVE(
        title = "72h Veteran Tester Wave",
        costKarma = 75,
        description = "Prioritizes your app for 4.5★+ veteran testers with completed 14-day streaks.",
        icon = "⭐"
    )
}

@Serializable
data class AppListing(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("package_name") val packageName: String,
    val title: String,
    @SerialName("short_summary") val shortSummary: String = "Closed testing campaign on BuildGate",
    @SerialName("play_store_opt_in_url") val playStoreOptInUrl: String,
    @SerialName("google_group_url") val googleGroupUrl: String,
    @SerialName("deep_link_scheme") val deepLinkScheme: String? = null,
    @SerialName("current_build_version") val currentBuildVersion: String = "1.0.0",
    @SerialName("min_version_code") val minVersionCode: Long = 1L,
    @SerialName("target_days") val targetDays: Int = 14,
    @SerialName("target_testers") val targetTesters: Int = 16,
    @SerialName("active_testers_count") val activeTestersCount: Int = 0,
    @SerialName("completed_testers_count") val completedTestersCount: Int = 0,
    @SerialName("owner_karma_balance") val ownerKarmaBalance: Int = 120,
    val category: String = "Productivity",
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("created_at") val createdAtIso: String = "",
    @SerialName("featured_until") val featuredUntilIso: String? = null,
    @SerialName("priority_bounty_remaining") val priorityBountyRemaining: Int = 0,
    @SerialName("retest_ping_until") val retestPingUntilIso: String? = null,
    @SerialName("veteran_wave_until") val veteranWaveUntilIso: String? = null
) {
    val hasDeepLinkHandshake: Boolean
        get() = !deepLinkScheme.isNullOrBlank()

    val isFeaturedSpotlight: Boolean
        get() = !featuredUntilIso.isNullOrBlank()

    val discoveryScore: Int
        get() = (ownerKarmaBalance * 2) + (activeTestersCount * 5) + if (isFeaturedSpotlight) 500 else 0
}
