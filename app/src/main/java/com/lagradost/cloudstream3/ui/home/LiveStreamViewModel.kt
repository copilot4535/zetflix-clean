package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.utils.AppContextUtils.filterHomePageListByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia

import com.lagradost.cloudstream3.utils.LIVESTREAM_CACHE_KEY

class LiveStreamViewModel : BaseHomeViewModel() {
    override val currentApiName: String = "Livestream"
    override val cacheKey: String = LIVESTREAM_CACHE_KEY

    private val searchQuery = MutableLiveData<String?>(null)
    private val _filteredPage = MediatorLiveData<Resource<Map<String, ExpandableHomepageList>>>()
    val filteredPage: LiveData<Resource<Map<String, ExpandableHomepageList>>> = _filteredPage

    init {
        _filteredPage.addSource(page) { res ->
            val query = searchQuery.value
            _filteredPage.value = if (res is Resource.Success && !query.isNullOrBlank()) {
                filterPage(res.value, query)
            } else {
                res
            }
        }
        _filteredPage.addSource(searchQuery) { query ->
            val current = page.value
            if (current is Resource.Success) {
                _filteredPage.value = filterPage(current.value, query)
            }
        }
    }

    private fun filterPage(map: Map<String, ExpandableHomepageList>, query: String?): Resource<Map<String, ExpandableHomepageList>> {
        if (query.isNullOrBlank()) return Resource.Success(map)
        
        val filtered = map.mapValues { (_, value) ->
            val filteredItems = value.list.list.filter { 
                it.name.contains(query, ignoreCase = true) 
            }
            value.copy(list = value.list.copy(list = filteredItems))
        }.filter { it.value.list.list.isNotEmpty() }
        
        return Resource.Success(filtered)
    }

    fun search(query: String?) {
        searchQuery.postValue(query)
    }

    override fun getFilteredApis(): List<MainAPI> {
        val allApis = context?.filterProviderByPreferredMedia() ?: emptyList()
        // Prioritize APIs that explicitly support Live, match "iptv", "fred" or our sport keywords
        return allApis.filter { api ->
            api.supportedTypes.contains(TvType.Live) || 
            api.name.lowercase().contains("iptv") ||
            api.name.lowercase().contains("fred") ||
            sportKeywords.any { api.name.lowercase().contains(it) }
        }
    }

    override fun mergeHomeResult(resource: Resource<List<HomePageResponse?>>) {
        if (resource is Resource.Success) {
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

    override fun updatePreviewFromExpandable() {
        // No hero banner for Livestream as per request
        _preview.postValue(Resource.Failure(false, "No banner for Livestream"))
    }
}
