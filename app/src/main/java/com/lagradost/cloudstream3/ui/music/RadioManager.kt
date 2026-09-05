package com.lagradost.cloudstream3.ui.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RadioManager {
    private val youtube = YouTubeInstance.youtube

    suspend fun startRadio(videoId: String): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        try {
            // youtube.nextCustom returns NextResponse which contains the playlist panel (queue)
            val response = youtube.nextCustom(videoId).getOrNull()
            
            response?.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs
                ?.firstOrNull()?.tabRenderer?.content?.musicQueueRenderer?.content?.playlistPanelRenderer?.contents
                ?.mapNotNull { it.track }
                ?.map { 
                    MusicSearchResponse(
                        it.title?.runs?.firstOrNull()?.text ?: "",
                        it.longBylineText?.runs?.firstOrNull()?.text,
                        it.videoId ?: "",
                        it.thumbnail?.thumbnails?.firstOrNull()?.url
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
