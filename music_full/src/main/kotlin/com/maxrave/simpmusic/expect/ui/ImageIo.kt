@file:Suppress("ktlint:standard:filename")

package com.maxrave.simpmusic.expect.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

private const val TAG = "ImageIo"
private const val COVER_DIR = "playlist_covers"

/**
 * Decodes encoded image bytes — whatever the picker handed back — into something Compose can draw.
 *
 * Returns null rather than throwing: the bytes come from a file the user chose, which can be a
 * format the platform decoder does not know, or truncated.
 */
fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.onFailure { Logger.w(TAG, "Could not decode image: ${it.message}") }.getOrNull()

/**
 * Writes [bytes] into the app's own storage and returns a `file:` uri for it.
 *
 * Cropped images cannot stay at the uri the picker returned — that one points at the ORIGINAL,
 * uncropped file, and on Android the read permission granted for it does not survive a restart.
 * Returns null when the write fails, so the caller can keep the previous cover instead.
 */
suspend fun persistPickedImage(
    bytes: ByteArray,
    fileName: String,
): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val context = getKoin().get<Context>()
            val dir = File(context.filesDir, COVER_DIR).apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeBytes(bytes)
            file.toURI().toString()
        }.onFailure { Logger.w(TAG, "Could not persist image: ${it.message}") }.getOrNull()
    }
