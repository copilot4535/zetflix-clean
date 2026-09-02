package com.maxrave.kotlinytmusicscraper.extractor

import com.maxrave.kotlinytmusicscraper.models.SongItem
import com.maxrave.kotlinytmusicscraper.models.response.DownloadProgress

actual class Extractor {
    actual fun init() {}
    actual fun logIn(cookie: String?) {}
    actual fun mergeAudioVideoDownload(filePath: String): DownloadProgress = DownloadProgress.failed("Not implemented")
    actual fun saveAudioWithThumbnail(filePath: String, track: SongItem): DownloadProgress = DownloadProgress.failed("Not implemented")
    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> = emptyList()
}
