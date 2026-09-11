package com.lagradost.cloudstream3.sports.data.remote

import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.NiceResponse

class OpenLigaDBClient {
    private val baseUrl = "https://api.openligadb.de/api"

    suspend fun getMatches(leagueShortcut: String): List<OLDBMatch> {
        val url = "$baseUrl/getmatchdata/$leagueShortcut"
        val response: NiceResponse = app.get(url)
        return response.parsed<List<OLDBMatch>>()
    }

    suspend fun getAvailableLeagues(): List<OLDBLeague> {
        val url = "$baseUrl/getavailableleagues"
        return app.get(url).parsed<List<OLDBLeague>>()
    }

    suspend fun getMatch(matchId: String): OLDBMatch? {
        val url = "$baseUrl/getmatchdata/$matchId"
        val response = app.get(url)
        // Some endpoints return 200 OK with empty body or null if not found
        return if (response.text.isBlank() || response.text == "null") null else response.parsed<OLDBMatch>()
    }

    suspend fun getTable(leagueShortcut: String, season: String): List<OLDBStanding> {
        val url = "$baseUrl/getbltable/$leagueShortcut/$season"
        return app.get(url).parsed<List<OLDBStanding>>()
    }

    suspend fun getCurrentGroup(leagueShortcut: String): OLDBGroup? {
        val url = "$baseUrl/getcurrentgroup/$leagueShortcut"
        val response = app.get(url)
        return if (response.text.isBlank() || response.text == "null") null else response.parsed<OLDBGroup>()
    }

    suspend fun getLastMatch(leagueShortcut: String, teamId: String): OLDBMatch? {
        val url = "$baseUrl/getlastmatchbyleagueteam/$leagueShortcut/$teamId"
        val response = app.get(url)
        return if (response.text.isBlank() || response.text == "null") null else response.parsed<OLDBMatch>()
    }

    suspend fun getNextMatch(leagueShortcut: String, teamId: String): OLDBMatch? {
        val url = "$baseUrl/getnextmatchbyleagueteam/$leagueShortcut/$teamId"
        val response = app.get(url)
        return if (response.text.isBlank() || response.text == "null") null else response.parsed<OLDBMatch>()
    }
}
