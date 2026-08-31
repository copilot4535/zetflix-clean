package com.lagradost.cloudstream3.ui.livestreams

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.navigate

class LiveStreamFragment : BaseFragment<FragmentHomeBinding>(
    BaseFragment.BindingCreator.Bind(FragmentHomeBinding::bind)
) {
    private val liveStreamViewModel: LiveStreamViewModel by viewModels()

    override fun pickLayout(): Int = R.layout.fragment_home

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padTop = false,
            padBottom = isLandscape(),
            padLeft = false
        )

        binding?.stickyHeader?.let {
            fixSystemBarsPadding(
                it,
                heightResId = R.dimen.home_header_height,
                padBottom = false,
                padLeft = false,
                padRight = false
            )
        }

        binding?.homeMasterRecycler?.let {
            fixSystemBarsPadding(
                it,
                padTop = false,
                padBottom = false,
                padLeft = false,
                padRight = false
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentHomeBinding) {
        context?.let { HomeChildItemAdapter.updatePosterSize(it) }
        
        val adapter = LiveParentItemAdapterPreview(liveStreamViewModel)
        
        binding.apply {
            homeMasterRecycler.adapter = adapter
            homeMasterRecycler.setRecycledViewPool(ParentItemAdapter.sharedPool)
            
            // Solid header for Livestream since there is no hero banner
            val color = requireContext().colorFromAttribute(R.attr.primaryBlackBackground)
            stickyHeader.setBackgroundColor(color)
            homeHeaderScrim.isVisible = false
            stickyHeader.elevation = 4f
            
            // Show Livestream title and hide logo/avatar
            homeStickyLogo.isVisible = false
            homeAvatar.isVisible = false
            homeStickyTitle.isVisible = true
            homeStickyTitle.text = getString(R.string.livestreams)
            
            // Adjust shimmer for livestream (no banner or center line)
            homeLoadingShimmerBanner.isVisible = false
            binding.root.findViewById<View>(R.id.home_loading_shimmer_line)?.isVisible = false
            
            homeMasterRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val offset = recyclerView.computeVerticalScrollOffset()
                    stickyHeader.elevation = if (offset > 0) 4f else 0f
                }
            })

            homeReloadConnectionerror.setOnClickListener {
                liveStreamViewModel.load(true)
            }
            
            // Start shimmer immediately if we're going to load
            homeLoadingShimmer.startShimmer()
            homeLoading.isVisible = true
            homeLoadingError.isVisible = false
            homeMasterRecycler.isVisible = false
        }
        
        observe(liveStreamViewModel.page) { data ->
            binding.apply {
                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        adapter.submitList(d.values.map {
                            it.copy(
                                list = it.list.copy(list = it.list.list.toMutableList())
                            )
                        })

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isVisible = true
                        homeLoadingShimmer.stopShimmer()
                    }
                    is Resource.Loading -> {
                        homeLoadingShimmer.startShimmer()
                        homeLoading.isVisible = true
                        homeMasterRecycler.isVisible = false
                        homeLoadingError.isVisible = false
                    }
                    is Resource.Failure -> {
                        homeLoadingShimmer.stopShimmer()
                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = true
                        resultErrorText.text = data.errorString
                    }
                }
            }
        }
        
        binding.homeSearchIcon.setOnClickListener {
            activity?.navigate(R.id.navigation_search)
        }
        
        binding.homeAvatar.setOnClickListener {
            activity?.navigate(R.id.navigation_account)
        }

        liveStreamViewModel.load(false)
    }
}
