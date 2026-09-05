package com.lagradost.cloudstream3.ui.music

import kotlinx.serialization.Serializable

@Serializable
data class MusicBackupData(
    val likedSongs: List<MusicSearchResponse>,
    val history: List<MusicSearchResponse>,
    val playlists: List<MusicPlaylist>,
    val searchHistory: List<String>,
    val cookie: String?,
    val timestamp: Long = System.currentTimeMillis()
)
