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
import org.schabi.newpipe.extractor.stream.StreamInfo
import com.lagradost.cloudstream3.ui.music.spotify.SpotifyRepository
import com.lagradost.cloudstream3.utils.DataStoreHelper

data class MusicSearchResponse(
    val title: String,
    val artist: String?,
    val videoId: String,
    val thumbnailUrl: String?,
)

class MusicViewModel : ViewModel() {
    private val repository = MusicRepository()
    private val spotifyRepository = SpotifyRepository()

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

    private val _canvasUrl = MutableLiveData<String?>()
    val canvasUrl: LiveData<String?> = _canvasUrl

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
        _canvasUrl.postValue(null) // Reset canvas
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("MusicViewModel", "Extracting stream for ${song.title} (${song.videoId})")
                
                // Primary: Try YouTube player (InnerTube)
                val playerResult = YouTubeInstance.youtube.player(song.videoId)
                val triple = playerResult.getOrNull()
                val playerResponse = triple?.second
                
                if (playerResponse?.playabilityStatus?.status == "OK") {
                    val audioUrl = playerResponse.streamingData?.adaptiveFormats
                        ?.filter { it.isAudio }
                        ?.maxByOrNull { it.bitrate }?.url
                    
                    if (audioUrl != null && audioUrl.startsWith("http")) {
                        Log.d("MusicViewModel", "Found audio URL via InnerTube: $audioUrl")
                        _streamUrl.postValue(Resource.Success(audioUrl to song))
                        fetchLyrics(song)
                        fetchCanvas(song)
                        return@launchSafe
                    }
                }

                Log.w("MusicViewModel", "InnerTube extraction failed (status: ${playerResponse?.playabilityStatus?.status}) or returned invalid URL, falling back to NewPipe")

                // Secondary: Fallback to NewPipe
                initNewPipe()
                val service = ServiceList.YouTube
                val info = StreamInfo.getInfo(service, song.videoId)
                val audioStream = info.audioStreams.firstOrNull()
                if (audioStream != null && audioStream.content.startsWith("http")) {
                    Log.d("MusicViewModel", "Found audio URL via NewPipe: ${audioStream.content}")
                    _streamUrl.postValue(Resource.Success(audioStream.content to song))
                    fetchLyrics(song)
                    fetchCanvas(song)
                } else {
                    Log.e("MusicViewModel", "All extraction methods failed for ${song.videoId}")
                    _streamUrl.postValue(Resource.Failure(false, "Could not extract audio stream"))
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Exception during stream extraction for ${song.videoId}", e)
                _streamUrl.postValue(Resource.Failure(false, "Error: ${e.message ?: "Unknown error"}"))
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

    private fun fetchCanvas(song: MusicSearchResponse) {
        val spDc = DataStoreHelper.spotifySpDc ?: return
        
        viewModelScope.launchSafe(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val accessToken = spotifyRepository.getAccessToken(spDc) ?: return@launchSafe
                val clientToken = spotifyRepository.getClientToken() ?: return@launchSafe
                
                val query = "${song.title} ${song.artist ?: ""}"
                val trackId = spotifyRepository.searchTrack(query, accessToken, clientToken) ?: return@launchSafe
                
                val canvasUrl = spotifyRepository.getCanvasUrl(trackId, accessToken, clientToken)
                if (canvasUrl != null) {
                    _canvasUrl.postValue(canvasUrl)
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to fetch canvas", e)
            }
        }
    }
}
