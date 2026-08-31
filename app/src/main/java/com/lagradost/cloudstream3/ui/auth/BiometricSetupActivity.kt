package com.lagradost.cloudstream3.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.BiometricAuthenticator

class BiometricSetupActivity : AppCompatActivity(), BiometricAuthenticator.BiometricCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        
        if (!BiometricAuthenticator.isBiometricHardwareAvailable(this)) {
            navigateToMain()
            return
        }
        
        setContentView(R.layout.activity_biometric_setup)

        findViewById<Button>(R.id.enable_button).setOnClickListener {
            BiometricAuthenticator.startBiometricAuthentication(
                this,
                R.string.biometric_authentication_title,
                this
            )
        }

        findViewById<Button>(R.id.skip_button).setOnClickListener {
            navigateToMain()
        }
    }

    override fun onAuthenticationSuccess() {
        BiometricAuthenticator.setBiometricLoginEnabled(this, true)
        Toast.makeText(this, R.string.fingerprint_setup_success, Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    override fun onAuthenticationError() {
        // Just let them stay on this screen or skip
    }

    private fun navigateToMain() {
        val intent = Intent(this, com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity::class.java)
        intent.putExtra(com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity.EXTRA_FIRST_SETUP, true)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
