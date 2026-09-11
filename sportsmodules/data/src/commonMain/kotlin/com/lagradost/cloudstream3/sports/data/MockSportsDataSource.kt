package com.lagradost.cloudstream3.sports.data

import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.MatchStatus
import com.lagradost.cloudstream3.sports.domain.models.Sport
import com.lagradost.cloudstream3.sports.domain.models.Team
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

object MockSportsDataSource {
    fun getSports(): List<Sport> = listOf(
        Sport("1", "Football", "football"),
        Sport("2", "Basketball", "basketball"),
        Sport("3", "Tennis", "tennis")
    )

    fun getLiveMatches(): List<Match> = listOf(
        Match(
            id = "m1",
            homeTeam = Team("t1", "Arsenal", null),
            awayTeam = Team("t2", "Chelsea", null),
            homeScore = 2,
            awayScore = 1,
            startTime = kotlinx.datetime.Instant.parse("2026-09-11T12:00:00Z"),
            status = MatchStatus.LIVE,
            leagueId = "l1"
        )
    )

    fun getFixtures(): List<Match> = listOf(
        Match(
            id = "m2",
            homeTeam = Team("t3", "Liverpool", null),
            awayTeam = Team("t4", "Real Madrid", null),
            startTime = kotlinx.datetime.Instant.parse("2026-09-11T20:00:00Z"),
            status = MatchStatus.UPCOMING,
            leagueId = "l2"
        )
    )
}
