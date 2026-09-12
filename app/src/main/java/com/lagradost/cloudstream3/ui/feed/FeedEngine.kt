package com.lagradost.cloudstream3.ui.feed

import android.content.Context
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.movie.BaseHomeViewModel.ExpandableHomepageList
import com.lagradost.cloudstream3.utils.AppContextUtils.filterHomePageListByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.PluginPriorityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FeedEngine(
    private val scope: CoroutineScope,
    private val onStateUpdated: (Resource<Map<String, ExpandableHomepageList>>) -> Unit
) {
    val sportKeywords = listOf(
        "sports", "live", "cricket", "football", "soccer", "basketball", "tennis",
        "rugby", "golf", "iptv", "channel", "streaming", "volleyball", "baseball",
        "hockey", "formula1", "f1", "motogp", "ufc", "boxing", "wwe", "nba",
        "nfl", "mlb", "nhl", "badminton", "kabaddi", "esports", "racing",
        "fighting", "fifa", "olympics", "wrestling"
    )

    private val stage1PluginCount = 3
    private val stage1TotalTimeoutMs = 30_000L
    private val stage2TotalTimeoutMs = 60_000L
    private val totalLoadTimeoutMs = 90_000L
    private val perPluginTimeoutMs = 20_000L
    private val maxConcurrentPluginLoads = 3

    private val searchLock = Mutex()
    private val homeLock = Mutex()
    private val expandLock = mutableSetOf<String>()

    private val expandable: MutableMap<String, ExpandableHomepageList> = java.util.LinkedHashMap()
    private val searchExpandable: MutableMap<String, ExpandableHomepageList> = java.util.LinkedHashMap()

    private var currentQuery: String? = null
    private var currentCategory: SportCategory = SportCategory.Live
    private var currentHomePage = 1
    private var currentSearchPage = 1

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    fun getFilteredApis(): List<MainAPI> {
        val allApis = context?.filterProviderByPreferredMedia() ?: emptyList()
        return allApis.filter { api ->
            api.supportedTypes.contains(TvType.Live) || 
            api.name.lowercase().contains("iptv") ||
            api.name.lowercase().contains("fred") ||
            api.name.lowercase().contains("fred tv") ||
            sportKeywords.any { api.name.lowercase().contains(it) }
        }
    }

    fun load(cacheKey: String?): Job {
        loadJob?.cancel()
        searchJob?.cancel()
        currentHomePage = 1
        currentQuery = null
        currentSearchPage = 1

        loadJob = scope.ioSafe {
            val cached = cacheKey?.let { com.lagradost.cloudstream3.CloudStreamApp.getKey<Map<String, ExpandableHomepageList>>(it) }
            if (cached != null) {
                homeLock.withLock {
                    expandable.clear()
                    expandable.putAll(cached)
                }
                updateFilteredOutput()
            } else {
                onStateUpdated(Resource.Loading())
            }

            val filteredApis = getFilteredApis()
            if (filteredApis.isEmpty()) {
                if (cached == null) {
                    onStateUpdated(Resource.Success(emptyMap()))
                }
                return@ioSafe
            }

            val startTime = System.currentTimeMillis()
            val shuffledApis = filteredApis.shuffled()
            val stage1Plugins = PluginPriorityManager.selectInitialPlugins(filteredApis, stage1PluginCount)
            val stage2Plugins = shuffledApis.filterNot { api ->
                stage1Plugins.any { it.name == api.name && it.lang == api.lang }
            }

            suspend fun loadPlugins(plugins: List<MainAPI>, stageDeadline: Long) {
                plugins.chunked(maxConcurrentPluginLoads).forEach { chunk ->
                    if (System.currentTimeMillis() - startTime > totalLoadTimeoutMs) return@forEach
                    if (System.currentTimeMillis() - startTime > stageDeadline) return@forEach

                    chunk.amap { api ->
                        withTimeoutOrNull(perPluginTimeoutMs) {
                            APIRepository(api).getMainPage(1, null)
                        }
                    }.forEach { result ->
                        if (result != null) {
                            mergeHomeResult(result)
                        }
                    }
                    updateFilteredOutput()
                    cacheKey?.let { com.lagradost.cloudstream3.CloudStreamApp.setKey(it, expandable) }
                }
            }

            loadPlugins(stage1Plugins, stage1TotalTimeoutMs)
            loadPlugins(stage2Plugins, stage2TotalTimeoutMs)
            cacheKey?.let { com.lagradost.cloudstream3.CloudStreamApp.setKey(it, expandable) }
        }
        return loadJob!!
    }

    fun search(query: String?) {
        searchJob?.cancel()
        currentQuery = query
        currentSearchPage = 1

        if (query.isNullOrBlank()) {
            searchExpandable.clear()
            updateFilteredOutput()
            return
        }

        searchJob = scope.ioSafe {
            delay(500)
            searchLock.withLock {
                searchExpandable.clear()
            }
            onStateUpdated(Resource.Loading())

            val filteredApis = getFilteredApis()
            withContext(Dispatchers.IO) {
                filteredApis.amap { api ->
                    val repo = APIRepository(api)
                    val searchResult = repo.search(query, 1)
                    if (searchResult is Resource.Success) {
                        val res = searchResult.value
                        val liveItems = res.items.filter { 
                            it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                        }
                        if (liveItems.isNotEmpty()) {
                            searchLock.withLock {
                                searchExpandable[api.name] = ExpandableHomepageList(
                                    HomePageList(api.name, liveItems),
                                    1,
                                    res.hasNext
                                )
                            }
                        }
                    }
                }
            }
            updateFilteredOutput()
        }
    }

    fun loadMore(cacheKey: String?, onFinished: (Boolean) -> Unit) {
        val query = currentQuery
        val filteredApis = getFilteredApis()
        if (filteredApis.isEmpty()) {
            onFinished(false)
            return
        }

        scope.ioSafe {
            if (!query.isNullOrBlank()) {
                currentSearchPage++
                withContext(Dispatchers.IO) {
                    filteredApis.amap { api ->
                        val repo = APIRepository(api)
                        val searchResult = repo.search(query, currentSearchPage)
                        if (searchResult is Resource.Success) {
                            val res = searchResult.value
                            val liveItems = res.items.filter {
                                it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                            }
                            if (liveItems.isNotEmpty()) {
                                searchLock.withLock {
                                    searchExpandable[api.name] = (searchExpandable[api.name] ?: ExpandableHomepageList(
                                        HomePageList(api.name, emptyList()),
                                        currentSearchPage,
                                        res.hasNext
                                    )).apply {
                                        list.list = (list.list + liveItems).distinctBy { it.url }
                                        hasNext = res.hasNext
                                        currentPage = currentSearchPage
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                currentHomePage++
                withContext(Dispatchers.IO) {
                    filteredApis.amap { api ->
                        val repo = APIRepository(api)
                        val result = repo.getMainPage(currentHomePage, null)
                        if (result is Resource.Success) {
                            mergeHomeResult(result)
                        }
                    }
                    cacheKey?.let { com.lagradost.cloudstream3.CloudStreamApp.setKey(it, expandable) }
                }
            }
            updateFilteredOutput()
            onFinished(false)
        }
    }

    fun expand(name: String) {
        val query = currentQuery
        scope.ioSafe {
            if (query.isNullOrBlank()) {
                expandHome(name)
            } else {
                expandSearch(name, query)
            }
        }
    }

    private suspend fun expandHome(name: String) {
        synchronized(expandLock) {
            if (expandLock.contains(name)) return
            expandLock += name
        }
        val api = getFilteredApis().find { it.name == name }
        if (api != null) {
            val current = homeLock.withLock { expandable[name] }
            if (current != null) {
                val nextPage = current.currentPage + 1
                val repo = APIRepository(api)
                repo.waitForHomeDelay()
                val next = repo.getMainPage(nextPage, api.mainPage.indexOfFirst { it.name == name })
                if (next is Resource.Success) {
                    next.value.filterNotNull().forEach { main ->
                        main.items.forEach { newList ->
                            val strictlyLiveItems = newList.list.filter {
                                it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                            }
                            homeLock.withLock {
                                expandable[name]?.apply {
                                    hasNext = main.hasNext
                                    currentPage = nextPage
                                    this.list.list = (this.list.list + strictlyLiveItems).distinctBy { it.url }
                                }
                            }
                        }
                    }
                } else {
                    homeLock.withLock { expandable[name]?.hasNext = false }
                }
            }
        }
        synchronized(expandLock) { expandLock -= name }
        updateFilteredOutput()
    }

    private suspend fun expandSearch(name: String, query: String) {
        synchronized(expandLock) {
            if (expandLock.contains(name)) return
            expandLock += name
        }
        val api = getFilteredApis().find { it.name == name }
        if (api != null) {
            val current = searchLock.withLock { searchExpandable[name] }
            if (current != null) {
                val nextPage = current.currentPage + 1
                val repo = APIRepository(api)
                val search = repo.search(query, nextPage)
                if (search is Resource.Success) {
                    val searchResult = search.value
                    val liveItems = searchResult.items.filter { 
                        it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                    }
                    searchLock.withLock {
                        current.list.list = (current.list.list + liveItems).distinctBy { it.url }
                        current.currentPage = nextPage
                        current.hasNext = searchResult.hasNext
                    }
                } else {
                    searchLock.withLock { current.hasNext = false }
                }
            }
        }
        synchronized(expandLock) { expandLock -= name }
        updateFilteredOutput()
    }

    fun setSportCategory(category: SportCategory) {
        currentCategory = category
        updateFilteredOutput()
    }

    private fun updateFilteredOutput() {
        val query = currentQuery
        val category = currentCategory
        
        val sourceMap = if (query.isNullOrBlank()) {
            homeLock.blockingLock { java.util.LinkedHashMap(expandable) }
        } else {
            searchLock.blockingLock { java.util.LinkedHashMap(searchExpandable) }
        }

        val filteredMap = sourceMap.mapValues { (catName, expandableList) ->
            val filteredItems = expandableList.list.list.filter { item ->
                isItemInCategory(item, catName, category)
            }
            expandableList.copy(list = expandableList.list.copy(list = filteredItems))
        }.filterValues { it.list.list.isNotEmpty() }
        
        if (filteredMap.isEmpty() && category != SportCategory.Live) {
            onStateUpdated(Resource.Failure(false, "No ${category.name} streams available"))
        } else {
            onStateUpdated(Resource.Success(filteredMap))
        }
    }

    private fun isItemInCategory(item: SearchResponse, categoryName: String, category: SportCategory): Boolean {
        if (category == SportCategory.Live) return true
        val name = item.name.lowercase()
        val cat = categoryName.lowercase()
        return when (category) {
            SportCategory.Football -> {
                cat.contains("football") || cat.contains("soccer") || 
                name.contains("football") || name.contains("soccer") || 
                name.contains("premier league") || name.contains("la liga") ||
                name.contains("serie a") || name.contains("bundesliga") ||
                name.contains("ligue 1") || name.contains("champions league") ||
                name.contains("europa league")
            }
            SportCategory.Cricket -> {
                cat.contains("cricket") || name.contains("cricket") || 
                name.contains("ipl") || name.contains("icc") ||
                name.contains("t20") || name.contains("odi") ||
                name.contains("test match")
            }
            SportCategory.More -> {
                (cat.contains("sports") || name.contains("sports")) && 
                !isItemInCategory(item, categoryName, SportCategory.Football) &&
                !isItemInCategory(item, categoryName, SportCategory.Cricket)
            }
        }
    }

    private suspend fun mergeHomeResult(resource: Resource<List<HomePageResponse?>>) {
        if (resource is Resource.Success) {
            val freshApis = resource.value.flatMap { it?.items ?: emptyList() }
                .flatMap { it.list }
                .map { it.apiName }
                .distinct()

            homeLock.withLock {
                if (freshApis.isNotEmpty()) {
                    expandable.values.forEach { expandableList ->
                        expandableList.list.list = expandableList.list.list.filterNot {
                            freshApis.contains(it.apiName)
                        }
                    }
                }

                resource.value.forEach { home ->
                    home?.items?.forEach { list ->
                        val categoryName = list.name.lowercase()
                        val isLiveList = list.list.any { it.type == TvType.Live } ||
                                sportKeywords.any { categoryName.contains(it) }

                        if (!isLiveList) return@forEach

                        val strictlyLiveItems = list.list.filter {
                            it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                        }

                        if (strictlyLiveItems.isEmpty()) return@forEach

                        val noMoviesList = list.copy(list = strictlyLiveItems)
                        val filteredList = context?.filterHomePageListByFilmQuality(noMoviesList) ?: noMoviesList
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

                val sortedEntries = expandable.entries.sortedByDescending { (name, _) ->
                    val n = name.lowercase()
                    when {
                        n.contains("football") || n.contains("soccer") -> 3
                        n.contains("cricket") -> 2
                        n.contains("sports") -> 1
                        else -> 0
                    }
                }

                expandable.clear()
                sortedEntries.forEach { (name, list) ->
                    expandable[name] = list
                }
            }
        }
    }

    private inline fun <T> Mutex.blockingLock(action: () -> T): T {
        return synchronized(this) { action() }
    }
}
