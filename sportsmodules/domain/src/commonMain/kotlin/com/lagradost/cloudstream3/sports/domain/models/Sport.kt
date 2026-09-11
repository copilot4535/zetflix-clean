package com.lagradost.cloudstream3.sports.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Sport(
    val id: String,
    val name: String,
    val slug: String,
    val iconUrl: String? = null
)
