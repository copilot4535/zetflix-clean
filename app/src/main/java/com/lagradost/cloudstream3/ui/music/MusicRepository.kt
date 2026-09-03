package com.lagradost.cloudstream3.ui.music

import android.util.Log
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.YouTubeLocale
import com.maxrave.kotlinytmusicscraper.models.SongItem
import com.maxrave.kotlinytmusicscraper.models.VideoItem
import com.maxrave.kotlinytmusicscraper.models.AlbumItem
import com.maxrave.kotlinytmusicscraper.models.PlaylistItem
import com.maxrave.kotlinytmusicscraper.models.ArtistItem
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
    private var cachedHomeSections: List<MusicHomeSection>? = null

    suspend fun searchSongs(query: String, filter: YouTube.SearchFilter = YouTube.SearchFilter.FILTER_SONG): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        val result = youtube.search(query, filter)
        result.getOrNull()?.items?.mapNotNull { item ->
            val title = item.title
            val id = item.id
            val thumb = item.thumbnail
            val artist = when (item.type) {
                YTItemType.SONG -> (item as? SongItem)?.artists?.joinToString(", ") { it.name }
                YTItemType.VIDEO -> (item as? VideoItem)?.artists?.joinToString(", ") { it.name }
                YTItemType.ALBUM -> (item as? AlbumItem)?.artists?.joinToString(", ") { it.name }
                YTItemType.PLAYLIST -> (item as? PlaylistItem)?.author?.name
                YTItemType.ARTIST -> (item as? ArtistItem)?.subscribers
                else -> null
            }
            if (id.isNotEmpty()) {
                MusicSearchResponse(title, artist, id, thumb)
            } else null
        } ?: emptyList()
    }

    suspend fun getHomeSections(): List<MusicHomeSection> = withContext(Dispatchers.IO) {
        cachedHomeSections?.let { return@withContext it }
        try {
            val browseResult = youtube.browse("FEmusic_home", null).getOrNull()
            if (browseResult == null) {
                Log.e("MusicHome", "Browse result is null for FEmusic_home")
                return@withContext emptyList()
            }
            Log.d("MusicHome", "InnerTube returned ${browseResult.items.size} sections")
            
            browseResult.items.filter { it.items.isNotEmpty() }.map { section ->
                Log.d("MusicHome", "Processing section: ${section.title} with ${section.items.size} items")
                MusicHomeSection(
                    title = section.title ?: "Recommended",
                    items = section.items.map { item ->
                        MusicHomeItem(
                            title = item.title,
                            subtitle = when (item.type) {
                                YTItemType.SONG -> (item as? SongItem)?.artists?.joinToString(", ") { it.name }
                                YTItemType.VIDEO -> (item as? VideoItem)?.artists?.joinToString(", ") { it.name }
                                YTItemType.ALBUM -> (item as? AlbumItem)?.artists?.joinToString(", ") { it.name }
                                YTItemType.PLAYLIST -> (item as? PlaylistItem)?.author?.name
                                YTItemType.ARTIST -> (item as? ArtistItem)?.subscribers
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
            }.also { cachedHomeSections = it }
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

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            youtube.getYTMusicSearchSuggestions(query).getOrNull()?.queries ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRelatedSongs(videoId: String): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val response = youtube.nextCustom(videoId).getOrNull()
            // Map NextResponse to MusicSearchResponse
            // Look for the "Up Next" section
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

    suspend fun getArtistDetails(artistId: String): List<MusicHomeSection> = withContext(Dispatchers.IO) {
        try {
            val artistPage = youtube.artist(artistId).getOrNull()
            artistPage?.sections?.map { section ->
                MusicHomeSection(
                    title = section.title ?: "Section",
                    items = section.items.map { item ->
                        MusicHomeItem(
                            item.title,
                            "", // Subtitle mapping can be refined
                            item.id,
                            item.thumbnail,
                            when (item.type) {
                                com.maxrave.kotlinytmusicscraper.models.YTItemType.SONG -> MusicItemType.SONG
                                com.maxrave.kotlinytmusicscraper.models.YTItemType.ALBUM -> MusicItemType.ALBUM
                                com.maxrave.kotlinytmusicscraper.models.YTItemType.PLAYLIST -> MusicItemType.PLAYLIST
                                com.maxrave.kotlinytmusicscraper.models.YTItemType.ARTIST -> MusicItemType.ARTIST
                                com.maxrave.kotlinytmusicscraper.models.YTItemType.VIDEO -> MusicItemType.SONG
                            }
                        )
                    }
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMoodAndGenres(): List<MusicHomeSection> = withContext(Dispatchers.IO) {
        try {
            val moods = youtube.moodAndGenres().getOrNull()
            moods?.map { mood ->
                MusicHomeSection(
                    title = mood.title,
                    items = mood.items.map { item ->
                        MusicHomeItem(
                            item.title,
                            null,
                            item.endpoint.params ?: "",
                            null, // Genres usually don't have thumbnails in this model
                            MusicItemType.PLAYLIST // Treat as playlist/params for browsing
                        )
                    }
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
