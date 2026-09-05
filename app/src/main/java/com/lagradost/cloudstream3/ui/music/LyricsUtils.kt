package com.lagradost.cloudstream3.ui.music

import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponse(
    val id: Int? = null,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Int? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

@Serializable
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

typealias LrcLine = LyricLine

object LrcParser {
    private val lrcRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")

    fun parse(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        
        return lrc.lines().mapNotNull { line ->
            val match = lrcRegex.find(line)
            if (match != null) {
                val (mm, ss, ms, text) = match.destructured
                val timeMs = mm.toLong() * 60 * 1000 + 
                             ss.toLong() * 1000 + 
                             ms.padEnd(3, '0').take(3).toLong()
                LyricLine(timeMs, text.trim())
            } else null
        }
    }
}
