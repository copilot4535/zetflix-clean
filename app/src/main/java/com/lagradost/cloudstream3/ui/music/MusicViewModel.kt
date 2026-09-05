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
import com.lagradost.cloudstream3.utils.StringUtils.encodeUrl
import com.maxrave.kotlinytmusicscraper.YouTube
import com.lagradost.cloudstream3.services.music.MusicService
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    val params: String? = null
)

class MusicViewModel : ViewModel() {
    private val _isInitialized = MutableLiveData<Boolean>(false)
    val isInitialized: LiveData<Boolean> = _isInitialized

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

    private val _currentQueue = MutableLiveData<List<MusicSearchResponse>>(emptyList())
    val currentQueueLiveData: LiveData<List<MusicSearchResponse>> = _currentQueue

    private val _queueReady = MutableLiveData<Event<Pair<Resource<Pair<List<Pair<MusicSearchResponse, String>>, Int>>, Int>>>()
    val queueReady: LiveData<Event<Pair<Resource<Pair<List<Pair<MusicSearchResponse, String>>, Int>>, Int>>> = _queueReady

    private val _queueUpdate = MutableLiveData<Event<Pair<Resource<Pair<List<Pair<MusicSearchResponse, String>>, Int>>, Int>>>()
    val queueUpdate: LiveData<Event<Pair<Resource<Pair<List<Pair<MusicSearchResponse, String>>, Int>>, Int>>> = _queueUpdate

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

    val downloadStates = com.lagradost.cloudstream3.services.music.MusicDownloadManager.downloadStates

    private val _sleepTimerTimeLeft = MutableLiveData<Long?>()
    val sleepTimerTimeLeft: LiveData<Long?> = _sleepTimerTimeLeft

    private val _rateStatus = MutableLiveData<RateStatus>()
    val rateStatus: LiveData<RateStatus> = _rateStatus

    private val accountRepository = YtmAccountRepository()
    private val radioManager = RadioManager()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var queueJob: Job? = null
    var currentQueueRequestId = 0
        private set
    
    private val currentQueueList = mutableListOf<MusicSearchResponse>()
    
    private val prefetchJob = SupervisorJob()
    private val prefetchScope = CoroutineScope(Dispatchers.IO + prefetchJob)
    private val prefetchSemaphore = kotlinx.coroutines.sync.Semaphore(5)

    private fun initNewPipe() {
        try {
            NewPipe.getDownloader()
        } catch (e: Exception) {
            DownloaderTestImpl.getInstance()?.let {
                NewPipe.init(it)
            }
        }
    }

    private val activeDownloadRequests = mutableMapOf<String, MusicSearchResponse>()

    init {
        // We now call loadPersistenceData via initMusic for better control
        // loadPersistenceData()
        
        viewModelScope.launchSafe {
            downloadStates.collect { states ->
                states.values.forEach { state ->
                    if (state.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                        activeDownloadRequests.remove(state.videoId)?.let { song ->
                            if (MusicPersistence.getDownloadedSongs().none { it.videoId == song.videoId }) {
                                MusicPersistence.addDownloadedSong(song)
                                _downloadedSongs.postValue(MusicPersistence.getDownloadedSongs())
                            }
                        }
                    }
                }
            }
        }
    }

    fun initMusic() {
        if (_isInitialized.value == true) return

        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                // 1. Reload YTM session cookies / account data
                // Assuming accountRepository is already initialized
                // We could explicitly refresh if needed
                
                // 2. Warm up Persistence
                loadPersistenceData()

                // 3. Pre-initialize dependencies (NewPipe, etc.)
                initNewPipe()
                
                // Ensure minimum dwell time of 400ms
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 400) {
                    kotlinx.coroutines.delay(400 - elapsed)
                }

                _isInitialized.postValue(true)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Initialization failed", e)
                // Fallback to initialized even on error to not block UI forever
                _isInitialized.postValue(true)
            }
        }
    }

    fun loadPersistenceData() {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            _likedSongs.postValue(MusicPersistence.getLikedSongs())
            _history.postValue(MusicPersistence.getHistory())
            _playlists.postValue(MusicPersistence.getPlaylists())
            _downloadedSongs.postValue(MusicPersistence.getDownloadedSongs())
            _searchHistory.postValue(MusicPersistence.getSearchHistory())
        }
    }

    @androidx.media3.common.util.UnstableApi
    fun downloadSong(song: MusicSearchResponse) {
        activeDownloadRequests[song.videoId] = song
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            val url = extractStreamUrl(song.videoId, song.params)
            if (url != null) {
                com.lagradost.cloudstream3.CloudStreamApp.context?.let { ctx ->
                    val downloadRequest = androidx.media3.exoplayer.offline.DownloadRequest.Builder(song.videoId, android.net.Uri.parse(url))
                        .setData(song.title.toByteArray())
                        .build()
                    
                    androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                        ctx,
                        com.lagradost.cloudstream3.services.music.ZetFlixDownloadService::class.java,
                        downloadRequest,
                        /* foreground= */ true
                    )
                }
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    fun removeDownload(videoId: String) {
        com.lagradost.cloudstream3.CloudStreamApp.context?.let { ctx ->
            androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                ctx,
                com.lagradost.cloudstream3.services.music.ZetFlixDownloadService::class.java,
                videoId,
                /* foreground= */ false
            )
        }
    }

    fun toggleLikeSong(song: MusicSearchResponse) {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            MusicPersistence.toggleLikeSong(song)
            _likedSongs.postValue(MusicPersistence.getLikedSongs())
        }
    }

    fun addToHistory(song: MusicSearchResponse) {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            MusicPersistence.addSongToHistory(song)
            _history.postValue(MusicPersistence.getHistory())
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            MusicPersistence.createPlaylist(name)
            _playlists.postValue(MusicPersistence.getPlaylists())
        }
    }

    fun addSongToPlaylist(playlistName: String, song: MusicSearchResponse) {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            MusicPersistence.addSongToPlaylist(playlistName, song)
            _playlists.postValue(MusicPersistence.getPlaylists())
        }
    }

    fun search(query: String, filter: YouTube.SearchFilter = YouTube.SearchFilter.FILTER_SONG) {
        if (query.isBlank()) {
            loadTrendingSongs()
            return
        }
        _searchResult.postValue(Resource.Loading())
        _searchSuggestions.postValue(emptyList())
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    val homeDeferred = async { repository.getHomeSections() }
                    val moodDeferred = async { repository.getMoodAndGenres() }
                    val trendingDeferred = async { repository.searchSongs("trending music") }
                    val podcastDeferred = async { repository.searchSongs("podcast episodes") }
                    val topArtistsDeferred = async { repository.searchSongs("top artists", YouTube.SearchFilter.FILTER_ARTIST) }
                    
                    val curatedCategories = listOf("Chill Hits", "Workout Energy", "Romantic Hits", "Top 50 Global")
                    val curatedDeferred = curatedCategories.map { category ->
                        async {
                            category to repository.searchSongs(category).take(15)
                        }
                    }

                    val rawHomeSections = homeDeferred.await()
                    val moodSections = moodDeferred.await()
                    val trendingSongs = trendingDeferred.await()
                    val podcastSongs = podcastDeferred.await()
                    val topArtists = topArtistsDeferred.await()
                    val curatedResults = curatedDeferred.awaitAll()

                    val finalSections = mutableListOf<MusicHomeSection>()

                    // Sections in order: Quick Picks, Made For You, New Releases, Trending Artists, Top Artists, Charts, Moods & Genres, Trending, Curated, Podcasts (at bottom)
                    val quickPicks = rawHomeSections.find { it.title.contains("Quick", true) || it.title.contains("Recent", true) }
                    val madeForYou = rawHomeSections.find { it.title.contains("Made For", true) || it.title.contains("Mix", true) }
                    val newReleases = rawHomeSections.find { it.title.contains("New", true) || it.title.contains("Release", true) }
                    val trendingArtists = rawHomeSections.find { it.title.contains("Artist", true) }
                    val charts = rawHomeSections.find { it.title.contains("Chart", true) }
                    val popularPlaylists = rawHomeSections.find { it.title.contains("Popular", true) || it.title.contains("Playlist", true) }

                    quickPicks?.let { finalSections.add(it) }
                    madeForYou?.let { finalSections.add(it) }
                    newReleases?.let { finalSections.add(it) }
                    trendingArtists?.let { finalSections.add(it) }

                    if (topArtists.isNotEmpty()) {
                        finalSections.add(MusicHomeSection("Top Artists", topArtists.map { 
                            MusicHomeItem(it.title, it.artist, it.videoId, it.thumbnailUrl, MusicItemType.ARTIST)
                        }))
                    }

                    charts?.let { finalSections.add(it) }

                    // Moods & Genres
                    if (moodSections.isNotEmpty()) {
                        val items = moodSections.flatMap { it.items }.shuffled().take(12)
                        finalSections.add(MusicHomeSection("Moods & Genres", items))
                    }

                    // Trending
                    if (trendingSongs.isNotEmpty()) {
                        finalSections.add(MusicHomeSection("Trending", trendingSongs.map { 
                            MusicHomeItem(it.title, it.artist, it.videoId, it.thumbnailUrl, MusicItemType.SONG)
                        }))
                    }

                    popularPlaylists?.let { finalSections.add(it) }

                    // Curated Shelves
                    curatedResults.forEach { (title, songs) ->
                        if (songs.isNotEmpty()) {
                            finalSections.add(MusicHomeSection(title, songs.map { 
                                MusicHomeItem(it.title, it.artist, it.videoId, it.thumbnailUrl, MusicItemType.SONG)
                            }))
                        }
                    }

                    // Add any remaining raw home sections that weren't categorized (except Podcasts)
                    rawHomeSections.forEach { section ->
                        if (finalSections.none { it.title == section.title } && !section.title.contains("Podcast", true)) {
                            finalSections.add(section)
                        }
                    }

                    // Podcasts ALWAYS at the bottom as a rich vertical list
                    if (podcastSongs.isNotEmpty()) {
                        finalSections.add(MusicHomeSection("Podcasts", podcastSongs.map { 
                            MusicHomeItem(it.title, it.artist, it.videoId, it.thumbnailUrl, MusicItemType.SONG)
                        }))
                    }

                    _homeSections.postValue(Resource.Success(finalSections))
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load merged home sections", e)
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

    fun loadBrowseResult(params: String) {
        _searchResult.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val songs = repository.getBrowseResults(null, params)
                _searchResult.postValue(Resource.Success(songs))
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load browse results", e)
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
                Log.e("MusicViewModel", "Failed to load artist details", e)
                _homeSections.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
    }

    fun loadBrowseSections(browseId: String?, params: String?) {
        _homeSections.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val sections = repository.getBrowseSections(browseId, params)
                _homeSections.postValue(Resource.Success(sections))
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load browse sections", e)
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
                val streamUrlFound = extractStreamUrl(song.videoId, song.params)

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
    private suspend fun extractStreamUrl(videoId: String, params: String? = null): String? {
        // 0. Check cache
        StreamUrlCache.get(videoId)?.let { return it }

        // 0.1 Check if already downloaded
        com.lagradost.cloudstream3.CloudStreamApp.context?.let { ctx ->
            val downloadManager = com.lagradost.cloudstream3.services.music.MusicDownloadManager.getDownloadManager(ctx)
            val download = downloadManager?.downloadIndex?.getDownload(videoId)
            if (download != null && download.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                return download.request.uri.toString()
            }
        }

        // 1. Try InnerTube
        try {
            val playerResult = YouTubeInstance.youtube.player(videoId, params, false).getOrNull()
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

    private suspend fun extractAndCache(videoId: String, params: String? = null): String? {
        val url = extractStreamUrl(videoId, params)
        if (url != null) {
            StreamUrlCache.put(videoId, url)
        }
        return url
    }

    fun prefetchUrl(videoId: String, params: String? = null) {
        if (StreamUrlCache.get(videoId) != null) return
        prefetchScope.launch {
            prefetchSemaphore.withPermit {
                extractAndCache(videoId, params)
            }
        }
    }

    fun updateCurrentSong(mediaId: String?) {
        if (mediaId == null) return
        val song = currentQueueList.find { it.videoId == mediaId }
        if (song != null && _currentPlayingSong.value?.videoId != song.videoId) {
            _currentPlayingSong.postValue(song)
            fetchLyrics(song)
            addToHistory(song)
        }
    }

    fun playQueue(songs: List<MusicSearchResponse>, startIndex: Int) {
        val requestId = ++currentQueueRequestId
        queueJob?.cancel()
        
        currentQueueList.clear()
        currentQueueList.addAll(songs)
        _currentQueue.postValue(currentQueueList.toList())
        
        _queueReady.postValue(Event(Resource.Loading() to requestId))
        
        queueJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (songs.isEmpty()) {
                    _queueReady.postValue(Event(Resource.Failure(false, "Empty song list") to requestId))
                    return@launch
                }

                val safeStartIndex = if (startIndex in songs.indices) startIndex else 0
                val selectedSong = songs[safeStartIndex]

                // 1. Extract selected song immediately for fast start
                val firstUrl = extractAndCache(selectedSong.videoId, selectedSong.params)
                if (firstUrl != null) {
                    if (!isActive) return@launch

                    // Post initial single-item queue to start playback ASAP
                    val initialQueue = listOf(selectedSong to firstUrl)
                    _queueReady.postValue(Event(Resource.Success(initialQueue to 0) to requestId))
                    
                    // Update current song UI
                    _currentPlayingSong.postValue(selectedSong)
                    fetchLyrics(selectedSong)
                    addToHistory(selectedSong)

                    // 2. Extract remaining songs in parallel
                    val maxQueueSize = 30
                    val subset = songs.take(maxQueueSize)
                    
                    val fullQueue = coroutineScope {
                        subset.map { song ->
                            async {
                                if (song.videoId == selectedSong.videoId) {
                                    song to firstUrl
                                } else {
                                    val url = extractAndCache(song.videoId, song.params)
                                    if (url != null) song to url else null
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }

                    if (!isActive) return@launch

                    if (fullQueue.size > 1) {
                        val adjustedStartIndex = fullQueue.indexOfFirst { it.first.videoId == selectedSong.videoId }.coerceAtLeast(0)
                        // Post the full populated queue via queueUpdate to avoid prepare() hiccup
                        _queueUpdate.postValue(Event(Resource.Success(fullQueue to adjustedStartIndex) to requestId))
                    }
                } else {
                    _queueReady.postValue(Event(Resource.Failure(false, "Could not extract audio for: ${selectedSong.title}") to requestId))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("MusicViewModel", "Failed to build queue", e)
                _queueReady.postValue(Event(Resource.Failure(false, e.message ?: "Queue error") to requestId))
            }
        }
    }

    fun addToQueue(song: MusicSearchResponse) {
        currentQueueList.add(song)
        _currentQueue.postValue(currentQueueList.toList())
        viewModelScope.launch(Dispatchers.IO) {
            val url = extractStreamUrl(song.videoId, song.params)
            if (url != null) {
                sendQueueIntent(MusicService.ACTION_ADD_TO_QUEUE, song, url)
            }
        }
    }

    fun playNext(song: MusicSearchResponse) {
        val currentIndex = currentQueueList.indexOfFirst { it.videoId == _currentPlayingSong.value?.videoId }
        if (currentIndex != -1) {
            currentQueueList.add(currentIndex + 1, song)
        } else {
            currentQueueList.add(song)
        }
        _currentQueue.postValue(currentQueueList.toList())
        viewModelScope.launch(Dispatchers.IO) {
            val url = extractStreamUrl(song.videoId, song.params)
            if (url != null) {
                sendQueueIntent(MusicService.ACTION_PLAY_NEXT, song, url)
            }
        }
    }

    private fun sendQueueIntent(action: String, song: MusicSearchResponse, url: String) {
        val context = com.lagradost.cloudstream3.CloudStreamApp.context ?: return
        val intent = Intent(context, MusicService::class.java).apply {
            this.action = action
            putExtra(MusicService.EXTRA_URL, url)
            putExtra(MusicService.EXTRA_TITLE, song.title)
            putExtra(MusicService.EXTRA_ARTIST, song.artist)
            putExtra(MusicService.EXTRA_THUMBNAIL, song.thumbnailUrl)
            putExtra(MusicService.EXTRA_VIDEO_ID, song.videoId)
        }
        context.startService(intent)
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

    fun updateRateStatus(videoId: String) {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            val status = accountRepository.getRateStatus(videoId)
            _rateStatus.postValue(status)
        }
    }

    fun rateSong(videoId: String, status: RateStatus) {
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            if (accountRepository.rateSong(videoId, status)) {
                _rateStatus.postValue(status)
            }
        }
    }

    fun startRadio(videoId: String) {
        val requestId = ++currentQueueRequestId
        _queueReady.postValue(Event(Resource.Loading() to requestId))
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            val songs = radioManager.startRadio(videoId)
            if (songs.isNotEmpty()) {
                playQueue(songs, 0)
            } else {
                _queueReady.postValue(Event(Resource.Failure(false, "Failed to start radio") to requestId))
            }
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
                val url = "https://lrclib.net/api/get?artist_name=${artist.encodeUrl()}&track_name=${title.encodeUrl()}"
                val response = app.get(url)
                if (response.isSuccessful) {
                    val lyricsData = response.parsed<LyricsResponse>()
                    _lyrics.postValue(Resource.Success(lyricsData))
                } else {
                    _lyrics.postValue(Resource.Failure(false, "Lyrics not found"))
                }
            } catch (e: Exception) {
                _lyrics.postValue(Resource.Failure(false, "Lyrics error: ${e.message}"))
            }
        }
    }
}
