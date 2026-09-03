package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.simpmusic.cast.CastIconButton
import org.simpmusic.cast.isCastAvailable

/**
 * Cast button. Renders nothing on platforms without Cast, when the device has no Google Play
 * services, or when no Cast receiver is reachable on the network.
 *
 * [tint] colours the icon — callers flip it to signal an active Cast session.
 */
@Composable
fun PlatformCastButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    CastIconButton(modifier = modifier, tint = tint)
}

/**
 * Whether [PlatformCastButton] would actually render anything. Layouts that give the button its
 * own container (e.g. a slot in a connected button group) must hide the container too when this
 * is false — the button hides itself, but it cannot hide a wrapper it doesn't own.
 */
fun isPlatformCastAvailable(): Boolean = isCastAvailable()
