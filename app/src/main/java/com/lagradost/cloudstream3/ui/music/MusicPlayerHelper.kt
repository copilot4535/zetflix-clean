package com.lagradost.cloudstream3.ui.music

import androidx.media3.common.Player

object MusicPlayerHelper {
    /**
     * Threshold in milliseconds to restart the current song instead of going to previous.
     */
    const val RESTART_THRESHOLD_MS = 5000L

    fun handlePrevious(player: Player) {
        if (player.currentPosition > RESTART_THRESHOLD_MS) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }
}
