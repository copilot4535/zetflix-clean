package com.maxrave.data.io

import android.content.Context
import android.net.Uri
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin

/**
 * Reads the bytes of an image the user picked, given whatever string the platform's picker handed
 * back — a `content://` uri on Android, a file path or `file:` uri on Desktop.
 *
 * Returns null instead of throwing: every caller is reacting to something the user did, and the
 * picked file can be gone, unreadable, or on a revoked permission by the time it is read.
 */
suspend fun readLocalImageBytes(uri: String): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            val context = getKoin().get<Context>()
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        }.onFailure {
            Logger.w("LocalImage", "Could not read $uri: ${it.message}")
        }.getOrNull()
    }
