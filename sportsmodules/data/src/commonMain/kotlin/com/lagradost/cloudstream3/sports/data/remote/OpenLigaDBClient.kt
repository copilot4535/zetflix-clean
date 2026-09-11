package com.lagradost.cloudstream3.sports.data.remote

import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.NiceResponse

class OpenLigaDBClient {
    private val baseUrl = "https://api.openligadb.de/api"

    suspend fun getMatches(leagueShortcut: String): List<OLDBMatch> {
        val url = "$baseUrl/getmatchdata/$leagueShortcut"
        return try {
            val response: NiceResponse = app.get(url)
            response.parsed<List<OLDBMatch>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAvailableLeagues(): List<OLDBLeague> {
        val url = "$baseUrl/getavailableleagues"
        return try {
            app.get(url).parsed<List<OLDBLeague>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMatch(matchId: String): OLDBMatch? {
        val url = "$baseUrl/getmatchdata/$matchId"
        return try {
            app.get(url).parsed<OLDBMatch>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTable(leagueShortcut: String, season: String): List<OLDBStanding> {
        val url = "$baseUrl/getbltable/$leagueShortcut/$season"
        return try {
            app.get(url).parsed<List<OLDBStanding>>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
