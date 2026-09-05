package com.lagradost.cloudstream3.ui.music

import android.util.Log
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.*
import com.maxrave.kotlinytmusicscraper.models.response.BrowseResponse
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
    val type: MusicItemType,
    val params: String? = null
)

data class MusicHomeSection(
    val title: String,
    val items: List<MusicHomeItem>,
    val params: String? = null
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
                val params = (item as? PlaylistItem)?.playEndpoint?.params 
                MusicSearchResponse(title, artist, id, thumb, params)
            } else null
        } ?: emptyList()
    }

    suspend fun getHomeSections(): List<MusicHomeSection> = withContext(Dispatchers.IO) {
        cachedHomeSections?.let { return@withContext it }
        try {
            val response = youtube.customQuery("FEmusic_home").getOrNull()
            if (response == null) {
                Log.e("MusicHome", "Custom query response is null for FEmusic_home")
                return@withContext emptyList()
            }

            val sections = parseBrowseResponse(response)
            sections.also { cachedHomeSections = it }
        } catch (e: Exception) {
            Log.e("MusicHome", "Error loading home sections", e)
            emptyList()
        }
    }

    suspend fun getBrowseSections(browseId: String? = null, params: String? = null): List<MusicHomeSection> = withContext(Dispatchers.IO) {
        try {
            Log.d("MusicRepository", "Getting browse sections for id: $browseId, params: $params")
            val response = youtube.customQuery(browseId, params).getOrNull()
            if (response == null) {
                Log.e("MusicRepository", "Browse response is null for id: $browseId, params: $params")
                return@withContext emptyList()
            }
            parseBrowseResponse(response)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error browsing for id: $browseId, params: $params", e)
            emptyList()
        }
    }

    private fun parseBrowseResponse(response: BrowseResponse): List<MusicHomeSection> {
        val contents = response.contents
        
        // 1. Try to find the section list renderer
        val sectionListRenderer = contents?.sectionListRenderer
            ?: contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
            ?: contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer

        // 3. Parse Sections from SectionList
        val contentsList = sectionListRenderer?.contents
        if (contentsList == null) {
            Log.e("MusicRepository", "No sections found in browse response contents")
            return emptyList()
        }

        return contentsList.mapNotNull { content ->
            val carousel = content.musicCarouselShelfRenderer
            val shelf = content.musicShelfRenderer
            val playlistShelf = content.musicPlaylistShelfRenderer
            
            when {
                carousel != null -> {
                    val header = carousel.header?.musicCarouselShelfBasicHeaderRenderer
                    val sectionTitle = header?.title?.runs?.firstOrNull()?.text ?: "Recommended"
                    val sectionParams = header?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.params
                    val sectionItems = carousel.contents.mapNotNull { 
                        it.musicTwoRowItemRenderer?.let { renderer -> mapTwoRowToHomeItem(renderer) }
                        ?: it.musicResponsiveListItemRenderer?.let { renderer -> mapResponsiveToHomeItem(renderer) }
                    }
                    if (sectionItems.isNotEmpty()) MusicHomeSection(sectionTitle, sectionItems, sectionParams) else null
                }
                shelf != null -> {
                    val sectionTitle = shelf.title?.runs?.firstOrNull()?.text ?: "Recommended"
                    val sectionParams = shelf.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.params 
                                       ?: shelf.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.params
                    val sectionItems = shelf.contents?.mapNotNull {
                        it.musicResponsiveListItemRenderer?.let { renderer -> mapResponsiveToHomeItem(renderer) }
                    } ?: emptyList()
                    if (sectionItems.isNotEmpty()) MusicHomeSection(sectionTitle, sectionItems, sectionParams) else null
                }
                playlistShelf != null -> {
                    // Playlist shelf doesn't have a title in the library model, but might in the JSON.
                    // For now use a generic title or extract from header if possible
                    val sectionItems = playlistShelf.contents?.mapNotNull {
                        it.musicResponsiveListItemRenderer?.let { renderer -> mapResponsiveToHomeItem(renderer) }
                    } ?: emptyList()
                    if (sectionItems.isNotEmpty()) MusicHomeSection("Items", sectionItems) else null
                }
                else -> null
            }
        }
    }

    private fun mapTwoRowToHomeItem(renderer: MusicTwoRowItemRenderer): MusicHomeItem? {
        val title = renderer.title.runs?.firstOrNull()?.text ?: return null
        val id = renderer.navigationEndpoint.browseEndpoint?.browseId 
                 ?: renderer.navigationEndpoint.watchEndpoint?.videoId 
                 ?: renderer.navigationEndpoint.watchPlaylistEndpoint?.playlistId
                 ?: ""
        val thumb = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
        val subtitle = renderer.subtitle?.runs?.joinToString("") { it.text }
        val type = when {
            renderer.isSong || renderer.isVideo -> MusicItemType.SONG
            renderer.isAlbum -> MusicItemType.ALBUM
            renderer.isArtist -> MusicItemType.ARTIST
            renderer.isPlaylist -> MusicItemType.PLAYLIST
            else -> MusicItemType.SONG
        }
        val params = renderer.navigationEndpoint.browseEndpoint?.params
        return MusicHomeItem(title, subtitle, id, thumb, type, params)
    }

    private fun mapResponsiveToHomeItem(renderer: MusicResponsiveListItemRenderer): MusicHomeItem? {
        val title = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return null
        val id = renderer.videoId 
                 ?: renderer.navigationEndpoint?.browseEndpoint?.browseId
                 ?: renderer.navigationEndpoint?.watchPlaylistEndpoint?.playlistId
                 ?: ""
        val thumb = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
        val subtitle = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.joinToString("") { it.text }
        val type = when {
            renderer.videoId != null -> MusicItemType.SONG
            renderer.navigationEndpoint?.browseEndpoint?.isArtistEndpoint == true -> MusicItemType.ARTIST
            renderer.navigationEndpoint?.browseEndpoint?.isAlbumEndpoint == true -> MusicItemType.ALBUM
            else -> MusicItemType.PLAYLIST
        }
        val params = renderer.navigationEndpoint?.browseEndpoint?.params
        return MusicHomeItem(title, subtitle, id, thumb, type, params)
    }

    suspend fun getAlbumSongs(albumId: String): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        val result = youtube.album(albumId)
        val albumPage = result.getOrNull()
        if (albumPage != null && albumPage.songs.isNotEmpty()) {
            albumPage.songs.map {
                MusicSearchResponse(
                    title = it.title,
                    artist = it.artists.joinToString(", ") { artist -> artist.name },
                    videoId = it.id,
                    thumbnailUrl = it.thumbnail
                )
            }
        } else {
            // Fallback to browse
            getBrowseResults(albumId, null)
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        val result = youtube.playlist(playlistId)
        val playlistPage = result.getOrNull()
        if (playlistPage != null && playlistPage.songs.isNotEmpty()) {
            playlistPage.songs.map {
                MusicSearchResponse(
                    title = it.title,
                    artist = it.artists.joinToString(", ") { artist -> artist.name },
                    videoId = it.id,
                    thumbnailUrl = it.thumbnail
                )
            }
        } else {
            // Fallback to browse
            getBrowseResults(playlistId, null)
        }
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
            val tabRenderer = response?.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs?.firstOrNull()?.tabRenderer
            tabRenderer?.content?.musicQueueRenderer?.content?.playlistPanelRenderer?.contents
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
            Log.d("MusicRepository", "Getting artist details for: $artistId")
            // Try standard browse first as it uses my new robust parser
            val sections = getBrowseSections(artistId, null)
            if (sections.isNotEmpty()) return@withContext sections
            
            // Fallback to legacy artist page if browse parser fails
            val artistPage = youtube.artist(artistId).getOrNull()
            artistPage?.sections?.mapNotNull { section ->
                val items = section.items.mapNotNull { item ->
                    val title = item.title
                    val id = item.id
                    val thumb = item.thumbnail
                    val type = when (item.type) {
                        YTItemType.SONG -> MusicItemType.SONG
                        YTItemType.ALBUM -> MusicItemType.ALBUM
                        YTItemType.PLAYLIST -> MusicItemType.PLAYLIST
                        YTItemType.ARTIST -> MusicItemType.ARTIST
                        YTItemType.VIDEO -> MusicItemType.SONG
                    }
                    if (id.isNotEmpty()) {
                        MusicHomeItem(title, "", id, thumb, type)
                    } else null
                }
                if (items.isNotEmpty()) {
                    MusicHomeSection(
                        title = section.title ?: "Section",
                        items = items,
                        params = section.moreEndpoint?.params
                    )
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicHome", "Error loading artist details", e)
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
                            "", // No ID for genres if we use params
                            null, 
                            MusicItemType.PLAYLIST,
                            params = item.endpoint.params
                        )
                    }
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getBrowseResults(browseId: String?, params: String?): List<MusicSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val sections = getBrowseSections(browseId, params)
            sections.flatMap { section ->
                section.items.map { item ->
                    MusicSearchResponse(item.title, item.subtitle, item.id, item.thumbnailUrl, item.params)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
