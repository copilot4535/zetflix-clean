package com.lagradost.cloudstream3.ui.sports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.data.SportsRepositoryImpl
import com.lagradost.cloudstream3.sports.domain.models.League
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.MatchStatus
import com.lagradost.cloudstream3.sports.domain.models.Sport
import com.lagradost.cloudstream3.sports.domain.models.Standing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FilterMode {
    LIVE,
    FIXTURES,
    RESULTS
}

class SportsViewModel : ViewModel() {
    private val repository = SportsRepositoryImpl()

    private val _sports = MutableLiveData<SportsResource<List<Sport>>>()
    val sports: LiveData<SportsResource<List<Sport>>> = _sports

    private val _leagues = MutableLiveData<SportsResource<List<League>>>()
    val leagues: LiveData<SportsResource<List<League>>> = _leagues

    private val _selectedLeague = MutableLiveData<League?>(null)
    val selectedLeague: LiveData<League?> = _selectedLeague

    private val _filterMode = MutableLiveData<FilterMode>(FilterMode.LIVE)
    val filterMode: LiveData<FilterMode> = _filterMode

    private val _standings = MutableLiveData<SportsResource<List<Standing>>>()
    val standings: LiveData<SportsResource<List<Standing>>> = _standings

    private val _matches = MutableLiveData<SportsResource<List<Match>>>()
    
    private val _displayMatches = MediatorLiveData<SportsResource<List<Match>>>()
    val displayMatches: LiveData<SportsResource<List<Match>>> = _displayMatches

    private var pollingJob: Job? = null

    init {
        _displayMatches.addSource(_matches) { updateDisplayMatches() }
        _displayMatches.addSource(_filterMode) { updateDisplayMatches() }
    }

    private fun updateDisplayMatches() {
        val resource = _matches.value ?: return
        val mode = _filterMode.value ?: FilterMode.LIVE

        if (resource is SportsResource.Success) {
            val filtered = when (mode) {
                FilterMode.LIVE -> resource.data.filter { it.status == MatchStatus.LIVE }
                FilterMode.FIXTURES -> resource.data.filter { it.status == MatchStatus.UPCOMING }
                FilterMode.RESULTS -> resource.data.filter { it.status == MatchStatus.FINISHED }
            }
            _displayMatches.value = SportsResource.Success(filtered)
        } else {
            _displayMatches.value = resource
        }
    }

    fun loadLeagues() = viewModelScope.launch {
        repository.getLeagues().collectLatest {
            _leagues.postValue(it)
            if (it is SportsResource.Success && _selectedLeague.value == null) {
                selectLeague(it.data.firstOrNull())
            }
        }
    }

    fun selectLeague(league: League?) {
        _selectedLeague.value = league
        league?.let { restartPolling(it.shortcut) }
    }

    fun setFilterMode(mode: FilterMode) {
        _filterMode.value = mode
        if (mode == FilterMode.RESULTS) {
            loadStandings()
        }
    }

    fun loadStandings() = viewModelScope.launch {
        val league = _selectedLeague.value ?: return@launch
        // Default to 2024 for now, future work will handle multi-season
        repository.getStandings(league.shortcut, "2024").collectLatest {
            _standings.postValue(it)
        }
    }

    private fun restartPolling(shortcut: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                repository.getLiveMatches(shortcut).collect { resource ->
                    _matches.postValue(resource)
                }

                val currentMatches = (_matches.value as? SportsResource.Success)?.data
                val hasLive = currentMatches?.any { it.status == MatchStatus.LIVE } ?: false

                val interval = if (hasLive) 30_000L else 300_000L
                delay(interval)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
