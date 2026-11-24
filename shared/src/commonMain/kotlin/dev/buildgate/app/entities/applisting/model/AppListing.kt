package dev.buildgate.app.entities.applisting.model

import kotlinx.serialization.Serializable

@Serializable
data class AppListing(
    val id: String,
    val ownerId: String,
    val packageName: String,
    val title: String,
    val playStoreOptInUrl: String,
    val googleGroupUrl: String,
    val targetDays: Int = 14,
    val activeTestersCount: Int = 0,
    val category: String = "Productivity",
    val iconUrl: String? = null
)
