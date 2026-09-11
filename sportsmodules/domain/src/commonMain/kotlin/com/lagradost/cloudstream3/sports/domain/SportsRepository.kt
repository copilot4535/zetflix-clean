package com.lagradost.cloudstream3.sports.domain

import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.Sport
import kotlinx.coroutines.flow.Flow

interface SportsRepository {
    fun getSports(): Flow<SportsResource<List<Sport>>>
    fun getLiveMatches(sportId: String? = null): Flow<SportsResource<List<Match>>>
    fun getFixtures(sportId: String? = null): Flow<SportsResource<List<Match>>>
}
