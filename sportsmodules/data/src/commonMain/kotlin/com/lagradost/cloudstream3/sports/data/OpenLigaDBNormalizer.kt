package com.lagradost.cloudstream3.sports.data

import com.lagradost.cloudstream3.sports.data.remote.OLDBGoal
import com.lagradost.cloudstream3.sports.data.remote.OLDBMatch
import com.lagradost.cloudstream3.sports.data.remote.OLDBTeam
import com.lagradost.cloudstream3.sports.domain.models.Goal
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.MatchStatus
import com.lagradost.cloudstream3.sports.domain.models.Team
import kotlinx.datetime.Instant
import kotlin.time.Clock

object OpenLigaDBNormalizer {
    fun normalizeMatch(oldbMatch: OLDBMatch): Match {
        val startTime = Instant.parse(oldbMatch.matchDateTimeUTC)
        return Match(
            id = oldbMatch.matchId.toString(),
            homeTeam = normalizeTeam(oldbMatch.team1),
            awayTeam = normalizeTeam(oldbMatch.team2),
            homeScore = getHomeScore(oldbMatch),
            awayScore = getAwayScore(oldbMatch),
            startTime = startTime,
            status = mapStatus(oldbMatch, startTime),
            leagueId = oldbMatch.leagueShortcut ?: oldbMatch.leagueId?.toString() ?: "",
            leagueName = oldbMatch.leagueName,
            matchdayName = oldbMatch.group?.groupName,
            matchdayOrder = oldbMatch.group?.groupOrderId,
            goals = oldbMatch.goals.map { normalizeGoal(it) }.sortedBy { it.minute }
        )
    }

    private fun normalizeTeam(oldbTeam: OLDBTeam): Team {
        return Team(
            id = oldbTeam.teamId.toString(),
            name = oldbTeam.teamName,
            logoUrl = oldbTeam.teamIconUrl
        )
    }

    private fun normalizeGoal(oldbGoal: OLDBGoal): Goal {
        return Goal(
            id = oldbGoal.goalId.toString(),
            scorerName = oldbGoal.goalGetterName ?: "Unknown",
            minute = oldbGoal.matchMinute ?: 0,
            scoreHome = oldbGoal.scoreTeam1,
            scoreAway = oldbGoal.scoreTeam2,
            isPenalty = oldbGoal.isPenalty ?: false,
            isOwnGoal = oldbGoal.isOwnGoal ?: false
        )
    }

    private fun mapStatus(oldbMatch: OLDBMatch, startTime: Instant): MatchStatus {
        if (oldbMatch.matchIsFinished) return MatchStatus.FINISHED

        val nowMs = Clock.System.now().toEpochMilliseconds()
        val startMs = startTime.toEpochMilliseconds()

        // Heuristic: LIVE if started and within 120 minutes (7,200,000 ms) of start time
        return if (nowMs >= startMs && nowMs < (startMs + 7_200_000)) {
            MatchStatus.LIVE
        } else {
            MatchStatus.UPCOMING
        }
    }

    private fun getHomeScore(oldbMatch: OLDBMatch): Int? {
        return oldbMatch.matchResults.find { it.resultTypeId == 2 }?.pointsTeam1
            ?: oldbMatch.goals.lastOrNull()?.scoreTeam1
    }

    private fun getAwayScore(oldbMatch: OLDBMatch): Int? {
        return oldbMatch.matchResults.find { it.resultTypeId == 2 }?.pointsTeam2
            ?: oldbMatch.goals.lastOrNull()?.scoreTeam2
    }

    fun normalizeLeague(oldbLeague: com.lagradost.cloudstream3.sports.data.remote.OLDBLeague): com.lagradost.cloudstream3.sports.domain.models.League {
        return com.lagradost.cloudstream3.sports.domain.models.League(
            id = oldbLeague.leagueId.toString(),
            name = oldbLeague.leagueName,
            shortcut = oldbLeague.leagueShortcut,
            season = oldbLeague.leagueSeason,
            sportId = "1" // Default to football
        )
    }

    fun normalizeStanding(oldbStanding: com.lagradost.cloudstream3.sports.data.remote.OLDBStanding, position: Int): com.lagradost.cloudstream3.sports.domain.models.Standing {
        return com.lagradost.cloudstream3.sports.domain.models.Standing(
            position = position,
            teamId = oldbStanding.teamId.toString(),
            teamName = oldbStanding.teamName,
            teamLogoUrl = oldbStanding.teamIconUrl,
            played = oldbStanding.matches,
            won = oldbStanding.won,
            drawn = oldbStanding.draw,
            lost = oldbStanding.lost,
            goalsFor = oldbStanding.goals,
            goalsAgainst = oldbStanding.opponentGoals,
            goalDifference = oldbStanding.goalDiff,
            points = oldbStanding.points
        )
    }

    fun normalizeGroup(oldbGroup: com.lagradost.cloudstream3.sports.data.remote.OLDBGroup, leagueShortcut: String, season: String): com.lagradost.cloudstream3.sports.domain.models.Matchday {
        return com.lagradost.cloudstream3.sports.domain.models.Matchday(
            id = oldbGroup.groupId.toString(),
            name = oldbGroup.groupName,
            order = oldbGroup.groupOrderId,
            leagueShortcut = leagueShortcut,
            season = season
        )
    }
}
