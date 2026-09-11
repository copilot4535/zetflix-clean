package com.lagradost.cloudstream3.sports.data

import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.data.remote.OpenLigaDBClient
import com.lagradost.cloudstream3.sports.domain.SportsRepository
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.Sport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SportsRepositoryImpl : SportsRepository {
    private val client = OpenLigaDBClient()

    override fun getSports(): Flow<SportsResource<List<Sport>>> = flow {
        emit(SportsResource.Loading)
        // Static for now
        emit(SportsResource.Success(MockSportsDataSource.getSports()))
    }

    override fun getLiveMatches(sportId: String?): Flow<SportsResource<List<Match>>> = flow {
        emit(SportsResource.Loading)
        try {
            val matches = client.getMatches("bl1")
                .map { OpenLigaDBNormalizer.normalizeMatch(it) }
            emit(SportsResource.Success(matches))
        } catch (e: Exception) {
            emit(SportsResource.Error("Failed to fetch matches: ${e.message}", e))
        }
    }

    override fun getFixtures(sportId: String?): Flow<SportsResource<List<Match>>> = flow {
        emit(SportsResource.Loading)
        try {
            // In a real app we'd fetch specific fixtures, but for now we reuse current matchday
            val matches = client.getMatches("bl1")
                .map { OpenLigaDBNormalizer.normalizeMatch(it) }
            emit(SportsResource.Success(matches))
        } catch (e: Exception) {
            emit(SportsResource.Error("Failed to fetch fixtures: ${e.message}", e))
        }
    }
}
