package com.lagradost.cloudstream3.ui.music

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlin.math.roundToInt

@UnstableApi
data class TechnicalAudioSpecs(
    val codec: String?,
    val container: String?,
    val sampleRateKHz: String?,
    val bitrateKbps: String?
)

@UnstableApi
object SongMetadataParser {
    fun extractSpecs(controller: MediaController?): TechnicalAudioSpecs {
        val format = controller?.currentTracks?.groups
            ?.flatMap { it.getTrackFormat(0).let { f -> listOf(f) } }
            ?.firstOrNull { it.sampleRate != Format.NO_VALUE }

        return TechnicalAudioSpecs(
            codec = format?.sampleMimeType?.substringAfterLast('/'),
            container = format?.containerMimeType?.substringAfterLast('/'),
            sampleRateKHz = format?.sampleRate?.let { "${it / 1000.0} kHz" },
            bitrateKbps = format?.bitrate?.let { if (it != Format.NO_VALUE) "${(it / 1000.0).roundToInt()} kbps" else null }
        )
    }
}
