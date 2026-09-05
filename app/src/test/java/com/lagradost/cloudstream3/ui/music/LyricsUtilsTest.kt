package com.lagradost.cloudstream3.ui.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {

    @Test
    fun `parse valid LRC string returns correct list of lines`() {
        val lrc = """
            [00:12.34]Line one
            [01:23.45]Line two
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(2, result.size)
        
        // 00:12.34 -> 12 * 1000 + 340 = 12340ms
        assertEquals(12340L, result[0].timestampMs)
        assertEquals("Line one", result[0].text)
        
        // 01:23.45 -> 1 * 60 * 1000 + 23 * 1000 + 450 = 60000 + 23000 + 450 = 83450ms
        assertEquals(83450L, result[1].timestampMs)
        assertEquals("Line two", result[1].text)
    }

    @Test
    fun `parse LRC with 3 digit milliseconds returns correct timestamp`() {
        val lrc = "[00:01.123]Test"
        val result = LrcParser.parse(lrc)
        
        assertEquals(1, result.size)
        assertEquals(1123L, result[0].timestampMs)
        assertEquals("Test", result[0].text)
    }

    @Test
    fun `parse null or empty string returns empty list`() {
        assertTrue(LrcParser.parse(null).isEmpty())
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("   ").isEmpty())
    }

    @Test
    fun `parse string without timestamps returns empty list`() {
        val lrc = """
            This is just plain text
            No timestamps here
        """.trimIndent()
        
        val result = LrcParser.parse(lrc)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse malformed LRC lines skips them`() {
        val lrc = """
            [invalid]Line
            [00:10.00]Valid
            [00:11]Too short
        """.trimIndent()
        
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals("Valid", result[0].text)
    }
}
