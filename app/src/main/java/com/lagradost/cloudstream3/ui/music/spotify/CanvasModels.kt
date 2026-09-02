package com.lagradost.cloudstream3.ui.music.spotify

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CanvasResponse(
    @ProtoNumber(1)
    val canvases: List<Canvas> = emptyList()
) {
    @Serializable
    data class Canvas(
        @ProtoNumber(1)
        val id: String = "",
        @ProtoNumber(2)
        val canvasUrl: String = "",
        @ProtoNumber(5)
        val trackUri: String = "",
        @ProtoNumber(11)
        val canvasUri: String = ""
    )
}

@Serializable
data class CanvasRequest(
    val tracks: List<Track>
) {
    @Serializable
    data class Track(
        val track_uri: String
    )
}

@Serializable
data class SpotifyAccessTokenResponse(
    val accessToken: String,
    val accessTokenExpirationTimestampMs: Long
)

@Serializable
data class SpotifyClientTokenResponse(
    val grantedToken: GrantedToken
) {
    @Serializable
    data class GrantedToken(
        val token: String,
        val expiresAfterSeconds: Int
    )
}

@Serializable
data class SpotifySearchResponse(
    val data: SearchData? = null
) {
    @Serializable
    data class SearchData(
        val searchV2: SearchV2? = null
    )

    @Serializable
    data class SearchV2(
        val tracksV2: TracksV2? = null
    )

    @Serializable
    data class TracksV2(
        val items: List<TrackItem> = emptyList()
    )

    @Serializable
    data class TrackItem(
        val item: ItemData? = null
    )

    @Serializable
    data class ItemData(
        val data: TrackData? = null
    )

    @Serializable
    data class TrackData(
        val id: String = "",
        val uri: String = "",
        val name: String = "",
        val duration: Duration? = null
    )

    @Serializable
    data class Duration(
        val totalMilliseconds: Long = 0
    )
}
