package com.lagradost.cloudstream3.ui.music

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MusicPalette(
    @ColorInt val dominantColor: Int,
    @ColorInt val vibrantColor: Int,
    @ColorInt val darkMutedColor: Int,
    @ColorInt val darkVibrantColor: Int,
    val isLight: Boolean
)

data class HomePodcastCardPalette(
    @ColorInt val startColor: Int,
    @ColorInt val middleColor: Int,
    @ColorInt val endColor: Int,
    @ColorInt val foregroundPrimary: Int,
    @ColorInt val foregroundSecondary: Int,
    @ColorInt val foregroundTertiary: Int,
    val isLight: Boolean
)

data class LyricsPalette(
    @ColorInt val background: Int,
    @ColorInt val foregroundPrimary: Int,
    @ColorInt val foregroundSecondary: Int,
    @ColorInt val foregroundTertiary: Int,
    @ColorInt val accent: Int,
    val isLight: Boolean
)

object MusicColorHelper {
    private val paletteCache = mutableMapOf<String, MusicPalette>()

    private const val DEFAULT_SURFACE = 0xFF121212.toInt()
    private const val DEFAULT_ACCENT = 0xFFE50914.toInt()

    suspend fun getPalette(mediaId: String?, bitmap: Bitmap): MusicPalette = withContext(Dispatchers.Default) {
        if (mediaId != null) {
            paletteCache[mediaId]?.let { return@withContext it }
        }

        if (bitmap.isRecycled) return@withContext MusicPalette(DEFAULT_SURFACE, DEFAULT_ACCENT, DEFAULT_SURFACE, DEFAULT_SURFACE, false)

        val safeBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else bitmap

        val musicPalette = try {
            val palette = Palette.from(safeBitmap).generate()
            val dominant = palette.getDominantColor(DEFAULT_SURFACE)
            val vibrant = palette.getVibrantColor(DEFAULT_ACCENT)
            val darkMuted = palette.getDarkMutedColor(DEFAULT_SURFACE)
            val darkVibrant = palette.getDarkVibrantColor(DEFAULT_SURFACE)
            
            // Calculate if the dominant color is light or dark
            val isLight = calculateLuminance(dominant) > 0.5f

            MusicPalette(
                dominantColor = dominant,
                vibrantColor = vibrant,
                darkMutedColor = darkMuted,
                darkVibrantColor = darkVibrant,
                isLight = isLight
            )
        } catch (e: Exception) {
            android.util.Log.e("MusicColorHelper", "Palette generation failed", e)
            MusicPalette(DEFAULT_SURFACE, DEFAULT_ACCENT, DEFAULT_SURFACE, DEFAULT_SURFACE, false)
        }
        
        if (mediaId != null) {
            paletteCache[mediaId] = musicPalette
        }
        musicPalette
    }

    /**
     * Calculates the perceived luminance of a color.
     * Returns a value between 0.0 (black) and 1.0 (white).
     */
    fun calculateLuminance(@ColorInt color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Blends the given color with black to ensure it's suitable for a background.
     * @param color The color to darken.
     * @param ratio The amount of the original color to keep (0.0 to 1.0).
     */
    @ColorInt
    fun darkenColor(@ColorInt color: Int, ratio: Float = 0.7f): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * ratio).toInt()
        val g = (Color.green(color) * ratio).toInt()
        val b = (Color.blue(color) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }

    /**
     * Darkens or lightens a color to ensure enough contrast for text.
     */
    @ColorInt
    fun ensureContrast(@ColorInt backgroundColor: Int, @ColorInt textColor: Int, minContrastRatio: Float = 4.5f): Int {
        // Simple implementation: if background is dark, return light text, and vice versa
        return if (calculateLuminance(backgroundColor) < 0.5f) Color.WHITE else Color.BLACK
    }

    /**
     * Generates a Spotify-style lyrics palette based on artwork colors.
     */
    fun generateLyricsPalette(palette: MusicPalette): LyricsPalette {
        // 1. Blend Dominant and Dark Muted colors (60-75%)
        var lyricsBg = blendColors(palette.dominantColor, palette.darkMutedColor, 0.7f)

        // 2. Tone-map for readability
        val luminance = calculateLuminance(lyricsBg)
        val hsv = FloatArray(3)
        Color.colorToHSV(lyricsBg, hsv)

        if (luminance > 0.4f) {
            // Reduce brightness if too high
            hsv[2] *= 0.6f
        }
        
        if (hsv[1] > 0.7f) {
            // Reduce saturation if excessively high
            hsv[1] *= 0.8f
        }

        // Keep the original character if already dark
        if (luminance > 0.15f) {
            lyricsBg = Color.HSVToColor(hsv)
        }

        // 3. Foreground logic
        val bgLuminance = calculateLuminance(lyricsBg)
        val isLight = bgLuminance > 0.5f
        
        val primary = if (isLight) Color.BLACK else Color.WHITE
        val secondary = adjustAlpha(primary, 0.75f)
        val tertiary = adjustAlpha(primary, 0.55f)

        // 4. Accent color (active lyric)
        // Try to use vibrant color, but ensure it's readable on the background
        var accent = palette.vibrantColor
        val accentHsv = FloatArray(3)
        Color.colorToHSV(accent, accentHsv)
        
        if (isLight) {
            // Light background
            if (accentHsv[2] > 0.5f) accentHsv[2] = 0.4f 
        } else {
            // Dark background
            if (accentHsv[2] < 0.7f) accentHsv[2] = 0.9f 
            if (accentHsv[1] < 0.2f) accentHsv[1] = 0.4f
        }
        accent = Color.HSVToColor(accentHsv)

        return LyricsPalette(
            background = lyricsBg,
            foregroundPrimary = primary,
            foregroundSecondary = secondary,
            foregroundTertiary = tertiary,
            accent = accent,
            isLight = isLight
        )
    }

    /**
     * Generates a premium, artwork-driven palette for Home Podcast Cards.
     */
    fun generateHomePodcastCardPalette(palette: MusicPalette): HomePodcastCardPalette {
        // 1. Base color selection
        // We want a color that represents the artwork but isn't too bright
        val baseColor = if (palette.darkMutedColor != DEFAULT_SURFACE) {
            palette.darkMutedColor
        } else if (palette.darkVibrantColor != DEFAULT_SURFACE) {
            palette.darkVibrantColor
        } else {
            darkenColor(palette.dominantColor, 0.5f)
        }

        // 2. Tonal gradient colors
        // Start: The vibrant base
        val start = baseColor
        // Middle: Muted version
        val middle = darkenColor(baseColor, 0.8f)
        // End: Very dark version for depth
        val end = darkenColor(baseColor, 0.4f)

        // 3. Contrast-aware foregrounds
        val luminance = calculateLuminance(middle)
        val isLight = luminance > 0.5f
        
        val primary = if (isLight) Color.BLACK else Color.WHITE
        val secondary = adjustAlpha(primary, 0.7f)
        val tertiary = adjustAlpha(primary, 0.5f)

        return HomePodcastCardPalette(
            startColor = start,
            middleColor = middle,
            endColor = end,
            foregroundPrimary = primary,
            foregroundSecondary = secondary,
            foregroundTertiary = tertiary,
            isLight = isLight
        )
    }

    @ColorInt
    fun blendColors(@ColorInt color1: Int, @ColorInt color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(color1) * ratio + Color.alpha(color2) * inverseRatio).toInt()
        val r = (Color.red(color1) * ratio + Color.red(color2) * inverseRatio).toInt()
        val g = (Color.green(color1) * ratio + Color.green(color2) * inverseRatio).toInt()
        val b = (Color.blue(color1) * ratio + Color.blue(color2) * inverseRatio).toInt()
        return Color.argb(a, r, g, b)
    }

    @ColorInt
    fun adjustAlpha(@ColorInt color: Int, alpha: Float): Int {
        return Color.argb((255 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }

    fun animateColorChange(@ColorInt fromColor: Int, @ColorInt toColor: Int, duration: Long = 500L, onUpdate: (Int) -> Unit): ValueAnimator {
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        animator.duration = duration
        animator.addUpdateListener {
            onUpdate(it.animatedValue as Int)
        }
        animator.start()
        return animator
    }

    fun animateGradientChange(view: View?, fromColors: IntArray, toColors: IntArray, duration: Long = 500L): ValueAnimator? {
        if (view == null) return null
        
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
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                colors
            )
            view.background = gradient
            view.invalidate()
        }
        animator.start()
        return animator
    }
}
