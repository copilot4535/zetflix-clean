package com.maxrave.kotlinytmusicscraper.extractor

import com.maxrave.kotlinytmusicscraper.models.SongItem
import com.maxrave.kotlinytmusicscraper.models.response.DownloadProgress
import com.maxrave.logger.Logger
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

private const val TAG = "Extractor"

actual class Extractor {
    private var downloader = BraveNewPipeDownloaderImpl(proxy = null)

    actual fun init() {
        try {
            NewPipe.getDownloader()
        } catch (e: Exception) {
            NewPipe.init(downloader)
        }
    }

    actual fun logIn(cookie: String?) {
        // org.schabi version might not support tokens directly like this in ServiceList
        // but we can set it via downloader if needed or use a custom service
    }

    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        return braveStreams(videoId)
    }

    private fun braveStreams(videoId: String): List<Pair<Int, String>> =
        runCatching {
            val streamInfo =
                StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            streamsList
                .mapNotNull {
                    (it.itagItem?.id ?: return@mapNotNull null) to it.content
                }.also {
                    ExtractSource.record(videoId, "BravePipe")
                    Logger.d(TAG, "extract source=BravePipe itags=${it.map { pair -> pair.first }} for $videoId")
                }
        }.onFailure {
            Logger.w(TAG, "BravePipe extractor failed for $videoId: ${it.message}")
        }.getOrElse { emptyList() }

    actual fun mergeAudioVideoDownload(filePath: String): DownloadProgress {
        return DownloadProgress.failed("Not implemented")
    }

    actual fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress {
        return DownloadProgress.failed("Not implemented")
    }
}
