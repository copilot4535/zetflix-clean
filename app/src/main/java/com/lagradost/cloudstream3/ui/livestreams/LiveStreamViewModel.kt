package com.lagradost.cloudstream3.ui.livestreams

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppContextUtils.filterHomePageListByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.PluginPriorityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

class LiveStreamViewModel : ViewModel() {
    companion object {
        private const val STAGE1_PLUGIN_COUNT = 10
        private const val STAGE1_TOTAL_TIMEOUT_MS = 30_000L
        private const val STAGE2_TOTAL_TIMEOUT_MS = 60_000L
        private const val TOTAL_LOAD_TIMEOUT_MS = 90_000L
        private const val PER_PLUGIN_TIMEOUT_MS = 20_000L
        private const val MAX_CONCURRENT_PLUGIN_LOADS = 8

        data class LoadClickCallback(
            val action: Int,
            val view: View,
            val position: Int,
            val response: LoadResponse,
        )
    }

    var repo: APIRepository? = null

    private val _apiName = MutableLiveData<String>()
    val apiName: LiveData<String> = _apiName

    private val expandable: MutableMap<String, HomeViewModel.ExpandableHomepageList> = mutableMapOf()
    private val _page =
        MutableLiveData<Resource<Map<String, HomeViewModel.ExpandableHomepageList>>>(Resource.Loading())
    val page: LiveData<Resource<Map<String, HomeViewModel.ExpandableHomepageList>>> = _page

    private val _randomItems = MutableLiveData<List<SearchResponse>?>(null)
    val randomItems: LiveData<List<SearchResponse>?> = _randomItems

    private var currentShuffledList: List<SearchResponse> = listOf()

    private val _preview = MutableLiveData<Resource<Pair<Boolean, List<LoadResponse>>>>()
    val preview: LiveData<Resource<Pair<Boolean, List<LoadResponse>>>> = _preview

    private val previewResponses = CopyOnWriteArrayList<LoadResponse>()
    private val previewResponsesAdded = mutableSetOf<String>()

    private val _bookmarks = MutableLiveData<Pair<Boolean, List<SearchResponse>>>()
    val bookmarks: LiveData<Pair<Boolean, List<SearchResponse>>> = _bookmarks

    private val _resumeWatching = MutableLiveData<List<SearchResponse>>()
    val resumeWatching: LiveData<List<SearchResponse>> = _resumeWatching

    private var onGoingLoad: Job? = null

    private fun mergeHomeResult(resource: Resource<List<HomePageResponse?>>) {
        if (resource is Resource.Success) {
            resource.value.forEach { home ->
                home?.items?.forEach { list ->
                    val filteredList = context?.filterHomePageListByFilmQuality(list) ?: list
                    
                    val listName = list.name.lowercase()
                    // Extremely aggressive filter for any Live/Sports/TV related content
                    val isLiveCategory = listName.contains("live") || 
                                         listName.contains("sport") ||
                                         listName.contains("tv") ||
                                         listName.contains("channel") ||
                                         listName.contains("stream") ||
                                         listName.contains("broadcast") ||
                                         listName.contains("iptv") ||
                                         listName.contains("football") ||
                                         listName.contains("cricket") ||
                                         listName.contains("match") ||
                                         listName.contains("news")

                    // Broad filter: TvType.Live OR any item that belongs to a "Live-ish" category
                    // OR any item that has "sport" or "live" in its name/tags
                    val liveOnlyItems = filteredList.list.filter { 
                        val itemName = it.name.lowercase()
                        it.type == TvType.Live || 
                        isLiveCategory || 
                        itemName.contains("live") || 
                        itemName.contains("sport") ||
                        itemName.contains("match") ||
                        itemName.contains("channel") ||
                        itemName.contains("stream")
                    }
                    
                    if (liveOnlyItems.isEmpty()) return@forEach
                    
                    val liveOnlyList = filteredList.copy(list = liveOnlyItems)

                    val key = list.name
                    val existing = expandable[key]
                    if (existing != null) {
                        existing.list.list += liveOnlyList.list
                        existing.list.list = existing.list.list.distinctBy { it.url }
                    } else {
                        expandable[key] = HomeViewModel.ExpandableHomepageList(
                            liveOnlyList.copy(list = liveOnlyList.list.toList()),
                            1,
                            false
                        )
                    }
                }
            }
        }
    }

    private suspend fun updatePreviewResponses(
        current: MutableList<LoadResponse>,
        alreadyAdded: MutableSet<String>,
        shuffledList: List<SearchResponse>,
        size: Int
    ): Int {
        var count = 0

        val addItems = arrayListOf<SearchResponse>()
        for (searchResponse in shuffledList) {
            if (!alreadyAdded.contains(searchResponse.url)) {
                addItems.add(searchResponse)
                previewResponsesAdded.add(searchResponse.url)
                if (++count >= size) {
                    break
                }
            }
        }

        val add = addItems.amap { searchResponse ->
            val api = getApiFromNameNull(searchResponse.apiName)
            if (api != null) {
                APIRepository(api).load(searchResponse.url)
            } else {
                null
            }
        }.mapNotNull { if (it != null && it is Resource.Success) it.value else null }
        current.addAll(add)
        return add.size
    }

    private var addJob: Job? = null
    fun loadMoreHomeScrollResponses() {
        addJob = ioSafe {
            updatePreviewResponses(previewResponses, previewResponsesAdded, currentShuffledList, 1)
            _preview.postValue(Resource.Success((previewResponsesAdded.size < currentShuffledList.size) to previewResponses))
        }
    }

    private fun updatePreviewFromExpandable() {
        val allItems = expandable.values.flatMap { it.list.list }.distinctBy { it.url }

        if (allItems.isNotEmpty()) {
            val shuffledItems = allItems.shuffled()
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

    fun load(forceReload: Boolean = true) = ioSafe {
        if (!forceReload && expandable.isNotEmpty()) return@ioSafe

        onGoingLoad?.cancel()
        _apiName.postValue("Livestreams")
        
        expandable.clear()
        _page.postValue(Resource.Loading())
        _preview.postValue(Resource.Loading())

        val filteredApis = context?.filterProviderByPreferredMedia() ?: emptyList()
        val allApis = apis
        
        // Use filtered APIs first, but fallback to all if needed
        val apisToUse = if (filteredApis.isNotEmpty()) filteredApis else allApis

        if (apisToUse.isEmpty()) {
            _page.postValue(Resource.Success(emptyMap()))
            _preview.postValue(Resource.Failure(false, "No plugins found"))
            return@ioSafe
        }

        val startTime = System.currentTimeMillis()
        val shuffledApis = apisToUse.shuffled()

        // Prioritize providers that likely have live content
        val priorityKeywords = listOf("iptv", "live", "tv", "sports", "stream")
        val prioritizedApis = shuffledApis.sortedByDescending { api ->
            priorityKeywords.any { kw -> api.name.lowercase().contains(kw) }
        }

        val stage1Plugins = prioritizedApis.take(STAGE1_PLUGIN_COUNT)
        val stage2Plugins = prioritizedApis.drop(STAGE1_PLUGIN_COUNT)

        suspend fun loadPlugins(plugins: List<MainAPI>, stageDeadline: Long) {
            plugins.chunked(MAX_CONCURRENT_PLUGIN_LOADS).forEach { chunk ->
                if (System.currentTimeMillis() - startTime > TOTAL_LOAD_TIMEOUT_MS) return@forEach
                if (System.currentTimeMillis() - startTime > stageDeadline) return@forEach

                chunk.amap { api ->
                    withTimeoutOrNull(PER_PLUGIN_TIMEOUT_MS) {
                        try {
                            APIRepository(api).getMainPage(1, null)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.forEach { result ->
                    if (result != null) {
                        mergeHomeResult(result)
                    }
                }
                
                _page.postValue(Resource.Success(expandable))
            }
        }

        loadPlugins(stage1Plugins, STAGE1_TOTAL_TIMEOUT_MS)
        updatePreviewFromExpandable()
        loadPlugins(stage2Plugins, STAGE2_TOTAL_TIMEOUT_MS)
        updatePreviewFromExpandable()
        
        // Final update to ensure UI knows we're done
        if (expandable.isEmpty()) {
             _page.postValue(Resource.Failure(false, "No live content found. Try enabling more providers."))
        } else {
             _page.postValue(Resource.Success(expandable))
        }
    }

    fun expand(name: String) = viewModelScope.launchSafe {
        // Expand logic for filtered items might need more thought
    }

    fun click(callback: SearchClickCallback) {
        if (callback.action != SEARCH_ACTION_FOCUSED) {
            SearchHelper.handleSearchClickCallback(callback)
        }
    }

    fun click(load: LoadClickCallback) {
        loadResult(load.response.url, load.response.apiName, load.response.name, load.action)
    }

    private val _popup = MutableLiveData<Pair<HomeViewModel.ExpandableHomepageList, (() -> Unit)?>?>(null)
    val popup: LiveData<Pair<HomeViewModel.ExpandableHomepageList, (() -> Unit)?>?> = _popup

    fun popup(list: HomeViewModel.ExpandableHomepageList?, deleteCallback: (() -> Unit)? = null) {
        if (list == null)
            _popup.postValue(null)
        else
            _popup.postValue(list to deleteCallback)
    }
}
