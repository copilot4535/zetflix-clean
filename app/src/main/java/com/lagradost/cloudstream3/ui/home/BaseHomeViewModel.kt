package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppContextUtils.filterHomePageListByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE_BACKUP
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.deleteAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllWatchStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.PluginPriorityManager
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.EnumSet
import java.util.concurrent.CopyOnWriteArrayList

abstract class BaseHomeViewModel : ViewModel() {
    open val sportKeywords = listOf(
        "sports", "live", "cricket", "football", "soccer", "basketball", "tennis",
        "rugby", "golf", "iptv", "channel", "streaming", "volleyball", "baseball",
        "hockey", "formula1", "f1", "motogp", "ufc", "boxing", "wwe", "nba",
        "nfl", "mlb", "nhl", "badminton", "kabaddi", "esports", "racing",
        "fighting", "fifa", "olympics", "wrestling"
    )

    open val stage1PluginCount = 3
    open val stage1TotalTimeoutMs = 15_000L
    open val stage2TotalTimeoutMs = 30_000L
    open val totalLoadTimeoutMs = 45_000L
    open val perPluginTimeoutMs = 10_000L
    open val maxConcurrentPluginLoads = 3

    companion object {
        @Deprecated("Use open properties instead")
        protected const val STAGE1_PLUGIN_COUNT = 3
        @Deprecated("Use open properties instead")
        protected const val STAGE1_TOTAL_TIMEOUT_MS = 15_000L
        @Deprecated("Use open properties instead")
        protected const val STAGE2_TOTAL_TIMEOUT_MS = 30_000L
        @Deprecated("Use open properties instead")
        protected const val TOTAL_LOAD_TIMEOUT_MS = 45_000L
        @Deprecated("Use open properties instead")
        protected const val PER_PLUGIN_TIMEOUT_MS = 10_000L
        @Deprecated("Use open properties instead")
        protected const val MAX_CONCURRENT_PLUGIN_LOADS = 3

        protected val sportKeywords = listOf("sports", "live", "cricket", "football", "soccer", "basketball", "tennis", "rugby", "golf", "iptv", "channel", "streaming")

        suspend fun getResumeWatching(): List<DataStoreHelper.ResumeWatchingResult>? {
            val resumeWatching = withContext(Dispatchers.IO) {
                getAllResumeStateIds()?.mapNotNull { id ->
                    getLastWatched(id)
                }?.sortedBy { -it.updateTime }
            }
            val resumeWatchingResult = withContext(Dispatchers.IO) {
                resumeWatching?.mapNotNull { resume ->
                    val headerCache = getKey<DownloadObjects.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        resume.parentId.toString()
                    )

                    val data = if (headerCache == null) {
                        val oldData = getKey<DownloadObjects.DownloadHeaderCached>(
                            DOWNLOAD_HEADER_CACHE_BACKUP,
                            resume.parentId.toString()
                        ) ?: return@mapNotNull null

                        setKey(DOWNLOAD_HEADER_CACHE, resume.parentId.toString(), oldData)
                        oldData
                    } else {
                        headerCache
                    }

                    val watchPos = getViewPos(resume.episodeId)

                    DataStoreHelper.ResumeWatchingResult(
                        data.name,
                        data.url,
                        data.apiName,
                        data.type,
                        data.poster,
                        watchPos,
                        resume.episodeId,
                        resume.parentId,
                        resume.episode,
                        resume.season,
                        resume.isFromDownload
                    )
                }
            }
            return resumeWatchingResult
        }
    }

    fun deleteResumeWatching() {
        deleteAllResumeStateIds()
        loadResumeWatching()
    }

    fun deleteBookmarks(list: List<SearchResponse>) {
        list.forEach { DataStoreHelper.deleteBookmarkedData(it.id) }
        loadStoredData()
    }

    var repo: APIRepository? = null

    protected val _apiName = MutableLiveData<String>()
    val apiName: LiveData<String> = _apiName

    protected val _currentAccount = MutableLiveData<DataStoreHelper.Account?>()
    val currentAccount: MutableLiveData<DataStoreHelper.Account?> = _currentAccount

    protected val _randomItems = MutableLiveData<List<SearchResponse>?>(null)
    val randomItems: LiveData<List<SearchResponse>?> = _randomItems

    protected var currentShuffledList: List<SearchResponse> = listOf()

    protected val _availableWatchStatusTypes =
        MutableLiveData<Pair<Set<WatchType>, Set<WatchType>>>()
    val availableWatchStatusTypes: LiveData<Pair<Set<WatchType>, Set<WatchType>>> =
        _availableWatchStatusTypes
    protected val _bookmarks = MutableLiveData<Pair<Boolean, List<SearchResponse>>>()
    val bookmarks: LiveData<Pair<Boolean, List<SearchResponse>>> = _bookmarks

    protected val _resumeWatching = MutableLiveData<List<SearchResponse>>()
    protected val _preview = MutableLiveData<Resource<Pair<Boolean, List<LoadResponse>>>>()
    protected val previewResponses = CopyOnWriteArrayList<LoadResponse>()
    protected val previewResponsesAdded = mutableSetOf<String>()

    val resumeWatching: LiveData<List<SearchResponse>> = _resumeWatching
    val preview: LiveData<Resource<Pair<Boolean, List<LoadResponse>>>> = _preview

    protected fun loadResumeWatching() = viewModelScope.launchSafe {
        val resumeWatchingResult = getResumeWatching()
        resumeWatchingResult?.let {
            _resumeWatching.postValue(it)
        }
    }

    fun loadStoredData(preferredWatchStatus: Set<WatchType>?) = viewModelScope.launchSafe {
        val watchStatusIds = withContext(Dispatchers.IO) {
            getAllWatchStateIds()?.map { id ->
                Pair(id, getResultWatchState(id))
            }
        }?.distinctBy { it.first } ?: return@launchSafe

        val length = WatchType.entries.size
        val currentWatchTypes = mutableSetOf<WatchType>()

        for (watch in watchStatusIds) {
            currentWatchTypes.add(watch.second)
            if (currentWatchTypes.size >= length) {
                break
            }
        }

        currentWatchTypes.remove(WatchType.NONE)

        if (currentWatchTypes.size <= 0) {
            DataStoreHelper.homeBookmarkedList = intArrayOf()
            _availableWatchStatusTypes.postValue(setOf<WatchType>() to setOf())
            _bookmarks.postValue(Pair(false, ArrayList()))
            return@launchSafe
        }

        val watchPrefNotNull = preferredWatchStatus ?: EnumSet.of(currentWatchTypes.first())

        DataStoreHelper.homeBookmarkedList = watchPrefNotNull.map { it.internalId }.toIntArray()
        _availableWatchStatusTypes.postValue(
            watchPrefNotNull to currentWatchTypes
        )

        val list = withContext(Dispatchers.IO) {
            watchStatusIds.filter { watchPrefNotNull.contains(it.second) }
                .mapNotNull { getBookmarkedData(it.first) }
                .sortedBy { -it.latestUpdatedTime }
        }
        _bookmarks.postValue(Pair(true, list))
    }

    protected var onGoingLoad: Job? = null

    data class ExpandableHomepageList(
        var list: HomePageList,
        var currentPage: Int,
        var hasNext: Boolean,
    )

    protected val expandable: MutableMap<String, ExpandableHomepageList> = java.util.LinkedHashMap()
    protected val _page =
        MutableLiveData<Resource<Map<String, ExpandableHomepageList>>>(Resource.Loading())
    val page: LiveData<Resource<Map<String, ExpandableHomepageList>>> = _page

    val lock: MutableSet<String> = mutableSetOf()

    suspend fun expandAndReturn(name: String): ExpandableHomepageList? {
        if (lock.contains(name)) return null
        lock += name

        repo?.apply {
            waitForHomeDelay()

            expandable[name]?.let { current ->
                debugAssert({ !current.hasNext }) {
                    "Expand called when not needed"
                }

                val nextPage = current.currentPage + 1
                val next = getMainPage(nextPage, mainPage.indexOfFirst { it.name == name })
                if (next is Resource.Success) {
                    next.value.filterNotNull().forEach { main ->
                        main.items.forEach { newList ->
                            val key = newList.name
                            expandable[key]?.apply {
                                hasNext = main.hasNext
                                currentPage = nextPage

                                debugWarning({ newList.list.any { outer -> this.list.list.any { it.url == outer.url } } }) {
                                    "Expanded contained an item that was previously already in the list\n${list.name} = ${this.list.list}\n${newList.name} = ${newList.list}"
                                }

                                this.list.list += newList.list
                                this.list.list.distinctBy { it.url }
                            } ?: debugWarning {
                                "Expanded an item not in main load named $key, current list is ${expandable.keys}"
                            }
                        }
                    }
                } else {
                    current.hasNext = false
                }
            }
            _page.postValue(Resource.Success(expandable))
        }

        lock -= name

        return expandable[name]
    }

    open fun expand(name: String) = viewModelScope.launchSafe {
        expandAndReturn(name)
    }

    protected suspend fun updatePreviewResponses(
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

    protected abstract suspend fun mergeHomeResult(resource: Resource<List<HomePageResponse?>>)

    protected abstract val currentApiName: String
    protected abstract val cacheKey: String?

    protected open fun load(): Job = ioSafe {
        repo = null

        _apiName.postValue(currentApiName)
        _randomItems.postValue(listOf())

        addJob?.cancel()

        val startTime = System.currentTimeMillis()
        var cachedShown = false

        val cached = cacheKey?.let { getKey<Map<String, ExpandableHomepageList>>(it) }
        if (cached != null) {
            expandable.clear()
            expandable.putAll(cached)
            _page.postValue(Resource.Success(expandable))
            cachedShown = true
        }

        if (!cachedShown) {
            expandable.clear()
            _page.postValue(Resource.Loading())
            _preview.postValue(Resource.Loading())
        }

        val filteredApis = getFilteredApis()

        if (filteredApis.isEmpty()) {
            if (!cachedShown) {
                _page.postValue(Resource.Success(emptyMap()))
                _preview.postValue(Resource.Failure(false, "No plugins found"))
            }
            return@ioSafe
        }

        // Overall list shuffled for stage 2
        val shuffledApis = filteredApis.shuffled()

        // Select initial plugins with priority
        val stage1Plugins = PluginPriorityManager.selectInitialPlugins(
            filteredApis,
            stage1PluginCount
        )

        // Stage 2: remaining plugins excluding stage 1, shuffled
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
                
                _page.postValue(Resource.Success(expandable))
                cacheKey?.let { setKey(it, expandable) }
            }
        }

        loadPlugins(stage1Plugins, stage1TotalTimeoutMs)

        updatePreviewFromExpandable()

        loadPlugins(stage2Plugins, stage2TotalTimeoutMs)

        updatePreviewFromExpandable()

        cacheKey?.let { setKey(it, expandable) }
    }

    protected open fun getFilteredApis(): List<MainAPI> {
        return context?.filterProviderByPreferredMedia() ?: emptyList()
    }

    protected abstract fun updatePreviewFromExpandable()

    fun click(callback: SearchClickCallback) {
        if (callback.action != SEARCH_ACTION_FOCUSED) {
            SearchHelper.handleSearchClickCallback(callback)
        }
    }

    protected val _popup = MutableLiveData<Pair<ExpandableHomepageList, (() -> Unit)?>?>(null)
    val popup: LiveData<Pair<ExpandableHomepageList, (() -> Unit)?>?> = _popup

    fun popup(list: ExpandableHomepageList?, deleteCallback: (() -> Unit)? = null) {
        if (list == null)
            _popup.postValue(null)
        else
            _popup.postValue(list to deleteCallback)
    }

    protected fun bookmarksUpdated(unused: Boolean) {
        reloadStored()
    }

    protected fun afterPluginsLoaded(forceReload: Boolean) {
        loadAndCancel(DataStoreHelper.currentHomePage, forceReload)
    }

    protected fun afterMainPluginsLoaded(unused: Boolean = false) {
        loadAndCancel(DataStoreHelper.currentHomePage, false)
    }

    protected fun reloadHome(unused: Boolean = false) {
        loadAndCancel(DataStoreHelper.currentHomePage, true)
    }

    protected fun reloadAccount(unused: Boolean = false) {
        _currentAccount.postValue(
            getCurrentAccount()
        )
    }

    init {
        MainActivity.bookmarksUpdatedEvent += ::bookmarksUpdated
        MainActivity.afterPluginsLoadedEvent += ::afterPluginsLoaded
        MainActivity.mainPluginsLoadedEvent += ::afterMainPluginsLoaded
        MainActivity.reloadHomeEvent += ::reloadHome
        MainActivity.reloadAccountEvent += ::reloadAccount
    }

    override fun onCleared() {
        MainActivity.bookmarksUpdatedEvent -= ::bookmarksUpdated
        MainActivity.afterPluginsLoadedEvent -= ::afterPluginsLoaded
        MainActivity.mainPluginsLoadedEvent -= ::afterMainPluginsLoaded
        MainActivity.reloadHomeEvent -= ::reloadHome
        MainActivity.reloadAccountEvent -= ::reloadAccount
        super.onCleared()
    }

    fun queryTextSubmit(query: String) {
        QuickSearchFragment.pushSearch(
            query,
            repo?.name?.let { arrayOf(it) })
    }

    fun queryTextChange(newText: String) {
    }

    fun loadStoredData() {
        val list = EnumSet.noneOf(WatchType::class.java)
        DataStoreHelper.homeBookmarkedList.map { WatchType.fromInternalId(it) }.let {
            list.addAll(it)
        }
        loadStoredData(list)
    }

    fun reloadStored() {
        loadResumeWatching()
        loadStoredData()
    }

    fun click(load: LoadClickCallback) {
        loadResult(load.response.url, load.response.apiName, load.response.name, load.action)
    }

    fun loadAndCancel(
        preferredApiName: String?,
        forceReload: Boolean = true,
        fromUI: Boolean = false
    ) = ioSafe {
            val currentPage = page.value

            if (!forceReload && (currentPage is Resource.Success && currentPage.value.isNotEmpty())) {
                return@ioSafe
            }

            onGoingLoad?.cancel()
            onGoingLoad = load()
            reloadAccount()
        }
}
