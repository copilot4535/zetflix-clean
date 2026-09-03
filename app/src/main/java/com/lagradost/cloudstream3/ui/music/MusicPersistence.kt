package com.lagradost.cloudstream3.ui.music

import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import kotlinx.serialization.Serializable

@Serializable
data class MusicPlaylist(
    val name: String,
    val songs: List<MusicSearchResponse> = emptyList()
)

object MusicPersistence {
    private const val MUSIC_LIKED_SONGS = "music_liked_songs"
    private const val MUSIC_HISTORY = "music_history"
    private const val MUSIC_PLAYLISTS = "music_playlists"

    fun getLikedSongs(): List<MusicSearchResponse> {
        return getKey(currentAccount, MUSIC_LIKED_SONGS) ?: emptyList()
    }

    fun setLikedSongs(songs: List<MusicSearchResponse>) {
        setKey(currentAccount, MUSIC_LIKED_SONGS, songs)
    }

    fun toggleLikeSong(song: MusicSearchResponse) {
        val songs = getLikedSongs().toMutableList()
        if (songs.any { it.videoId == song.videoId }) {
            songs.removeAll { it.videoId == song.videoId }
        } else {
            songs.add(0, song)
        }
        setLikedSongs(songs)
    }

    fun isSongLiked(videoId: String): Boolean {
        return getLikedSongs().any { it.videoId == videoId }
    }

    fun getHistory(): List<MusicSearchResponse> {
        return getKey(currentAccount, MUSIC_HISTORY) ?: emptyList()
    }

    fun addSongToHistory(song: MusicSearchResponse) {
        val history = getHistory().toMutableList()
        history.removeAll { it.videoId == song.videoId }
        history.add(0, song)
        if (history.size > 50) {
            history.removeAt(history.size - 1)
        }
        setKey(currentAccount, MUSIC_HISTORY, history)
    }

    fun getPlaylists(): List<MusicPlaylist> {
        return getKey(currentAccount, MUSIC_PLAYLISTS) ?: emptyList()
    }

    fun savePlaylists(playlists: List<MusicPlaylist>) {
        setKey(currentAccount, MUSIC_PLAYLISTS, playlists)
    }

    fun createPlaylist(name: String) {
        val playlists = getPlaylists().toMutableList()
        if (playlists.none { it.name == name }) {
            playlists.add(MusicPlaylist(name))
            savePlaylists(playlists)
        }
    }

    fun addSongToPlaylist(playlistName: String, song: MusicSearchResponse) {
        val playlists = getPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.name == playlistName }
        if (index != -1) {
            val playlist = playlists[index]
            if (playlist.songs.none { it.videoId == song.videoId }) {
                playlists[index] = playlist.copy(songs = playlist.songs + song)
                savePlaylists(playlists)
            }
        }
    }
}
