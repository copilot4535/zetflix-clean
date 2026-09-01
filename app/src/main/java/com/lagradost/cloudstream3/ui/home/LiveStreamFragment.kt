package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentLivestreamBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard

class LiveStreamFragment : BaseHomeFragment<FragmentLivestreamBinding>(
    R.layout.fragment_livestream,
    FragmentLivestreamBinding::bind
) {
    override val viewModel: LiveStreamViewModel by viewModels()

    override val masterRecycler: RecyclerView
        get() = binding?.homeMasterRecycler ?: throw Exception("Binding is null")
    override val loadingView: View
        get() = binding?.homeLoading ?: throw Exception("Binding is null")
    override val errorView: View
        get() = binding?.homeLoadingError ?: throw Exception("Binding is null")
    override val loadingShimmer: com.facebook.shimmer.ShimmerFrameLayout?
        get() = binding?.homeLoadingShimmer
    override val errorText: android.widget.TextView?
        get() = binding?.resultErrorText
    override val reloadButton: View?
        get() = binding?.homeReloadConnectionerror

    override val pageLiveData: androidx.lifecycle.LiveData<Resource<Map<String, BaseHomeViewModel.ExpandableHomepageList>>>
        get() = viewModel.filteredPage

    override fun setupRecyclerView() {
        val adapter = ParentItemAdapter(
            id = "LiveStreamFragment".hashCode(),
            clickCallback = {
                viewModel.click(it)
            },
            moreInfoClickCallback = {
                viewModel.popup(it)
            },
            expandCallback = {
                viewModel.expand(it)
            }
        )
        masterRecycler.adapter = adapter
    }

    override fun onDestroyView() {
        (activity as? ComponentActivity)?.detachBackPressedCallback("LiveStreamFragment_BackPress")
        super.onDestroyView()
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentLivestreamBinding) {
        super.onBindingCreated(binding)
        (activity as? ComponentActivity)?.attachBackPressedCallback("LiveStreamFragment_BackPress") {
            if (binding.livestreamSearchBar.isVisible) {
                closeSearch(binding)
            } else {
                runDefault()
            }
        }

        binding.livestreamSearch.setOnClickListener {
            binding.stickyHeader.isGone = true
            binding.livestreamSearchBar.isVisible = true
            binding.livestreamSearchView.requestFocus()
        }

        binding.livestreamSearchBack.setOnClickListener {
            closeSearch(binding)
        }

        binding.livestreamSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.search(query)
                hideKeyboard()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText)
                return true
            }
        })
    }

    private fun closeSearch(binding: FragmentLivestreamBinding) {
        binding.livestreamSearchView.setQuery("", false)
        viewModel.search(null)
        binding.livestreamSearchBar.isGone = true
        binding.stickyHeader.isVisible = true
        hideKeyboard()
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = false
        )
    }
}
