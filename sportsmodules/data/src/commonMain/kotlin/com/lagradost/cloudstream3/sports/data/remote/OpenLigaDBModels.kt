package com.lagradost.cloudstream3.sports.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OLDBMatch(
    @SerialName("matchID") val matchId: Int,
    @SerialName("matchDateTimeUTC") val matchDateTimeUTC: String,
    @SerialName("leagueId") val leagueId: Int? = null,
    @SerialName("leagueShortcut") val leagueShortcut: String? = null,
    @SerialName("team1") val team1: OLDBTeam,
    @SerialName("team2") val team2: OLDBTeam,
    @SerialName("matchIsFinished") val matchIsFinished: Boolean,
    @SerialName("matchResults") val matchResults: List<OLDBMatchResult> = emptyList(),
    @SerialName("goals") val goals: List<OLDBGoal> = emptyList()
)

@Serializable
data class OLDBTeam(
    @SerialName("teamId") val teamId: Int,
    @SerialName("teamName") val teamName: String,
    @SerialName("shortName") val shortName: String? = null,
    @SerialName("teamIconUrl") val teamIconUrl: String? = null
)

@Serializable
data class OLDBMatchResult(
    @SerialName("resultID") val resultId: Int,
    @SerialName("resultName") val resultName: String,
    @SerialName("pointsTeam1") val pointsTeam1: Int,
    @SerialName("pointsTeam2") val pointsTeam2: Int,
    @SerialName("resultOrderID") val resultOrderId: Int,
    @SerialName("resultTypeID") val resultTypeId: Int
)

@Serializable
data class OLDBGoal(
    @SerialName("goalID") val goalId: Int,
    @SerialName("scoreTeam1") val scoreTeam1: Int,
    @SerialName("scoreTeam2") val scoreTeam2: Int,
    @SerialName("matchMinute") val matchMinute: Int? = null,
    @SerialName("goalGetterName") val goalGetterName: String? = null,
    @SerialName("isPenalty") val isPenalty: Boolean? = null,
    @SerialName("isOwnGoal") val isOwnGoal: Boolean? = null
)

@Serializable
data class OLDBLeague(
    @SerialName("leagueId") val leagueId: Int,
    @SerialName("leagueName") val leagueName: String,
    @SerialName("leagueShortcut") val leagueShortcut: String,
    @SerialName("leagueSeason") val leagueSeason: String? = null
)

@Serializable
data class OLDBStanding(
    @SerialName("teamId") val teamId: Int,
    @SerialName("teamName") val teamName: String,
    @SerialName("shortName") val shortName: String? = null,
    @SerialName("teamIconUrl") val teamIconUrl: String? = null,
    @SerialName("points") val points: Int,
    @SerialName("opponentGoals") val opponentGoals: Int,
    @SerialName("goals") val goals: Int,
    @SerialName("matches") val matches: Int,
    @SerialName("won") val won: Int,
    @SerialName("lost") val lost: Int,
    @SerialName("draw") val draw: Int,
    @SerialName("goalDiff") val goalDiff: Int
)
