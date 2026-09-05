package com.lagradost.cloudstream3.services.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import org.schabi.newpipe.extractor.NewPipe

@UnstableApi
class MusicService : MediaSessionService() {
    companion object {
        const val ACTION_PLAY = "com.lagradost.cloudstream3.services.music.ACTION_PLAY"
        const val ACTION_PLAY_QUEUE = "com.lagradost.cloudstream3.services.music.ACTION_PLAY_QUEUE"
        const val ACTION_UPDATE_QUEUE = "com.lagradost.cloudstream3.services.music.ACTION_UPDATE_QUEUE"
        const val ACTION_ADD_TO_QUEUE = "com.lagradost.cloudstream3.services.music.ACTION_ADD_TO_QUEUE"
        const val ACTION_PLAY_NEXT = "com.lagradost.cloudstream3.services.music.ACTION_PLAY_NEXT"
        const val ACTION_STOP = "com.lagradost.cloudstream3.services.music.ACTION_STOP"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_ARTIST = "EXTRA_ARTIST"
        const val EXTRA_THUMBNAIL = "EXTRA_THUMBNAIL"
        const val EXTRA_VIDEO_ID = "EXTRA_VIDEO_ID"

        const val EXTRA_URLS = "EXTRA_URLS"
        const val EXTRA_TITLES = "EXTRA_TITLES"
        const val EXTRA_ARTISTS = "EXTRA_ARTISTS"
        const val EXTRA_THUMBNAILS = "EXTRA_THUMBNAILS"
        const val EXTRA_VIDEO_IDS = "EXTRA_VIDEO_IDS"
        const val EXTRA_START_INDEX = "EXTRA_START_INDEX"
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = MusicDownloadManager.getReadOnlyDataSourceFactory(this)
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
            
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                updateWidget()
            }
        })
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: androidx.media3.session.SessionCommand,
                    args: android.os.Bundle
                ): ListenableFuture<androidx.media3.session.SessionResult> {
                    if (customCommand.customAction == "GET_AUDIO_SESSION_ID") {
                        val resultBundle = android.os.Bundle().apply {
                            putInt("AUDIO_SESSION_ID", exoPlayer.audioSessionId)
                        }
                        return com.google.common.util.concurrent.Futures.immediateFuture(
                            androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS, resultBundle)
                        )
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        try {
            NewPipe.getDownloader()
        } catch (e: Exception) {
            DownloaderTestImpl.getInstance()?.let {
                NewPipe.init(it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PLAY) {
            val url = intent.getStringExtra(EXTRA_URL)
            val title = intent.getStringExtra(EXTRA_TITLE)
            val artist = intent.getStringExtra(EXTRA_ARTIST)
            val thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL)
            val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)

            Log.d("MusicService", "Received ACTION_PLAY for: $title, URL: $url")

            if (!url.isNullOrBlank()) {
                showNotification(title, artist)
                play(url, title, artist, thumbnail, videoId)
            } else {
                Log.e("MusicService", "Invalid or null URL received. Stopping service.")
                stopForeground(true)
                stopSelf()
            }
        } else if (intent?.action == ACTION_PLAY_QUEUE) {
            val urls = intent.getStringArrayListExtra(EXTRA_URLS)
            val titles = intent.getStringArrayListExtra(EXTRA_TITLES)
            val artists = intent.getStringArrayListExtra(EXTRA_ARTISTS)
            val thumbnails = intent.getStringArrayListExtra(EXTRA_THUMBNAILS)
            val videoIds = intent.getStringArrayListExtra(EXTRA_VIDEO_IDS)
            val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

            if (!urls.isNullOrEmpty()) {
                val mediaItems = mutableListOf<MediaItem>()
                for (i in urls.indices) {
                    val url = urls[i]
                    val title = titles?.getOrNull(i)
                    val artist = artists?.getOrNull(i)
                    val thumbnail = thumbnails?.getOrNull(i)
                    val videoId = videoIds?.getOrNull(i)

                    val metadata = MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setArtworkUri(thumbnail?.toUri())
                        .build()

                    mediaItems.add(
                        MediaItem.Builder()
                            .setMediaId(videoId ?: url)
                            .setUri(url)
                            .setMediaMetadata(metadata)
                            .build()
                    )
                }

                if (mediaItems.isEmpty()) {
                    Log.e("MusicService", "Empty media items in queue. Stopping.")
                    stopForeground(true)
                    stopSelf()
                    return super.onStartCommand(intent, flags, startId)
                }

                val safeStartIndex = if (startIndex in mediaItems.indices) startIndex else 0
                val currentTitle = titles?.getOrNull(safeStartIndex) ?: "Playing Queue"
                val currentArtist = artists?.getOrNull(safeStartIndex) ?: ""
                showNotification(currentTitle, currentArtist)

                player?.let {
                    it.setMediaItems(mediaItems, safeStartIndex, 0)
                    it.prepare()
                    it.play()
                }
            }
        } else if (intent?.action == ACTION_UPDATE_QUEUE) {
            val urls = intent.getStringArrayListExtra(EXTRA_URLS)
            val titles = intent.getStringArrayListExtra(EXTRA_TITLES)
            val artists = intent.getStringArrayListExtra(EXTRA_ARTISTS)
            val thumbnails = intent.getStringArrayListExtra(EXTRA_THUMBNAILS)
            val videoIds = intent.getStringArrayListExtra(EXTRA_VIDEO_IDS)
            val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

            if (!urls.isNullOrEmpty()) {
                val mediaItems = mutableListOf<MediaItem>()
                for (i in urls.indices) {
                    val url = urls[i]
                    val metadata = MediaMetadata.Builder()
                        .setTitle(titles?.getOrNull(i))
                        .setArtist(artists?.getOrNull(i))
                        .setArtworkUri(thumbnails?.getOrNull(i)?.toUri())
                        .build()

                    mediaItems.add(
                        MediaItem.Builder()
                            .setMediaId(videoIds?.getOrNull(i) ?: url)
                            .setUri(url)
                            .setMediaMetadata(metadata)
                            .build()
                    )
                }

                player?.let {
                    val isActive = it.playbackState != ExoPlayer.STATE_IDLE
                    if (isActive) {
                        it.setMediaItems(mediaItems, startIndex, it.currentPosition)
                        // No prepare/play to avoid hiccup
                    } else {
                        it.setMediaItems(mediaItems, startIndex, 0)
                        it.prepare()
                        it.play()
                    }
                }
            }
        } else if (intent?.action == ACTION_ADD_TO_QUEUE || intent?.action == ACTION_PLAY_NEXT) {
            val url = intent.getStringExtra(EXTRA_URL)
            val title = intent.getStringExtra(EXTRA_TITLE)
            val artist = intent.getStringExtra(EXTRA_ARTIST)
            val thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL)
            val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)

            if (!url.isNullOrBlank()) {
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(thumbnail?.toUri())
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(videoId ?: url)
                    .setUri(url)
                    .setMediaMetadata(metadata)
                    .build()

                player?.let {
                    if (intent.action == ACTION_ADD_TO_QUEUE) {
                        it.addMediaItem(mediaItem)
                    } else {
                        it.addMediaItem(it.currentMediaItemIndex + 1, mediaItem)
                    }
                }
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

    private fun play(url: String, title: String?, artist: String?, thumbnail: String?, videoId: String? = null) {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumbnail?.toUri())
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(videoId ?: url)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()

        player?.let {
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }

    private fun updateWidget() {
        val intent = Intent(this, com.lagradost.cloudstream3.ui.music.MusicWidgetProvider::class.java).apply {
            action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = android.appwidget.AppWidgetManager.getInstance(application)
                .getAppWidgetIds(ComponentName(application, com.lagradost.cloudstream3.ui.music.MusicWidgetProvider::class.java))
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Continue playing in background even if task is removed from recents
        // super.onTaskRemoved(rootIntent) // By default MediaSessionService stops playback
    }

    override fun onDestroy() {
        player?.release()
        mediaSession?.release()
        super.onDestroy()
    }
}
