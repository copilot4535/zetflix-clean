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
import com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZetFlixLoginActivity : AppCompatActivity(), BiometricAuthenticator.BiometricCallback {

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
        val fingerprintLoginButton = findViewById<Button>(R.id.fingerprint_login_button)

        setupPasswordToggle(passwordEdit, passwordToggle)

        val isFingerprintEnabled = BiometricAuthenticator.isFingerprintEnabled(this)
        fingerprintLoginButton.visibility = if (isFingerprintEnabled) View.VISIBLE else View.GONE

        if (isFingerprintEnabled) {
            fingerprintLoginButton.setOnClickListener {
                BiometricAuthenticator.startBiometricAuthentication(
                    this,
                    R.string.biometric_authentication_title,
                    false
                )
            }
        }

        modeSwitchLink.setOnClickListener {
            startActivity(Intent(this, ZetFlixRegisterActivity::class.java))
        }

        googleSigninLink.setOnClickListener {
            Toast.makeText(this, "Not available, sign in with email", Toast.LENGTH_SHORT).show()
        }

        loginButton.setOnClickListener {
            val identifierRaw = identifierEdit.text.toString().trim()
            val passwordInput = passwordEdit.text.toString().trim()

            if (identifierRaw.isEmpty()) {
                Toast.makeText(this, "Please enter email or phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (passwordInput.length < 8) {
                Toast.makeText(this, R.string.zetflix_password_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val storedEmail = ZetFlixAuthPrefs.getStoredEmail(this@ZetFlixLoginActivity)
                    val storedPhone = ZetFlixAuthPrefs.getStoredPhoneNationalNumber(this@ZetFlixLoginActivity)
                    val storedCountryCode = ZetFlixAuthPrefs.getStoredPhoneCountryCode(this@ZetFlixLoginActivity)
                    val storedPassword = ZetFlixAuthPrefs.getStoredPassword(this@ZetFlixLoginActivity)

                    val classification: String
                    val normalizedIdentifier: String

                    Log.d("ZetFlixAuthDebug", "Login Attempt")
                    Log.d("ZetFlixAuthDebug", "Entered Identifier: $identifierRaw")

                    if (identifierRaw.contains("@")) {
                        classification = "email"
                        normalizedIdentifier = identifierRaw.trim().lowercase()
                    } else {
                        classification = "phone"
                        normalizedIdentifier = identifierRaw.filter { it.isDigit() }
                    }

                    Log.d("ZetFlixAuthDebug", "Identifier type: $classification")
                    Log.d("ZetFlixAuthDebug", "Normalized identifier: $normalizedIdentifier")
                    Log.d("ZetFlixAuthDebug", "Stored email: $storedEmail")
                    Log.d("ZetFlixAuthDebug", "Stored country code: $storedCountryCode")
                    Log.d("ZetFlixAuthDebug", "Stored national number: $storedPhone")

                    var emailMatch = false
                    var phoneMatch = false

                    if (classification == "email") {
                        emailMatch = storedEmail != null && normalizedIdentifier == storedEmail.lowercase()
                        Log.d("ZetFlixAuthDebug", "Email match result: $emailMatch")
                    } else {
                        val storedNational = storedPhone?.filter { it.isDigit() } ?: ""
                        val storedCountryDigits = storedCountryCode?.filter { it.isDigit() } ?: ""

                        val inputWithoutLeadingZero = normalizedIdentifier.removePrefix("0")
                        val storedWithoutLeadingZero = storedNational.removePrefix("0")

                        phoneMatch = (
                                normalizedIdentifier == storedNational ||
                                        normalizedIdentifier == (storedCountryDigits + storedNational) ||
                                        (inputWithoutLeadingZero.isNotEmpty() && inputWithoutLeadingZero == storedWithoutLeadingZero)
                                )
                        Log.d("ZetFlixAuthDebug", "Phone match result: $phoneMatch")
                    }

                    val passwordMatch = storedPassword != null && passwordInput == storedPassword.trim()
                    Log.d("ZetFlixAuthDebug", "Password match result: $passwordMatch")
                    
                    val loginSuccess = (emailMatch || phoneMatch) && passwordMatch
                    Log.d("ZetFlixAuthDebug", "Final login result: $loginSuccess")

                    if (loginSuccess) {
                        Log.d("ZetFlixAuthDebug", "Login Result: Success")
                        ZetFlixAuthPrefs.setZetFlixAuthenticated(this@ZetFlixLoginActivity, true)
                        ZetFlixSessionManager.setLoginTimestamp(this@ZetFlixLoginActivity)
                        withContext(Dispatchers.Main) {
                            setKey("HAS_DONE_SETUP", true)
                            navigateToLoading()
                        }
                    } else {
                        Log.d("ZetFlixAuthDebug", "Login Result: Failure")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ZetFlixLoginActivity, "Invalid email/phone or password", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ZetFlixAuthDebug", "Error during login", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ZetFlixLoginActivity, "Error accessing secure storage", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onAuthenticationSuccess() {
        ZetFlixSessionManager.setLoginTimestamp(this)
        navigateToLoading()
    }

    override fun onAuthenticationError() {
        Toast.makeText(this, R.string.fingerprint_login_failed, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToLoading() {
        val intent = Intent(this, ZetFlixLoadingActivity::class.java)
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
