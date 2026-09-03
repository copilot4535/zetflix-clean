package com.maxrave.simpmusic.expect.ui

import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.Bitmap.CompressFormat.PNG
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.toBitmap
import java.io.ByteArrayOutputStream

fun ImageBitmap.toByteArray(): ByteArray? {
    val byteArrayOutputStream = ByteArrayOutputStream()
    this.asAndroidBitmap().compress(JPEG, 100, byteArrayOutputStream)
    val bytesArray = byteArrayOutputStream.toByteArray()
    return bytesArray
}

/**
 * PNG rather than the JPEG [toByteArray] produces.
 *
 * The share card is mostly flat colour behind crisp text, which is the exact case JPEG handles
 * worst — its 8x8 blocks ring around every glyph edge, and at the sizes these cards get viewed
 * the artefacts are plainly visible. PNG also keeps the card's rounded corners transparent
 * instead of filling them black.
 */
fun ImageBitmap.toPngByteArray(): ByteArray? {
    val byteArrayOutputStream = ByteArrayOutputStream()
    // The quality argument is ignored for PNG — it is lossless — but the signature still demands one.
    this.asAndroidBitmap().compress(PNG, 100, byteArrayOutputStream)
    return byteArrayOutputStream.toByteArray()
}

fun Image.toImageBitmap(): ImageBitmap = this.toBitmap().asImageBitmap()
