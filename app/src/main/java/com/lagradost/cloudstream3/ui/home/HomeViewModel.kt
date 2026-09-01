package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppContextUtils.filterHomePageListByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import androidx.lifecycle.viewModelScope

class HomeViewModel : BaseHomeViewModel() {
    override val currentApiName: String = "Home"
    override val cacheKey: String = "home_cache" // Using string for now, but should ideally use HOME_CACHE_KEY

    override fun getFilteredApis(): List<MainAPI> {
        val allApis = super.getFilteredApis()
        // Filter out APIs with "sport" or other live keywords in their name
        return allApis.filterNot { api ->
            sportKeywords.any { api.name.lowercase().contains(it) }
        }
    }

    override fun mergeHomeResult(resource: Resource<List<HomePageResponse?>>) {
        if (resource is Resource.Success) {
            resource.value.forEach { home ->
                home?.items?.forEach { list ->
                    val categoryName = list.name.lowercase()

                    // 1. Strictly block categories that sound like Sports/Live
                    if (sportKeywords.any { categoryName.contains(it) }) return@forEach

                    // 2. Filter out any individual items marked as Live
                    val strictlyNonLiveItems = list.list.filter {
                        it.type != TvType.Live
                    }

                    if (strictlyNonLiveItems.isEmpty()) return@forEach

                    val noSportsList = list.copy(list = strictlyNonLiveItems)
                    val filteredList = context?.filterHomePageListByFilmQuality(noSportsList) ?: noSportsList
                    val key = list.name
                    val existing = expandable[key]
                    if (existing != null) {
                        existing.list.list += filteredList.list
                        existing.list.list = existing.list.list.distinctBy { it.url }
                    } else {
                        expandable[key] = ExpandableHomepageList(
                            filteredList.copy(list = filteredList.list.toList()),
                            1,
                            false
                        )
                    }
                }
            }
        }
    }

    override fun updatePreviewFromExpandable() {
        val allItems = expandable.values.flatMap { it.list.list }.distinctBy { it.url }

        if (allItems.isNotEmpty()) {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            
            // Strictly Movie content only, and no Sports content
            val movieOnlyItems = allItems.filter { item ->
                (item.type == TvType.Movie || item is MovieSearchResponse) &&
                sportKeywords.none { item.name.lowercase().contains(it) }
            }

            val filteredItems = movieOnlyItems.filter { item ->
                val api = getApiFromNameNull(item.apiName)
                val isEnglish = api?.lang == "en"

                val year = when (item) {
                    is MovieSearchResponse -> item.year
                    is TvSeriesSearchResponse -> item.year
                    is AnimeSearchResponse -> item.year
                    else -> null
                }
                // Only show content from the last 2 years
                val isRecent = year != null && year >= currentYear - 1

                isEnglish && isRecent
            }

            // Fallback to English movies if no recent ones, then all movies
            val bannerPool = filteredItems.ifEmpty {
                movieOnlyItems.filter { getApiFromNameNull(it.apiName)?.lang == "en" }
            }.ifEmpty { movieOnlyItems }

            val shuffledItems = bannerPool.shuffled()
            val randomItems = context?.filterSearchResultByFilmQuality(shuffledItems) ?: shuffledItems

            viewModelScope.launchSafe {
                previewResponses.clear()
                previewResponsesAdded.clear()
                updatePreviewResponses(
                    previewResponses,
                    previewResponsesAdded,
                    randomItems,
                    3
                )

                _randomItems.postValue(randomItems)
                currentShuffledList = randomItems
                
                if (previewResponses.isNotEmpty()) {
                    _preview.postValue(Resource.Success((previewResponsesAdded.size < currentShuffledList.size) to previewResponses))
                } else if (_preview.value !is Resource.Success) {
                    _preview.postValue(Resource.Failure(false, "No preview items"))
                }
            }
        }
    }
}
