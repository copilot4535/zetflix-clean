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
            
            // Initial state for header
            stickyHeader.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            homeHeaderScrim.alpha = 1f
            stickyHeader.elevation = 0f
            
            homeMasterRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val offset = recyclerView.computeVerticalScrollOffset()
                    val alpha = (offset / 200f).coerceIn(0f, 1f)
                    val color = requireContext().colorFromAttribute(R.attr.primaryBlackBackground)
                    stickyHeader.setBackgroundColor(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(color, (alpha * 255).toInt())
                    )
                    homeHeaderScrim.alpha = 1f - alpha
                }
            })
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
                    }
                    is Resource.Loading -> {
                        homeLoading.isVisible = true
                        homeMasterRecycler.isVisible = false
                    }
                    is Resource.Failure -> {
                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = true
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
