package com.lagradost.cloudstream3.ui.settings.extensions

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentPluginsBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.movie.HomeFragment.Companion.bindChips
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setSystemBarsPadding
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji

const val PLUGINS_BUNDLE_DATA = "data"
const val PLUGINS_BUNDLE_LOCAL = "isLocal"

class PluginsFragment : BaseFragment<FragmentPluginsBinding>(
    BindingCreator.Inflate(FragmentPluginsBinding::inflate),
) {
    private lateinit var pluginViewModel: PluginsViewModel

    override fun onDestroyView() {
        pluginViewModel.clear()
        super.onDestroyView()
    }

    override fun fixLayout(view: View) {
        setSystemBarsPadding()
    }

    override fun onBindingCreated(binding: FragmentPluginsBinding) {
        pluginViewModel = ViewModelProvider(this)[PluginsViewModel::class.java]

        pluginViewModel.tvTypes.clear()
        pluginViewModel.selectedLanguages = listOf()
        pluginViewModel.clear()

        activity?.let {
            val providerLangs = it.getApiProviderLangSettings().toList()
            if (!providerLangs.contains(AllLanguagesName)) {
                pluginViewModel.selectedLanguages = mutableListOf("none") + providerLangs
            }
        }

        val repositoryData = arguments?.getString(PLUGINS_BUNDLE_DATA)?.let { data ->
            tryParseJson<RepositoryData>(data)
        }
        val isLocal = arguments?.getBoolean(PLUGINS_BUNDLE_LOCAL) == true
        val downloadAllButton = binding.settingsToolbar.menu?.findItem(R.id.download_all)

        if (repositoryData == null) {
            dispatchBackPressed()
            return
        }

        setToolBarScrollFlags()
        setUpToolbar(repositoryData.name)
        binding.settingsToolbar.apply {
            setOnMenuItemClickListener { menuItem ->
                when (menuItem?.itemId) {
                    R.id.download_all -> {
                        PluginsViewModel.downloadAll(activity, repositoryData, pluginViewModel)
                    }

                    R.id.lang_filter -> {
                        val languagesTagName = pluginViewModel.pluginLanguages
                            .asSequence()
                            .map { langTag ->
                                Pair(
                                    langTag,
                                    getNameNextToFlagEmoji(langTag) ?: langTag
                                )
                            }
                            .sortedBy {
                                it.second.substringAfter("\u00a0").lowercase()
                            }
                            .toMutableList()

                        if (languagesTagName.remove(Pair("none", "none"))) {
                            languagesTagName.add(0, Pair("none", getString(R.string.no_data)))
                        }

                        val currentIndexList = pluginViewModel.selectedLanguages.map { langTag ->
                            languagesTagName.indexOfFirst { lang -> lang.first == langTag }
                        }

                        activity?.showMultiDialog(
                            languagesTagName.map { it.second },
                            currentIndexList,
                            getString(R.string.provider_lang_settings),
                            {}
                        ) { selectedList ->
                            pluginViewModel.selectedLanguages =
                                selectedList.map { languagesTagName[it].first }
                            pluginViewModel.updateFilteredPlugins()
                        }
                    }

                    else -> {}
                }
                return@setOnMenuItemClickListener true
            }

            val searchView =
                menu?.findItem(R.id.search_button)?.actionView as? SearchView

            setNavigationOnClickListener {
                if (searchView?.isIconified == false) {
                    searchView.isIconified = true
                } else {
                    dispatchBackPressed()
                }
            }

            searchView?.setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                    pluginViewModel.search(query)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    pluginViewModel.search(newText)
                    return true
                }
            })
        }

        binding.pluginRecyclerView.apply {
            setLinearListLayout(
                isHorizontal = false,
                nextDown = FOCUS_SELF,
                nextRight = FOCUS_SELF,
            )
            setRecycledViewPool(PluginAdapter.sharedPool)
            adapter =
                PluginAdapter {
                    pluginViewModel.handlePluginAction(activity, listOf(repositoryData), it, isLocal)
                }
        }

        observe(pluginViewModel.filteredPlugins) { (scrollToTop, list) ->
            (binding.pluginRecyclerView.adapter as? PluginAdapter)?.submitList(list)
            if (scrollToTop) {
                binding.pluginRecyclerView.scrollToPosition(0)
            }
        }

        if (isLocal) {
            downloadAllButton?.isVisible = false
            binding.settingsToolbar.menu?.findItem(R.id.lang_filter)?.isVisible = false
            pluginViewModel.updatePluginListLocal()

            binding.tvtypesChipsScroll.root.isVisible = false
        } else {
            pluginViewModel.updatePluginList(context, listOf(repositoryData))
            binding.tvtypesChipsScroll.root.isVisible = true
            downloadAllButton?.isVisible = BuildConfig.DEBUG

            bindChips(
                binding.tvtypesChipsScroll.tvtypesChips,
                emptyList(),
                TvType.entries.toList(),
                callback = { list ->
                    pluginViewModel.tvTypes.clear()
                    pluginViewModel.tvTypes.addAll(list.map { it.name })
                    pluginViewModel.updateFilteredPlugins()
                },
                nextFocusDown = R.id.plugin_recycler_view,
                nextFocusUp = null,
            )
        }
    }

    companion object {
        fun newInstance(repositoryData: RepositoryData): Bundle {
            return Bundle().apply {
                putString(PLUGINS_BUNDLE_DATA, repositoryData.toJson())
                putBoolean(PLUGINS_BUNDLE_LOCAL, false)
            }
        }
         fun newLocalInstance(name: String): Bundle {
            return Bundle().apply {
                putString(PLUGINS_BUNDLE_DATA, RepositoryData("", name, "").toJson())
                putBoolean(PLUGINS_BUNDLE_LOCAL, true)
            }
        }
    }
}
