package com.lagradost.cloudstream3.ui.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.getString
import androidx.navigation.NavOptions
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.safefile.SafeFile

object OfflinePlaybackHelper {
    /**
     * Pop any existing player off the nav back stack before pushing the new one,
     * keeping the stack flat (at most one player at a time). This prevents an
     * OOM when many files are opened in sequence via DownloadedPlayerActivity.
     */
    private val replacePlayerNavOptions = NavOptions.Builder()
        .setPopUpTo(R.id.navigation_player, inclusive = true, saveState = false)
        .build()

    fun playLink(activity: Activity, url: String) {
        activity.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                LinkGenerator(
                    listOf(
                        BasicLink(url)
                    ), id = url.hashCode()
                ), 0
            ),
            replacePlayerNavOptions
        )
    }

    // See CloudStreamPackage
    fun playIntent(activity: Activity, intent: Intent?): Boolean {
        // TODO: External player integration removed. Keep internal playback only.
        return false
    }

    fun playUri(activity: Activity, uri: Uri) {
        if (uri.scheme == "magnet") {
            playLink(activity, uri.toString())
            return
        }
        val name = SafeFile.fromUri(activity, uri)?.name()
        activity.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                DownloadFileGenerator(
                    listOf(
                        ExtractorUri(
                            uri = uri,
                            name = name ?: getString(activity, R.string.downloaded_file),
                            // well not the same as a normal id, but we take it as users may want to
                            // play downloaded files and save the location
                            id = uri.lastPathSegment?.toLongOrNull()?.hashCode() ?: uri.lastPathSegment?.hashCode()
                        )
                    )
                ), 0
            ),
            replacePlayerNavOptions
        )
    }
}