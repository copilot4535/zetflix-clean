package com.lagradost.cloudstream3.ui.sports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.data.SportsRepositoryImpl
import com.lagradost.cloudstream3.sports.domain.models.Match
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TeamDetailsViewModel : ViewModel() {
    private val repository = SportsRepositoryImpl()

    private val _recentMatch = MutableLiveData<SportsResource<Match>>()
    val recentMatch: LiveData<SportsResource<Match>> = _recentMatch

    private val _nextMatch = MutableLiveData<SportsResource<Match>>()
    val nextMatch: LiveData<SportsResource<Match>> = _nextMatch

    fun loadTeamData(leagueShortcut: String, teamId: String) {
        viewModelScope.launch {
            repository.getTeamRecentMatch(leagueShortcut, teamId).collectLatest {
                _recentMatch.postValue(it)
            }
        }
        viewModelScope.launch {
            repository.getTeamNextMatch(leagueShortcut, teamId).collectLatest {
                _nextMatch.postValue(it)
            }
        }
    }
}
