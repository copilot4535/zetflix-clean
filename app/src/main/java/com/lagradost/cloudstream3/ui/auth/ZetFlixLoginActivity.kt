package com.lagradost.cloudstream3.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZetFlixLoginActivity : AppCompatActivity(), BiometricAuthenticator.BiometricCallback {

    companion object {
        private const val TAG = "ZetFlixAuthDebug"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zetflix_login)

        val identifierEdit = findViewById<EditText>(R.id.identifier_edit)
        val passwordEdit = findViewById<EditText>(R.id.password_edit)
        val passwordToggle = findViewById<ImageView>(R.id.password_visibility_toggle)
        val loginButton = findViewById<Button>(R.id.login_button)
        val modeSwitchLink = findViewById<TextView>(R.id.mode_switch_link)
        val googleSigninLink = findViewById<TextView>(R.id.google_signin_link)
        val biometricLoginButton = findViewById<Button>(R.id.fingerprint_login_button)

        setupPasswordToggle(passwordEdit, passwordToggle)

        val isBiometricEnabled = BiometricAuthenticator.isBiometricLoginEnabled(this)
        val hasHardware = BiometricAuthenticator.isBiometricHardwareAvailable(this)
        
        biometricLoginButton.visibility = if (isBiometricEnabled && hasHardware) View.VISIBLE else View.GONE

        biometricLoginButton.setOnClickListener {
            BiometricAuthenticator.startBiometricAuthentication(
                this,
                R.string.biometric_authentication_title,
                this
            )
        }

        modeSwitchLink.setOnClickListener {
            startActivity(Intent(this, ZetFlixRegisterActivity::class.java))
        }

        googleSigninLink.setOnClickListener {
            Toast.makeText(this, "Not available, sign in with email", Toast.LENGTH_SHORT).show()
        }

        loginButton.setOnClickListener {
            performEmailLogin(identifierEdit.text.toString().trim(), passwordEdit.text.toString().trim())
        }
    }

    private fun performEmailLogin(identifierRaw: String, passwordInput: String) {
        if (identifierRaw.isEmpty()) {
            Toast.makeText(this, "Please enter email or phone number", Toast.LENGTH_SHORT).show()
            return
        }
        if (passwordInput.length < 8) {
            Toast.makeText(this, R.string.zetflix_password_error, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val storedEmail = ZetFlixAuthPrefs.getStoredEmail(this@ZetFlixLoginActivity)
                val storedPhone = ZetFlixAuthPrefs.getStoredPhoneNationalNumber(this@ZetFlixLoginActivity)
                val storedCountryCode = ZetFlixAuthPrefs.getStoredPhoneCountryCode(this@ZetFlixLoginActivity)
                val storedPassword = ZetFlixAuthPrefs.getStoredPassword(this@ZetFlixLoginActivity)

                val identifierType = if (identifierRaw.contains("@")) "Email" else "Phone"
                val normalizedIdentifier = if (identifierType == "Email") identifierRaw.lowercase() else identifierRaw.filter { it.isDigit() }

                var emailMatch = false
                var phoneMatch = false

                if (identifierType == "Email") {
                    emailMatch = storedEmail != null && normalizedIdentifier == storedEmail.lowercase()
                } else {
                    val storedNational = storedPhone?.filter { it.isDigit() } ?: ""
                    val storedCountryDigits = storedCountryCode?.filter { it.isDigit() } ?: ""
                    val inputWithoutLeadingZero = normalizedIdentifier.removePrefix("0")
                    val storedWithoutLeadingZero = storedNational.removePrefix("0")

                    phoneMatch = (normalizedIdentifier == storedNational ||
                                  normalizedIdentifier == (storedCountryDigits + storedNational) ||
                                  (inputWithoutLeadingZero.isNotEmpty() && inputWithoutLeadingZero == storedWithoutLeadingZero))
                }

                val passwordMatch = storedPassword != null && passwordInput == storedPassword.trim()
                
                if ((emailMatch || phoneMatch) && passwordMatch) {
                    onLoginSuccess()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ZetFlixLoginActivity, "Invalid email/phone or password", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during login", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ZetFlixLoginActivity, "Error accessing secure storage", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun onLoginSuccess() {
        ZetFlixAuthPrefs.setZetFlixAuthenticated(this@ZetFlixLoginActivity, true)
        withContext(Dispatchers.Main) {
            setKey("HAS_DONE_SETUP", true)
            navigateToMain()
        }
    }

    override fun onAuthenticationSuccess() {
        // Log in directly using stored credentials
        lifecycleScope.launch(Dispatchers.IO) {
            val storedEmail = ZetFlixAuthPrefs.getStoredEmail(this@ZetFlixLoginActivity)
            val storedPassword = ZetFlixAuthPrefs.getStoredPassword(this@ZetFlixLoginActivity)
            
            if (storedEmail != null && storedPassword != null) {
                onLoginSuccess()
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ZetFlixLoginActivity, "No stored credentials found. Please login manually once.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onAuthenticationError() {
        // Error toast already shown in Authenticator
    }

    private fun navigateToMain() {
        val intent = Intent(this, com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupPasswordToggle(editText: EditText, toggle: ImageView) {
        var isVisible = false
        toggle.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggle.setImageResource(R.drawable.ic_baseline_visibility_24)
            } else {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                toggle.setImageResource(R.drawable.ic_baseline_visibility_off_24)
            }
            editText.setSelection(editText.text.length)
        }
    }
}
