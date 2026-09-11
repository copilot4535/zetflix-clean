package com.lagradost.cloudstream3.sports.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Matchday(
    val id: String,
    val name: String,
    val order: Int,
    val leagueShortcut: String,
    val season: String
)
