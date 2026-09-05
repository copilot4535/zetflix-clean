package com.lagradost.cloudstream3.ui.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.widget.RemoteViews
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.services.music.MusicService
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@UnstableApi
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val appContext = context.applicationContext
        val views = RemoteViews(appContext.packageName, R.layout.widget_music)

        val sessionToken = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                val metadata = controller.mediaMetadata
                
                views.setTextViewText(R.id.widget_title, metadata.title ?: "Not Playing")
                views.setTextViewText(R.id.widget_artist, metadata.artist ?: "")
                
                val playPauseIcon = if (controller.isPlaying) R.drawable.netflix_pause else R.drawable.netflix_play
                views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

                // Intents
                views.setOnClickPendingIntent(R.id.widget_play_pause, getPendingIntent(appContext, ACTION_PLAY_PAUSE))
                views.setOnClickPendingIntent(R.id.widget_next, getPendingIntent(appContext, ACTION_NEXT))
                views.setOnClickPendingIntent(R.id.widget_prev, getPendingIntent(appContext, ACTION_PREV))
                
                val openIntent = Intent(appContext, MusicActivity::class.java)
                val openPendingIntent = PendingIntent.getActivity(appContext, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_container, openPendingIntent)

                metadata.artworkUri?.let { uri ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val request = ImageRequest.Builder(appContext)
                            .data(uri)
                            .size(200, 200)
                            .build()
                        val imgResult = appContext.imageLoader.execute(request).image
                        imgResult?.let {
                            views.setImageViewBitmap(R.id.widget_thumbnail, it.toBitmap())
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("MusicWidget", "Error updating widget", e)
            } finally {
                MediaController.releaseFuture(controllerFuture)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MusicWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in listOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeoutOrNull(5000L) {
                    val sessionToken = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
                    val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

                    val controller = suspendCancellableCoroutine<MediaController?> { continuation ->
                        controllerFuture.addListener({
                            try {
                                if (continuation.isActive) {
                                    continuation.resume(controllerFuture.get())
                                }
                            } catch (_: Exception) {
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }, MoreExecutors.directExecutor())
                        continuation.invokeOnCancellation {
                            MediaController.releaseFuture(controllerFuture)
                        }
                    }

                    controller?.let {
                        try {
                            when (action) {
                                ACTION_PLAY_PAUSE -> if (it.isPlaying) it.pause() else it.play()
                                ACTION_NEXT -> it.seekToNextMediaItem()
                                ACTION_PREV -> it.seekToPreviousMediaItem()
                            }
                        } finally {
                            MediaController.releaseFuture(controllerFuture)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicWidget", "Error in onReceive for action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.lagradost.cloudstream3.music.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.lagradost.cloudstream3.music.ACTION_NEXT"
        const val ACTION_PREV = "com.lagradost.cloudstream3.music.ACTION_PREV"
    }
}
