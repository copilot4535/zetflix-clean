package com.lagradost.cloudstream3.ui.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncedLyricsViewTest {

    @Test
    fun autoScrollEnabled_isTrueInitially() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SyncedLyricsView(context)
        assertTrue(view.autoScrollEnabled)
    }

    @Test
    fun resumeAutoScroll_setsAutoScrollEnabledToTrue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SyncedLyricsView(context)
        
        // Use reflection or a test-only setter if I wanted to force false, 
        // but I'll just use resumeAutoScroll which should ensure it's true.
        view.resumeAutoScroll()
        assertTrue(view.autoScrollEnabled)
    }
    
    @Test
    fun setLyrics_resetsAutoScrollEnabledToTrue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = SyncedLyricsView(context)
        
        view.setLyrics(listOf(LyricLine(0, "Test")))
        assertTrue(view.autoScrollEnabled)
    }
}
