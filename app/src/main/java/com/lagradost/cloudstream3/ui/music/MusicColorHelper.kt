package com.lagradost.cloudstream3.ui.music

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MusicPalette(
    @ColorInt val dominantColor: Int,
    @ColorInt val vibrantColor: Int,
    @ColorInt val darkMutedColor: Int,
    @ColorInt val darkVibrantColor: Int
)

object MusicColorHelper {
    private val paletteCache = mutableMapOf<String, MusicPalette>()

    private const val DEFAULT_SURFACE = 0xFF1A1A1A.toInt()
    private const val DEFAULT_ACCENT = 0xFFE50914.toInt()

    suspend fun getPalette(mediaId: String?, bitmap: Bitmap): MusicPalette = withContext(Dispatchers.Default) {
        if (mediaId != null) {
            paletteCache[mediaId]?.let { return@withContext it }
        }

        if (bitmap.isRecycled) return@withContext MusicPalette(DEFAULT_SURFACE, DEFAULT_ACCENT, DEFAULT_SURFACE, DEFAULT_SURFACE)

        val safeBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else bitmap

        val musicPalette = try {
            val palette = Palette.from(safeBitmap).generate()
            MusicPalette(
                dominantColor = palette.getDominantColor(DEFAULT_SURFACE),
                vibrantColor = palette.getVibrantColor(DEFAULT_ACCENT),
                darkMutedColor = palette.getDarkMutedColor(DEFAULT_SURFACE),
                darkVibrantColor = palette.getDarkVibrantColor(DEFAULT_SURFACE)
            )
        } catch (e: Exception) {
            android.util.Log.e("MusicColorHelper", "Palette generation failed", e)
            MusicPalette(DEFAULT_SURFACE, DEFAULT_ACCENT, DEFAULT_SURFACE, DEFAULT_SURFACE)
        }
        
        if (mediaId != null) {
            paletteCache[mediaId] = musicPalette
        }
        musicPalette
    }

    /**
     * Blends the given color with black to ensure it's suitable for a background.
     * @param color The color to darken.
     * @param ratio The amount of the original color to keep (0.0 to 1.0).
     */
    @ColorInt
    fun darkenColor(@ColorInt color: Int, ratio: Float = 0.7f): Int {
        val a = android.graphics.Color.alpha(color)
        val r = (android.graphics.Color.red(color) * ratio).toInt()
        val g = (android.graphics.Color.green(color) * ratio).toInt()
        val b = (android.graphics.Color.blue(color) * ratio).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }

    fun animateColorChange(@ColorInt fromColor: Int, @ColorInt toColor: Int, duration: Long = 500L, onUpdate: (Int) -> Unit) {
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        animator.duration = duration
        animator.addUpdateListener {
            onUpdate(it.animatedValue as Int)
        }
        animator.start()
    }

    fun animateGradientChange(view: android.view.View?, fromColors: IntArray, toColors: IntArray, duration: Long = 500L) {
        if (view == null) return
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration
        val evaluator = ArgbEvaluator()
        animator.addUpdateListener { anim ->
            val fraction = anim.animatedFraction
            val colors = IntArray(toColors.size)
            for (i in toColors.indices) {
                val from = if (i < fromColors.size) fromColors[i] else fromColors.last()
                colors[i] = evaluator.evaluate(fraction, from, toColors[i]) as Int
            }
            val gradient = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                colors
            )
            view.background = gradient
        }
        animator.start()
    }
}
