package dev.buildgate.app.entities.testmission.model

import kotlinx.serialization.Serializable

@Serializable
data class TestMission(
    val id: String,
    val appId: String,
    val buildVersion: String,
    val title: String,
    val instructions: String,
    val isRequired: Boolean = true,
    val isCompleted: Boolean = false
)
