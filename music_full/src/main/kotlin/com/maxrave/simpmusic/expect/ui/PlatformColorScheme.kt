package com.maxrave.simpmusic.expect.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.maxrave.simpmusic.extension.getActivityOrNull

/** Wallpaper-based (Material You) color scheme, or null when the platform can't provide one. */
@Composable
fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (isDark) {
        // Keep the OLED look: pin background/surface to pure black like the seed scheme.
        dynamicDarkColorScheme(context).copy(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        dynamicLightColorScheme(context)
    }
}

/** Whether this platform can derive a color scheme from the system wallpaper. */
fun isWallpaperDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Keeps the system bar icon appearance in sync with the app theme (Android only; no-op elsewhere). */
@Composable
fun SystemBarAppearanceEffect(isDark: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.getActivityOrNull()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // Light theme → dark icons; dark theme → light icons.
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
}
