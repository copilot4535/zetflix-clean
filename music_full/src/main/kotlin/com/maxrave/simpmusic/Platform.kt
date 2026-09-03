package com.maxrave.simpmusic

sealed class Platform {
    object Android : Platform()
    object iOS : Platform()
    object Desktop : Platform()

    fun osName(): String = when (this) {
        Android -> "android"
        iOS -> "iOS"
        Desktop -> System.getProperty("os.name") ?: "jvm"
    }
}

fun getPlatform(): Platform = Platform.Android