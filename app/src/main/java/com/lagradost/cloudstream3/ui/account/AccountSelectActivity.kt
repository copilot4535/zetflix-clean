package com.lagradost.cloudstream3.ui.account

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.ui.auth.ZetFlixAuthPrefs
import com.lagradost.cloudstream3.ui.auth.ZetFlixLoginActivity
import android.content.Intent
import androidx.activity.viewModels
import com.lagradost.cloudstream3.utils.BiometricAuthenticator.BiometricCallback
import com.lagradost.cloudstream3.utils.UIHelper.openActivity

class AccountSelectActivity : FragmentActivity(), BiometricCallback {

    val accountViewModel: AccountViewModel by viewModels()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enforce ZetFlix authentication
        if (!ZetFlixAuthPrefs.isZetFlixAuthenticated(this)) {
            startActivity(Intent(this, ZetFlixLoginActivity::class.java))
            finish()
            return
        }

        // If authenticated, go to MainActivity
        navigateToMainActivity()
    }

    @SuppressLint("UnsafeIntentLaunch")
    private fun navigateToMainActivity() {
        val intent = Intent(this, com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onAuthenticationSuccess() {
    }

    override fun onAuthenticationError() {
        finish()
    }
}
