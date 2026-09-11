package com.lagradost.cloudstream3.ui.sports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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

        viewModel.liveMatches.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is SportsResource.Success -> {
                    adapter.submitList(resource.data)
                }
                is SportsResource.Loading -> {
                    // Show loading if needed
                }
                is SportsResource.Error -> {
                    // Show error
                }
            }
        }

        viewModel.loadLiveMatches()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
