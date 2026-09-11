package com.lagradost.cloudstream3.sports.domain

import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.domain.models.League
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.Sport
import kotlinx.coroutines.flow.Flow

interface SportsRepository {
    fun getSports(): Flow<SportsResource<List<Sport>>>
    fun getLeagues(): Flow<SportsResource<List<League>>>
    fun getLiveMatches(leagueShortcut: String): Flow<SportsResource<List<Match>>>
    fun getFixtures(leagueShortcut: String): Flow<SportsResource<List<Match>>>
}
