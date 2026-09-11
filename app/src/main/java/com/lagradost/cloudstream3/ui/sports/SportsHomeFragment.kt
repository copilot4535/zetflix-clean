package com.lagradost.cloudstream3.ui.sports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentSportsHomeBinding
import com.lagradost.cloudstream3.sports.common.util.SportsResource

class SportsHomeFragment : Fragment() {
    private var binding: FragmentSportsHomeBinding? = null
    private lateinit var viewModel: SportsViewModel
    private val matchAdapter = MatchAdapter { match ->
        val bundle = Bundle().apply {
            putString(MatchDetailsFragment.ARG_MATCH_ID, match.id)
        }
        findNavController().navigate(R.id.action_navigation_sports_home_to_matchDetailsFragment, bundle)
    }
    private val standingAdapter = StandingAdapter { standing ->
        val bundle = Bundle().apply {
            putString(TeamDetailsFragment.ARG_TEAM_ID, standing.teamId)
            putString(TeamDetailsFragment.ARG_TEAM_NAME, standing.teamName)
            putString(TeamDetailsFragment.ARG_TEAM_LOGO, standing.teamLogoUrl)
            putString(TeamDetailsFragment.ARG_LEAGUE_SHORTCUT, viewModel.selectedLeague.value?.shortcut)
            putString(TeamDetailsFragment.ARG_LEAGUE_NAME, viewModel.selectedLeague.value?.name)
        }
        findNavController().navigate(R.id.action_navigation_sports_home_to_teamDetailsFragment, bundle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSportsHomeBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[SportsViewModel::class.java]
        
        binding?.sportsMatchesRecycler?.adapter = matchAdapter
        binding?.sportsStandingsRecycler?.adapter = standingAdapter

        setupLeagueSelection()
        setupFilterTabs()
        observeData()

        viewModel.loadLeagues()
    }

    private fun setupLeagueSelection() {
        viewModel.leagues.observe(viewLifecycleOwner) { resource ->
            if (resource is SportsResource.Success) {
                binding?.sportsLeagueChips?.removeAllViews()
                resource.data.forEach { league ->
                    val chip = Chip(requireContext()).apply {
                        text = league.name
                        isCheckable = true
                        tag = league.shortcut
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) viewModel.selectLeague(league)
                        }
                    }
                    binding?.sportsLeagueChips?.addView(chip)
                }
                
                // Select first league by default in UI
                val childCount = binding?.sportsLeagueChips?.childCount ?: 0
                if (childCount > 0) {
                    (binding?.sportsLeagueChips?.getChildAt(0) as? Chip)?.isChecked = true
                }
            }
        }
    }

    private fun setupFilterTabs() {
        binding?.sportsFilterTabs?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val mode = when (tab?.position) {
                    0 -> FilterMode.LIVE
                    1 -> FilterMode.FIXTURES
                    2 -> FilterMode.RESULTS
                    3 -> FilterMode.STANDINGS
                    else -> FilterMode.LIVE
                }
                viewModel.setFilterMode(mode)
                
                binding?.sportsMatchesRecycler?.isVisible = mode != FilterMode.STANDINGS
                binding?.sportsStandingsRecycler?.isVisible = mode == FilterMode.STANDINGS
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeData() {
        viewModel.currentMatchday.observe(viewLifecycleOwner) { resource ->
            if (resource is SportsResource.Success) {
                binding?.sportsMatchdayInfo?.text = resource.data.name
                binding?.sportsMatchdayInfo?.isVisible = true
            } else {
                binding?.sportsMatchdayInfo?.isVisible = false
            }
        }

        viewModel.displayMatches.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is SportsResource.Success -> {
                    matchAdapter.submitList(resource.data)
                }
                else -> {}
            }
        }

        viewModel.standings.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is SportsResource.Success -> {
                    standingAdapter.submitList(resource.data)
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
