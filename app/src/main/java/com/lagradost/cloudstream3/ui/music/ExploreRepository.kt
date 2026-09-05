package com.lagradost.cloudstream3.ui.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExploreRepository {
    private val youtube = YouTubeInstance.youtube

    suspend fun getTopCharts(): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val chart = youtube.getSimpMusicChart().getOrNull()
            val playlistId = chart?.data?.firstOrNull()?.youtubePlaylistId ?: "PL4fGSI1pDJn5kI81J1fYWK5eZRl1zJ5dg" // Fallback to a popular chart
            
            val response = youtube.playlist(playlistId).getOrNull()
            response?.songs?.map {
                MusicSearchResponse(
                    it.title,
                    it.artists.joinToString(", ") { a -> a.name },
                    it.id,
                    it.thumbnail
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
