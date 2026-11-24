package dev.buildgate.app.shared.platform

import kotlinx.serialization.Serializable

@Serializable
data class DeviceMetadata(
    val model: String,
    val osVersion: String,
    val screenResolution: String,
    val aspectRatio: String,
    val platformName: String
)
