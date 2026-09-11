package com.lagradost.cloudstream3.ui.sports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.lagradost.cloudstream3.databinding.FragmentSportsHomeBinding
import com.lagradost.cloudstream3.sports.common.util.SportsResource

class SportsHomeFragment : Fragment() {
    private var binding: FragmentSportsHomeBinding? = null
    private lateinit var viewModel: SportsViewModel
    private val adapter = MatchAdapter()

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
        
        binding?.sportsMatchesRecycler?.adapter = adapter

        setupLeagueSelection()
        setupFilterTabs()
        observeMatches()

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
                    else -> FilterMode.LIVE
                }
                viewModel.setFilterMode(mode)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeMatches() {
        viewModel.displayMatches.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is SportsResource.Success -> {
                    adapter.submitList(resource.data)
                    // Show empty state if list is empty
                }
                is SportsResource.Loading -> {
                    // Show loading if needed
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
