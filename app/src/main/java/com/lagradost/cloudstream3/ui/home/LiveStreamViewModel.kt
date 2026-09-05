package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.movie.BaseHomeViewModel
import com.lagradost.cloudstream3.ui.movie.BaseHomeViewModel.ExpandableHomepageList
import com.lagradost.cloudstream3.utils.AppContextUtils.filterHomePageListByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.LIVESTREAM_CACHE_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LiveStreamViewModel : BaseHomeViewModel() {
    override val currentApiName: String = "Livestream"
    override val cacheKey: String = LIVESTREAM_CACHE_KEY

    override val stage1TotalTimeoutMs = 30_000L
    override val stage2TotalTimeoutMs = 60_000L
    override val totalLoadTimeoutMs = 90_000L
    override val perPluginTimeoutMs = 20_000L

    private val searchQuery = MutableLiveData<String?>(null)
    private val _searchPage = MutableLiveData<Resource<Map<String, ExpandableHomepageList>>>()
    private val searchExpandable: MutableMap<String, ExpandableHomepageList> = java.util.LinkedHashMap()
    private val searchLock = Mutex()
    private val homeLock = Mutex()

    private val _filteredPage = MediatorLiveData<Resource<Map<String, ExpandableHomepageList>>>()
    val filteredPage: LiveData<Resource<Map<String, ExpandableHomepageList>>> = _filteredPage

    private val _searchLoading = MutableLiveData<Boolean>()
    val searchLoading: LiveData<Boolean> = _searchLoading

    private var searchJob: Job? = null

    private var currentHomePage = 1
    private var currentSearchPage = 1

    private val _loadMoreLoading = MutableLiveData<Boolean>(false)
    val loadMoreLoading: LiveData<Boolean> = _loadMoreLoading

    init {
        _filteredPage.addSource(page) { res ->
            if (searchQuery.value.isNullOrBlank()) {
                _filteredPage.value = res
            }
        }
        _filteredPage.addSource(_searchPage) { res ->
            if (!searchQuery.value.isNullOrBlank()) {
                _filteredPage.value = res
            }
        }
        _filteredPage.addSource(searchQuery) { query ->
            if (query.isNullOrBlank()) {
                _filteredPage.value = page.value
            }
        }
    }

    override fun expand(name: String) = viewModelScope.launchSafe {
        val query = searchQuery.value
        if (query.isNullOrBlank()) {
            expandAndReturn(name)
        } else {
            expandSearch(name, query)
        }
    }

    private suspend fun expandSearch(name: String, query: String) {
        if (lock.contains(name)) return
        searchLock.withLock {
            if (lock.contains(name)) return
            lock += name
        }

        val api = getFilteredApis().find { it.name == name }
        if (api != null) {
            searchLock.withLock {
                searchExpandable[name]
            }?.let { current ->
                val nextPage = current.currentPage + 1
                val repo = APIRepository(api)
                val search = repo.search(query, nextPage)
                
                if (search is Resource.Success) {
                    val searchResult = search.value
                    val liveItems = searchResult.items.filter { 
                        it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                    }
                    
                    searchLock.withLock {
                        current.list.list += liveItems
                        current.list.list = current.list.list.distinctBy { it.url }
                        current.currentPage = nextPage
                        current.hasNext = searchResult.hasNext
                    }
                } else {
                    searchLock.withLock {
                        current.hasNext = false
                    }
                }
                _searchPage.postValue(Resource.Success(searchExpandable))
            }
        }

        searchLock.withLock {
            lock -= name
        }
    }

    fun search(query: String?) {
        searchJob?.cancel()
        searchQuery.value = query
        currentSearchPage = 1
        
        if (query.isNullOrBlank()) {
            _searchLoading.postValue(false)
            searchExpandable.clear()
            _searchPage.postValue(Resource.Success(emptyMap()))
            return
        }

        searchJob = ioSafe {
            _searchLoading.postValue(true)
            delay(500)
            
            searchLock.withLock {
                searchExpandable.clear()
            }
            _searchPage.postValue(Resource.Loading())
            
            val filteredApis = getFilteredApis()
            
            withContext(Dispatchers.IO) {
                filteredApis.amap { api ->
                    val repo = APIRepository(api)
                    val search = repo.search(query, 1)
                    
                    if (search is Resource.Success) {
                        val searchResult = search.value
                        val liveItems = searchResult.items.filter { 
                            it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                        }
                        
                        if (liveItems.isNotEmpty()) {
                            searchLock.withLock {
                                searchExpandable[api.name] = ExpandableHomepageList(
                                    HomePageList(api.name, liveItems),
                                    1,
                                    searchResult.hasNext
                                )
                            }
                        }
                    }
                }
                _searchPage.postValue(Resource.Success(searchExpandable))
            }
            _searchLoading.postValue(false)
        }
    }

    override fun load(): Job {
        currentHomePage = 1
        return super.load()
    }

    fun loadMore() {
        if (_loadMoreLoading.value == true) return
        val query = searchQuery.value
        val filteredApis = getFilteredApis()
        if (filteredApis.isEmpty()) return

        _loadMoreLoading.value = true

        viewModelScope.launchSafe {
            if (!query.isNullOrBlank()) {
                currentSearchPage++
                withContext(Dispatchers.IO) {
                    filteredApis.amap { api ->
                        val repo = APIRepository(api)
                        val search = repo.search(query, currentSearchPage)

                        if (search is Resource.Success) {
                            val searchResult = search.value
                            val liveItems = searchResult.items.filter {
                                it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                            }

                            if (liveItems.isNotEmpty()) {
                                searchLock.withLock {
                                    searchExpandable[api.name] = (searchExpandable[api.name] ?: ExpandableHomepageList(
                                        HomePageList(api.name, emptyList()),
                                        currentSearchPage,
                                        searchResult.hasNext
                                    )).apply {
                                        list.list = (list.list + liveItems).distinctBy { it.url }
                                        hasNext = searchResult.hasNext
                                        currentPage = currentSearchPage
                                    }
                                }
                            }
                        }
                    }
                    _searchPage.postValue(Resource.Success(searchExpandable))
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
                    _page.postValue(Resource.Success(expandable))
                    cacheKey?.let { com.lagradost.cloudstream3.CloudStreamApp.setKey(it, expandable) }
                }
            }

            _loadMoreLoading.postValue(false)
        }
    }

    override fun getFilteredApis(): List<MainAPI> {
        val allApis = context?.filterProviderByPreferredMedia() ?: emptyList()
        // Prioritize APIs that explicitly support Live, match "iptv", "fred" or our sport keywords
        return allApis.filter { api ->
            api.supportedTypes.contains(TvType.Live) || 
            api.name.lowercase().contains("iptv") ||
            api.name.lowercase().contains("fred") ||
            api.name.lowercase().contains("fred tv") ||
            sportKeywords.any { api.name.lowercase().contains(it) }
        }
    }

    override suspend fun mergeHomeResult(resource: Resource<List<HomePageResponse?>>) {
        if (resource is Resource.Success) {
            val freshApis = resource.value.flatMap { it?.items ?: emptyList() }
                .flatMap { it.list }
                .map { it.apiName }
                .distinct()

            homeLock.withLock {
                // 0. Remove stale cached items from these specific providers
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

                        // 1. Identify if this is a Live list
                        // - Explicitly marked as Live by the provider
                        // - Category name contains live/sports keywords
                        val isLiveList = list.list.any { it.type == TvType.Live } ||
                                sportKeywords.any { categoryName.contains(it) }

                        if (!isLiveList) return@forEach

                        // 2. Strictly filter out any individual items marked as Movie or TvSeries
                        val strictlyLiveItems = list.list.filter {
                            it.type == TvType.Live || (it.type != TvType.Movie && it.type != TvType.AnimeMovie && it.type != TvType.TvSeries && it.type != TvType.AsianDrama)
                        }

                        if (strictlyLiveItems.isEmpty()) return@forEach

                        val noMoviesList = list.copy(list = strictlyLiveItems)
                        val filteredList =
                            context?.filterHomePageListByFilmQuality(noMoviesList) ?: noMoviesList
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

                // Priority sorting for UI rows
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

    override fun updatePreviewFromExpandable() {
        // No hero banner for Livestream as per request
        _preview.postValue(Resource.Failure(false, "No banner for Livestream"))
    }
}
