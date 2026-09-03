package com.lagradost.cloudstream3.ui.library

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
import android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.allViews
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.syncproviders.ListSorting
import com.lagradost.cloudstream3.databinding.FragmentLibraryBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.loadResult
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppContextUtils.reduceDragSensitivity
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

import com.lagradost.cloudstream3.ui.music.MusicActivity
import android.content.Intent

class LibraryFragment : BaseFragment<FragmentLibraryBinding>(
    BindingCreator.Bind(FragmentLibraryBinding::bind)
) {
    companion object {
        fun newInstance() = LibraryFragment()

        const val VIEWPAGER_ITEM_KEY = "viewpager_item"
    }

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private var toggleRandomButton = false

    override fun pickLayout(): Int = R.layout.fragment_library

    override fun onSaveInstanceState(outState: Bundle) {
        binding?.viewpager?.currentItem?.let { currentItem ->
            outState.putInt(VIEWPAGER_ITEM_KEY, currentItem)
        }
        super.onSaveInstanceState(outState)
    }

    private fun updateRandomVisibility(binding: FragmentLibraryBinding) {
        if (!toggleRandomButton) {
            binding.libraryRandom.isGone = true
            return
        }
        val position = libraryViewModel.currentPage.value ?: 0
        val pages = (libraryViewModel.pages.value as? Resource.Success)?.value ?: return
        val hasItems = pages[position].items.isNotEmpty()

        binding.libraryRandom.isVisible = hasItems
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = isLayout(PHONE)
        )
    }

    @SuppressLint("ResourceType", "CutPasteId")
    override fun onBindingCreated(
        binding: FragmentLibraryBinding,
        savedInstanceState: Bundle?
    ) {
        binding.sortFab.setOnClickListener(sortChangeClickListener)
        binding.librarySort.setOnClickListener(sortChangeClickListener)

        binding.libraryMusicShortcut.setOnClickListener {
            val intent = Intent(it.context, MusicActivity::class.java).apply {
                putExtra(MusicActivity.EXTRA_OPEN_TAB, "library")
            }
            it.context.startActivity(intent)
        }

        libraryViewModel.reloadPages(false)

        binding.listSelector.setOnClickListener {
            val items = libraryViewModel.availableApiNames
            val currentItem = libraryViewModel.currentApiName.value

            activity?.showBottomDialog(
                items,
                items.indexOf(currentItem),
                txt(R.string.select_library).asString(it.context),
                false,
                {}) { index ->
                val selectedItem = items.getOrNull(index) ?: return@showBottomDialog
                libraryViewModel.switchList(selectedItem)
            }
        }

        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton =
                settingsManager.getBoolean(
                    getString(R.string.random_button_key),
                    false
                )
            binding.libraryRandom.visibility = View.GONE
        }

        binding.viewpager.setPageTransformer(LibraryScrollTransformer())

        binding.viewpager.adapter = ViewpagerAdapter(
            { isScrollingDown: Boolean ->
                if (isScrollingDown) {
                    binding.sortFab.shrink()
                    binding.libraryRandom.shrink()
                } else {
                    binding.sortFab.extend()
                    binding.libraryRandom.extend()
                }
            }) callback@{ searchClickCallback ->
            debugAssert({
                searchClickCallback.card !is SyncAPI.LibraryItem
            }, {
                "searchClickCallback ${searchClickCallback.card} is not a LibraryItem"
            })

            when (searchClickCallback.action) {
                SEARCH_ACTION_SHOW_METADATA -> {
                    (activity as? MainActivity)?.loadPopup(
                        searchClickCallback.card,
                        load = false
                    )
                }

                SEARCH_ACTION_LOAD -> {
                    loadLibraryItem(searchClickCallback.card)
                }
            }
        }

        binding.apply {
            viewpager.offscreenPageLimit = 2
            viewpager.reduceDragSensitivity()
            searchBar.setExpanded(true)
        }

        val startLoading = Runnable {
            binding.apply {
                gridview.numColumns = root.context.getSpanCount()
                gridview.adapter =
                    context?.let { LoadingPosterAdapter(it, 6 * 3) }
                libraryLoadingOverlay.isVisible = true
                libraryLoadingShimmer.startShimmer()
                emptyListTextview.isVisible = false
            }
        }

        val stopLoading = Runnable {
            binding.apply {
                gridview.adapter = null
                libraryLoadingOverlay.isVisible = false
                libraryLoadingShimmer.stopShimmer()
            }
        }

        val handler = Handler(Looper.getMainLooper())

        observe(libraryViewModel.pages) { resource ->
            when (resource) {
                is Resource.Success -> {
                    handler.removeCallbacks(startLoading)
                    val pages = resource.value
                    val showNotice = pages.all { it.items.isEmpty() }

                    binding.apply {
                        emptyListTextview.isVisible = showNotice
                        if (showNotice) {
                            if (libraryViewModel.availableApiNames.size > 1) {
                                emptyListTextview.setText(R.string.empty_library_logged_in_message)
                            } else {
                                emptyListTextview.setText(R.string.empty_library_no_accounts_message)
                            }
                        }

                        (viewpager.adapter as? ViewpagerAdapter)?.submitList(pages.map {
                            it.copy(
                                items = CopyOnWriteArrayList(it.items)
                            )
                        })

                        libraryViewModel.currentPage.value?.let { page ->
                            binding.viewpager.setCurrentItem(page, false)
                            binding.searchBar.setExpanded(true)
                        }

                        if (toggleRandomButton) {
                            val randomClickListener = View.OnClickListener {
                                val position = libraryViewModel.currentPage.value ?: 0
                                pages[position].items.randomOrNull()?.let { item ->
                                    loadLibraryItem(item)
                                }
                            }
                            libraryRandom.setOnClickListener(randomClickListener)
                        }
                        updateRandomVisibility(binding)

                        handler.postDelayed(stopLoading, 300)

                        savedInstanceState?.getInt(VIEWPAGER_ITEM_KEY)?.let { currentPos ->
                            if (currentPos < 0) return@let
                            viewpager.setCurrentItem(currentPos, false)
                            savedInstanceState.putInt(VIEWPAGER_ITEM_KEY, -1)
                        }

                        fun hideViewpager(distance: Int) {
                            if (distance < 3) return

                            val hideAnimation = AlphaAnimation(1f, 0f).apply {
                                duration = distance * 50L
                                fillAfter = true
                            }
                            val showAnimation = AlphaAnimation(0f, 1f).apply {
                                duration = distance * 50L
                                startOffset = distance * 100L
                                fillAfter = true
                            }
                            viewpager.startAnimation(hideAnimation)
                            viewpager.startAnimation(showAnimation)
                        }

                        TabLayoutMediator(
                            libraryTabLayout,
                            viewpager,
                        ) { tab, position ->
                            tab.text = pages.getOrNull(position)?.title?.asStringNull(context)

                            tab.view.setOnClickListener {
                                val currentItem = binding.viewpager.currentItem
                                val distance = abs(position - currentItem)
                                hideViewpager(distance)
                            }
                            tab.view.setOnFocusChangeListener { _, _ ->
                                binding.searchBar.setExpanded(true)
                            }
                        }.attach()

                        binding.libraryTabLayout.addOnTabSelectedListener(object :
                            TabLayout.OnTabSelectedListener {
                            override fun onTabSelected(tab: TabLayout.Tab?) {
                                binding.libraryTabLayout.selectedTabPosition.let { page ->
                                    libraryViewModel.switchPage(page)
                                }
                            }

                            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
                            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
                        })
                    }
                }

                is Resource.Loading -> {
                    handler.postDelayed(startLoading, 200)
                }

                is Resource.Failure -> {
                    stopLoading.run()
                }
            }
        }

        observe(libraryViewModel.currentPage) { position ->
            updateRandomVisibility(binding)
            val all = binding.viewpager.allViews.toList()
                .filterIsInstance<AutofitRecyclerView>()

            all.forEach { view ->
                view.isVisible = view.tag == position
                view.isFocusable = view.tag == position

                if (view.tag == position)
                    view.descendantFocusability = FOCUS_AFTER_DESCENDANTS
                else
                    view.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            }
        }
    }

    private fun loadLibraryItem(
        card: SearchResponse
    ) {
        if (APIHolder.getApiFromNameNull(card.apiName) != null) {
            activity?.loadSearchResult(card)
        } else {
            QuickSearchFragment.pushSearch(activity, card.name)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val adapter = binding?.viewpager?.adapter ?: return
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    private val sortChangeClickListener = View.OnClickListener { view ->
        val methods = libraryViewModel.sortingMethods.map {
            txt(it.stringRes).asString(view.context)
        }

        activity?.showBottomDialog(
            methods,
            libraryViewModel.sortingMethods.indexOf(libraryViewModel.currentSortingMethod),
            txt(R.string.sort_by).asString(view.context),
            false,
            {},
            {
                val method = libraryViewModel.sortingMethods[it]
                libraryViewModel.sort(method)
            })
    }
}
