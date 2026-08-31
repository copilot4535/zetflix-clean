package com.lagradost.cloudstream3.ui.settings

import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentAccountBinding
import com.lagradost.cloudstream3.databinding.PreferenceZetflixSwitchBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.account.AccountHelper
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager
import com.lagradost.cloudstream3.utils.saveUriToInternalStorage
import kotlin.math.absoluteValue

class AccountFragment : BaseFragment<FragmentAccountBinding>(
    BindingCreator.Inflate(FragmentAccountBinding::inflate)
), BiometricAuthenticator.BiometricCallback {

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val context = context ?: return@registerForActivityResult
            val account = DataStoreHelper.getCurrentAccount() ?: DataStoreHelper.getDefaultAccount(context)
            val fileName = "profile_${account.keyIndex}_${System.currentTimeMillis()}.jpg"
            val path = saveUriToInternalStorage(context, uri, fileName)
            if (path != null) {
                val updatedAccount = account.copy(customImage = path)
                // Also update the account in the view model if it's the current one
                updateAccount(updatedAccount)
            }
        }
    }

    private fun updateAccount(account: DataStoreHelper.Account) {
        val context = context ?: return
        val viewModel = ViewModelProvider(requireActivity())[AccountViewModel::class.java]
        viewModel.handleAccountUpdate(account, context)
        loadUserData(binding ?: return)
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = isLayout(PHONE)
        )
    }

    override fun onBindingCreated(binding: FragmentAccountBinding) {
        binding.accountToolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        setupAccountSection(binding)
        loadUserData(binding)
    }

    private fun setupAccountSection(binding: FragmentAccountBinding) {
        val hasHardware = BiometricAuthenticator.isBiometricHardwareAvailable(requireContext())
        
        if (hasHardware) {
            binding.rowBiometric.root.visibility = View.VISIBLE
            bindSwitch(
                binding.rowBiometric,
                getString(R.string.biometric_key),
                R.string.biometric_setting,
                R.drawable.ic_fingerprint,
                summary = getString(R.string.biometric_setting_summary)
            ) { newValue ->
                if (newValue) {
                    BiometricAuthenticator.startBiometricAuthentication(
                        requireActivity(),
                        R.string.biometric_authentication_title,
                        this
                    )
                    false // Wait for success callback
                } else {
                    BiometricAuthenticator.setBiometricLoginEnabled(requireContext(), false)
                    true
                }
            }
        } else {
            // Show that biometric is not available on this device
            binding.rowBiometric.root.visibility = View.VISIBLE
            binding.rowBiometric.root.isEnabled = false
            binding.rowBiometric.root.alpha = 0.5f
            binding.rowBiometric.rowTitle.setText(R.string.biometric_setting)
            binding.rowBiometric.rowIcon.setImageResource(R.drawable.ic_fingerprint)
            binding.rowBiometric.rowSummary.visibility = View.VISIBLE
            binding.rowBiometric.rowSummary.setText(R.string.biometric_unsupported)
            binding.rowBiometric.rowSwitch.visibility = View.GONE
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
                val account = DataStoreHelper.getCurrentAccount() ?: DataStoreHelper.getDefaultAccount(context)

                main {
                    val header = binding.accountHeader
                    
                    val displayName = if (account.name == getString(R.string.default_account) && email.isNotEmpty()) {
                        email
                    } else {
                        account.name
                    }
                    header.accountUsername.text = displayName
                    header.accountEmail.text = email
                    
                    header.accountAvatar.loadImage(account.image)
                    
                    val clickListener = View.OnClickListener {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    
                    header.accountAvatar.setOnClickListener(clickListener)
                    header.editAvatarIcon.setOnClickListener(clickListener)
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
        switch.isChecked = settingsManager.getBoolean(key, false)
        
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
        BiometricAuthenticator.setBiometricLoginEnabled(requireContext(), true)
        binding?.rowBiometric?.rowSwitch?.isChecked = true
    }

    override fun onAuthenticationError() {
        binding?.rowBiometric?.rowSwitch?.isChecked = false
    }
}
