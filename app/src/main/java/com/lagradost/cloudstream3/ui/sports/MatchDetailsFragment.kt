package com.lagradost.cloudstream3.ui.sports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.databinding.FragmentMatchDetailsBinding
import com.lagradost.cloudstream3.sports.common.util.SportsResource
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class MatchDetailsFragment : Fragment() {
    private var binding: FragmentMatchDetailsBinding? = null
    private lateinit var viewModel: MatchDetailsViewModel
    private val goalAdapter = GoalAdapter()

    companion object {
        const val ARG_MATCH_ID = "match_id"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentMatchDetailsBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[MatchDetailsViewModel::class.java]

        val matchId = arguments?.getString(ARG_MATCH_ID) ?: return

        binding?.apply {
            matchDetailsToolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
            matchDetailsTimeline.adapter = goalAdapter
        }

        observeViewModel()
        viewModel.loadMatchDetails(matchId)
    }

    private fun observeViewModel() {
        viewModel.match.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is SportsResource.Success -> {
                    val match = resource.data
                    binding?.apply {
                        matchDetailsHomeName.text = match.homeTeam.name
                        matchDetailsAwayName.text = match.awayTeam.name
                        matchDetailsHomeLogo.loadImage(match.homeTeam.logoUrl)
                        matchDetailsAwayLogo.loadImage(match.awayTeam.logoUrl)
                        matchDetailsScore.text = if (match.homeScore != null && match.awayScore != null) {
                            "${match.homeScore} - ${match.awayScore}"
                        } else {
                            "vs"
                        }
                        matchDetailsStatus.text = match.status.name
                        matchDetailsLeague.text = match.leagueName ?: match.leagueId
                        
                        goalAdapter.submitList(match.goals)
                        matchDetailsNoGoals.isVisible = match.goals.isEmpty()
                    }
                }
                is SportsResource.Loading -> {
                    // Show loading
                }
                is SportsResource.Error -> {
                    // Show error
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
