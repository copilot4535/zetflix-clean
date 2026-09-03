package com.maxrave.data.io

import android.content.Context
import okio.FileSystem
import org.koin.mp.KoinPlatform.getKoin

fun fileSystem(): FileSystem = FileSystem.SYSTEM
fun fileDir(): String {
    val context = getKoin().get<Context>()
    return context.filesDir.absolutePath
}
