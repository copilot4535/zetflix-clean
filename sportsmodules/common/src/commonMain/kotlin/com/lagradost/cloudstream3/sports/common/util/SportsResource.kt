package com.lagradost.cloudstream3.sports.common.util

sealed class SportsResource<out T> {
    data class Success<T>(val data: T) : SportsResource<T>()
    data class Error(val message: String, val exception: Throwable? = null) : SportsResource<Nothing>()
    object Loading : SportsResource<Nothing>()
}
