package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentAccountBinding
import com.lagradost.cloudstream3.databinding.PreferenceZetflixSwitchBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager
import kotlin.math.absoluteValue

class AccountFragment : BaseFragment<FragmentAccountBinding>(
    BindingCreator.Inflate(FragmentAccountBinding::inflate)
), BiometricAuthenticator.BiometricCallback {

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = isLayout(PHONE)
        )
    }

    override fun onBindingCreated(binding: FragmentAccountBinding) {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        binding.accountToolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        setupAccountSection(binding)
        loadUserData(binding)
    }

    private fun setupAccountSection(binding: FragmentAccountBinding) {
        val isBiometricAvailable = BiometricAuthenticator.isBiometricHardwareAvailable(requireContext())
        binding.rowBiometric.root.visibility = if (isBiometricAvailable) View.VISIBLE else View.GONE
        
        if (isBiometricAvailable) {
            bindSwitch(
                binding.rowBiometric,
                getString(R.string.biometric_key),
                R.string.biometric_setting,
                R.drawable.video_locked,
                summary = getString(R.string.biometric_setting_summary)
            ) { newValue ->
                if (newValue) {
                    BiometricAuthenticator.startBiometricAuthentication(
                        requireActivity(),
                        R.string.biometric_authentication_title,
                        false,
                        this
                    )
                    false // Wait for callback
                } else {
                    BiometricAuthenticator.setFingerprintEnabled(requireContext(), false)
                    true
                }
            }
        }

        binding.rowLogout.logoutButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_confirmation_title)
                .setMessage(R.string.logout_confirmation_message)
                .setPositiveButton(R.string.logout_button) { dialog, _ ->
                    ZetFlixSessionManager.logout(requireContext())
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun loadUserData(binding: FragmentAccountBinding) {
        val context = context ?: return
        ioSafe {
            try {
                val email = ZetFlixAuthPrefs.getStoredEmail(context) ?: ""
                val username = if (email.isNotEmpty()) email.substringBefore("@") else ""

                main {
                    val header = binding.accountHeader
                    header.accountUsername.text = username
                    header.accountEmail.text = email
                    
                    val avatarView = header.accountAvatar
                    val backgrounds = listOf(
                        R.drawable.profile_bg_blue,
                        R.drawable.profile_bg_dark_blue,
                        R.drawable.profile_bg_orange,
                        R.drawable.profile_bg_pink,
                        R.drawable.profile_bg_purple,
                        R.drawable.profile_bg_red,
                        R.drawable.profile_bg_teal
                    )
                    val bgIndex = if (username.isNotEmpty()) username.hashCode().absoluteValue % backgrounds.size else 0
                    avatarView.setBackgroundResource(backgrounds[bgIndex])
                    avatarView.setImageResource(R.drawable.ic_outline_account_circle_24)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun bindSwitch(
        switchBinding: PreferenceZetflixSwitchBinding,
        key: String,
        titleRes: Int,
        iconRes: Int,
        summary: String? = null,
        onToggle: ((Boolean) -> Boolean)? = null
    ) {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())
        switchBinding.rowTitle.setText(titleRes)
        switchBinding.rowIcon.setImageResource(iconRes)
        if (summary != null) {
            switchBinding.rowSummary.visibility = View.VISIBLE
            switchBinding.rowSummary.text = summary
        } else {
            switchBinding.rowSummary.visibility = View.GONE
        }
        
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

    override fun onAuthenticationSuccess() {
        BiometricAuthenticator.setFingerprintEnabled(requireContext(), true)
        binding?.rowBiometric?.rowSwitch?.isChecked = true
    }

    override fun onAuthenticationError() {
        binding?.rowBiometric?.rowSwitch?.isChecked = false
    }
}
