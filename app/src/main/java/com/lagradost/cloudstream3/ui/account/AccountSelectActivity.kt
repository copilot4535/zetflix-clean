package com.lagradost.cloudstream3.ui.account

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.CommonActivity.loadThemes
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivityAccountSelectBinding
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.ui.auth.ZetFlixLoginActivity
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import android.content.Intent
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_EDIT_ACCOUNT
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.VIEW_TYPE_SELECT_ACCOUNT
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.BiometricCallback
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.biometricPrompt
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.deviceHasPasswordPinLock
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.isAuthEnabled
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.promptInfo
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.startBiometricAuthentication
import com.lagradost.cloudstream3.utils.DataStoreHelper.accounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.selectedKeyIndex
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.openActivity
import com.lagradost.cloudstream3.utils.UIHelper.setNavigationBarColorCompat

class AccountSelectActivity : FragmentActivity(), BiometricCallback {

    companion object {
        var hasLoggedIn: Boolean = false
    }

    val accountViewModel: AccountViewModel by viewModels()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ZetFlixAuthPrefs.isZetFlixAuthenticated(this)) {
            startActivity(Intent(this, ZetFlixLoginActivity::class.java))
            finish()
            return
        }

        ioSafe {
            val valid = ZetFlixSessionManager.isSessionValid(this@AccountSelectActivity)
            if (!valid) {
                main {
                    ZetFlixSessionManager.logout(this@AccountSelectActivity)
                }
                return@ioSafe
            }
        }

        val isEditingFromMainActivity = intent.getBooleanExtra(
            "isEditingFromMainActivity",
            false,
        )

        if (hasLoggedIn && !isEditingFromMainActivity) {
            navigateToMainActivity()
            return
        }

        loadThemes(this)

        enableEdgeToEdgeCompat()
        setNavigationBarColorCompat(R.attr.primaryBlackBackground)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val skipStartup = (settingsManager.getBoolean(
            getString(R.string.skip_startup_account_select_key), false
        )) || (accounts.count() <= 1)

        fun askBiometricAuth() {

            if (isAuthEnabled(this)) {
                if (deviceHasPasswordPinLock(this)) {
                    startBiometricAuthentication(
                        this,
                        R.string.biometric_authentication_title,
                        setDeviceCred = false
                    )

                    promptInfo?.let { prompt ->
                        biometricPrompt?.authenticate(prompt)
                    }
                }
            }
        }

        observe(accountViewModel.isAllowedLogin) { isAllowedLogin ->
            if (isAllowedLogin) {
                navigateToMainActivity()
            }
        }

        if (!isEditingFromMainActivity && skipStartup) {
            val currentAccount = accounts.firstOrNull { it.keyIndex == selectedKeyIndex }
            if (currentAccount?.lockPin != null) {
                CommonActivity.init(this)
                accountViewModel.handleAccountSelect(currentAccount, this, true)
            } else {
                if (accounts.count() > 1) {
                    showToast(
                        this, getString(
                            R.string.logged_account,
                            currentAccount?.name
                        )
                    )
                }

                navigateToMainActivity()
            }

            return
        }

        CommonActivity.init(this)

        val binding = ActivityAccountSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fixSystemBarsPadding(binding.root, padTop = false)

        val recyclerView: AutofitRecyclerView = binding.accountRecyclerView

        observe(accountViewModel.accounts) { liveAccounts ->
            val adapter = AccountAdapter(
                accountSelectCallback = {
                    accountViewModel.handleAccountSelect(it, this)
                },
                accountCreateCallback = { accountViewModel.handleAccountUpdate(it, this) },
                accountEditCallback = {
                    accountViewModel.handleAccountUpdate(it, this)
                    if (isEditingFromMainActivity) {
                        setAccount(it)
                        navigateToMainActivity()
                    }
                },
                accountDeleteCallback = { accountViewModel.handleAccountDelete(it, this) }
            ).apply {
                submitList(liveAccounts)
            }

            recyclerView.adapter = adapter

            observe(accountViewModel.selectedKeyIndex) { selectedKeyIndex ->
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                layoutManager.scrollToPositionWithOffset(selectedKeyIndex, 0)
            }

            observe(accountViewModel.isEditing) { isEditing ->
                if (isEditing) {
                    binding.editAccountButton.setImageResource(R.drawable.ic_baseline_close_24)
                    binding.title.setText(R.string.manage_accounts)
                    adapter.viewType = VIEW_TYPE_EDIT_ACCOUNT
                } else {
                    binding.editAccountButton.setImageResource(R.drawable.ic_baseline_edit_24)
                    binding.title.setText(R.string.select_an_account)
                    adapter.viewType = VIEW_TYPE_SELECT_ACCOUNT
                }

                adapter.notifyDataSetChanged()
            }

            if (isEditingFromMainActivity) {
                accountViewModel.setIsEditing(true)
            }

            binding.editAccountButton.setOnClickListener {
                if (isEditingFromMainActivity) {
                    navigateToMainActivity()
                    return@setOnClickListener
                }

                accountViewModel.toggleIsEditing()
            }
        }

        askBiometricAuth()
    }

    @SuppressLint("UnsafeIntentLaunch")
    private fun navigateToMainActivity() {
        hasLoggedIn = true
        openActivity(MainActivity::class.java, baseIntent = intent)
        finish()
    }

    override fun onAuthenticationSuccess() {
        Log.i(BiometricAuthenticator.TAG, "Authentication successful in AccountSelectActivity")
    }

    override fun onAuthenticationError() {
        finish()
    }
}
