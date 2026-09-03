package com.lagradost.cloudstream3.ui.music

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

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

    private val _streamUrl = MutableLiveData<Resource<Pair<String, MusicSearchResponse>>>()
    val streamUrl: LiveData<Resource<Pair<String, MusicSearchResponse>>> = _streamUrl

    private val _lyrics = MutableLiveData<Resource<LyricsResponse>>()
    val lyrics: LiveData<Resource<LyricsResponse>> = _lyrics

    private val _currentPlayingSong = MutableLiveData<MusicSearchResponse?>()
    val currentPlayingSong: LiveData<MusicSearchResponse?> = _currentPlayingSong

    private fun initNewPipe() {
        try {
            NewPipe.getDownloader()
        } catch (e: Exception) {
            DownloaderTestImpl.getInstance()?.let {
                NewPipe.init(it)
            }
        }
    }

    fun search(query: String) {
        _searchResult.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val results = repository.searchSongs(query)
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

    fun loadHomeSections() {
        _homeSections.postValue(Resource.Loading())
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val sections = repository.getHomeSections()
                _homeSections.postValue(Resource.Success(sections))
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load home sections", e)
                _homeSections.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
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

    fun loadStreamAndPlay(song: MusicSearchResponse) {
        _streamUrl.postValue(Resource.Loading())
        _currentPlayingSong.postValue(song)
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Try InnerTube (YouTubeInstance) primary extraction
                var streamUrlFound: String? = null
                try {
                    val playerResult = YouTubeInstance.youtube.player(song.videoId, null, false).getOrNull()
                    val formats = playerResult?.second?.streamingData?.adaptiveFormats
                    streamUrlFound = formats?.filter { it.isAudio }?.maxByOrNull { it.bitrate }?.url
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "InnerTube extraction failed for ${song.videoId}", e)
                }

                // 2. Fallback to NewPipe if InnerTube failed or URL is null
                if (streamUrlFound.isNullOrBlank()) {
                    try {
                        initNewPipe()
                        val service = ServiceList.YouTube
                        val info = StreamInfo.getInfo(service, song.videoId)
                        streamUrlFound = info.audioStreams.firstOrNull()?.content
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "NewPipe fallback failed for ${song.videoId}", e)
                    }
                }

                // 3. Post result if URL is valid
                if (!streamUrlFound.isNullOrBlank() && (streamUrlFound.startsWith("http://") || streamUrlFound.startsWith("https://"))) {
                    _streamUrl.postValue(Resource.Success(streamUrlFound to song))
                    // Fetch lyrics as well
                    fetchLyrics(song)
                } else {
                    _streamUrl.postValue(Resource.Failure(false, "Could not extract audio stream for: ${song.title}"))
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Total stream extraction failure", e)
                _streamUrl.postValue(Resource.Failure(false, e.message ?: "Unknown error"))
            }
        }
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
