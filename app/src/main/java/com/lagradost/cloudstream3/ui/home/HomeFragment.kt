package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.databinding.HomeEpisodesExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeSelectMainpageBinding
import com.lagradost.cloudstream3.databinding.TvtypesChipsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.AppContextUtils.isRecyclerScrollable
import com.lagradost.cloudstream3.utils.AppContextUtils.ownHide
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.EmptyEvent
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import kotlin.math.absoluteValue

class HomeFragment : BaseFragment<FragmentHomeBinding>(
    BindingCreator.Bind(FragmentHomeBinding::bind)
) {
    companion object {
        // Used for configuration changed events to fix any popups that are not attached to a fragment
        val configEvent = EmptyEvent()
        var currentSpan = 1

        private val errorProfilePics = listOf(
            R.drawable.monke_benene,
            R.drawable.monke_burrito,
            R.drawable.monke_coco,
            R.drawable.monke_cookie,
            R.drawable.monke_flusdered,
            R.drawable.monke_funny,
            R.drawable.monke_like,
            R.drawable.monke_party,
            R.drawable.monke_sob,
            R.drawable.monke_drink,
        )

        val errorProfilePic = errorProfilePics.random()

        fun Activity.loadHomepageList(
            expand: HomeViewModel.ExpandableHomepageList,
            deleteCallback: (() -> Unit)? = null,
            expandCallback: (suspend (String) -> HomeViewModel.ExpandableHomepageList?)? = null,
            dismissCallback: (() -> Unit),
        ): BottomSheetDialog {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)
            val binding: HomeEpisodesExpandedBinding = HomeEpisodesExpandedBinding.inflate(
                bottomSheetDialogBuilder.layoutInflater,
                null,
                false
            )
            bottomSheetDialogBuilder.setContentView(binding.root)

            val item = expand.list
            binding.homeExpandedText.text = item.name
            binding.homeExpandedDelete.isGone = deleteCallback == null
            if (deleteCallback != null) {
                binding.homeExpandedDelete.setOnClickListener {
                    try {
                        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                        val dialogClickListener =
                            DialogInterface.OnClickListener { _, which ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE -> {
                                        deleteCallback.invoke()
                                        bottomSheetDialogBuilder.dismissSafe(this)
                                    }

                                    DialogInterface.BUTTON_NEGATIVE -> {}
                                }
                            }

                        builder.setTitle(R.string.clear_history)
                            .setMessage(
                                context.getString(R.string.delete_message).format(
                                    item.name
                                )
                            )
                            .setPositiveButton(R.string.delete, dialogClickListener)
                            .setNegativeButton(R.string.cancel, dialogClickListener)
                            .show().setDefaultFocus()
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
            binding.homeExpandedDragDown.setOnClickListener {
                bottomSheetDialogBuilder.dismissSafe(this)
            }


            // Span settings
            binding.homeExpandedRecycler.spanCount = context.getSpanCount(item.isHorizontalImages)
            binding.homeExpandedRecycler.setRecycledViewPool(SearchAdapter.sharedPool)
            binding.homeExpandedRecycler.adapter =
                SearchAdapter(binding.homeExpandedRecycler,item.isHorizontalImages) { callback ->
                    handleSearchClickCallback(callback)
                    if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                        bottomSheetDialogBuilder.ownHide() // we hide here because we want to resume it later
                    }
                }.apply {
                    submitList(item.list)
                    hasNext = expand.hasNext
                }

            binding.homeExpandedRecycler.addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is SearchAdapter) return

                    val count = adapter.itemCount
                    val currentHasNext = adapter.hasNext
                    if (!recyclerView.isRecyclerScrollable() && currentHasNext && expandCount != count) {
                        expandCount = count
                        ioSafe {
                            expandCallback?.invoke(name)?.let { newExpand ->
                                (recyclerView.adapter as? SearchAdapter?)?.apply {
                                    hasNext = newExpand.hasNext
                                    submitList(newExpand.list.list)
                                }
                            }
                        }
                    }
                }
            })

            val spanListener = Runnable {
                binding.homeExpandedRecycler.spanCount = context.getSpanCount(item.isHorizontalImages)
                @SuppressLint("NotifyDataSetChanged")
                binding.homeExpandedRecycler.adapter?.notifyDataSetChanged()
            }

            configEvent += spanListener

            bottomSheetDialogBuilder.setOnDismissListener {
                dismissCallback.invoke()
                configEvent -= spanListener
            }

            bottomSheetDialogBuilder.show()
            return bottomSheetDialogBuilder
        }

        private fun getPairList(
            anime: Chip?,
            cartoons: Chip?,
            tvs: Chip?,
            docs: Chip?,
            movies: Chip?,
            asian: Chip?,
            livestream: Chip?,
            torrent: Chip?,
            others: Chip?,
        ): List<Pair<Chip?, List<TvType>>> {
            return listOf(
                Pair(movies, listOf(TvType.Movie)),
                Pair(tvs, listOf(TvType.TvSeries)),
                Pair(anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(asian, listOf(TvType.AsianDrama)),
                Pair(cartoons, listOf(TvType.Cartoon)),
                Pair(docs, listOf(TvType.Documentary)),
                Pair(livestream, listOf(TvType.Live)),
                Pair(torrent, listOf(TvType.Torrent)),
                Pair(others, listOf(TvType.Others)),
            )
        }

        private fun getPairList(header: TvtypesChipsBinding) = getPairList(
            header.homeSelectAnime,
            header.homeSelectCartoons,
            header.homeSelectTvSeries,
            header.homeSelectDocumentaries,
            header.homeSelectMovies,
            header.homeSelectAsian,
            header.homeSelectLivestreams,
            header.homeSelectTorrents,
            header.homeSelectOthers
        )

        fun validateChips(header: TvtypesChipsBinding?, validTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
            }
        }

        fun updateChips(header: TvtypesChipsBinding?, selectedTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                button?.isChecked =
                    button.isVisible && selectedTypes.any { types.contains(it) }
            }
        }

        fun bindChips(
            header: TvtypesChipsBinding?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit
        ) {
            bindChips(header, selectedTypes, validTypes, callback, null, null)
        }

        fun bindChips(
            header: TvtypesChipsBinding?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit,
            nextFocusDown: Int?,
            nextFocusUp: Int?
        ) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
                button?.isChecked = isValid && selectedTypes.any { types.contains(it) }
                button?.isFocusable = true

                if (nextFocusDown != null)
                    button?.nextFocusDownId = nextFocusDown

                if (nextFocusUp != null)
                    button?.nextFocusUpId = nextFocusUp

                button?.setOnCheckedChangeListener { _, _ ->
                    val list = ArrayList<TvType>()
                    for ((sbutton, vvalidTypes) in pairList) {
                        if (sbutton?.isChecked == true)
                            list.addAll(vvalidTypes)
                    }
                    callback(list)
                }
            }
        }

        fun Context.selectHomepage(selectedApiName: String?, callback: (String) -> Unit) {
            val validAPIs = filterProviderByPreferredMedia().toMutableList()

            validAPIs.add(0, randomApi)
            validAPIs.add(0, noneApi)
            val builder =
                BottomSheetDialog(this)

            builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            val binding: HomeSelectMainpageBinding = HomeSelectMainpageBinding.inflate(
                builder.layoutInflater,
                null,
                false
            )

            builder.setContentView(binding.root)
            builder.show()
            builder.let { dialog ->
                val isMultiLang = getApiProviderLangSettings().let { set ->
                    set.size > 1 || set.contains(AllLanguagesName)
                }

                var currentApiName = selectedApiName

                var currentValidApis: MutableList<MainAPI> = mutableListOf()
                val preSelectedTypes = DataStoreHelper.homePreference.toMutableList()

                binding.cancelBtt.setOnClickListener {
                    dialog.dismissSafe()
                }

                binding.applyBtt.setOnClickListener {
                    if (currentApiName != selectedApiName) {
                        currentApiName?.let(callback)
                    }
                    dialog.dismissSafe()
                }

                var pinnedphashset = DataStoreHelper.pinnedProviders.toHashSet()

                val listView = dialog.findViewById<ListView>(R.id.listview1)

                val arrayAdapter = object : ArrayAdapter<String>(
                    this, R.layout.sort_bottom_single_provider_choice,
                    mutableListOf()
                ) {
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        val view = convertView ?: LayoutInflater.from(context)
                            .inflate(R.layout.sort_bottom_single_provider_choice, parent, false)
                        val titleText = view.findViewById<TextView>(R.id.text1)
                        val pinIcon = view.findViewById<ImageView>(R.id.pinicon)
                        val settingsIcon = view.findViewById<ImageView>(R.id.action_settings)

                        val name = getItem(position)
                        titleText?.text = name
                        val providerApi = currentValidApis[position]
                        val isPinned =
                            pinnedphashset.contains(providerApi.name)
                        pinIcon.visibility = if (isPinned) View.VISIBLE else View.GONE

                        val pluginInstance = providerApi.sourcePlugin?.let { PluginManager.plugins[it] } as? Plugin
                        val isDownloadedPluginWithSettings = pluginInstance?.openSettings != null

                        settingsIcon.visibility = if (isDownloadedPluginWithSettings) View.VISIBLE else View.GONE
                        if (isDownloadedPluginWithSettings) {
                            settingsIcon.setOnClickListener {
                                try {
                                    val activityContext = it.context.getActivity() ?: it.context
                                    pluginInstance.openSettings?.invoke(activityContext)
                                } catch (e: Throwable) {
                                    logError(e)
                                }
                            }
                        }

                        return view
                    }
                }
                listView?.adapter = arrayAdapter
                listView?.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                listView?.setOnItemClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty()) {
                        currentApiName = currentValidApis[i].name
                        currentApiName.let(callback)
                        dialog.dismissSafe()
                    }
                }

                fun updateList() {
                    DataStoreHelper.homePreference = preSelectedTypes
                    val pinnedp = DataStoreHelper.pinnedProviders.toList()
                    pinnedphashset = pinnedp.toHashSet()
                    arrayAdapter.clear()
                    val sortedApis = validAPIs
                        .filter {
                            val isPinned = pinnedphashset.contains(it.name)

                            it.hasMainPage && (isPinned || it.supportedTypes.any(
                                preSelectedTypes::contains
                            ))
                        }
                        .sortedBy { it.name.lowercase() }

                    val sortedApiMap = LinkedHashMap<String, MainAPI>().apply {
                        sortedApis.forEach { put(it.name, it) }
                    }

                    val pinnedApis = pinnedp.asReversed().mapNotNull { name ->
                        sortedApiMap[name]
                    }

                    val remainingApis = sortedApis.filterNot { pinnedphashset.contains(it.name) }

                    currentValidApis = mutableListOf<MainAPI>().apply {
                        addAll(validAPIs.take(2))
                        addAll(pinnedApis)
                        addAll(remainingApis)
                    }

                    val names =
                        currentValidApis.map { if (isMultiLang) "${getFlagFromIso(it.lang)?.plus(" ") ?: ""}${it.name}" else it.name }
                    val index = currentValidApis.map { it.name }.indexOf(currentApiName)
                    listView?.setItemChecked(index, true)
                    arrayAdapter.addAll(names)
                    arrayAdapter.notifyDataSetChanged()
                }
                listView?.setOnItemLongClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty() && i > 1) {
                        val pinnedp = DataStoreHelper.pinnedProviders.toMutableList()
                        val thisapi = currentValidApis[i].name
                        if (pinnedp.contains(thisapi)) {
                            pinnedp.remove(thisapi)
                        } else {
                            pinnedp.add(thisapi)
                        }
                        DataStoreHelper.pinnedProviders = pinnedp.toTypedArray()
                        updateList()
                    }
                    true
                }

                bindChips(
                    binding.tvtypesChipsScroll.tvtypesChips,
                    preSelectedTypes,
                    validAPIs.flatMap { it.supportedTypes }.distinct()
                ) { list ->
                    preSelectedTypes.clear()
                    preSelectedTypes.addAll(list)
                    updateList()
                }
                updateList()
            }
        }
    }

    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun pickLayout(): Int = R.layout.fragment_home

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        bottomSheetDialog?.ownShow()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroyView() {
        (activity as? ComponentActivity)?.detachBackPressedCallback("HomeFragment_BackPress")
        bottomSheetDialog?.ownHide()
        MainActivity.reloadAccountEvent -= ::reloadAvatarObserver
        super.onDestroyView()
    }


    private var currentApiName: String? = null
    private var toggleRandomButton = false

    private var bottomSheetDialog: BottomSheetDialog? = null
    private var homeMasterAdapter: HomeParentItemAdapterPreview? = null

    private fun reloadAvatarObserver(reload: Boolean) {
        loadAvatar(binding ?: return)
    }

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

        configEvent.invoke()
    }

    @SuppressLint("SetTextI18n")
    override fun onBindingCreated(binding: FragmentHomeBinding) {
        context?.let { HomeChildItemAdapter.updatePosterSize(it) }
        (activity as? ComponentActivity)?.attachBackPressedCallback("HomeFragment_BackPress") {
            runDefault()
        }
        binding.apply {
            homeMasterAdapter = HomeParentItemAdapterPreview(
                homeViewModel
            )
            homeMasterRecycler.setRecycledViewPool(ParentItemAdapter.sharedPool)
            homeMasterRecycler.adapter = homeMasterAdapter

            homeAvatar.setOnClickListener {
                activity.navigate(R.id.navigation_account)
            }

            homeMasterRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val offset = recyclerView.computeVerticalScrollOffset()
                    val alpha = (offset / 200f).coerceIn(0f, 1f)
                    val context = context ?: return
                    
                    // Show background only when scrolled
                    val color = context.colorFromAttribute(R.attr.primaryBlackBackground)
                    val alphaInt = (alpha * 255).toInt() 
                    _binding?.stickyHeader?.setBackgroundColor(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(color, alphaInt)
                    )
                    
                    // Fade out scrim as we scroll to solid color
                    _binding?.homeHeaderScrim?.alpha = 1f - alpha
                    
                    // Dynamic blending could also mean adjusting elevation or visibility
                    _binding?.stickyHeader?.elevation = if (alpha > 0.1f) 4f else 0f
                }
            })
            
            // Initial state: transparent with scrim
            stickyHeader.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            homeHeaderScrim.alpha = 1f
            stickyHeader.elevation = 0f
        }

        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton =
                settingsManager.getBoolean(
                    getString(R.string.random_button_key),
                    false
                )
        }

        observe(homeViewModel.apiName) { apiName ->
            currentApiName = apiName
        }

        observe(homeViewModel.page) { data ->
            binding.apply {
                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.submitList(d.values.map {
                            it.copy(
                                list = it.list.copy(list = it.list.list.toMutableList())
                            )
                        })

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isVisible = true
                        homeLoadingShimmer.stopShimmer()
                    }

                    is Resource.Failure -> {
                        homeLoadingShimmer.stopShimmer()
                        homeReloadConnectionOpenInBrowser.setOnClickListener { view ->
                            val validAPIs = apis

                            view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api ->
                                Pair(
                                    index,
                                    api.name
                                )
                            }) {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW)
                                    i.data = validAPIs[itemId].mainUrl.toUri()
                                    startActivity(i)
                                } catch (e: Exception) {
                                    logError(e)
                                }
                            }
                        }

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = true
                        homeMasterRecycler.isInvisible = true

                        val hasNoNetworkConnection = context?.isNetworkAvailable() == false
                        val isNetworkError = data.isNetworkError

                        homeReloadConnectionGoToDownloads.isVisible =
                            hasNoNetworkConnection || isNetworkError

                        homeReloadConnectionOpenInBrowser.isGone = hasNoNetworkConnection

                        resultErrorText.text = if (hasNoNetworkConnection) {
                            getString(R.string.no_internet_connection)
                        } else {
                            data.errorString
                        }

                        homeReloadConnectionGoToDownloads.setOnClickListener {
                            activity.navigate(R.id.navigation_downloads)
                        }

                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.apply {
                            submitList(null)
                            clearState()
                        }
                    }

                    is Resource.Loading -> {
                        homeLoadingShimmer.startShimmer()
                        homeLoading.isVisible = true
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isInvisible = true
                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.apply {
                            submitList(null)
                            clearState()
                        }
                    }
                }
            }
        }

        observeNullable(homeViewModel.popup) { item ->
            if (item == null) {
                bottomSheetDialog?.dismissSafe()
                bottomSheetDialog = null
                return@observeNullable
            }

            if (bottomSheetDialog != null) {
                return@observeNullable
            }

            val (items, delete) = item

            bottomSheetDialog = activity?.loadHomepageList(items, expandCallback = {
                homeViewModel.expandAndReturn(it)
            }, dismissCallback = {
                homeViewModel.popup(null)
                bottomSheetDialog = null
            }, deleteCallback = delete)
        }

        homeViewModel.reloadStored()
        homeViewModel.loadAndCancel(DataStoreHelper.currentHomePage, false)

        loadAvatar(binding)

        MainActivity.reloadAccountEvent += ::reloadAvatarObserver
    }

    private fun loadAvatar(binding: FragmentHomeBinding) {
        val context = context ?: return
        ioSafe {
            try {
                val email = ZetFlixAuthPrefs.getStoredEmail(context) ?: ""
                val userId = if (email.isNotEmpty()) email.substringBefore("@") else ""
                val account = DataStoreHelper.getCurrentAccount() ?: DataStoreHelper.getDefaultAccount(context)

                main {
                    val avatarView = binding.homeAvatar
                    if (account.customImage != null) {
                        avatarView.loadImage(account.image)
                        avatarView.background = null
                    } else {
                        val backgrounds = listOf(
                            R.drawable.profile_bg_blue,
                            R.drawable.profile_bg_dark_blue,
                            R.drawable.profile_bg_orange,
                            R.drawable.profile_bg_pink,
                            R.drawable.profile_bg_purple,
                            R.drawable.profile_bg_red,
                            R.drawable.profile_bg_teal
                        )
                        val bgIndex = if (userId.isNotEmpty()) userId.hashCode().absoluteValue % backgrounds.size else 0
                        avatarView.setBackgroundResource(backgrounds[bgIndex])
                        avatarView.setImageResource(R.drawable.ic_outline_account_circle_24)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
