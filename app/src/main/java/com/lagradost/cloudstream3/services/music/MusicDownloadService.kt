package com.lagradost.cloudstream3.services.music

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.lagradost.cloudstream3.R

@UnstableApi
class MusicDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download,
    0
) {
    override fun getDownloadManager(): DownloadManager {
        return MusicDownloadManager.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler? {
        return null // Handle background scheduling via WorkManager if needed
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return MusicNotificationManager(this).getDownloadNotification(downloads)
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "music_download_channel"
    }
}
