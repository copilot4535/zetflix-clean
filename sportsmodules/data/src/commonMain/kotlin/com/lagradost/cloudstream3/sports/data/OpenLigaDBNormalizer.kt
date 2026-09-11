package com.lagradost.cloudstream3.sports.data

import com.lagradost.cloudstream3.sports.data.remote.OLDBMatch
import com.lagradost.cloudstream3.sports.data.remote.OLDBTeam
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
            leagueId = oldbMatch.leagueShortcut ?: oldbMatch.leagueId?.toString() ?: ""
        )
    }

    private fun normalizeTeam(oldbTeam: OLDBTeam): Team {
        return Team(
            id = oldbTeam.teamId.toString(),
            name = oldbTeam.teamName,
            logoUrl = oldbTeam.teamIconUrl
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
            sportId = "1" // Default to football
        )
    }
}
