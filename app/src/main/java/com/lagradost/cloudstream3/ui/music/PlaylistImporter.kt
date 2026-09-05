package com.lagradost.cloudstream3.ui.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PlaylistImporter {
    private val youtube = YouTubeInstance.youtube

    suspend fun importPlaylist(listId: String): MusicPlaylist? = withContext(Dispatchers.IO) {
        try {
            val response = youtube.playlist(listId).getOrNull() ?: return@withContext null
            val songs = response.songs.map {
                MusicSearchResponse(
                    it.title,
                    it.artists.joinToString(", ") { a -> a.name },
                    it.id,
                    it.thumbnail
                )
            }
            MusicPlaylist(response.playlist.title ?: "Imported Playlist", songs)
        } catch (e: Exception) {
            null
        }
    }

    fun extractListId(url: String): String? {
        val regex = Regex("[?&]list=([a-zA-Z0-9_-]+)")
        return regex.find(url)?.groupValues?.get(1)
    }
}
