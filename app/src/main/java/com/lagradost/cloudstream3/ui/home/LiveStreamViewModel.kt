package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.ui.movie.BaseHomeViewModel
import com.lagradost.cloudstream3.ui.movie.BaseHomeViewModel.ExpandableHomepageList
import com.lagradost.cloudstream3.ui.feed.FeedEngine
import com.lagradost.cloudstream3.ui.feed.SportCategory
import com.lagradost.cloudstream3.utils.LIVESTREAM_CACHE_KEY
import kotlinx.coroutines.Job

class LiveStreamViewModel : BaseHomeViewModel() {
    override val currentApiName: String = "Livestream"
    override val cacheKey: String = LIVESTREAM_CACHE_KEY

    override val stage1TotalTimeoutMs = 30_000L
    override val stage2TotalTimeoutMs = 60_000L
    override val totalLoadTimeoutMs = 90_000L
    override val perPluginTimeoutMs = 20_000L

    private val searchQuery = MutableLiveData<String?>(null)
    private val _currentSportCategory = MutableLiveData(SportCategory.Live)
    val currentSportCategory: LiveData<SportCategory> = _currentSportCategory

    private val _filteredPage = MediatorLiveData<Resource<Map<String, ExpandableHomepageList>>>()
    val filteredPage: LiveData<Resource<Map<String, ExpandableHomepageList>>> = _filteredPage

    private val _searchLoading = MutableLiveData<Boolean>()
    val searchLoading: LiveData<Boolean> = _searchLoading

    private val _loadMoreLoading = MutableLiveData<Boolean>(false)
    val loadMoreLoading: LiveData<Boolean> = _loadMoreLoading

    private val feedEngine = FeedEngine(viewModelScope) { state ->
        _filteredPage.postValue(state)
        if (state is Resource.Loading) {
            _searchLoading.postValue(true)
        } else {
            _searchLoading.postValue(false)
        }
    }

    init {
        // Retain compatibility wiring if anything observes currentSportCategory directly
        _filteredPage.addSource(_currentSportCategory) { category ->
            feedEngine.setSportCategory(category)
        }
    }

    fun setSportCategory(category: SportCategory) {
        _currentSportCategory.value = category
    }

    override fun expand(name: String) = viewModelScope.launchSafe {
        feedEngine.expand(name)
    }

    fun search(query: String?) {
        searchQuery.value = query
        feedEngine.search(query)
    }

    override fun load(): Job {
        return feedEngine.load(cacheKey)
    }

    fun loadMore() {
        if (_loadMoreLoading.value == true) return
        _loadMoreLoading.value = true
        feedEngine.loadMore(cacheKey) {
            _loadMoreLoading.postValue(false)
        }
    }

    override fun getFilteredApis(): List<MainAPI> {
        return feedEngine.getFilteredApis()
    }

    override suspend fun mergeHomeResult(resource: Resource<List<HomePageResponse?>>) {
        // Logic handled inside feedEngine
    }

    override fun updatePreviewFromExpandable() {
        _preview.postValue(Resource.Failure(false, "No banner for Livestream"))
    }

    // Retain compatibility alias for enum
    typealias SportCategory = com.lagradost.cloudstream3.ui.feed.SportCategory
}
