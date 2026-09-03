package com.lagradost.cloudstream3.ui.music

import android.util.Log
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.YouTubeLocale
import com.maxrave.kotlinytmusicscraper.models.SongItem
import com.maxrave.kotlinytmusicscraper.models.YTItemType
import com.maxrave.kotlinytmusicscraper.pages.BrowseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MusicItemType {
    SONG, ALBUM, PLAYLIST, ARTIST
}

data class MusicHomeItem(
    val title: String,
    val subtitle: String?,
    val id: String,
    val thumbnailUrl: String?,
    val type: MusicItemType
)

data class MusicHomeSection(
    val title: String,
    val items: List<MusicHomeItem>
)

object YouTubeInstance {
    val youtube = YouTube().apply {
        locale = YouTubeLocale("US", "en")
        visitorData = YouTube.DEFAULT_VISITOR_DATA
    }
}

class MusicRepository {
    private val youtube = YouTubeInstance.youtube

    suspend fun searchSongs(query: String): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        val result = youtube.search(query, YouTube.SearchFilter.FILTER_SONG)
        result.getOrNull()?.items?.filterIsInstance<SongItem>()?.map {
            MusicSearchResponse(
                title = it.title,
                artist = it.artists.joinToString(", ") { artist -> artist.name },
                videoId = it.id,
                thumbnailUrl = it.thumbnail
            )
        } ?: emptyList()
    }

    suspend fun getHomeSections(): List<MusicHomeSection> = withContext(Dispatchers.IO) {
        try {
            val browseResult = youtube.browse("FEmusic_home", null).getOrNull()
            if (browseResult == null) {
                Log.e("MusicHome", "Browse result is null for FEmusic_home")
                return@withContext emptyList()
            }
            Log.d("MusicHome", "InnerTube returned ${browseResult.items.size} sections")
            
            browseResult.items.map { section ->
                Log.d("MusicHome", "Processing section: ${section.title} with ${section.items.size} items")
                MusicHomeSection(
                    title = section.title ?: "Recommended",
                    items = section.items.map { item ->
                        MusicHomeItem(
                            title = item.title,
                            subtitle = when (item.type) {
                                YTItemType.SONG -> (item as? SongItem)?.artists?.joinToString(", ") { it.name }
                                YTItemType.VIDEO -> (item as? com.maxrave.kotlinytmusicscraper.models.VideoItem)?.artists?.joinToString(", ") { it.name }
                                YTItemType.ALBUM -> (item as? com.maxrave.kotlinytmusicscraper.models.AlbumItem)?.artists?.joinToString(", ") { it.name }
                                YTItemType.PLAYLIST -> (item as? com.maxrave.kotlinytmusicscraper.models.PlaylistItem)?.author?.name
                                YTItemType.ARTIST -> (item as? com.maxrave.kotlinytmusicscraper.models.ArtistItem)?.subscribers
                            },
                            id = item.id,
                            thumbnailUrl = item.thumbnail,
                            type = when (item.type) {
                                YTItemType.SONG -> MusicItemType.SONG
                                YTItemType.ALBUM -> MusicItemType.ALBUM
                                YTItemType.PLAYLIST -> MusicItemType.PLAYLIST
                                YTItemType.ARTIST -> MusicItemType.ARTIST
                                YTItemType.VIDEO -> MusicItemType.SONG // Treat videos as songs
                            }
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("MusicHome", "Error loading home sections", e)
            emptyList()
        }
    }

    suspend fun getAlbumSongs(albumId: String): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        val albumPage = youtube.album(albumId).getOrNull()
        albumPage?.songs?.map {
            MusicSearchResponse(
                title = it.title,
                artist = it.artists.joinToString(", ") { artist -> artist.name },
                videoId = it.id,
                thumbnailUrl = it.thumbnail
            )
        } ?: emptyList()
    }

    suspend fun getPlaylistSongs(playlistId: String): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        val playlistPage = youtube.playlist(playlistId).getOrNull()
        playlistPage?.songs?.map {
            MusicSearchResponse(
                title = it.title,
                artist = it.artists.joinToString(", ") { artist -> artist.name },
                videoId = it.id,
                thumbnailUrl = it.thumbnail
            )
        } ?: emptyList()
    }
}
