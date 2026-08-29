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
import androidx.preference.*
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.material.appbar.MaterialToolbar
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.APIHolder.allProviders
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.databinding.AddRemoveSitesBinding
import com.lagradost.cloudstream3.databinding.AddSiteInputBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.io.File
import kotlin.math.absoluteValue

class SettingsFragment : BasePreferenceFragmentCompat(), BiometricAuthenticator.BiometricCallback {

    companion object {
        fun PreferenceFragmentCompat.getPref(id: Int): Preference? {
            return try {
                findPreference(getString(id))
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        fun getFolderSize(dir: File): Long {
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

        fun Fragment.pickDownloadPath(uri: Uri?, path: String?) {
            if (uri == null) return
            val context = context ?: com.lagradost.cloudstream3.CloudStreamApp.context ?: return
            val visual = path ?: uri.toString()
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putString(getString(R.string.download_path_key), uri.toString())
                putString(context.getString(R.string.download_path_key_visual), visual)
            }
        }

        // Restore static helpers for other fragments
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

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class CustomSite(
        @JsonProperty("parentClassName") @JsonAlias("parentJavaClass")
        @SerialName("parentClassName") @JsonNames("parentJavaClass")
        val parentClassName: String,
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("url") @SerialName("url") val url: String,
        @JsonProperty("lang") @SerialName("lang") val lang: String,
    )

    private val pathPicker = getChooseFolderLauncher { uri, path ->
        pickDownloadPath(uri, path)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_all, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        setupAccountSection()
        setupGeneralSection(settingsManager)
        setupPlayerSection(settingsManager)
        setupUISection(settingsManager)
        setupUpdatesSection(settingsManager)
    }

    private fun setupAccountSection() {
        getPref(R.string.biometric_key)?.let { pref ->
            val isBiometricAvailable = BiometricAuthenticator.isBiometricAvailable(requireContext())
            pref.isVisible = isBiometricAvailable
            pref.setOnPreferenceChangeListener { _, newValue ->
                val isChecked = newValue as Boolean
                if (isChecked) {
                    BiometricAuthenticator.startBiometricAuthentication(
                        requireActivity(),
                        R.string.biometric_authentication_title,
                        false,
                        this
                    )
                    false // Don't update yet, wait for callback
                } else {
                    BiometricAuthenticator.setFingerprintEnabled(requireContext(), false)
                    true
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
    }

    private fun loadUserData() {
        val context = context ?: return
        ioSafe {
            try {
                val email = ZetFlixAuthPrefs.getStoredEmail(context) ?: ""
                val username = if (email.isNotEmpty()) email.substringBefore("@") else ""

                main {
                    val listView = listView
                    // Hack to bind views in custom layouts inside PreferenceScreen
                    for (i in 0 until listView.childCount) {
                        val child = listView.getChildAt(i)
                        val usernameView = child.findViewById<TextView>(R.id.account_username)
                        if (usernameView != null) {
                            usernameView.text = username
                            child.findViewById<TextView>(R.id.account_email)?.text = email
                            
                            val avatarView = child.findViewById<ImageView>(R.id.account_avatar)
                            val backgrounds = listOf(
                                R.drawable.profile_bg_blue,
                                R.drawable.profile_bg_dark_blue,
                                R.drawable.profile_bg_orange,
                                R.drawable.profile_bg_pink,
                                R.drawable.profile_bg_purple,
                                R.drawable.profile_bg_red,
                                R.drawable.profile_bg_teal,
                            )
                            val bgIndex = if (username.isNotEmpty()) username.hashCode().absoluteValue % backgrounds.size else 0
                            avatarView?.setBackgroundResource(backgrounds[bgIndex])
                            avatarView?.setImageResource(R.drawable.ic_outline_account_circle_24)
                        }
                        
                        val logoutButton = child.findViewById<View>(R.id.logout_button)
                        logoutButton?.setOnClickListener {
                            AlertDialog.Builder(requireContext())
                                .setTitle(R.string.logout_confirmation_title)
                                .setMessage(R.string.logout_confirmation_message)
                                .setPositiveButton(R.string.logout_button) { dialogInterface: DialogInterface, _: Int ->
                                    ZetFlixSessionManager.logout(requireContext())
                                    dialogInterface.dismiss()
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onAuthenticationSuccess() {
        BiometricAuthenticator.setFingerprintEnabled(requireContext(), true)
        (getPref(R.string.biometric_key) as? SwitchPreference)?.isChecked = true
    }

    override fun onAuthenticationError() {
        (getPref(R.string.biometric_key) as? SwitchPreference)?.isChecked = false
    }

    private fun setupGeneralSection(settingsManager: SharedPreferences) {
        getPref(R.string.battery_optimisation_key)?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false
            if (isAppRestricted(ctx)) ctx.showBatteryOptimizationDialog()
            else CommonActivity.showToast(activity, R.string.app_unrestricted_toast)
            true
        }

        getPref(R.string.override_site_key)?.setOnPreferenceClickListener {
            val current = requireContext().getKey<Array<CustomSite>>(USER_PROVIDER_API)?.toMutableList() ?: mutableListOf<CustomSite>()
            if (current.isEmpty()) showAddSite(current)
            else showAddRemoveSites(current)
            true
        }

        getPref(R.string.dns_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.dns_pref)
            val prefValues = resources.getIntArray(R.array.dns_pref_values)
            val currentDns = settingsManager.getInt(getString(R.string.dns_pref), 0)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(currentDns), getString(R.string.dns_pref), true, {}) {
                settingsManager.edit { putInt(getString(R.string.dns_pref), prefValues[it]) }
                app.initClient(requireContext())
            }
            true
        }

        getPref(R.string.jsdelivr_proxy_key)?.setOnPreferenceChangeListener { _, newValue ->
            requireContext().setKey(getString(R.string.jsdelivr_proxy_key), newValue)
            true
        }

        getPref(R.string.download_parallel_key)?.setOnPreferenceChangeListener { _, _ ->
            DownloadQueueManager.forceRefreshQueue()
            true
        }

        getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
            val dirs = getDownloadDirs()
            val currentDir = settingsManager.getString(getString(R.string.download_path_key_visual), null)
                ?: DownloadFileManagement.getDefaultDir(requireContext())?.filePath()
            activity?.showBottomDialog(dirs + listOf(getString(R.string.custom)), dirs.indexOf(currentDir), getString(R.string.download_path_pref), true, {}) {
                if (it == dirs.size) safe { pathPicker.launch(Uri.EMPTY) }
                else settingsManager.edit {
                    putString(getString(R.string.download_path_key), dirs[it])
                    putString(getString(R.string.download_path_key_visual), dirs[it])
                }
            }
            true
        }
    }

    private fun getDownloadDirs(): List<String> {
        val ctx = requireContext()
        val defaultDir = DownloadFileManagement.getDefaultDir(ctx)?.filePath()
        val first = listOf(defaultDir)
        return (try {
            val currentDir = ctx.getBasePath().let { it.first?.filePath() ?: it.second }
            (first + ctx.getExternalFilesDirs("").mapNotNull { it.path } + currentDir)
        } catch (_: Exception) { first }).filterNotNull().distinct()
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
                    requireContext().setKey(USER_PROVIDER_API, current.toTypedArray())
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
                requireContext().setKey(USER_PROVIDER_API, current.toTypedArray())
            }
            dialog.dismissSafe(activity)
        }
    }

    private fun setupPlayerSection(settingsManager: SharedPreferences) {
        getPref(R.string.video_buffer_length_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_length_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_length_values)
            val current = settingsManager.getInt(getString(R.string.video_buffer_length_key), 0)
            activity?.showDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.video_buffer_length_settings), true, {}) {
                settingsManager.edit { putInt(getString(R.string.video_buffer_length_key), prefValues[it]) }
            }
            true
        }

        getPref(R.string.prefer_limit_title_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.limit_title_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.limit_title), true, {}) {
                settingsManager.edit { putInt(getString(R.string.prefer_limit_title_key), prefValues[it]) }
            }
            true
        }

        getPref(R.string.software_decoding_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.software_decoding_switch)
            val prefValues = resources.getIntArray(R.array.software_decoding_switch_values)
            val current = settingsManager.getInt(getString(R.string.software_decoding_key), -1)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.software_decoding), true, {}) {
                settingsManager.edit { putInt(getString(R.string.software_decoding_key), prefValues[it]) }
            }
            true
        }

        getPref(R.string.prefer_limit_show_player_info)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.title_info_pref_names)
            val keys = resources.getStringArray(R.array.title_info_pref_values)
            val playerDefaults = mapOf(getString(R.string.show_name_key) to true, getString(R.string.show_resolution_key) to true, getString(R.string.show_media_info_key) to false)
            val selectedIndices = keys.mapIndexedNotNull { index, key -> if (settingsManager.getBoolean(key, playerDefaults[key] ?: false)) index else null }
            activity?.showMultiDialog(prefNames.toList(), selectedIndices, getString(R.string.limit_title_rez), {}) { selected ->
                settingsManager.edit { for ((index, key) in keys.withIndex()) putBoolean(key, selected.contains(index)) }
            }
            true
        }

        getPref(R.string.player_default_key)?.setOnPreferenceClickListener {
            val players = VideoClickActionHolder.getPlayers(activity).mapNotNull { (it.name.asStringNull(activity) ?: it::class.simpleName)?.let { name -> it to name } }
            val names = listOf(getString(R.string.player_settings_play_in_app)) + players.map { it.second }
            val values = listOf("") + players.map { it.first.uniqueId() }
            val current = settingsManager.getString(getString(R.string.player_default_key), "") ?: ""
            activity?.showBottomDialog(names, values.indexOf(current), getString(R.string.player_pref), true, {}) {
                settingsManager.edit { putString(getString(R.string.player_default_key), values[it]) }
            }
            true
        }

        getPref(R.string.subtitle_settings_key)?.setOnPreferenceClickListener { SubtitlesFragment.push(activity, false); true }
        getPref(R.string.subtitle_settings_chromecast_key)?.setOnPreferenceClickListener { ChromecastSubtitlesFragment.push(activity, false); true }
        getPref(R.string.player_source_priority_key)?.setOnPreferenceClickListener {
            ioSafe {
                val defaultSources = QualityProfileDialog.getAllDefaultSources()
                activity?.runOnUiThread { QualityProfileDialog(requireActivity(), R.style.DialogFullscreenPlayer, defaultSources).show() }
            }
            true
        }

        val cacheDir = requireContext().cacheDir
        getPref(R.string.video_buffer_clear_key)?.let { pref ->
            fun updateSummary() { pref.summary = formatShortFileSize(requireContext(), getFolderSize(cacheDir)) }
            updateSummary()
            pref.setOnPreferenceClickListener { cacheDir.deleteRecursively(); updateSummary(); true }
        }
    }

    private fun setupUISection(settingsManager: SharedPreferences) {
        getPref(R.string.bottom_title_key)?.setOnPreferenceChangeListener { _, _ ->
            HomeChildItemAdapter.sharedPool.clear(); ParentItemAdapter.sharedPool.clear(); SearchAdapter.sharedPool.clear(); true
        }

        getPref(R.string.poster_size_key)?.setOnPreferenceChangeListener { _, newValue ->
            HomeChildItemAdapter.sharedPool.clear(); ParentItemAdapter.sharedPool.clear(); SearchAdapter.sharedPool.clear()
            context?.let { HomeChildItemAdapter.updatePosterSize(it, newValue as? Int) }; true
        }

        getPref(R.string.poster_ui_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.poster_ui_options)
            val keys = resources.getStringArray(R.array.poster_ui_options_values)
            val prefValues = keys.mapIndexedNotNull { index, key -> if (settingsManager.getBoolean(key, true)) index else null }
            activity?.showMultiDialog(prefNames.toList(), prefValues, getString(R.string.poster_ui_settings), {}) { list ->
                settingsManager.edit { for ((i, key) in keys.withIndex()) putBoolean(key, list.contains(i)) }
                SearchResultBuilder.updateCache(requireContext())
            }
            true
        }

        getPref(R.string.pref_filter_search_quality_key)?.setOnPreferenceClickListener {
            val names = SearchQuality.entries.asSequence().sortedBy { it.name }.map { it.name }.toList()
            val currentList = settingsManager.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())?.map { it.toInt() } ?: listOf()
            activity?.showMultiDialog(names, currentList, getString(R.string.pref_filter_search_quality), {}) { selectedList ->
                settingsManager.edit { putStringSet(getString(R.string.pref_filter_search_quality_key), selectedList.map { it.toString() }.toMutableSet()) }
            }
            true
        }

        getPref(R.string.confirm_exit_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.confirm_exit)
            val prefValues = resources.getIntArray(R.array.confirm_exit_values)
            val current = settingsManager.getInt(getString(R.string.confirm_exit_key), -1)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.confirm_before_exiting_title), true, {}) {
                settingsManager.edit { putInt(getString(R.string.confirm_exit_key), prefValues[it]) }
            }
            true
        }
    }

    private fun setupUpdatesSection(settingsManager: SharedPreferences) {
        getPref(R.string.apk_installer_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.apk_installer_pref)
            val prefValues = resources.getIntArray(R.array.apk_installer_values)
            val current = settingsManager.getInt(getString(R.string.apk_installer_key), 1)
            activity?.showBottomDialog(prefNames.toList(), prefValues.indexOf(current), getString(R.string.apk_installer_settings), true, {}) { num ->
                settingsManager.edit { putInt(getString(R.string.apk_installer_key), prefValues[num]) }
            }
            true
        }

        getPref(R.string.manual_check_update_key)?.let { pref ->
            pref.summary = BuildConfig.VERSION_NAME
            pref.setOnPreferenceClickListener {
                ioSafe {
                    if (activity?.runAutoUpdate(false) == false) {
                        activity?.runOnUiThread { CommonActivity.showToast(activity, R.string.no_update_found, Toast.LENGTH_SHORT) }
                    }
                }
                true
            }
        }
    }
}
