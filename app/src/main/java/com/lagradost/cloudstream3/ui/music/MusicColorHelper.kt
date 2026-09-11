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
     * Darkens a color by a certain ratio.
     */
    @ColorInt
    fun darkenColor(@ColorInt color: Int, ratio: Float = 0.25f): Int {
        val a = Color.alpha(color)
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * ratio).coerceIn(0f, 1f)
        return Color.HSVToColor(a, hsv)
    }

    /**
     * Desaturates a color by a certain ratio.
     */
    @ColorInt
    fun desaturateColor(@ColorInt color: Int, ratio: Float = 0.5f): Int {
        val a = Color.alpha(color)
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * ratio).coerceIn(0f, 1f)
        return Color.HSVToColor(a, hsv)
    }

    /**
     * Normalizes a color for atmospheric backgrounds.
     * Prevents neutral colors from defaulting to Red by respecting low saturation.
     */
    private fun normalizeAtmosphericColor(
        @ColorInt color: Int,
        minSat: Float,
        maxSat: Float,
        minVal: Float,
        maxVal: Float
    ): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        // If the color is very desaturated (neutral), don't force a minimum saturation.
        // This prevents gray/black from becoming muddy Red (Hue 0).
        val isNeutral = hsv[1] < 0.08f
        if (!isNeutral) {
            hsv[1] = hsv[1].coerceIn(minSat, maxSat)
        } else {
            // Maintain neutral look for grays/blacks
            hsv[1] = hsv[1].coerceAtMost(maxSat)
        }

        hsv[2] = hsv[2].coerceIn(minVal, maxVal)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    /**
     * Generates a Spotify-style background gradient.
     * Starts with a darker, desaturated version of the artwork color at the top,
     * fading to black at the bottom.
     */
    fun generateSpotifyGradient(palette: MusicPalette): IntArray {
        // Selection Logic: Blend dominant and darkMuted for a more stable atmosphere
        val baseColor = when {
            palette.darkMutedColor != DEFAULT_SURFACE -> {
                // Blend darkMuted with dominant to ensure we have enough "color" if muted is too dark
                blendColors(palette.darkMutedColor, palette.dominantColor, 0.7f)
            }
            palette.darkVibrantColor != DEFAULT_SURFACE -> {
                blendColors(palette.darkVibrantColor, palette.dominantColor, 0.5f)
            }
            else -> palette.dominantColor
        }

        // Spotify top color is very desaturated and dark
        val topColor = normalizeAtmosphericColor(
            baseColor,
            minSat = 0.12f,
            maxSat = 0.30f,
            minVal = 0.12f,
            maxVal = 0.22f
        )

        // Mid color is a deeper version of top
        val midColor = normalizeAtmosphericColor(
            topColor,
            minSat = 0.08f,
            maxSat = 0.25f,
            minVal = 0.06f,
            maxVal = 0.12f
        )

        // Bottom color is always near-black for depth
        val bottomColor = Color.parseColor("#08080A")

        return intArrayOf(topColor, midColor, bottomColor)
    }

    /**
     * Generates a Spotify-style mini player background.
     * Usually a very dark, desaturated version of the artwork color.
     */
    @ColorInt
    fun generateSpotifyMiniPlayerBackground(palette: MusicPalette): Int {
        val baseColor = if (palette.darkMutedColor != DEFAULT_SURFACE) palette.darkMutedColor else palette.dominantColor
        
        return normalizeAtmosphericColor(
            baseColor,
            minSat = 0.15f,
            maxSat = 0.35f,
            minVal = 0.07f,
            maxVal = 0.12f
        )
    }

    /**
     * Generates a Spotify-style lyrics palette.
     */
    fun generateLyricsPalette(palette: MusicPalette): LyricsPalette {
        val baseColor = if (palette.darkMutedColor != DEFAULT_SURFACE) palette.darkMutedColor else palette.dominantColor
        
        // Lyrics background is slightly more saturated than the main player top
        // to provide a distinct "sheet" feel while remaining atmospheric
        val lyricsBg = normalizeAtmosphericColor(
            baseColor,
            minSat = 0.20f,
            maxSat = 0.40f,
            minVal = 0.10f,
            maxVal = 0.20f
        )

        return LyricsPalette(
            background = lyricsBg,
            foregroundPrimary = Color.WHITE,
            foregroundSecondary = adjustAlpha(Color.WHITE, 0.7f),
            foregroundTertiary = adjustAlpha(Color.WHITE, 0.4f),
            accent = Color.WHITE,
            isLight = false
        )
    }

    /**
     * Generates a premium, artwork-driven palette for Home Podcast Cards.
     */
    fun generateHomePodcastCardPalette(palette: MusicPalette): HomePodcastCardPalette {
        val baseColor = if (palette.darkMutedColor != DEFAULT_SURFACE) {
            palette.darkMutedColor
        } else if (palette.darkVibrantColor != DEFAULT_SURFACE) {
            palette.darkVibrantColor
        } else {
            darkenColor(palette.dominantColor, 0.5f)
        }

        val start = baseColor
        val middle = darkenColor(baseColor, 0.8f)
        val end = darkenColor(baseColor, 0.4f)

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

    /**
     * Extracts a vibrant accent color for progress and active icons.
     */
    @ColorInt
    fun getVibrantAccent(palette: MusicPalette): Int {
        return if (palette.vibrantColor != DEFAULT_ACCENT) {
            palette.vibrantColor
        } else if (palette.darkVibrantColor != DEFAULT_SURFACE) {
            palette.darkVibrantColor
        } else {
            DEFAULT_ACCENT
        }
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
