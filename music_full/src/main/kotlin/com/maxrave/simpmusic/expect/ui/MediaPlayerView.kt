package com.maxrave.simpmusic.expect.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.media3.ui.MediaPlayerView
import com.maxrave.media3.ui.MediaPlayerViewWithSubtitle
import com.maxrave.simpmusic.extension.findActivity
import com.maxrave.simpmusic.extension.getScreenSizeInfo
import com.maxrave.simpmusic.ui.theme.typo

@Composable
fun MediaPlayerView(
    url: String,
    modifier: Modifier,
    // When true, the video center scales-to-cover the given frame (ContentScale.Crop)
    // and clips the overflow. Default false keeps the legacy fit-height-by-screen
    // behavior used by NowPlaying / Fullscreen.
    cropToBounds: Boolean = false,
) {
    MediaPlayerView(
        modifier = modifier,
        context = LocalContext.current,
        density = LocalDensity.current,
        url = url,
        screenSize = getScreenSizeInfo(),
        cropToBounds = cropToBounds,
    )
}

@Composable
fun MediaPlayerViewWithSubtitle(
    modifier: Modifier,
    playerName: String,
    shouldPip: Boolean = false,
    shouldShowSubtitle: Boolean,
    shouldScaleDownSubtitle: Boolean = false,
    isInPipMode: Boolean,
    timelineState: TimeLine,
    lyricsData: Lyrics? = null,
    translatedLyricsData: Lyrics? = null,
    mainTextStyle: TextStyle,
    translatedTextStyle: TextStyle,
) {
    MediaPlayerViewWithSubtitle(
        playerName = playerName,
        modifier = modifier,
        shouldShowSubtitle = shouldShowSubtitle,
        shouldPip = shouldPip,
        shouldScaleDownSubtitle = shouldScaleDownSubtitle,
        timelineState = timelineState,
        lyricsData = lyricsData,
        translatedLyricsData = translatedLyricsData,
        context = LocalContext.current,
        activity = LocalActivity.current as? ComponentActivity ?: LocalContext.current.findActivity(),
        isInPipMode = isInPipMode,
        mainTextStyle = typo().bodyLarge,
        translatedTextStyle = typo().bodyMedium,
    )
}
