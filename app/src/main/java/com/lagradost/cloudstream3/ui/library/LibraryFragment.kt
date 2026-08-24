package com.lagradost.cloudstream3.ui.library

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.core.view.allViews
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.material.tabs.TabLayout
import com.lagradost.cloudstream3.APIHolder
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


class LibraryFragment : BaseFragment<FragmentLibraryBinding>(
    BaseFragment.BindingCreator.Bind(FragmentLibraryBinding::bind)
) {
    companion object {
        fun newInstance() = LibraryFragment()

        /**
         * Store which page was last seen when exiting the fragment and returning
         **/
        const val VIEWPAGER_ITEM_KEY = "viewpager_item"
    }

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private var toggleRandomButton = false

    override fun pickLayout(): Int? =
        if (isLayout(PHONE)) R.layout.fragment_library else R.layout.fragment_library_tv

    override fun onSaveInstanceState(outState: Bundle) {
        /*
        binding?.viewpager?.currentItem?.let { currentItem ->
            outState.putInt(VIEWPAGER_ITEM_KEY, currentItem)
        }
        */
        super.onSaveInstanceState(outState)
    }

    private fun updateRandomVisibility(binding: FragmentLibraryBinding) {
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = !isLayout(PHONE)
        )
    }

    @SuppressLint("ResourceType", "CutPasteId")
    override fun onBindingCreated(
        binding: FragmentLibraryBinding,
        savedInstanceState: Bundle?
    ) {
        // binding.sortFab.setOnClickListener(sortChangeClickListener)
        // binding.librarySort.setOnClickListener(sortChangeClickListener)

        binding.libraryRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
            ?.apply {
                tag = "tv_no_focus_tag"
                // Expand the Appbar when search bar is focused, fixing scroll up issue
                setOnFocusChangeListener { _, _ ->
                    binding.searchBar.setExpanded(true)
                }
            }

        /*val searchCallback = Runnable {
            val newText = binding.mainSearch.query.toString()
            libraryViewModel.sort(ListSorting.Query, newText)
        }*/

        binding.mainSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // libraryViewModel.sort(ListSorting.Query, query)
                return true
            }

            // This is required to prevent the first text change
            // When this is attached it'll immediately send a onQueryTextChange("")
            // Which we do not want
            // var hasInitialized = false
            override fun onQueryTextChange(newText: String?): Boolean {
                /*if (!hasInitialized) {
                    hasInitialized = true
                    return true
                }

                binding.mainSearch.removeCallbacks(searchCallback)

                // Delay the execution of the search operation by 1 second (adjust as needed)
                // this prevents running search when the user is typing
                binding.mainSearch.postDelayed(searchCallback, 1000)*/

                return true
            }
        })

        libraryViewModel.reloadPages(false)

        /*binding.listSelector.setOnClickListener {
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
        }*/

        //Load value for toggling Random button. Hide at startup
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton =
                settingsManager.getBoolean(
                    getString(R.string.random_button_key),
                    false
                )
            binding.libraryRandom.visibility = View.GONE
            binding.libraryRandomButtonTv.visibility = View.GONE
        }

        /*
        /**
         * Shows a plugin selection dialogue and saves the response
         **/
        fun Activity.showPluginSelectionDialog(
            key: String,
            syncId: SyncIdName,
            apiName: String? = null,
        ) {
            val availableProviders = allProviders.filter {
                it.supportedSyncNames.contains(syncId)
            }.map { it.name } +
                // Add the api if it exists
                (APIHolder.getApiFromNameNull(apiName)?.let { listOf(it.name) }
                    ?: emptyList())

            val baseOptions = listOf(
                LibraryOpenerType.Default,
                LibraryOpenerType.None,
                LibraryOpenerType.Browser,
                LibraryOpenerType.Search
            )

            val items = baseOptions.map { txt(it.stringRes).asString(this) } + availableProviders

            val savedSelection = getKey<LibraryOpener>("$currentAccount/$LIBRARY_FOLDER", key)
            val selectedIndex =
                when {
                    savedSelection == null -> 0
                    // If provider
                    savedSelection.openType == LibraryOpenerType.Provider
                            && savedSelection.providerData?.apiName != null -> {
                        availableProviders.indexOf(savedSelection.providerData.apiName)
                            .takeIf { it != -1 }
                            ?.plus(baseOptions.size) ?: 0
                    }
                    // Else base option
                    else -> baseOptions.indexOf(savedSelection.openType)
                }

            this.showBottomDialog(
                items,
                selectedIndex,
                txt(R.string.open_with).asString(this),
                false,
                {},
            ) {
                val savedData = if (it < baseOptions.size) {
                    LibraryOpener(
                        baseOptions[it],
                        null
                    )
                } else {
                    LibraryOpener(
                        LibraryOpenerType.Provider,
                        ProviderLibraryData(items[it])
                    )
                }

                setKey(
                    "$currentAccount/$LIBRARY_FOLDER",
                    key,
                    savedData,
                )
            }
        }
        */

        /*binding.providerSelector.setOnClickListener {
            val syncName = libraryViewModel.currentSyncApi?.syncIdName ?: return@setOnClickListener
            activity?.showPluginSelectionDialog(syncName.name, syncName)
        }*/

        val libraryAdapter = LibrarySectionAdapter { searchClickCallback ->
            // To prevent future accidents
            debugAssert({
                searchClickCallback.card !is SyncAPI.LibraryItem
            }, {
                "searchClickCallback ${searchClickCallback.card} is not a LibraryItem"
            })

            val syncId = (searchClickCallback.card as SyncAPI.LibraryItem).syncId
            val syncName =
                libraryViewModel.currentSyncApi?.syncIdName ?: return@LibrarySectionAdapter

            when (searchClickCallback.action) {
                SEARCH_ACTION_SHOW_METADATA -> {
                    (activity as? MainActivity)?.loadPopup(
                        searchClickCallback.card,
                        load = false
                    )
                }

                SEARCH_ACTION_LOAD -> {
                    loadLibraryItem(syncName, syncId, searchClickCallback.card)
                }
            }
        }
        binding.librarySectionsRecycler.adapter = libraryAdapter


        binding.apply {
            // viewpager.offscreenPageLimit = 2
            // viewpager.reduceDragSensitivity()
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

                        libraryAdapter.submitList(pages)

                        // Set up random button click listener
                        if (toggleRandomButton) {
                            val randomClickListener = View.OnClickListener {
                                val syncIdName = libraryViewModel.currentSyncApi?.syncIdName ?: return@OnClickListener
                                pages.flatMap { it.items }.randomOrNull()?.let { item ->
                                    loadLibraryItem(syncIdName, item.syncId, item)
                                }
                            }
                            libraryRandom.setOnClickListener(randomClickListener)
                            libraryRandomButtonTv.setOnClickListener(randomClickListener)
                        }
                        updateRandomVisibility(binding)

                        handler.postDelayed(stopLoading, 300)
                    }
                }

                is Resource.Loading -> {
                    // Only start loading after 200ms to prevent loading cached lists
                    handler.postDelayed(startLoading, 200)
                }

                is Resource.Failure -> {
                    stopLoading.run()
                }
            }
        }

    }

    private fun loadLibraryItem(
        _syncName: SyncIdName,
        _syncId: String,
        card: SearchResponse
    ) {
        if (APIHolder.getApiFromNameNull(card.apiName) != null) {
            activity?.loadSearchResult(
                card
            )
        } else {
            // Search when no provider can open
            QuickSearchFragment.pushSearch(
                activity,
                card.name
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val adapter = binding?.librarySectionsRecycler?.adapter ?: return
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

}

class MenuSearchView(context: Context) : SearchView(context)
