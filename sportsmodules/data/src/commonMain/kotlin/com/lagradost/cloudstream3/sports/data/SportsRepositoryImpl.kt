package com.lagradost.cloudstream3.sports.data

import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.data.remote.OpenLigaDBClient
import com.lagradost.cloudstream3.sports.domain.SportsRepository
import com.lagradost.cloudstream3.sports.domain.models.League
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.Matchday
import com.lagradost.cloudstream3.sports.domain.models.Sport
import com.lagradost.cloudstream3.sports.domain.models.Standing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class SportsRepositoryImpl : SportsRepository {
    private val client = OpenLigaDBClient()
    private val cache = mutableMapOf<String, Pair<Long, List<Match>>>()
    private val matchCache = mutableMapOf<String, Pair<Long, Match>>()
    private val tableCache = mutableMapOf<String, Pair<Long, List<Standing>>>()
    private val groupCache = mutableMapOf<String, Pair<Long, Matchday>>()
    private val leagueCache = mutableListOf<League>()
    private val cacheMutex = Mutex()

    private val FRESHNESS_TTL = 15_000L // 15 seconds de-duplication
    private val MATCH_TTL = 60_000L // 1 minute for team matches
    private val TABLE_TTL = 3_600_000L // 1 hour for standings
    private val GROUP_TTL = 3_600_000L // 1 hour for matchday info

    private val featuredShortcuts = setOf("bl1", "pl", "ll")

    override fun getSports(): Flow<SportsResource<List<Sport>>> = flow {
        emit(SportsResource.Loading)
        // Static for now
        emit(SportsResource.Success(MockSportsDataSource.getSports()))
    }

    override fun getLeagues(): Flow<SportsResource<List<League>>> = flow {
        if (leagueCache.isNotEmpty()) {
            emit(SportsResource.Success(leagueCache))
            return@flow
        }

        emit(SportsResource.Loading)
        try {
            val leagues = client.getAvailableLeagues()
                .filter { it.leagueShortcut.lowercase() in featuredShortcuts }
                .map { OpenLigaDBNormalizer.normalizeLeague(it) }
            
            leagueCache.clear()
            leagueCache.addAll(leagues)
            emit(SportsResource.Success(leagues))
        } catch (e: Exception) {
            emit(SportsResource.Error("Failed to fetch leagues: ${e.message}", e))
        }
    }

    override fun getLiveMatches(leagueShortcut: String): Flow<SportsResource<List<Match>>> = flow {
        val cacheKey = "live_$leagueShortcut"

        val cached = cacheMutex.withLock { cache[cacheKey] }
        if (cached != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - cached.first < FRESHNESS_TTL) {
                emit(SportsResource.Success(cached.second))
                return@flow
            }
            // Emit stale cache while fetching fresh data
            emit(SportsResource.Success(cached.second))
        } else {
            emit(SportsResource.Loading)
        }

        try {
            val matches = client.getMatches(leagueShortcut)
                .map { OpenLigaDBNormalizer.normalizeMatch(it) }

            cacheMutex.withLock {
                cache[cacheKey] = Pair(Clock.System.now().toEpochMilliseconds(), matches)
            }

            emit(SportsResource.Success(matches))
        } catch (e: Exception) {
            val stale = cacheMutex.withLock { cache[cacheKey]?.second }
            if (stale != null) {
                emit(SportsResource.Success(stale))
            } else {
                emit(SportsResource.Error("Failed to fetch matches: ${e.message}", e))
            }
        }
    }

    override fun getFixtures(leagueShortcut: String): Flow<SportsResource<List<Match>>> = flow {
        val cacheKey = "fixtures_$leagueShortcut"

        val cached = cacheMutex.withLock { cache[cacheKey] }
        if (cached != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - cached.first < FRESHNESS_TTL) {
                emit(SportsResource.Success(cached.second))
                return@flow
            }
            emit(SportsResource.Success(cached.second))
        } else {
            emit(SportsResource.Loading)
        }

        try {
            val matches = client.getMatches(leagueShortcut)
                .map { OpenLigaDBNormalizer.normalizeMatch(it) }

            cacheMutex.withLock {
                cache[cacheKey] = Pair(Clock.System.now().toEpochMilliseconds(), matches)
            }

            emit(SportsResource.Success(matches))
        } catch (e: Exception) {
            val stale = cacheMutex.withLock { cache[cacheKey]?.second }
            if (stale != null) {
                emit(SportsResource.Success(stale))
            } else {
                emit(SportsResource.Error("Failed to fetch fixtures: ${e.message}", e))
            }
        }
    }

    override fun getMatchDetails(matchId: String): Flow<SportsResource<Match>> = flow {
        emit(SportsResource.Loading)
        try {
            val oldbMatch = client.getMatch(matchId)
            if (oldbMatch != null) {
                emit(SportsResource.Success(OpenLigaDBNormalizer.normalizeMatch(oldbMatch)))
            } else {
                emit(SportsResource.Error("Match not found"))
            }
        } catch (e: Exception) {
            emit(SportsResource.Error("Failed to fetch match details: ${e.message}", e))
        }
    }

    override fun getStandings(leagueShortcut: String, season: String): Flow<SportsResource<List<Standing>>> = flow {
        val cacheKey = "table_${leagueShortcut}_$season"

        val cached = cacheMutex.withLock { tableCache[cacheKey] }
        if (cached != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - cached.first < TABLE_TTL) {
                emit(SportsResource.Success(cached.second))
                return@flow
            }
        }

        emit(SportsResource.Loading)
        try {
            val oldbTable = client.getTable(leagueShortcut, season)
            val standings = oldbTable.mapIndexed { index, oldbStanding ->
                OpenLigaDBNormalizer.normalizeStanding(oldbStanding, index + 1)
            }

            cacheMutex.withLock {
                tableCache[cacheKey] = Pair(Clock.System.now().toEpochMilliseconds(), standings)
            }

            emit(SportsResource.Success(standings))
        } catch (e: Exception) {
            val stale = cacheMutex.withLock { tableCache[cacheKey]?.second }
            if (stale != null) {
                emit(SportsResource.Success(stale))
            } else {
                emit(SportsResource.Error("Failed to fetch standings: ${e.message}", e))
            }
        }
    }

    override fun getCurrentMatchday(leagueShortcut: String): Flow<SportsResource<Matchday>> = flow {
        val league = leagueCache.find { it.shortcut == leagueShortcut }
        if (league == null) {
            emit(SportsResource.Error("League metadata not found for $leagueShortcut"))
            return@flow
        }
        val season = league.season ?: "" // Empty string if missing, still isolated by shortcut
        val cacheKey = "group_${leagueShortcut}_$season"

        val cached = cacheMutex.withLock { groupCache[cacheKey] }
        if (cached != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - cached.first < GROUP_TTL) {
                emit(SportsResource.Success(cached.second))
                return@flow
            }
        }

        emit(SportsResource.Loading)
        try {
            val oldbGroup = client.getCurrentGroup(leagueShortcut)
            if (oldbGroup != null) {
                val matchday = OpenLigaDBNormalizer.normalizeGroup(oldbGroup, leagueShortcut, season)

                cacheMutex.withLock {
                    groupCache[cacheKey] = Pair(Clock.System.now().toEpochMilliseconds(), matchday)
                }

                emit(SportsResource.Success(matchday))
            } else {
                emit(SportsResource.Error("Current matchday not found"))
            }
        } catch (e: Exception) {
            val stale = cacheMutex.withLock { groupCache[cacheKey]?.second }
            if (stale != null) {
                emit(SportsResource.Success(stale))
            } else {
                emit(SportsResource.Error("Failed to fetch matchday: ${e.message}", e))
            }
        }
    }

    override fun getTeamRecentMatch(leagueShortcut: String, teamId: String): Flow<SportsResource<Match>> = flow {
        val cacheKey = "recent_${leagueShortcut}_$teamId"
        val cached = cacheMutex.withLock { matchCache[cacheKey] }
        if (cached != null && Clock.System.now().toEpochMilliseconds() - cached.first < MATCH_TTL) {
            emit(SportsResource.Success(cached.second))
            return@flow
        }

        emit(SportsResource.Loading)
        try {
            val oldbMatch = client.getLastMatch(leagueShortcut, teamId)
            if (oldbMatch != null) {
                val match = OpenLigaDBNormalizer.normalizeMatch(oldbMatch)
                cacheMutex.withLock { matchCache[cacheKey] = Pair(Clock.System.now().toEpochMilliseconds(), match) }
                emit(SportsResource.Success(match))
            } else {
                emit(SportsResource.Error("Recent match not found"))
            }
        } catch (e: Exception) {
            emit(SportsResource.Error("Failed to fetch recent match: ${e.message}", e))
        }
    }

    override fun getTeamNextMatch(leagueShortcut: String, teamId: String): Flow<SportsResource<Match>> = flow {
        val cacheKey = "next_${leagueShortcut}_$teamId"
        val cached = cacheMutex.withLock { matchCache[cacheKey] }
        if (cached != null && Clock.System.now().toEpochMilliseconds() - cached.first < MATCH_TTL) {
            emit(SportsResource.Success(cached.second))
            return@flow
        }

        emit(SportsResource.Loading)
        try {
            val oldbMatch = client.getNextMatch(leagueShortcut, teamId)
            if (oldbMatch != null) {
                val match = OpenLigaDBNormalizer.normalizeMatch(oldbMatch)
                cacheMutex.withLock { matchCache[cacheKey] = Pair(Clock.System.now().toEpochMilliseconds(), match) }
                emit(SportsResource.Success(match))
            } else {
                emit(SportsResource.Error("Next match not found"))
            }
        } catch (e: Exception) {
            emit(SportsResource.Error("Failed to fetch next match: ${e.message}", e))
        }
    }
}
