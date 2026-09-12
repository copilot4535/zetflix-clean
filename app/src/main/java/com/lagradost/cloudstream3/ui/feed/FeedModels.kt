package com.lagradost.cloudstream3.ui.feed

import com.lagradost.cloudstream3.ui.movie.BaseHomeViewModel.ExpandableHomepageList
import com.lagradost.cloudstream3.mvvm.Resource

enum class SportCategory {
    Live,
    Football,
    Cricket,
    More
}

data class FeedQuery(
    val searchQuery: String? = null,
    val category: SportCategory = SportCategory.Live,
    val page: Int = 1
)

sealed class FeedState {
    object Loading : FeedState()
    data class Success(val data: Map<String, ExpandableHomepageList>) : FeedState()
    data class Failure(val isClean: Boolean, val error: String) : FeedState()
}
