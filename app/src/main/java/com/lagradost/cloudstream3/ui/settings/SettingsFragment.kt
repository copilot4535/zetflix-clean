package com.lagradost.cloudstream3.ui.settings

import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.databinding.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.ui.clear
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.settings.utils.getChooseFolderLauncher
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.BatteryOptimizationChecker.isAppRestricted
import com.lagradost.cloudstream3.utils.BatteryOptimizationChecker.showBatteryOptimizationDialog
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.InAppUpdater.runAutoUpdate
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getBasePath
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import kotlin.math.absoluteValue

fun Fragment.pickDownloadPath(uri: Uri?, path: String?) {
    if (uri == null) return
    val context = context ?: CloudStreamApp.context ?: return
    val visual = path ?: uri.toString()
    PreferenceManager.getDefaultSharedPreferences(context).edit {
        putString(getString(R.string.download_path_key), uri.toString())
        putString(context.getString(R.string.download_path_key_visual), visual)
    }
}

class SettingsFragment : BaseFragment<FragmentSettingsUnifiedBinding>(
    BindingCreator.Inflate(FragmentSettingsUnifiedBinding::inflate)
) {

    private lateinit var settingsManager: SharedPreferences

    @OptIn(ExperimentalSerializationApi::class)
    @kotlinx.serialization.Serializable
    data class CustomSite(
        @com.fasterxml.jackson.annotation.JsonProperty("parentClassName") @com.fasterxml.jackson.annotation.JsonAlias("parentJavaClass")
        @kotlinx.serialization.SerialName("parentClassName") @kotlinx.serialization.json.JsonNames("parentJavaClass")
        val parentClassName: String,
        @com.fasterxml.jackson.annotation.JsonProperty("name") @kotlinx.serialization.SerialName("name") val name: String,
        @com.fasterxml.jackson.annotation.JsonProperty("url") @kotlinx.serialization.SerialName("url") val url: String,
        @com.fasterxml.jackson.annotation.JsonProperty("lang") @kotlinx.serialization.SerialName("lang") val lang: String,
    )

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = isLayout(PHONE)
        )
    }

    override fun onBindingCreated(binding: FragmentSettingsUnifiedBinding) {
        settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        setupGeneralSection(binding)
        setupPlayerSection(binding)
        setupGesturesSection(binding)
        setupLayoutSection(binding)
        setupCacheSection(binding)
        setupUpdatesSection(binding)
    }

    private fun setupGeneralSection(binding: FragmentSettingsUnifiedBinding) {
        setupRow(binding.rowDownloadPath, R.string.download_path_pref, R.drawable.netflix_download) {
            val ctx = requireContext()
            val defaultDir = DownloadFileManagement.getDefaultDir(ctx)?.filePath()
            val currentDir = settingsManager.getString(getString(R.string.download_path_key_visual), null) ?: defaultDir
            
            val dirs = (try {
                val base = ctx.getBasePath().let { it.first?.filePath() ?: it.second }
                (listOf(defaultDir) + ctx.getExternalFilesDirs("").mapNotNull { it.path } + base)
            } catch (_: Exception) { listOf(defaultDir) }).filterNotNull().distinct()

            activity?.showBottomDialog(dirs + listOf(getString(R.string.custom)), dirs.indexOf(currentDir), getString(R.string.download_path_pref), true, {}) {
                if (it == dirs.size) safe {
                    val picker = getChooseFolderLauncher { uri, path -> 
                        if (uri != null) {
                            settingsManager.edit {
                                putString(getString(R.string.download_path_key), uri.toString())
                                putString(getString(R.string.download_path_key_visual), path ?: uri.toString())
                            }
                        }
                    }
                    picker.launch(Uri.EMPTY)
                }
                else settingsManager.edit {
                    putString(getString(R.string.download_path_key), dirs[it])
                    putString(getString(R.string.download_path_key_visual), dirs[it])
                }
            }
        }

        setupRow(binding.rowDownloadParallel, R.string.parallel_downloads, R.drawable.arrow_or_edge_24px) {
            val values = (1..10).map { it }
            val current = settingsManager.getInt(getString(R.string.download_parallel_key), 3)
            activity?.showDialog(values.map { it.toString() }, values.indexOf(current), getString(R.string.parallel_downloads), true, {}) {
                settingsManager.edit { putInt(getString(R.string.download_parallel_key), values[it]) }
                DownloadQueueManager.forceRefreshQueue()
            }
        }

        setupRow(binding.rowDownloadConcurrent, R.string.concurrent_connections, R.drawable.arrow_and_edge_24px) {
            val values = (1..10).map { it }
            val current = settingsManager.getInt(getString(R.string.download_concurrent_key), 3)
            activity?.showDialog(values.map { it.toString() }, values.indexOf(current), getString(R.string.concurrent_connections), true, {}) {
                settingsManager.edit { putInt(getString(R.string.download_concurrent_key), values[it]) }
            }
        }

        setupRow(binding.rowBattery, R.string.battery_dialog_title, R.drawable.ic_battery) {
            if (isAppRestricted(requireContext())) requireContext().showBatteryOptimizationDialog()
            else CommonActivity.showToast(activity, R.string.app_unrestricted_toast)
        }

        setupRow(binding.rowOverrideSite, R.string.add_site_pref, R.drawable.ic_baseline_add_24) {
            val current = requireContext().getKey<Array<CustomSite>>(USER_PROVIDER_API)?.toMutableList() ?: mutableListOf<CustomSite>()
            if (current.isEmpty()) showAddSite(current) else showAddRemoveSites(current)
        }

        setupRow(binding.rowDns, R.string.dns_pref, R.drawable.ic_baseline_dns_24) {
            val prefNames = resources.getStringArray(R.array.dns_pref)
            val prefValues = resources.getIntArray(R.array.dns_pref_values)
            val currentDns = settingsManager.getInt(getString(R.string.dns_pref), 0)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(currentDns), getString(R.string.dns_pref), true, {}) {
                settingsManager.edit { putInt(getString(R.string.dns_pref), prefValues[it]) }
                app.initClient(requireContext())
            }
        }

        bindSwitch(binding.rowJsdelivr, getString(R.string.jsdelivr_proxy_key), R.string.jsdelivr_proxy, R.drawable.ic_github_logo, summary = getString(R.string.jsdelivr_proxy_summary))
    }

    private fun setupPlayerSection(binding: FragmentSettingsUnifiedBinding) {
        bindSwitch(binding.rowSync, getString(R.string.episode_sync_enabled_key), R.string.episode_sync_settings, R.drawable.baseline_sync_24, summary = getString(R.string.episode_sync_settings_des))
        
        setupRow(binding.rowPlayerDefault, R.string.player_pref, R.drawable.netflix_play) {
            val players = VideoClickActionHolder.getPlayers(activity).mapNotNull { (it.name.asStringNull(activity) ?: it::class.simpleName)?.let { name -> it to name } }
            val names = listOf(getString(R.string.player_settings_play_in_app)) + players.map { it.second }
            val values = listOf("") + players.map { it.first.uniqueId() }
            val current = settingsManager.getString(getString(R.string.player_default_key), "") ?: ""
            activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.player_pref), true, {}) {
                settingsManager.edit { putString(getString(R.string.player_default_key), values[it]) }
            }
        }

        setupRow(binding.rowLimitTitle, R.string.limit_title, R.drawable.ic_baseline_text_format_24) {
            val prefNames = resources.getStringArray(R.array.limit_title_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.limit_title), true, {}) {
                settingsManager.edit { putInt(getString(R.string.prefer_limit_title_key), prefValues[it]) }
            }
        }

        setupRow(binding.rowLimitRez, R.string.limit_title_rez, R.drawable.ic_baseline_text_format_24) {
            val prefNames = resources.getStringArray(R.array.title_info_pref_names)
            val keys = resources.getStringArray(R.array.title_info_pref_values)
            val playerDefaults = mapOf(getString(R.string.show_name_key) to true, getString(R.string.show_resolution_key) to true, getString(R.string.show_media_info_key) to false)
            val selectedIndices = keys.mapIndexedNotNull { index, key -> if (settingsManager.getBoolean(key, playerDefaults[key] ?: false)) index else null }
            activity?.showMultiDialog(prefNames.toList(), selectedIndices, getString(R.string.limit_title_rez), {}) { selected ->
                settingsManager.edit { for ((index, key) in keys.withIndex()) putBoolean(key, selected.contains(index)) }
            }
        }

        bindSwitch(binding.rowHideControls, getString(R.string.hide_player_control_names_key), R.string.hide_player_control_names, R.drawable.ic_baseline_text_format_24)
        setupRow(binding.rowSubtitles, R.string.player_subtitles_settings, R.drawable.ic_outline_subtitles_24) { SubtitlesFragment.push(activity, false) }
        setupRow(binding.rowSubtitlesChromecast, R.string.chromecast_subtitles_settings, R.drawable.ic_outline_subtitles_24) { ChromecastSubtitlesFragment.push(activity, false) }
        setupRow(binding.rowSourcePriority, R.string.source_priority, R.drawable.ic_baseline_people_24) {
            ioSafe {
                val defaultSources = QualityProfileDialog.getAllDefaultSources()
                activity?.runOnUiThread { QualityProfileDialog(requireActivity(), R.style.DialogFullscreenPlayer, defaultSources).show() }
            }
        }

        bindSwitch(binding.rowPip, getString(R.string.pip_enabled_key), R.string.picture_in_picture, R.drawable.ic_baseline_picture_in_picture_alt_24, summary = getString(R.string.picture_in_picture_des))
        bindSwitch(binding.rowResize, getString(R.string.player_resize_enabled_key), R.string.player_size_settings, R.drawable.ic_baseline_aspect_ratio_24, summary = getString(R.string.player_size_settings_des))
        bindSwitch(binding.rowSpeed, getString(R.string.playback_speed_enabled_key), R.string.eigengraumode_settings, R.drawable.ic_baseline_speed_24, summary = getString(R.string.speed_setting_summary))
        bindSwitch(binding.rowSpeedup, getString(R.string.speedup_key), R.string.speedup_title, R.drawable.speedup, summary = getString(R.string.speedup_summary))
        bindSwitch(binding.rowAutoplay, getString(R.string.autoplay_next_key), R.string.autoplay_next_settings, R.drawable.ic_baseline_skip_next_24, summary = getString(R.string.autoplay_next_settings_des))
        bindSwitch(binding.rowSkipOp, getString(R.string.enable_skip_op_from_database), R.string.video_skip_op, R.drawable.ic_baseline_skip_next_24, summary = getString(R.string.enable_skip_op_from_database_des))
        bindSwitch(binding.rowRotate, getString(R.string.rotate_video_key), R.string.rotate_video, R.drawable.screen_rotation, summary = getString(R.string.rotate_video_desc))
        bindSwitch(binding.rowAutoRotate, getString(R.string.auto_rotate_video_key), R.string.auto_rotate_video, R.drawable.screen_rotation, summary = getString(R.string.auto_rotate_video_desc))
        bindSwitch(binding.rowPreview, getString(R.string.preview_seekbar_key), R.string.preview_seekbar, R.drawable.preview_seekbar_24, summary = getString(R.string.preview_seekbar_desc))
        
        setupRow(binding.rowSoftwareDecoding, R.string.software_decoding, R.drawable.ic_baseline_extension_24) {
            val prefNames = resources.getStringArray(R.array.software_decoding_switch)
            val prefValues = resources.getIntArray(R.array.software_decoding_switch_values)
            val current = settingsManager.getInt(getString(R.string.software_decoding_key), -1)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.software_decoding), true, {}) {
                settingsManager.edit { putInt(getString(R.string.software_decoding_key), prefValues[it]) }
            }
        }

        bindSwitch(binding.rowExtraBrightness, getString(R.string.extra_brightness_key), R.string.extra_brightness_settings, R.drawable.sun_7_24, summary = getString(R.string.extra_brightness_settings_des))
    }

    private fun setupGesturesSection(binding: FragmentSettingsUnifiedBinding) {
        bindSwitch(binding.rowSwipeSeek, getString(R.string.swipe_enabled_key), R.string.swipe_to_seek_settings, R.drawable.ic_baseline_ondemand_video_24, summary = getString(R.string.swipe_to_seek_settings_des))
        bindSwitch(binding.rowSwipeChange, getString(R.string.swipe_vertical_enabled_key), R.string.swipe_to_change_settings, R.drawable.ic_baseline_ondemand_video_24, summary = getString(R.string.swipe_to_change_settings_des))
        bindSwitch(binding.rowDoubleTapSeek, getString(R.string.double_tap_enabled_key), R.string.double_tap_to_seek_settings, R.drawable.ic_baseline_touch_app_24, summary = getString(R.string.double_tap_to_seek_settings_des))
        bindSwitch(binding.rowDoubleTapPause, getString(R.string.double_tap_pause_enabled_key), R.string.double_tap_to_pause_settings, R.drawable.netflix_pause, summary = getString(R.string.double_tap_to_pause_settings_des))
        
        setupRow(binding.rowSeekTime, R.string.double_tap_to_seek_amount_settings, R.drawable.go_forward_30) {
            val values = (5..60 step 5).map { it }
            val current = settingsManager.getInt(getString(R.string.double_tap_seek_time_key), 10)
            activity?.showDialog(values.map { "$it s" }, values.indexOf(current), getString(R.string.double_tap_to_seek_amount_settings), true, {}) {
                settingsManager.edit { putInt(getString(R.string.double_tap_seek_time_key), values[it]) }
            }
        }
    }

    private fun setupLayoutSection(binding: FragmentSettingsUnifiedBinding) {
        bindSwitch(binding.rowBottomTitle, getString(R.string.bottom_title_key), R.string.bottom_title_settings, R.drawable.title_24px, summary = getString(R.string.bottom_title_settings_des)) {
            HomeChildItemAdapter.sharedPool.clear(); ParentItemAdapter.sharedPool.clear(); SearchAdapter.sharedPool.clear(); true
        }

        setupRow(binding.rowPosterSize, R.string.poster_size_settings, R.drawable.baseline_grid_view_24) {
            val values = (0..15).map { it }
            val current = settingsManager.getInt(getString(R.string.poster_size_key), 0)
            activity?.showDialog(values.map { it.toString() }, values.indexOf(current), getString(R.string.poster_size_settings), true, {}) {
                settingsManager.edit { putInt(getString(R.string.poster_size_key), values[it]) }
                HomeChildItemAdapter.sharedPool.clear(); ParentItemAdapter.sharedPool.clear(); SearchAdapter.sharedPool.clear()
                context?.let { ctx -> HomeChildItemAdapter.updatePosterSize(ctx, values[it]) }
            }
        }

        setupRow(binding.rowPosterUi, R.string.poster_ui_settings, R.drawable.ic_baseline_tv_24) {
            val prefNames = resources.getStringArray(R.array.poster_ui_options)
            val keys = resources.getStringArray(R.array.poster_ui_options_values)
            val prefValues = keys.mapIndexedNotNull { index, key -> if (settingsManager.getBoolean(key, true)) index else null }
            activity?.showMultiDialog(prefNames.toList(), prefValues, getString(R.string.poster_ui_settings), {}) { list ->
                settingsManager.edit { for ((i, key) in keys.withIndex()) putBoolean(key, list.contains(i)) }
                SearchResultBuilder.updateCache(requireContext())
            }
        }

        bindSwitch(binding.rowAdvancedSearch, "advanced_search", R.string.advanced_search, R.drawable.search_icon, summary = getString(R.string.advanced_search_des))
        bindSwitch(binding.rowSuggestions, "search_suggestions_enabled", R.string.search_suggestions, R.drawable.search_icon, summary = getString(R.string.search_suggestions_des))
        bindSwitch(binding.rowTrailers, getString(R.string.show_trailers_key), R.string.show_trailers_settings, R.drawable.baseline_theaters_24)
        bindSwitch(binding.rowCast, getString(R.string.show_cast_in_details_key), R.string.show_cast_in_details, R.drawable.ic_baseline_people_24)
        bindSwitch(binding.rowFiller, getString(R.string.show_fillers_key), R.string.show_fillers_settings, R.drawable.ic_baseline_skip_next_24)
        bindSwitch(binding.rowMetadata, getString(R.string.show_player_metadata_key), R.string.show_player_metadata_overlay, R.drawable.metadata_overlay_icon)
        bindSwitch(binding.rowRandom, getString(R.string.random_button_key), R.string.random_button_settings, R.drawable.ic_baseline_play_arrow_24, summary = getString(R.string.random_button_settings_desc))
        
        setupRow(binding.rowConfirmExit, R.string.confirm_before_exiting_title, R.drawable.ic_baseline_exit_24) {
            val prefNames = resources.getStringArray(R.array.confirm_exit)
            val prefValues = resources.getIntArray(R.array.confirm_exit_values)
            val confirmExit = settingsManager.getInt(getString(R.string.confirm_exit_key), -1)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(confirmExit), getString(R.string.confirm_before_exiting_title), true, {}) {
                settingsManager.edit { putInt(getString(R.string.confirm_exit_key), prefValues[it]) }
            }
        }

        setupRow(binding.rowFilterQuality, R.string.pref_filter_search_quality, R.drawable.ic_baseline_filter_list_24) {
            val names = SearchQuality.entries.asSequence().sortedBy { it.name }.map { it.name }.toList()
            val currentList = settingsManager.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())?.map { it.toInt() } ?: listOf()
            activity?.showMultiDialog(names, currentList, getString(R.string.pref_filter_search_quality), {}) { selectedList ->
                settingsManager.edit { putStringSet(getString(R.string.pref_filter_search_quality_key), selectedList.map { it.toString() }.toMutableSet()) }
            }
        }
    }

    private fun setupCacheSection(binding: FragmentSettingsUnifiedBinding) {
        setupRow(binding.rowCacheDisk, R.string.video_buffer_disk_settings, R.drawable.ic_baseline_storage_24) {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)
            val current = settingsManager.getInt(getString(R.string.video_buffer_disk_key), 0)
            activity?.showDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.video_buffer_disk_settings), true, {}) {
                settingsManager.edit { putInt(getString(R.string.video_buffer_disk_key), prefValues[it]) }
            }
        }
        setupRow(binding.rowCacheSize, R.string.video_buffer_size_settings, R.drawable.ic_baseline_storage_24) {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)
            val current = settingsManager.getInt(getString(R.string.video_buffer_size_key), 0)
            activity?.showDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.video_buffer_size_settings), true, {}) {
                settingsManager.edit { putInt(getString(R.string.video_buffer_size_key), prefValues[it]) }
            }
        }
        setupRow(binding.rowCacheLength, R.string.video_buffer_length_settings, R.drawable.ic_baseline_storage_24) {
            val prefNames = resources.getStringArray(R.array.video_buffer_length_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_length_values)
            val current = settingsManager.getInt(getString(R.string.video_buffer_length_key), 0)
            activity?.showDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.video_buffer_length_settings), true, {}) {
                settingsManager.edit { putInt(getString(R.string.video_buffer_length_key), prefValues[it]) }
            }
        }
        setupRow(binding.rowCacheClear, R.string.video_buffer_clear_settings, R.drawable.ic_baseline_delete_outline_24) {
            val cacheDir = requireContext().cacheDir
            cacheDir.deleteRecursively()
            CommonActivity.showToast(activity, "Cache cleared", Toast.LENGTH_SHORT)
            updateCacheSummary(binding)
        }
        updateCacheSummary(binding)
    }

    private fun updateCacheSummary(binding: FragmentSettingsUnifiedBinding) {
        val cacheDir = requireContext().cacheDir
        val size = formatShortFileSize(requireContext(), getFolderSize(cacheDir))
        binding.rowCacheClear.rowSummary.apply {
            visibility = View.VISIBLE
            text = size
        }
    }

    private fun setupUpdatesSection(binding: FragmentSettingsUnifiedBinding) {
        setupRow(binding.rowCheckUpdate, R.string.check_for_update, R.drawable.ic_baseline_system_update_24) {
            ioSafe {
                if (activity?.runAutoUpdate(false) == false) {
                    activity?.runOnUiThread { CommonActivity.showToast(activity, R.string.no_update_found, Toast.LENGTH_SHORT) }
                }
            }
        }
        setupRow(binding.rowApkInstaller, R.string.apk_installer_settings, R.drawable.netflix_download) {
            val prefNames = resources.getStringArray(R.array.apk_installer_pref)
            val prefValues = resources.getIntArray(R.array.apk_installer_values)
            val current = settingsManager.getInt(getString(R.string.apk_installer_key), 1)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.apk_installer_settings), true, {}) { num ->
                settingsManager.edit { putInt(getString(R.string.apk_installer_key), prefValues[num]) }
            }
        }
        bindSwitch(binding.rowAutoUpdate, getString(R.string.auto_update_key), R.string.updates_settings, R.drawable.ic_baseline_notifications_active_24, summary = getString(R.string.updates_settings_des))
    }

    private fun setupRow(rowBinding: PreferenceZetflixRowBinding, titleRes: Int, iconRes: Int, summary: String? = null, onClick: () -> Unit) {
        rowBinding.rowTitle.setText(titleRes)
        rowBinding.rowIcon.setImageResource(iconRes)
        if (summary != null) { rowBinding.rowSummary.visibility = View.VISIBLE; rowBinding.rowSummary.text = summary }
        else rowBinding.rowSummary.visibility = View.GONE
        rowBinding.root.setOnClickListener { onClick() }
    }

    private fun bindSwitch(switchBinding: PreferenceZetflixSwitchBinding, key: String, titleRes: Int, iconRes: Int, summary: String? = null, onToggle: ((Boolean) -> Boolean)? = null) {
        switchBinding.rowTitle.setText(titleRes)
        switchBinding.rowIcon.setImageResource(iconRes)
        if (summary != null) { switchBinding.rowSummary.visibility = View.VISIBLE; switchBinding.rowSummary.text = summary }
        else switchBinding.rowSummary.visibility = View.GONE
        
        val switch = switchBinding.rowSwitch
        switch.isChecked = settingsManager.getBoolean(key, true)
        
        switchBinding.root.setOnClickListener {
            val newValue = !switch.isChecked
            if (onToggle != null) {
                if (onToggle(newValue)) {
                    switch.isChecked = newValue
                    settingsManager.edit().putBoolean(key, newValue).apply()
                }
            } else {
                switch.isChecked = newValue
                settingsManager.edit().putBoolean(key, newValue).apply()
            }
        }
    }

    private fun showAddSite(current: MutableList<CustomSite>) {
        val providers = allProviders.distinctBy { it::class }.sortedBy { it.name }
        activity?.showDialog(providers.map { "${it.name} (${it.mainUrl})" }, -1, getString(R.string.add_site_pref), true, {}) { selection ->
            val provider = providers.getOrNull(selection) ?: return@showDialog
            val binding = AddSiteInputBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom).setView(binding.root).show()
            binding.text2.text = provider.name
            binding.applyBtt.setOnClickListener {
                val name = binding.siteNameInput.text?.toString()
                val url = binding.siteUrlInput.text?.toString()
                val lang = binding.siteLangInput.text?.toString()
                val realLang = if (lang.isNullOrBlank()) provider.lang else lang
                if (url.isNullOrBlank() || name.isNullOrBlank()) {
                    CommonActivity.showToast(activity, R.string.error_invalid_data)
                } else {
                    current.add(CustomSite(provider::class.simpleName!!, name, url, realLang))
                    requireContext().setKey<Array<CustomSite>>(USER_PROVIDER_API, current.toTypedArray())
                    MainActivity.afterPluginsLoadedEvent.invoke(false)
                    dialog.dismissSafe(activity)
                }
            }
            binding.cancelBtt.setOnClickListener { dialog.dismissSafe(activity) }
        }
    }

    private fun showAddRemoveSites(current: MutableList<CustomSite>) {
        val binding = AddRemoveSitesBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom).setView(binding.root).show()
        binding.addSite.setOnClickListener { showAddSite(current); dialog.dismissSafe(activity) }
        binding.removeSite.setOnClickListener {
            activity?.showMultiDialog(current.map { it.name }, listOf(), getString(R.string.remove_site_pref), {}) { indexes ->
                current.removeAll(indexes.map { current[it] })
                requireContext().setKey<Array<CustomSite>>(USER_PROVIDER_API, current.toTypedArray())
            }
            dialog.dismissSafe(activity)
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size: Long = 0
        dir.listFiles()?.let {
            for (file in it) {
                size += if (file.isFile) {
                    file.length()
                } else getFolderSize(file)
            }
        }
        return size
    }

    companion object {
        fun setPaddingBottom() {}
        fun setToolBarScrollFlags() {}
        
        fun Fragment.setUpToolbar(title: String) {
            val settingsToolbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar) ?: return
            settingsToolbar.apply {
                setTitle(title)
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                }
            }
        }

        fun Fragment.setUpToolbar(@StringRes title: Int) {
            val settingsToolbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar) ?: return
            settingsToolbar.apply {
                setTitle(title)
                setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
                setNavigationOnClickListener {
                    safe { activity?.onBackPressedDispatcher?.onBackPressed() }
                }
            }
        }

        fun Fragment.setSystemBarsPadding() {
            view?.let {
                fixSystemBarsPadding(
                    it,
                    padLeft = isLayout(PHONE),
                    padBottom = isLandscape()
                )
            }
        }

        fun Preference?.hideOn(layoutFlags: Int): Preference? {
            if (this == null) return null
            this.isVisible = !isLayout(layoutFlags)
            return if(this.isVisible) this else null
        }
    }
}
