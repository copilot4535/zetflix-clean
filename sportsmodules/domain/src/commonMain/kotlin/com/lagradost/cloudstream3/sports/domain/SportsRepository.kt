package com.lagradost.cloudstream3.sports.domain

import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.domain.models.League
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.Matchday
import com.lagradost.cloudstream3.sports.domain.models.Sport
import com.lagradost.cloudstream3.sports.domain.models.Standing
import kotlinx.coroutines.flow.Flow

interface SportsRepository {
    fun getSports(): Flow<SportsResource<List<Sport>>>
    fun getLeagues(): Flow<SportsResource<List<League>>>
    fun getLiveMatches(leagueShortcut: String): Flow<SportsResource<List<Match>>>
    fun getFixtures(leagueShortcut: String): Flow<SportsResource<List<Match>>>
    fun getMatchDetails(matchId: String): Flow<SportsResource<Match>>
    fun getStandings(leagueShortcut: String, season: String): Flow<SportsResource<List<Standing>>>
    fun getCurrentMatchday(leagueShortcut: String): Flow<SportsResource<Matchday>>
}
