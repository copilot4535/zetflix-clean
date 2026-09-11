package com.lagradost.cloudstream3.sports.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class League(
    val id: String,
    val name: String,
    val country: String? = null,
    val logoUrl: String? = null,
    val sportId: String
)
