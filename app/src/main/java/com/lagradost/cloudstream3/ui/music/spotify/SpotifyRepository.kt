package com.lagradost.cloudstream3.ui.music.spotify

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.JsonAsString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi

class SpotifyRepository {
    private val TAG = "SpotifyRepository"

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getAccessToken(spDc: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
            val response = app.get(url, headers = mapOf("Cookie" to "sp_dc=$spDc"))
            val data = response.parsed<SpotifyAccessTokenResponse>()
            data.accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
            null
        }
    }

    suspend fun getClientToken(): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://clienttoken.spotify.com/v1/clienttoken"
            val body = "{\"client_data\":{\"client_id\":\"d8a5ed1b71ff4f678bd6277c0500705a\",\"js_sdk_data\":{\"device_model\":\"unknown\",\"engine\":\"unknown\",\"os\":\"unknown\",\"os_version\":\"unknown\"}}}"
            val response = app.post(
                url,
                json = JsonAsString(body)
            )
            val data = response.parsed<SpotifyClientTokenResponse>()
            data.grantedToken.token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get client token", e)
            null
        }
    }

    suspend fun searchTrack(query: String, accessToken: String, clientToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val sha = "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"
            val variables = "{\"searchTerm\":\"$query\",\"offset\":0,\"limit\":1,\"numberOfTopResults\":1,\"includeAudiobooks\":true,\"includePreReleases\":false}"
            val url = "https://api-partner.spotify.com/pathfinder/v1/query"
            
            val response = app.get(
                url,
                params = mapOf(
                    "operationName" to "searchTracks",
                    "variables" to variables,
                    "extensions" to "{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"$sha\"}}"
                ),
                headers = mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "Client-Token" to clientToken
                )
            )
            val data = response.parsed<SpotifySearchResponse>()
            data.data?.searchV2?.tracksV2?.items?.firstOrNull()?.item?.data?.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search track", e)
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getCanvasUrl(trackId: String, accessToken: String, clientToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
            val request = CanvasRequest(
                tracks = listOf(CanvasRequest.Track("spotify:track:$trackId"))
            )
            val bodyBytes = ProtoBuf.encodeToByteArray(CanvasRequest.serializer(), request)
            
            val response = app.post(
                url,
                headers = mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "Client-Token" to clientToken,
                    "Accept" to "application/protobuf",
                    "Content-Type" to "application/protobuf"
                ),
                requestBody = bodyBytes.toRequestBody("application/protobuf".toMediaType())
            )
            
            val canvasResponse = ProtoBuf.decodeFromByteArray(CanvasResponse.serializer(), response.body.bytes())
            canvasResponse.canvases.firstOrNull()?.canvasUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get canvas URL", e)
            null
        }
    }
}
