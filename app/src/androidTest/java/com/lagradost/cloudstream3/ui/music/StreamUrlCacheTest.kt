package com.lagradost.cloudstream3.ui.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamUrlCacheTest {

    @Before
    fun setup() {
        StreamUrlCache.clear()
    }

    @Test
    fun putAndGetUrlWorks() {
        val videoId = "test_video_id"
        val url = "https://example.com/stream.m3u8"
        
        StreamUrlCache.put(videoId, url)
        
        assertEquals(url, StreamUrlCache.get(videoId))
    }

    @Test
    fun getNonExistentIdReturnsNull() {
        assertNull(StreamUrlCache.get("non_existent"))
    }

    @Test
    fun lruEvictionWorks() {
        // Cache size is 50. Fill it.
        for (i in 1..50) {
            StreamUrlCache.put("id_$i", "url_$i")
        }
        
        // Verify all 50 are there
        for (i in 1..50) {
            assertEquals("url_$i", StreamUrlCache.get("id_$i"))
        }
        
        // Add one more, id_1 (the oldest) should be evicted
        StreamUrlCache.put("id_51", "url_51")
        
        assertNull("id_1 should have been evicted", StreamUrlCache.get("id_1"))
        assertEquals("url_51", StreamUrlCache.get("id_51"))
        assertEquals("url_2", StreamUrlCache.get("id_2")) // Should still be there
    }

    @Test
    fun removeWorks() {
        val videoId = "to_remove"
        StreamUrlCache.put(videoId, "some_url")
        assertEquals("some_url", StreamUrlCache.get(videoId))
        
        StreamUrlCache.remove(videoId)
        assertNull(StreamUrlCache.get(videoId))
    }
}
