package com.lagradost.cloudstream3.ui.sports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.data.SportsRepositoryImpl
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.MatchStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MatchDetailsViewModel : ViewModel() {
    private val repository = SportsRepositoryImpl()

    private val _match = MutableLiveData<SportsResource<Match>>()
    val match: LiveData<SportsResource<Match>> = _match

    private var pollingJob: Job? = null

    fun loadMatchDetails(matchId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                repository.getMatchDetails(matchId).collectLatest { resource ->
                    _match.postValue(resource)
                    
                    val currentMatch = (resource as? SportsResource.Success)?.data
                    if (currentMatch?.status != MatchStatus.LIVE) {
                        // If not live, stop polling after one successful fetch or error
                        if (resource !is SportsResource.Loading) return@collectLatest
                    }
                }
                
                delay(30_000L) // 30s refresh for live matches
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
