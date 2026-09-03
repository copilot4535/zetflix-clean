package com.lagradost.cloudstream3.ui.music

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.lagradost.cloudstream3.services.music.MusicDownloadManager
import com.lagradost.cloudstream3.services.music.MusicDownloadService

@UnstableApi
class MusicDownloadRepository(private val context: Context) {
    fun downloadSong(song: MusicSearchResponse, url: String) {
        val downloadRequest = DownloadRequest.Builder(song.videoId, Uri.parse(url))
            .setData(song.title.toByteArray()) // Simple way to store some data in DownloadManager
            .build()
        
        DownloadService.sendAddDownload(
            context,
            MusicDownloadService::class.java,
            downloadRequest,
            /* foreground= */ true
        )
        MusicPersistence.addDownloadedSong(song)
    }

    fun removeDownload(videoId: String) {
        DownloadService.sendRemoveDownload(
            context,
            MusicDownloadService::class.java,
            videoId,
            /* foreground= */ false
        )
        MusicPersistence.removeDownloadedSong(videoId)
    }

    fun isDownloaded(videoId: String): Boolean {
        return MusicPersistence.getDownloadedSongs().any { it.videoId == videoId }
    }

    fun getDownloadedSongs(): List<MusicSearchResponse> {
        return MusicPersistence.getDownloadedSongs()
    }
}
