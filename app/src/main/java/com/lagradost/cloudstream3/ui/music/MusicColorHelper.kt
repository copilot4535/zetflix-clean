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
     * Calculates the contrast ratio between two colors.
     * returns a value between 1.0 and 21.0.
     */
    fun calculateContrast(@ColorInt color1: Int, @ColorInt color2: Int): Float {
        val l1 = calculateLuminance(color1) + 0.05f
        val l2 = calculateLuminance(color2) + 0.05f
        return if (l1 > l2) l1 / l2 else l2 / l1
    }

    /**
     * Darkens or lightens a color to ensure enough contrast for text.
     */
    @ColorInt
    fun ensureContrast(@ColorInt backgroundColor: Int, @ColorInt textColor: Int, minContrastRatio: Float = 4.5f): Int {
        var result = textColor
        val bgLuminance = calculateLuminance(backgroundColor)
        val isBgDark = bgLuminance < 0.5f
        
        val hsv = FloatArray(3)
        Color.colorToHSV(result, hsv)
        
        var contrast = calculateContrast(backgroundColor, result)
        var iterations = 0
        
        // Iteratively adjust luminance to reach contrast goal
        while (contrast < minContrastRatio && iterations < 10) {
            if (isBgDark) {
                hsv[2] = minOf(1.0f, hsv[2] + 0.1f) // Lighten
            } else {
                hsv[2] = maxOf(0.0f, hsv[2] - 0.1f) // Darken
            }
            result = Color.HSVToColor(hsv)
            contrast = calculateContrast(backgroundColor, result)
            iterations++
        }
        
        // If still not enough contrast, try adjusting saturation
        if (contrast < minContrastRatio) {
            iterations = 0
            while (contrast < minContrastRatio && iterations < 5) {
                if (isBgDark) {
                    hsv[1] = maxOf(0.0f, hsv[1] - 0.1f) // Desaturate to make it whiter
                } else {
                    hsv[1] = minOf(1.0f, hsv[1] + 0.1f) // Saturate or leave it
                }
                result = Color.HSVToColor(hsv)
                contrast = calculateContrast(backgroundColor, result)
                iterations++
            }
        }
        
        // Final fallback
        if (contrast < minContrastRatio) {
            return if (isBgDark) Color.WHITE else Color.BLACK
        }
        
        return result
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
        // Ensure contrast against background
        val accent = ensureContrast(lyricsBg, palette.vibrantColor)

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

    /**
     * Generates a premium, artwork-driven dark background for the Mini Player.
     * Uses luminance reduction and saturation control to keep it sophisticated.
     */
    @ColorInt
    fun generatePremiumMiniPlayerBackground(palette: MusicPalette): Int {
        // Prefer darkMutedColor, then darkVibrantColor, then darken the dominant color
        val baseColor = if (palette.darkMutedColor != DEFAULT_SURFACE) {
            palette.darkMutedColor
        } else if (palette.darkVibrantColor != DEFAULT_SURFACE) {
            palette.darkVibrantColor
        } else {
            palette.dominantColor
        }

        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)

        // 1. Saturation control (Desaturate to prevent "cheap" look)
        hsv[1] = hsv[1].coerceIn(0.1f, 0.4f) 

        // 2. Luminance reduction (Ensure it's almost black but tinted)
        hsv[2] = hsv[2].coerceIn(0.05f, 0.12f)

        return Color.HSVToColor(hsv)
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
