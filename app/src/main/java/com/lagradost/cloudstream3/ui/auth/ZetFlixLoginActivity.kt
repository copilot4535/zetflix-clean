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
                    Log.d(TAG, "Login Attempt initiated. Using file: ${ZetFlixAuthPrefs.PREFS_FILE}")
                    
                    val storedEmail = ZetFlixAuthPrefs.getStoredEmail(this@ZetFlixLoginActivity)
                    val storedPhone = ZetFlixAuthPrefs.getStoredPhoneNationalNumber(this@ZetFlixLoginActivity)
                    val storedCountryCode = ZetFlixAuthPrefs.getStoredPhoneCountryCode(this@ZetFlixLoginActivity)
                    val storedPassword = ZetFlixAuthPrefs.getStoredPassword(this@ZetFlixLoginActivity)

                    val identifierType: String
                    val normalizedIdentifier: String

                    Log.d(TAG, "Entered Identifier: $identifierRaw")

                    if (identifierRaw.contains("@")) {
                        identifierType = "Email"
                        normalizedIdentifier = identifierRaw.lowercase()
                    } else {
                        identifierType = "Phone"
                        normalizedIdentifier = identifierRaw.filter { it.isDigit() }
                    }

                    Log.d(TAG, "Identifier Type: $identifierType")
                    Log.d(TAG, "Normalized Identifier: $normalizedIdentifier")
                    Log.d(TAG, "Stored Email: $storedEmail")
                    Log.d(TAG, "Stored Phone: $storedPhone")
                    Log.d(TAG, "Stored Country Code: $storedCountryCode")

                    var emailMatch: Boolean = false
                    var phoneMatch: Boolean = false

                    if (identifierType == "Email") {
                        emailMatch = storedEmail != null && normalizedIdentifier == storedEmail.lowercase()
                        Log.d(TAG, "Email Match Result: $emailMatch")
                    } else {
                        val storedNational: String = storedPhone?.filter { it.isDigit() } ?: ""
                        val storedCountryDigits: String = storedCountryCode?.filter { it.isDigit() } ?: ""

                        val inputWithoutLeadingZero: String = normalizedIdentifier.removePrefix("0")
                        val storedWithoutLeadingZero: String = storedNational.removePrefix("0")

                        phoneMatch = (
                                normalizedIdentifier == storedNational ||
                                        normalizedIdentifier == (storedCountryDigits + storedNational) ||
                                        (inputWithoutLeadingZero.isNotEmpty() && inputWithoutLeadingZero == storedWithoutLeadingZero)
                                )
                        Log.d(TAG, "Phone Match Result: $phoneMatch")
                    }

                    val passwordMatch: Boolean = storedPassword != null && passwordInput == storedPassword.trim()
                    Log.d(TAG, "Password Match Result: $passwordMatch")
                    
                    val loginSuccess: Boolean = (emailMatch || phoneMatch) && passwordMatch
                    Log.d(TAG, "Final Login Result: $loginSuccess")

                    if (loginSuccess) {
                        Log.d(TAG, "Login Result: Success")
                        ZetFlixAuthPrefs.setZetFlixAuthenticated(this@ZetFlixLoginActivity, true)
                        withContext(Dispatchers.Main) {
                            setKey("HAS_DONE_SETUP", true)
                            navigateToLoading()
                        }
                    } else {
                        Log.d(TAG, "Login Result: Failure")
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
    }

    override fun onAuthenticationSuccess() {
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
