package com.lagradost.cloudstream3.ui.auth

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager
import com.lagradost.cloudstream3.utils.ZetFlixCryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ZetFlixLoginActivity : AppCompatActivity(), BiometricAuthenticator.BiometricCallback {

    private data class Country(val flag: String, val name: String, val code: String, val length: Int) {
        override fun toString(): String = "$flag $code"
    }

    private val countries = listOf(
        Country("🇳🇵", "Nepal", "+977", 10),
        Country("🇮🇳", "India", "+91", 10),
        Country("🇧🇩", "Bangladesh", "+880", 10),
        Country("🇱🇰", "Sri Lanka", "+94", 9),
        Country("🇵🇰", "Pakistan", "+92", 10),
        Country("🇲🇻", "Maldives", "+960", 7),
        Country("🇧🇹", "Bhutan", "+975", 8),
        Country("🇦🇫", "Afghanistan", "+93", 9)
    )

    private var isRegisterMode = false
    private var isBiometricSetupMode = false

    private val phoneHintLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                val phoneNumber = Identity.getSignInClient(this)
                    .getPhoneNumberFromIntent(result.data!!)
                findViewById<EditText>(R.id.phone_number_edit).setText(phoneNumber)
            } catch (e: Exception) {
                // Fallback: user enters manually
            }
        }
    }

    private fun requestPhoneHint() {
        val request = GetPhoneNumberHintIntentRequest.builder().build()
        Identity.getSignInClient(this)
            .getPhoneNumberHintIntent(request)
            .addOnSuccessListener { pendingIntent ->
                try {
                    phoneHintLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } catch (e: Exception) {
                    // Fallback: manual entry
                }
            }
            .addOnFailureListener {
                // Fallback: manual entry
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zetflix_login)

        val countrySpinner = findViewById<Spinner>(R.id.country_code_spinner)
        val phoneEdit = findViewById<EditText>(R.id.phone_number_edit)
        val emailEdit = findViewById<EditText>(R.id.email_edit)
        val identifierEdit = findViewById<EditText>(R.id.identifier_edit)
        val passwordEdit = findViewById<EditText>(R.id.password_edit)
        val passwordToggle = findViewById<ImageView>(R.id.password_visibility_toggle)
        val confirmPasswordLayout = findViewById<View>(R.id.confirm_password_layout)
        val confirmPasswordEdit = findViewById<EditText>(R.id.confirm_password_edit)
        val confirmPasswordToggle = findViewById<ImageView>(R.id.confirm_password_visibility_toggle)
        val privacyCheckbox = findViewById<CheckBox>(R.id.privacy_checkbox)
        val loginButton = findViewById<Button>(R.id.login_button)
        val modeSwitchLink = findViewById<TextView>(R.id.mode_switch_link)
        val fingerprintLoginButton = findViewById<Button>(R.id.fingerprint_login_button)
        val phoneLayout = findViewById<View>(R.id.phone_layout)
        val emailLayout = findViewById<View>(R.id.email_layout)
        val identifierLayout = findViewById<View>(R.id.identifier_layout)

        setupPasswordToggle(passwordEdit, passwordToggle)
        setupPasswordToggle(confirmPasswordEdit, confirmPasswordToggle)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countries)
        countrySpinner.adapter = adapter

        phoneEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && isRegisterMode) {
                requestPhoneHint()
            }
        }

        fun updateUI() {
            if (isRegisterMode) {
                loginButton.setText(R.string.zetflix_register_button)
                modeSwitchLink.setText(R.string.zetflix_login_toggle)
                confirmPasswordLayout?.visibility = View.VISIBLE
                privacyCheckbox?.visibility = View.VISIBLE
                phoneLayout?.visibility = View.VISIBLE
                emailLayout?.visibility = View.VISIBLE
                identifierLayout?.visibility = View.GONE
                fingerprintLoginButton.visibility = View.GONE
            } else {
                loginButton.setText(R.string.zetflix_login_button)
                modeSwitchLink.setText(R.string.zetflix_register_toggle)
                confirmPasswordLayout?.visibility = View.GONE
                privacyCheckbox?.visibility = View.GONE
                phoneLayout?.visibility = View.GONE
                emailLayout?.visibility = View.GONE
                identifierLayout?.visibility = View.VISIBLE
                fingerprintLoginButton.visibility = if (BiometricAuthenticator.isAuthEnabled(this)) View.VISIBLE else View.GONE
            }
        }

        updateUI()

        if (BiometricAuthenticator.isAuthEnabled(this)) {
            fingerprintLoginButton.setOnClickListener {
                isBiometricSetupMode = false
                BiometricAuthenticator.startBiometricAuthentication(
                    this,
                    R.string.biometric_authentication_title,
                    false
                )
            }
        }

        modeSwitchLink.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUI()
        }

        loginButton.setOnClickListener {
            val country = countrySpinner.selectedItem as Country
            val phone = phoneEdit.text.toString()
            val emailRaw = emailEdit.text.toString()
            val identifierRaw = identifierEdit.text.toString()
            val password = passwordEdit.text.toString()
            val confirmPassword = confirmPasswordEdit.text.toString()

            if (isRegisterMode) {
                if (phone.trim().isEmpty()) {
                    Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (emailRaw.trim().isEmpty() || !emailRaw.trim().lowercase().endsWith("@gmail.com")) {
                    Toast.makeText(this, R.string.zetflix_email_error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (password.length < 8) {
                    Toast.makeText(this, R.string.zetflix_password_error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (password != confirmPassword) {
                    Toast.makeText(this, R.string.zetflix_password_mismatch, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!privacyCheckbox.isChecked) {
                    Toast.makeText(this, "Please agree to the privacy policy", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else {
                if (identifierRaw.trim().isEmpty()) {
                    Toast.makeText(this, "Please enter email or phone number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password.length < 8) {
                    Toast.makeText(this, R.string.zetflix_password_error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val prefs = ZetFlixCryptoUtils.getEncryptedPrefs(this@ZetFlixLoginActivity)
                    val storedEmail = prefs.getString("email", null)
                    val storedPhone = prefs.getString("phoneNationalNumber", null)
                    val storedCountryCode = prefs.getString("phoneCountryCode", null)
                    val storedPassword = prefs.getString("password", null)

                    withContext(Dispatchers.Main) {
                        if (isRegisterMode) {
                            val normalizedEmail = emailRaw.trim().lowercase()
                            if (storedEmail != null && storedEmail.equals(normalizedEmail, ignoreCase = true)) {
                                Toast.makeText(this@ZetFlixLoginActivity, "Account already exists. Please login.", Toast.LENGTH_SHORT).show()
                                return@withContext
                            }

                            lifecycleScope.launch(Dispatchers.IO) {
                                val normalizedPhone = phone.filter { it.isDigit() }
                                saveAuthData(prefs, country.code, normalizedPhone, normalizedEmail, password)
                                
                                Log.d("ZetFlixAuthDebug", "Registration successful")
                                Log.d("ZetFlixAuthDebug", "Stored Email: $normalizedEmail")
                                Log.d("ZetFlixAuthDebug", "Stored Phone Country Code: ${country.code}")
                                Log.d("ZetFlixAuthDebug", "Stored Phone National Number: $normalizedPhone")
                                Log.d("ZetFlixAuthDebug", "Stored Password Length: ${password.length}")

                                ZetFlixAuthPrefs.setZetFlixAuthenticated(this@ZetFlixLoginActivity, true)
                                ZetFlixSessionManager.setLoginTimestamp(this@ZetFlixLoginActivity)
                                
                                withContext(Dispatchers.Main) {
                                    setKey("HAS_DONE_SETUP", true)
                                    if (BiometricAuthenticator.canSetupBiometrics(this@ZetFlixLoginActivity)) {
                                        isBiometricSetupMode = true
                                        BiometricSetupDialog.show(
                                            this@ZetFlixLoginActivity,
                                            onEnable = {
                                                BiometricAuthenticator.startBiometricAuthentication(
                                                    this@ZetFlixLoginActivity,
                                                    R.string.fingerprint_setup_title,
                                                    false
                                                )
                                            },
                                            onSkip = {
                                                navigateToLoading()
                                            }
                                        )
                                    } else {
                                        navigateToLoading()
                                    }
                                }
                            }
                        } else {
                            // Login Mode
                            var isMatch = false
                            
                            val inputIdentifier = identifierRaw.trim()
                            val inputPassword = password

                            Log.d("ZetFlixAuthDebug", "Login Attempt")
                            Log.d("ZetFlixAuthDebug", "Entered Identifier: $inputIdentifier")
                            
                            val normalizedStoredEmail = storedEmail?.trim()?.lowercase()
                            val normalizedStoredPhone = storedPhone?.filter { it.isDigit() } ?: ""
                            val normalizedStoredCountryCode = storedCountryCode?.filter { it.isDigit() } ?: ""

                            if (inputIdentifier.contains("@")) {
                                Log.d("ZetFlixAuthDebug", "Classification: Email")
                                val normalizedInputEmail = inputIdentifier.lowercase()
                                Log.d("ZetFlixAuthDebug", "Normalized Identifier: $normalizedInputEmail")
                                if (normalizedStoredEmail != null && normalizedInputEmail == normalizedStoredEmail) {
                                    isMatch = true
                                }
                                Log.d("ZetFlixAuthDebug", "Email Match: $isMatch")
                            } else {
                                Log.d("ZetFlixAuthDebug", "Classification: Phone")
                                val normalizedInputPhone = inputIdentifier.filter { it.isDigit() }
                                Log.d("ZetFlixAuthDebug", "Normalized Identifier: $normalizedInputPhone")
                                
                                if (normalizedStoredPhone.isNotEmpty()) {
                                    val matchA = normalizedInputPhone == normalizedStoredPhone
                                    val matchB = normalizedInputPhone == (normalizedStoredCountryCode + normalizedStoredPhone)
                                    
                                    // Handle leading zeros
                                    val inputNoLeadingZero = normalizedInputPhone.removePrefix("0")
                                    val storedNoLeadingZero = normalizedStoredPhone.removePrefix("0")
                                    val matchC = inputNoLeadingZero == storedNoLeadingZero
                                    
                                    isMatch = matchA || matchB || matchC
                                }
                                Log.d("ZetFlixAuthDebug", "Phone Match: $isMatch")
                            }

                            Log.d("ZetFlixAuthDebug", "Stored Email: $normalizedStoredEmail")
                            Log.d("ZetFlixAuthDebug", "Stored Phone: $normalizedStoredPhone")
                            
                            val passwordMatch = storedPassword == inputPassword
                            Log.d("ZetFlixAuthDebug", "Entered Password Length: ${inputPassword.length}")
                            Log.d("ZetFlixAuthDebug", "Password Match: $passwordMatch")

                            if (isMatch && passwordMatch) {
                                Log.d("ZetFlixAuthDebug", "Login Result: Success")
                                ZetFlixAuthPrefs.setZetFlixAuthenticated(this@ZetFlixLoginActivity, true)
                                ZetFlixSessionManager.setLoginTimestamp(this@ZetFlixLoginActivity)
                                setKey("HAS_DONE_SETUP", true)
                                navigateToLoading()
                            } else {
                                Log.d("ZetFlixAuthDebug", "Login Result: Failure")
                                Toast.makeText(this@ZetFlixLoginActivity, "Invalid email/phone or password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ZetFlixAuthDebug", "Error during login/register", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ZetFlixLoginActivity, "Error accessing secure storage", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onAuthenticationSuccess() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (isBiometricSetupMode) {
                PreferenceManager.getDefaultSharedPreferences(this@ZetFlixLoginActivity)
                    .edit()
                    .putBoolean(getString(R.string.biometric_key), true)
                    .apply()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ZetFlixLoginActivity, R.string.fingerprint_setup_success, Toast.LENGTH_SHORT).show()
                    navigateToLoading()
                }
            } else {
                ZetFlixSessionManager.setLoginTimestamp(this@ZetFlixLoginActivity)
                withContext(Dispatchers.Main) {
                    navigateToLoading()
                }
            }
        }
    }

    override fun onAuthenticationError() {
        if (isBiometricSetupMode) {
            Toast.makeText(this, R.string.fingerprint_setup_failed, Toast.LENGTH_SHORT).show()
            navigateToLoading()
        } else {
            Toast.makeText(this, R.string.fingerprint_login_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToLoading() {
        startActivity(Intent(this, ZetFlixLoadingActivity::class.java))
        finish()
    }

    private fun saveAuthData(prefs: SharedPreferences, countryCode: String, phone: String, email: String, password: String) {
        try {
            val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString()
            val deviceSecret = prefs.getString("device_secret", null) ?: (UUID.randomUUID().toString() + UUID.randomUUID().toString())

            prefs.edit().apply {
                putString("phoneCountryCode", countryCode)
                putString("phoneNationalNumber", phone)
                putString("email", email)
                putString("password", password)
                putString("device_id", deviceId)
                putString("device_secret", deviceSecret)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
