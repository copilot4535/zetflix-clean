package com.lagradost.cloudstream3.ui.sports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentTeamDetailsBinding
import com.lagradost.cloudstream3.databinding.ItemMatchBinding
import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.sports.domain.models.Match
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class TeamDetailsFragment : Fragment() {
    private var binding: FragmentTeamDetailsBinding? = null
    private lateinit var viewModel: TeamDetailsViewModel

    companion object {
        const val ARG_TEAM_ID = "team_id"
        const val ARG_TEAM_NAME = "team_name"
        const val ARG_TEAM_LOGO = "team_logo"
        const val ARG_LEAGUE_SHORTCUT = "league_shortcut"
        const val ARG_LEAGUE_NAME = "league_name"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentTeamDetailsBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[TeamDetailsViewModel::class.java]

        val teamId = arguments?.getString(ARG_TEAM_ID) ?: return
        val teamName = arguments?.getString(ARG_TEAM_NAME)
        val teamLogo = arguments?.getString(ARG_TEAM_LOGO)
        val leagueShortcut = arguments?.getString(ARG_LEAGUE_SHORTCUT) ?: return
        val leagueName = arguments?.getString(ARG_LEAGUE_NAME)

        binding?.apply {
            teamDetailsToolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
            teamDetailsName.text = teamName
            teamDetailsLogo.loadImage(teamLogo)
            teamDetailsLeague.text = leagueName ?: leagueShortcut
        }

        observeViewModel()
        viewModel.loadTeamData(leagueShortcut, teamId)
    }

    private fun observeViewModel() {
        viewModel.recentMatch.observe(viewLifecycleOwner) { resource ->
            val matchBinding = ItemMatchBinding.bind(binding!!.teamDetailsRecentMatch.root)
            handleMatchResource(resource, matchBinding)
        }
        viewModel.nextMatch.observe(viewLifecycleOwner) { resource ->
            val matchBinding = ItemMatchBinding.bind(binding!!.teamDetailsNextMatch.root)
            handleMatchResource(resource, matchBinding)
        }
    }

    private fun handleMatchResource(resource: SportsResource<Match>, matchBinding: ItemMatchBinding) {
        when (resource) {
            is SportsResource.Success -> {
                val match = resource.data
                matchBinding.root.isVisible = true
                matchBinding.apply {
                    matchHomeTeamName.text = match.homeTeam.name
                    matchAwayTeamName.text = match.awayTeam.name
                    matchScore.text = if (match.homeScore != null && match.awayScore != null) {
                        "${match.homeScore} - ${match.awayScore}"
                    } else {
                        "vs"
                    }
                    matchStatus.text = match.status.name
                    root.setOnClickListener {
                        val bundle = Bundle().apply {
                            putString(MatchDetailsFragment.ARG_MATCH_ID, match.id)
                        }
                        findNavController().navigate(R.id.action_global_navigation_match_details, bundle)
                    }
                }
            }
            is SportsResource.Loading -> {
                matchBinding.root.isVisible = false
            }
            is SportsResource.Error -> {
                matchBinding.root.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
