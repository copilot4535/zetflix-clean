package com.lagradost.cloudstream3.ui.music

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicPlayerHelperTest {

    @Test
    fun handlePrevious_restartsSong_whenPositionAboveThreshold() {
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 6000L
        
        MusicPlayerHelper.handlePrevious(player)
        
        verify { player.seekTo(0) }
        verify(exactly = 0) { player.seekToPreviousMediaItem() }
    }

    @Test
    fun handlePrevious_skipsToPrevious_whenPositionBelowThreshold() {
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 1000L
        
        MusicPlayerHelper.handlePrevious(player)
        
        verify { player.seekToPreviousMediaItem() }
        verify(exactly = 0) { player.seekTo(0) }
    }
}
