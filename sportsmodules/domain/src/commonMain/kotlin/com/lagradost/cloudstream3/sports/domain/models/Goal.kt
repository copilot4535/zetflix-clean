package com.lagradost.cloudstream3.sports.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Goal(
    val id: String,
    val scorerName: String,
    val minute: Int,
    val scoreHome: Int,
    val scoreAway: Int,
    val isPenalty: Boolean = false,
    val isOwnGoal: Boolean = false
)
