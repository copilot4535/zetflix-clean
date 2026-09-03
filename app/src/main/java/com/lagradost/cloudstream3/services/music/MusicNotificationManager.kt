package com.lagradost.cloudstream3.services.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.lagradost.cloudstream3.R

@UnstableApi
class MusicNotificationManager(private val context: Context) {
    fun getDownloadNotification(downloads: List<Download>): Notification {
        val channelId = "music_download_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Music Downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val downloadingCount = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val title = if (downloadingCount > 0) "Downloading $downloadingCount songs" else "Music Downloads"
        
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.netflix_download)
            .setOngoing(downloadingCount > 0)
            .build()
    }
}
