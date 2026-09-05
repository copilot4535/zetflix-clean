package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.mvvm.logError
import android.util.Log

/**
 * Defensive registry to wrap plugin calls in try-catch blocks.
 * Prevents third-party provider crashes from breaking the main application.
 */
object SafeProviderRegistry {
    private const val TAG = "SafeProviderRegistry"

    suspend fun safeSearch(api: MainAPI, query: String): List<SearchResponse> {
        return try {
            api.search(query) ?: emptyList()
        } catch (e: Throwable) {
            Log.e(TAG, "Search failure in plugin: ${api.name}", e)
            logError(e)
            emptyList()
        }
    }

    suspend fun safeLoad(api: MainAPI, url: String): LoadResponse? {
        return try {
            api.load(url)
        } catch (e: Throwable) {
            Log.e(TAG, "Load failure in plugin: ${api.name}", e)
            logError(e)
            null
        }
    }

    suspend fun safeMainPage(api: MainAPI, page: Int, request: MainPageRequest): HomePageResponse? {
        return try {
            api.getMainPage(page, request)
        } catch (e: Throwable) {
            Log.e(TAG, "Main page failure in plugin: ${api.name}", e)
            logError(e)
            null
        }
    }
}
