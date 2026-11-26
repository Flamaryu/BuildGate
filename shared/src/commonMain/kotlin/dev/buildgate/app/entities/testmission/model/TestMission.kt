package dev.buildgate.app.entities.testmission.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TestMission(
    val id: String,
    @SerialName("app_id") val appId: String,
    @SerialName("build_version") val buildVersion: String,
    val title: String,
    val instructions: String,
    @SerialName("is_required") val isRequired: Boolean = true,
    val isCompleted: Boolean = false
)
