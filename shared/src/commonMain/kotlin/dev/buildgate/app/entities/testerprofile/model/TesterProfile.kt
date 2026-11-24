package dev.buildgate.app.entities.testerprofile.model

import dev.buildgate.app.shared.platform.DeviceMetadata
import kotlinx.serialization.Serializable

@Serializable
data class TesterProfile(
    val id: String,
    val username: String,
    val karmaPoints: Int = 120,
    val trustedScore: Double = 4.8,
    val registeredDevices: List<DeviceMetadata> = emptyList()
)
