package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager
import com.lagradost.cloudstream3.utils.ZetFlixCryptoUtils
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlin.math.absoluteValue

class SettingsAccount : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_zetflix_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.zetflix_account_title)
        loadUserData(view)
        setupLogout(view)
    }

    private fun loadUserData(view: View) {
        val context = context ?: return
        ioSafe {
            try {
                val prefs = ZetFlixCryptoUtils.getEncryptedPrefs(context)
                val email = prefs.getString("email", "") ?: ""
                val countryCode = prefs.getString("phoneCountryCode", "") ?: ""
                val phone = prefs.getString("phoneNationalNumber", "") ?: ""

                val username = if (email.isNotEmpty()) email.substringBefore("@") else ""

                val maskedPhone = if (phone.length >= 4) {
                    val first2 = phone.take(2)
                    val last4 = phone.takeLast(4)
                    "$countryCode $first2****$last4"
                } else {
                    "$countryCode $phone"
                }

                val isBiometricEnabled = BiometricAuthenticator.isAuthEnabled(context)

                main {
                    view.findViewById<TextView>(R.id.account_username).text = username
                    view.findViewById<TextView>(R.id.account_email_sub).text = email
                    view.findViewById<TextView>(R.id.account_email).text = email
                    view.findViewById<TextView>(R.id.account_phone).text = maskedPhone
                    view.findViewById<TextView>(R.id.account_fingerprint_status).text =
                        if (isBiometricEnabled) {
                            getString(R.string.zetflix_account_fingerprint_enabled)
                        } else {
                            getString(R.string.zetflix_account_fingerprint_disabled)
                        }

                    // Avatar logic: monogram for now
                    val avatarView = view.findViewById<ImageView>(R.id.account_avatar)
                    val backgrounds = listOf(
                        R.drawable.profile_bg_blue,
                        R.drawable.profile_bg_dark_blue,
                        R.drawable.profile_bg_orange,
                        R.drawable.profile_bg_pink,
                        R.drawable.profile_bg_purple,
                        R.drawable.profile_bg_red,
                        R.drawable.profile_bg_teal
                    )
                    val bgIndex =
                        if (username.isNotEmpty()) username.hashCode().absoluteValue % backgrounds.size else 0
                    avatarView.setBackgroundResource(backgrounds[bgIndex])
                    avatarView.setImageResource(R.drawable.ic_outline_account_circle_24)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupLogout(view: View) {
        view.findViewById<Button>(R.id.logout_button).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_confirmation_title)
                .setMessage(R.string.logout_confirmation_message)
                .setPositiveButton(R.string.logout_button) { _, _ ->
                    ZetFlixSessionManager.logout(requireContext())
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
