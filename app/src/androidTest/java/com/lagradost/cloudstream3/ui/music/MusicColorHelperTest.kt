package com.lagradost.cloudstream3.ui.music

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicColorHelperTest {

    @Test
    fun calculateLuminance_returnsCorrectValues() {
        // White should be 1.0
        assertEquals(1.0f, MusicColorHelper.calculateLuminance(Color.WHITE), 0.01f)
        // Black should be 0.0
        assertEquals(0.0f, MusicColorHelper.calculateLuminance(Color.BLACK), 0.01f)
        // Red (1, 0, 0) -> 0.2126
        assertEquals(0.2126f, MusicColorHelper.calculateLuminance(Color.RED), 0.01f)
        // Green (0, 1, 0) -> 0.7152
        assertEquals(0.7152f, MusicColorHelper.calculateLuminance(Color.GREEN), 0.01f)
        // Blue (0, 0, 1) -> 0.0722
        assertEquals(0.0722f, MusicColorHelper.calculateLuminance(Color.BLUE), 0.01f)
    }

    @Test
    fun darkenColor_reducesIndividualColorChannels() {
        val color = Color.rgb(100, 100, 100)
        val ratio = 0.5f
        val darkened = MusicColorHelper.darkenColor(color, ratio)
        
        assertEquals(50, Color.red(darkened))
        assertEquals(50, Color.green(darkened))
        assertEquals(50, Color.blue(darkened))
    }

    @Test
    fun darkenColor_preservesAlpha() {
        val color = Color.argb(128, 255, 255, 255)
        val darkened = MusicColorHelper.darkenColor(color, 0.5f)
        
        assertEquals(128, Color.alpha(darkened))
    }
}
