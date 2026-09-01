package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentLivestreamBinding
import com.lagradost.cloudstream3.databinding.LivestreamLoadMoreBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.ViewHolderState
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

    inner class LiveStreamParentAdapter(
        id: Int,
        clickCallback: (com.lagradost.cloudstream3.ui.search.SearchClickCallback) -> Unit,
        moreInfoClickCallback: (BaseHomeViewModel.ExpandableHomepageList) -> Unit,
        expandCallback: ((String) -> Unit)? = null,
    ) : ParentItemAdapter(id, clickCallback, moreInfoClickCallback, expandCallback) {
        override val footers: Int = 1

        override fun onCreateCustomFooter(parent: ViewGroup, viewType: Int): ViewHolderState<Bundle> {
            val inflater = LayoutInflater.from(parent.context)
            val binding = LivestreamLoadMoreBinding.inflate(inflater, parent, false)
            return ViewHolderState<Bundle>(binding)
        }

        override fun onBindFooter(holder: ViewHolderState<Bundle>) {
            val binding = holder.view as? LivestreamLoadMoreBinding ?: return
            val loading = viewModel.loadMoreLoading.value ?: false

            binding.livestreamLoadMoreButton.setOnClickListener {
                viewModel.loadMore()
            }

            binding.livestreamLoadMoreButton.isEnabled = !loading
            binding.livestreamLoadMoreButton.text = if (loading) "" else "Load More"
            binding.livestreamLoadMoreButton.setIconResource(if (loading) 0 else R.drawable.ic_baseline_autorenew_24)
            binding.livestreamLoadMoreProgress.isVisible = loading
        }
    }

    override fun setupRecyclerView() {
        val adapter = LiveStreamParentAdapter(
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

    override fun observeViewModel() {
        super.observeViewModel()
        val searchExitIcon =
            binding?.livestreamSearchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)

        observe(viewModel.searchLoading) { loading ->
            binding?.livestreamSearchLoading?.alpha = if (loading) 1f else 0f
            searchExitIcon?.alpha = if (loading) 0f else 1f
        }

        observe(viewModel.loadMoreLoading) {
            val adapter = masterRecycler.adapter
            adapter?.notifyItemChanged(adapter.itemCount - 1)
        }
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
