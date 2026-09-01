package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList

abstract class BaseHomeFragment<T : ViewBinding>(
    @androidx.annotation.LayoutRes private val layoutId: Int,
    bind: (View) -> T
) : BaseFragment<T>(BindingCreator.Bind(bind)) {

    override fun pickLayout() = layoutId

    abstract val viewModel: BaseHomeViewModel
    abstract val masterRecycler: RecyclerView
    abstract val loadingView: View
    abstract val errorView: View
    abstract val loadingShimmer: com.facebook.shimmer.ShimmerFrameLayout?
    abstract val errorText: android.widget.TextView?
    abstract val reloadButton: View?

    open val pageLiveData: androidx.lifecycle.LiveData<Resource<Map<String, BaseHomeViewModel.ExpandableHomepageList>>>
        get() = viewModel.page

    override fun fixLayout(view: View) {
        // Default implementation, can be overridden
    }

    override fun onBindingCreated(binding: T) {
        context?.let { HomeChildItemAdapter.updatePosterSize(it) }
        
        setupRecyclerView()
        observeViewModel()
    }

    protected open fun setupRecyclerView() {
        val adapter = HomeParentItemAdapterPreview(viewModel)
        masterRecycler.adapter = adapter
    }

    protected open fun observeViewModel() {
        observe(pageLiveData) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    (masterRecycler.adapter as? ParentItemAdapter)?.submitList(d.values.map {
                        it.copy(list = it.list.copy(list = it.list.list.toMutableList()))
                    })

                    loadingView.isVisible = false
                    errorView.isVisible = false
                    masterRecycler.isVisible = true
                    loadingShimmer?.stopShimmer()
                }

                is Resource.Failure -> {
                    loadingShimmer?.stopShimmer()
                    loadingView.isVisible = false
                    errorView.isVisible = true
                    masterRecycler.isInvisible = true

                    val hasNoNetworkConnection = context?.isNetworkAvailable() == false
                    errorText?.text = if (hasNoNetworkConnection) {
                        getString(R.string.no_internet_connection)
                    } else {
                        data.errorString
                    }

                    (masterRecycler.adapter as? ParentItemAdapter)?.apply {
                        submitList(null)
                        clearState()
                    }
                }

                is Resource.Loading -> {
                    loadingShimmer?.startShimmer()
                    loadingView.isVisible = true
                    errorView.isVisible = false
                    masterRecycler.isInvisible = true
                    (masterRecycler.adapter as? ParentItemAdapter)?.apply {
                        submitList(null)
                        clearState()
                    }
                }
            }
        }

        observeNullable(viewModel.popup) { item ->
            if (item == null) return@observeNullable
            val (items, delete) = item
            activity?.loadHomepageList(items, expandCallback = {
                viewModel.expandAndReturn(it)
            }, dismissCallback = {
                viewModel.popup(null)
            }, deleteCallback = delete)
        }
        
        viewModel.reloadStored()
        viewModel.loadAndCancel(DataStoreHelper.currentHomePage, false)
    }
}
