package com.lagradost.cloudstream3.ui.music

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.maxrave.kotlinytmusicscraper.YouTube
import com.lagradost.cloudstream3.services.music.MusicService
import androidx.media3.common.util.UnstableApi
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlinx.serialization.Serializable

@Serializable
data class MusicSearchResponse(
    val title: String,
    val artist: String?,
    val videoId: String,
    val thumbnailUrl: String?,
)

class MusicViewModel : ViewModel() {
    private val repository = MusicRepository()

    private val _searchResult = MutableLiveData<Resource<List<MusicSearchResponse>>>()
    val searchResult: LiveData<Resource<List<MusicSearchResponse>>> = _searchResult

    private val _homeSections = MutableLiveData<Resource<List<MusicHomeSection>>>()
    val homeSections: LiveData<Resource<List<MusicHomeSection>>> = _homeSections

    private val _relatedSongs = MutableLiveData<Resource<List<MusicSearchResponse>>>()
    val relatedSongs: LiveData<Resource<List<MusicSearchResponse>>> = _relatedSongs

    private val _streamUrl = MutableLiveData<Resource<Pair<String, MusicSearchResponse>>>()
    val streamUrl: LiveData<Resource<Pair<String, MusicSearchResponse>>> = _streamUrl

    private val _lyrics = MutableLiveData<Resource<LyricsResponse>>()
    val lyrics: LiveData<Resource<LyricsResponse>> = _lyrics

    private val _currentPlayingSong = MutableLiveData<MusicSearchResponse?>()
    val currentPlayingSong: LiveData<MusicSearchResponse?> = _currentPlayingSong

    private val _queueReady = MutableLiveData<Resource<Pair<List<Pair<MusicSearchResponse, String>>, Int>>>()
    val queueReady: LiveData<Resource<Pair<List<Pair<MusicSearchResponse, String>>, Int>>> = _queueReady

    private val _likedSongs = MutableLiveData<List<MusicSearchResponse>>()
    val likedSongs: LiveData<List<MusicSearchResponse>> = _likedSongs

    private val _history = MutableLiveData<List<MusicSearchResponse>>()
    val history: LiveData<List<MusicSearchResponse>> = _history

    private val _playlists = MutableLiveData<List<MusicPlaylist>>()
    val playlists: LiveData<List<MusicPlaylist>> = _playlists

    private val _searchSuggestions = MutableLiveData<List<String>>()
    val searchSuggestions: LiveData<List<String>> = _searchSuggestions

    private val _searchHistory = MutableLiveData<List<String>>()
    val searchHistory: LiveData<List<String>> = _searchHistory

    private val _downloadedSongs = MutableLiveData<List<MusicSearchResponse>>()
    val downloadedSongs: LiveData<List<MusicSearchResponse>> = _downloadedSongs

    private val _sleepTimerTimeLeft = MutableLiveData<Long?>()
    val sleepTimerTimeLeft: LiveData<Long?> = _sleepTimerTimeLeft

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    private fun initNewPipe() {
        try {
            NewPipe.getDownloader()
        } catch (e: Exception) {
            DownloaderTestImpl.getInstance()?.let {
                NewPipe.init(it)
            }
        }
    }

    init {
        loadPersistenceData()
    }

    fun loadPersistenceData() {
        _likedSongs.postValue(MusicPersistence.getLikedSongs())
        _history.postValue(MusicPersistence.getHistory())
        _playlists.postValue(MusicPersistence.getPlaylists())
        _downloadedSongs.postValue(MusicPersistence.getDownloadedSongs())
        _searchHistory.postValue(MusicPersistence.getSearchHistory())
    }

    @androidx.media3.common.util.UnstableApi
    fun downloadSong(song: MusicSearchResponse) {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            val url = extractStreamUrl(song.videoId)
            if (url != null) {
                com.lagradost.cloudstream3.CloudStreamApp.context?.let { ctx ->
                    MusicDownloadRepository(ctx).downloadSong(song, url)
                    _downloadedSongs.postValue(MusicPersistence.getDownloadedSongs())
                }
            } else {
                // Post failure if needed
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    fun removeDownload(videoId: String) {
        com.lagradost.cloudstream3.CloudStreamApp.context?.let { ctx ->
            MusicDownloadRepository(ctx).removeDownload(videoId)
            _downloadedSongs.postValue(MusicPersistence.getDownloadedSongs())
        }
    }

    fun toggleLikeSong(song: MusicSearchResponse) {
        MusicPersistence.toggleLikeSong(song)
        _likedSongs.postValue(MusicPersistence.getLikedSongs())
    }

    fun addToHistory(song: MusicSearchResponse) {
        MusicPersistence.addSongToHistory(song)
        _history.postValue(MusicPersistence.getHistory())
    }

    fun createPlaylist(name: String) {
        MusicPersistence.createPlaylist(name)
        _playlists.postValue(MusicPersistence.getPlaylists())
    }

    fun addSongToPlaylist(playlistName: String, song: MusicSearchResponse) {
        MusicPersistence.addSongToPlaylist(playlistName, song)
        _playlists.postValue(MusicPersistence.getPlaylists())
    }

    fun search(query: String, filter: YouTube.SearchFilter = YouTube.SearchFilter.FILTER_SONG) {
        if (query.isBlank()) {
            loadTrendingSongs()
            return
        }
        _searchResult.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val results = repository.searchSongs(query, filter)
                if (results.isEmpty()) {
                    _searchResult.postValue(Resource.Failure(false, "No results found"))
                } else {
                    _searchResult.postValue(Resource.Success(results))
                }
            } catch (e: Exception) {
                Log.e("MusicSearch", "Search failed for query: $query", e)
                _searchResult.postValue(Resource.Failure(false, "Search failed: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    fun loadSearchSuggestions(query: String) {
        if (query.isBlank()) {
            _searchSuggestions.postValue(emptyList())
            return
        }
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            val suggestions = repository.getSearchSuggestions(query)
            _searchSuggestions.postValue(suggestions)
        }
    }

    fun loadHomeSections() {
        _homeSections.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("MusicViewModel", "Loading home sections with visitorData: ${YouTubeInstance.youtube.visitorData}")
                val sections = repository.getHomeSections()
                Log.d("MusicViewModel", "Loaded ${sections.size} sections")
                _homeSections.postValue(Resource.Success(sections))
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load home sections", e)
                _homeSections.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadTrendingSongs() {
        _searchResult.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Fetch curated trending songs (fallback to popular search)
                val songs = repository.searchSongs("trending music")
                _searchResult.postValue(Resource.Success(songs))
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load trending songs", e)
                _searchResult.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadAlbumSongs(albumId: String) {
        _searchResult.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val songs = repository.getAlbumSongs(albumId)
                _searchResult.postValue(Resource.Success(songs))
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load album songs", e)
                _searchResult.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadPlaylistSongs(playlistId: String) {
        _searchResult.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val songs = repository.getPlaylistSongs(playlistId)
                _searchResult.postValue(Resource.Success(songs))
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load playlist songs", e)
                _searchResult.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadRelatedSongs(videoId: String) {
        _relatedSongs.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val songs = repository.getRelatedSongs(videoId)
                _relatedSongs.postValue(Resource.Success(songs))
            } catch (e: Exception) {
                _relatedSongs.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadArtistDetails(artistId: String) {
        _homeSections.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val sections = repository.getArtistDetails(artistId)
                _homeSections.postValue(Resource.Success(sections))
            } catch (e: Exception) {
                _homeSections.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadMoodAndGenres() {
        _homeSections.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val sections = repository.getMoodAndGenres()
                _homeSections.postValue(Resource.Success(sections))
            } catch (e: Exception) {
                _homeSections.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadStreamAndPlay(song: MusicSearchResponse) {
        _streamUrl.postValue(Resource.Loading())
        _currentPlayingSong.postValue(song)
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val streamUrlFound = extractStreamUrl(song.videoId)

                if (!streamUrlFound.isNullOrBlank()) {
                    _streamUrl.postValue(Resource.Success(streamUrlFound to song))
                    fetchLyrics(song)
                    addToHistory(song)
                } else {
                    _streamUrl.postValue(Resource.Failure(false, "Could not extract audio stream for: ${song.title}"))
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Total stream extraction failure", e)
                _streamUrl.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    private suspend fun extractStreamUrl(videoId: String): String? {
        // 0. Check if already downloaded
        com.lagradost.cloudstream3.CloudStreamApp.context?.let { ctx ->
            val downloadManager = com.lagradost.cloudstream3.services.music.MusicDownloadManager.getDownloadManager(ctx)
            val download = downloadManager.downloadIndex.getDownload(videoId)
            if (download != null && download.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                return download.request.uri.toString()
            }
        }

        // 1. Try InnerTube
        try {
            val playerResult = YouTubeInstance.youtube.player(videoId, null, false).getOrNull()
            val formats = playerResult?.second?.streamingData?.adaptiveFormats
            val url = formats?.filter { it.isAudio }?.maxByOrNull { it.bitrate }?.url
            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                return url
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "InnerTube extraction failed for $videoId", e)
        }

        // 2. Fallback to NewPipe
        try {
            initNewPipe()
            val service = ServiceList.YouTube
            val info = StreamInfo.getInfo(service, videoId)
            val url = info.audioStreams.firstOrNull()?.content
            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                return url
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "NewPipe fallback failed for $videoId", e)
        }

        return null
    }

    fun playQueue(songs: List<MusicSearchResponse>, startIndex: Int) {
        _queueReady.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (songs.isEmpty()) {
                    _queueReady.postValue(Resource.Failure(false, "Empty song list"))
                    return@launchSafe
                }

                val safeStartIndex = if (startIndex in songs.indices) startIndex else 0
                val selectedSong = songs[safeStartIndex]

                // 1. Extract selected song immediately for fast start
                val firstUrl = extractStreamUrl(selectedSong.videoId)
                if (firstUrl != null) {
                    // Post initial single-item queue to start playback ASAP
                    val initialQueue = listOf(selectedSong to firstUrl)
                    _queueReady.postValue(Resource.Success(initialQueue to 0))
                    
                    // Update current song UI
                    _currentPlayingSong.postValue(selectedSong)
                    fetchLyrics(selectedSong)
                    addToHistory(selectedSong)

                    // 2. Extract remaining songs in the background
                    val fullQueue = mutableListOf<Pair<MusicSearchResponse, String>>()
                    val maxQueueSize = 30
                    val subset = songs.take(maxQueueSize)

                    for (song in subset) {
                        if (song.videoId == selectedSong.videoId) {
                            fullQueue.add(song to firstUrl)
                            continue
                        }
                        val url = extractStreamUrl(song.videoId)
                        if (url != null) {
                            fullQueue.add(song to url)
                        }
                    }

                    if (fullQueue.size > 1) {
                        val adjustedStartIndex = fullQueue.indexOfFirst { it.first.videoId == selectedSong.videoId }.coerceAtLeast(0)
                        // Post the full populated queue
                        _queueReady.postValue(Resource.Success(fullQueue to adjustedStartIndex))
                    }
                } else {
                    _queueReady.postValue(Resource.Failure(false, "Could not extract audio for: ${selectedSong.title}"))
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to build queue", e)
                _queueReady.postValue(Resource.Failure(false, e.message ?: "Queue error"))
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerTimeLeft.postValue(null)
            return
        }

        val millis = minutes * 60 * 1000L
        _sleepTimerTimeLeft.postValue(millis)

        sleepTimerJob = viewModelScope.launchSafe {
            var remaining = millis
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining -= 1000
                _sleepTimerTimeLeft.postValue(remaining)
            }
            _sleepTimerTimeLeft.postValue(null)
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        val context = com.lagradost.cloudstream3.CloudStreamApp.context ?: return
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun fetchLyrics(song: MusicSearchResponse) {
        val artist = song.artist ?: ""
        val title = song.title
        
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = "https://lrclib.net/api/get?artist=${artist}&title=${title}"
                val response = app.get(url)
                val lyricsData = response.parsed<LyricsResponse>()
                _lyrics.postValue(Resource.Success(lyricsData))
            } catch (e: Exception) {
                _lyrics.postValue(Resource.Failure(false, "Lyrics not found"))
            }
        }
    }
}
