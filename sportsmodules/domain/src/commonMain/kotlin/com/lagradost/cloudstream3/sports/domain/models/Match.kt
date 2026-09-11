package com.lagradost.cloudstream3.sports.domain.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class Match(
    val id: String,
    val homeTeam: Team,
    val awayTeam: Team,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val startTime: Instant,
    val status: MatchStatus,
    val leagueId: String
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val logoUrl: String? = null
)

@Serializable
enum class MatchStatus {
    UPCOMING,
    LIVE,
    FINISHED,
    CANCELLED,
    POSTPONED
}
