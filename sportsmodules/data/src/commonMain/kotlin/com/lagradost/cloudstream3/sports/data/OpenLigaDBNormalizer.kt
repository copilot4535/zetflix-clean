package com.lagradost.cloudstream3.sports.data

import com.lagradost.cloudstream3.sports.data.remote.OLDBMatch
import com.lagradost.cloudstream3.sports.data.remote.OLDBTeam
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.MatchStatus
import com.lagradost.cloudstream3.sports.domain.models.Team
import kotlinx.datetime.Instant

object OpenLigaDBNormalizer {
    fun normalizeMatch(oldbMatch: OLDBMatch): Match {
        return Match(
            id = oldbMatch.matchId.toString(),
            homeTeam = normalizeTeam(oldbMatch.team1),
            awayTeam = normalizeTeam(oldbMatch.team2),
            homeScore = getHomeScore(oldbMatch),
            awayScore = getAwayScore(oldbMatch),
            startTime = Instant.parse(oldbMatch.matchDateTimeUTC),
            status = mapStatus(oldbMatch),
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

    private fun mapStatus(oldbMatch: OLDBMatch): MatchStatus {
        return if (oldbMatch.matchIsFinished) {
            MatchStatus.FINISHED
        } else {
            // Very basic heuristic for LIVE: 
            // In a real app we'd check if current time is > start time and < start time + 2h
            // OpenLigaDB doesn't have a specific "isLive" flag in the match object itself
            // but we can assume if it's not finished and started, it might be live.
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
}
