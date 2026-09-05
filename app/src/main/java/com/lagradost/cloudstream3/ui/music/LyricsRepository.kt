package com.lagradost.cloudstream3.ui.music

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.utils.StringUtils.encodeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepository {
    suspend fun fetchSyncedLyrics(artist: String, title: String): Resource<List<LyricLine>> = withContext(Dispatchers.IO) {
        try {
            // Using lrclib.net as a reliable external provider for synced lyrics
            val url = "https://lrclib.net/api/get?artist_name=${artist.encodeUrl()}&track_name=${title.encodeUrl()}"
            val response = app.get(url)
            
            if (response.isSuccessful) {
                val lyricsData = response.parsed<LyricsResponse>()
                if (!lyricsData.syncedLyrics.isNullOrBlank()) {
                    val lines = LrcParser.parse(lyricsData.syncedLyrics)
                    Resource.Success(lines)
                } else if (!lyricsData.plainLyrics.isNullOrBlank()) {
                    // If only plain lyrics, convert to fake synced or handle as static
                    Resource.Failure(false, "Only plain lyrics available")
                } else {
                    Resource.Failure(false, "Lyrics not found")
                }
            } else {
                Resource.Failure(false, "Lyrics not found")
            }
        } catch (e: Exception) {
            Resource.Failure(false, e.message ?: "Network error")
        }
    }
}
