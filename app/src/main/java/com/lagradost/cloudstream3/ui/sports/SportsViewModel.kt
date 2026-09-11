package com.lagradost.cloudstream3.ui.sports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.data.SportsRepositoryImpl
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.sports.domain.models.Sport
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SportsViewModel : ViewModel() {
    private val repository = SportsRepositoryImpl()

    private val _sports = MutableLiveData<SportsResource<List<Sport>>>()
    val sports: LiveData<SportsResource<List<Sport>>> = _sports

    private val _liveMatches = MutableLiveData<SportsResource<List<Match>>>()
    val liveMatches: LiveData<SportsResource<List<Match>>> = _liveMatches

    private val _fixtures = MutableLiveData<SportsResource<List<Match>>>()
    val fixtures: LiveData<SportsResource<List<Match>>> = _fixtures

    fun loadSports() = viewModelScope.launch {
        repository.getSports().collectLatest {
            _sports.postValue(it)
        }
    }

    fun loadLiveMatches(sportId: String? = null) = viewModelScope.launch {
        repository.getLiveMatches(sportId).collectLatest {
            _liveMatches.postValue(it)
        }
    }

    fun loadFixtures(sportId: String? = null) = viewModelScope.launch {
        repository.getFixtures(sportId).collectLatest {
            _fixtures.postValue(it)
        }
    }
}
