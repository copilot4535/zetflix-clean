package com.lagradost.cloudstream3.sports.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Standing(
    val position: Int,
    val teamId: String,
    val teamName: String,
    val teamLogoUrl: String? = null,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int
)
