package com.lagradost.cloudstream3.ui.music

import kotlinx.serialization.Serializable

enum class AccountConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    REAUTH_REQUIRED,
    ERROR
}

@Serializable
data class AccountMetadata(
    val accountId: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
    val lastValidatedAt: Long = System.currentTimeMillis()
)

data class YouTubeAccountState(
    val connectionState: AccountConnectionState,
    val metadata: AccountMetadata? = null,
    val errorMessage: String? = null
)
