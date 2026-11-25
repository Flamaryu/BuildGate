package dev.buildgate.app.entities.applisting.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("created_at") val createdAtIso: String = ""
) {
    val hasDeepLinkHandshake: Boolean
        get() = !deepLinkScheme.isNullOrBlank()

    val discoveryScore: Int
        get() = (ownerKarmaBalance * 2) + (activeTestersCount * 5)
}
