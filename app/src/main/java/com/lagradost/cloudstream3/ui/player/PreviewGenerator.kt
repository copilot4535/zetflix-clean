package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.graphics.scale
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.log2

const val MAX_LOD = 6
const val MIN_LOD = 3

data class ImageParams(
    val width: Int,
    val height: Int,
) {
    companion object {
        val DEFAULT = ImageParams(200, 320)
        fun new16by9(width: Int): ImageParams {
            if (width < 100) {
                return DEFAULT
            }
            return ImageParams(
                width / 4,
                (width * 9) / (4 * 16)
            )
        }
    }

    init {
        assert(width > 0 && height > 0)
    }
}

interface IPreviewGenerator {
    fun hasPreview(): Boolean
    fun getPreviewImage(fraction: Float): Bitmap?
    fun release()

    var params: ImageParams

    var durationMs: Long
    var loadedImages: Int

    companion object {
        fun new(): IPreviewGenerator {
            val userDisabled = CloudStreamApp.context?.let { ctx ->
                PreferenceManager.getDefaultSharedPreferences(ctx)?.getBoolean(
                    ctx.getString(R.string.preview_seekbar_key), true
                ) == false
            } ?: false
            return if (userDisabled) {
                empty()
            } else {
                PreviewGenerator()
            }
        }

        fun empty(): IPreviewGenerator {
            return NoPreviewGenerator()
        }
    }
}

private fun rescale(image: Bitmap, params: ImageParams): Bitmap {
    if (image.width <= params.width && image.height <= params.height) return image
    val new = image.scale(params.width, params.height)
    if (new != image) {
        image.recycle()
    }
    return new
}

private fun MediaMetadataRetriever.image(timeUs: Long, params: ImageParams): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        this.getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            params.width,
            params.height
        )
    } else {
        return rescale(this.getFrameAtTime(timeUs) ?: return null, params)
    }
}

class PreviewGenerator : IPreviewGenerator {

    private var currentGenerator: IPreviewGenerator = NoPreviewGenerator()

    private var lastGenerator: IPreviewGenerator = NoPreviewGenerator()

    private val dummy: IPreviewGenerator = NoPreviewGenerator()

    private fun isSameLength(): Boolean =
        currentGenerator.durationMs.minus(lastGenerator.durationMs).absoluteValue < 10_000L

    private val backupGenerator: IPreviewGenerator
        get() {
            if (currentGenerator.durationMs == 0L || isSameLength()) {
                return lastGenerator
            }
            return dummy
        }

    override fun hasPreview(): Boolean {
        return currentGenerator.hasPreview() || backupGenerator.hasPreview()
    }

    override fun getPreviewImage(fraction: Float): Bitmap? {
        return try {
            currentGenerator.getPreviewImage(fraction) ?: backupGenerator.getPreviewImage(fraction)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    override fun release() {
        lastGenerator.release()
        currentGenerator.release()
        lastGenerator = NoPreviewGenerator()
        currentGenerator = NoPreviewGenerator()
    }

    override var params: ImageParams = ImageParams.DEFAULT
        set(value) {
            field = value
            lastGenerator.params = value
            backupGenerator.params = value
            currentGenerator.params = value
        }

    override var durationMs: Long
        get() = currentGenerator.durationMs
        set(_) {}
    override var loadedImages: Int
        get() = currentGenerator.loadedImages
        set(_) {}

    fun clear(keepCache: Boolean) {
        if (keepCache) {
            if (!isSameLength() || currentGenerator.loadedImages >= lastGenerator.loadedImages || lastGenerator.durationMs == 0L) {
                lastGenerator.release()
                lastGenerator = currentGenerator
            } else {
                currentGenerator.release()
            }
        } else {
            lastGenerator.release()
            lastGenerator = NoPreviewGenerator()
            currentGenerator.release()
        }
    }

    fun load(link: ExtractorLink, keepCache: Boolean) {
        clear(keepCache)

        when (link.type) {
            ExtractorLinkType.M3U8 -> {
                currentGenerator = M3u8PreviewGenerator(params).apply {
                    load(url = link.url, headers = link.getAllHeaders())
                }
            }

            ExtractorLinkType.VIDEO -> {
                currentGenerator = Mp4PreviewGenerator(params).apply {
                    load(url = link.url, headers = link.getAllHeaders())
                }
            }

            else -> {
                Log.i("PreviewImg", "unsupported format for $link")
            }
        }
    }

    fun load(context: Context, link: ExtractorUri, keepCache: Boolean) {
        clear(keepCache)
        currentGenerator = Mp4PreviewGenerator(params).apply {
            load(keepCache = keepCache, context = context, uri = link.uri)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private class NoPreviewGenerator : IPreviewGenerator {
    override fun hasPreview(): Boolean = false
    override fun getPreviewImage(fraction: Float): Bitmap? = null
    override fun release() = Unit
    override var params: ImageParams
        get() = ImageParams(0, 0)
        set(value) {}
    override var durationMs: Long = 0L
    override var loadedImages: Int = 0
}

private class M3u8PreviewGenerator(override var params: ImageParams) : IPreviewGenerator {
    private var images: Array<Bitmap?> = arrayOf()

    companion object {
        private const val TAG = "PreviewImgM3u8"
    }


    private var prefixSum: Array<Double> = arrayOf()

    override var loadedImages: Int = 0

    private var totalImages: Int = 0

    override fun hasPreview(): Boolean {
        return totalImages > 0 && loadedImages >= minOf(totalImages, 4)
    }

    override fun getPreviewImage(fraction: Float): Bitmap? {
        var bestIdx = -1
        var bestDiff = Double.MAX_VALUE
        synchronized(images) {
            for (i in images.indices) {
                val diff = prefixSum[i].minus(fraction).absoluteValue
                if (diff > bestDiff) {
                    break
                }
                if (images[i] != null) {
                    bestIdx = i
                    bestDiff = diff
                }
            }
            return images.getOrNull(bestIdx)
        }
    }

    private fun clear() {
        synchronized(images) {
            currentJob?.cancel()
            images = arrayOf()
            prefixSum = arrayOf()
            loadedImages = 0
            totalImages = 0
        }
    }

    override fun release() {
        clear()
        images = arrayOf()
    }

    override var durationMs: Long = 0L

    private var currentJob: Job? = null
    fun load(url: String, headers: Map<String, String>) {
        clear()
        currentJob?.cancel()
        currentJob = ioSafe {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Loading with url = $url headers = $headers")
                val retriever = MediaMetadataRetriever()
                val hsl = M3u8Helper2.hslLazy(
                    M3u8Helper.M3u8Stream(
                        streamUrl = url,
                        headers = headers
                    ),
                    selectBest = false,
                    requireAudio = false,
                )

                if (hsl.isEncrypted) {
                    Log.i(TAG, "m3u8 is encrypted")
                    totalImages = 0
                    return@withContext
                }

                val duration = hsl.allTsLinks.sumOf { it.time ?: 0.0 }
                durationMs = (duration * 1000.0).toLong()
                val durationInv = 1.0 / duration

                if (duration <= 10.0) {
                    totalImages = 0
                    return@withContext
                }

                totalImages = hsl.allTsLinks.size

                prefixSum = Array(hsl.allTsLinks.size + 1) { 0.0 }
                var runningSum = 0.0
                for (i in hsl.allTsLinks.indices) {
                    runningSum += (hsl.allTsLinks[i].time ?: 0.0)
                    prefixSum[i + 1] = runningSum * durationInv
                }
                synchronized(images) {
                    images = Array(hsl.size) { null }
                    loadedImages = 0
                }

                val maxLod = ceil(log2(duration)).toInt().coerceIn(MIN_LOD, MAX_LOD)
                val count = hsl.allTsLinks.size
                for (l in 1..maxLod) {
                    val items = (1 shl (l - 1))
                    for (i in 0 until items) {
                        val index = (count.div(1 shl l) + (i * count) / items).coerceIn(0, hsl.size)
                        if (synchronized(images) { images[index] } != null) {
                            continue
                        }
                        Log.i(TAG, "Generating preview for $index")

                        val ts = hsl.allTsLinks[index]
                        try {
                            retriever.setDataSource(ts.url, hsl.headers)
                            if (!isActive) {
                                return@withContext
                            }
                            val img = retriever.image(0, params)
                            if (!isActive) {
                                return@withContext
                            }
                            if (img == null || img.width <= 1 || img.height <= 1) continue
                            synchronized(images) {
                                images[index] = img
                                loadedImages += 1
                            }
                        } catch (t: Throwable) {
                            logError(t)
                            continue
                        }
                    }
                }

            }
        }
    }
}

private class Mp4PreviewGenerator(override var params: ImageParams) : IPreviewGenerator {
    private var loadedLod = 0
    override var loadedImages = 0
    private var images = Array<Bitmap?>((1 shl MAX_LOD) - 1) {
        null
    }

    companion object {
        private const val TAG = "PreviewImgMp4"
    }

    override fun hasPreview(): Boolean {
        synchronized(images) {
            return loadedLod >= MIN_LOD
        }
    }

    override fun getPreviewImage(fraction: Float): Bitmap? {
        synchronized(images) {
            if (loadedLod < MIN_LOD) {
                Log.i(TAG, "Requesting preview for $fraction but $loadedLod < $MIN_LOD")
                return null
            }
            Log.i(TAG, "Requesting preview for $fraction")

            var bestIdx = 0
            var bestDiff = 0.5f.minus(fraction).absoluteValue

            for (l in 1..loadedLod + 1) {
                val items = (1 shl (l - 1))
                for (i in 0 until items) {
                    val idx = items - 1 + i
                    if (idx > loadedImages) {
                        break
                    }
                    if (images[idx] == null) {
                        continue
                    }
                    val currentFraction =
                        (1.0f.div((1 shl l).toFloat()) + i * 1.0f.div(items.toFloat()))
                    val diff = currentFraction.minus(fraction).absoluteValue
                    if (diff < bestDiff) {
                        bestDiff = diff
                        bestIdx = idx
                    }
                }
            }
            Log.i(TAG, "Best diff found at ${bestDiff * 100}% diff (${bestIdx})")
            return images[bestIdx]
        }
    }

    private val retriever: MediaMetadataRetriever = MediaMetadataRetriever()

    private fun clear(keepCache: Boolean) {
        if (keepCache) return
        synchronized(images) {
            loadedLod = 0
            loadedImages = 0
            images.fill(null)
        }
    }

    private var currentJob: Job? = null
    fun load(url: String, headers: Map<String, String>) {
        currentJob?.cancel()
        currentJob = ioSafe {
            Log.i(TAG, "Loading with url = $url headers = $headers")
            clear(true)
            retriever.setDataSource(url, headers)
            start(this)
        }
    }

    fun load(keepCache: Boolean, context: Context, uri: Uri) {
        currentJob?.cancel()
        currentJob = ioSafe {
            Log.i(TAG, "Loading with uri = $uri")
            clear(keepCache)
            retriever.setDataSource(context, uri)
            start(this)
        }
    }

    override fun release() {
        currentJob?.cancel()
        clear(false)
    }

    override var durationMs: Long = 0L

    @Throws
    @WorkerThread
    private fun start(scope: CoroutineScope) {
        Log.i(TAG, "Started loading preview")

        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                ?: throw IllegalArgumentException("Bad video duration")
        this.durationMs = durationMs
        val durationUs = (durationMs * 1000L).toFloat()

        val maxLod = ceil(log2((durationMs / 10_000).toFloat())).toInt().coerceIn(MIN_LOD, MAX_LOD)

        for (l in 1..maxLod) {
            val items = (1 shl (l - 1))
            for (i in 0 until items) {
                val idx = items - 1 + i
                val fraction = (1.0f.div((1 shl l).toFloat()) + i * 1.0f.div(items.toFloat()))
                Log.i(TAG, "Generating preview for ${fraction * 100}%")
                val frame = durationUs * fraction
                val img = retriever.image(frame.toLong(), params)
                if (!scope.isActive) return
                if (img == null || img.width <= 1 || img.height <= 1) continue
                synchronized(images) {
                    images[idx] = img
                    loadedImages = maxOf(loadedImages, idx)
                }
            }

            synchronized(images) {
                loadedLod = maxOf(loadedLod, l)
            }
        }
    }
}
