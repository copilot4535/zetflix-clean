package com.lagradost.cloudstream3.services.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import org.schabi.newpipe.extractor.NewPipe

class MusicService : MediaSessionService() {
    companion object {
        const val ACTION_PLAY = "com.lagradost.cloudstream3.services.music.ACTION_PLAY"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_ARTIST = "EXTRA_ARTIST"
        const val EXTRA_THUMBNAIL = "EXTRA_THUMBNAIL"
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        try {
            NewPipe.getDownloader()
        } catch (e: Exception) {
            DownloaderTestImpl.getInstance()?.let {
                NewPipe.init(it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY) {
            val url = intent.getStringExtra(EXTRA_URL)
            val title = intent.getStringExtra(EXTRA_TITLE)
            val artist = intent.getStringExtra(EXTRA_ARTIST)
            val thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL)

            Log.d("MusicService", "Received ACTION_PLAY for: $title, URL: $url")

            if (url != null && url.startsWith("http")) {
                showNotification(title, artist)
                play(url, title, artist, thumbnail)
            } else {
                Log.e("MusicService", "Invalid or null URL received in ACTION_PLAY")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showNotification(title: String?, artist: String?) {
        val channelId = "music_playback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Music Playback"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title ?: "Playing Music")
            .setContentText(artist ?: "")
            .setSmallIcon(com.lagradost.cloudstream3.R.drawable.ic_baseline_play_arrow_24)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun play(url: String, title: String?, artist: String?, thumbnail: String?) {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumbnail?.toUri())
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()

        player?.let {
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        player?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
