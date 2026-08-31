package com.lagradost.cloudstream3.ui.auth

import android.app.Activity
import android.content.Intent
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
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.BiometricAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZetFlixRegisterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ZetFlixAuthDebug"
    }

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

    private val phoneHintLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                val phoneNumber = Identity.getSignInClient(this)
                    .getPhoneNumberFromIntent(result.data!!)
                var filteredPhone = phoneNumber.filter { it.isDigit() }
                
                for (country in countries) {
                    val codeDigits = country.code.filter { it.isDigit() }
                    if (filteredPhone.startsWith(codeDigits)) {
                        filteredPhone = filteredPhone.substring(codeDigits.length)
                        val spinner = findViewById<Spinner>(R.id.country_code_spinner)
                        val index = countries.indexOf(country)
                        if (index != -1) spinner.setSelection(index)
                        break
                    }
                }
                
                findViewById<EditText>(R.id.phone_number_edit).setText(filteredPhone)
            } catch (e: Exception) {
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
                }
            }
            .addOnFailureListener {
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zetflix_register)

        val countrySpinner = findViewById<Spinner>(R.id.country_code_spinner)
        val phoneEdit = findViewById<EditText>(R.id.phone_number_edit)
        val emailEdit = findViewById<EditText>(R.id.email_edit)
        val passwordEdit = findViewById<EditText>(R.id.password_edit)
        val passwordToggle = findViewById<ImageView>(R.id.password_visibility_toggle)
        val confirmPasswordEdit = findViewById<EditText>(R.id.confirm_password_edit)
        val confirmPasswordToggle = findViewById<ImageView>(R.id.confirm_password_visibility_toggle)
        val privacyCheckbox = findViewById<CheckBox>(R.id.privacy_checkbox)
        val registerButton = findViewById<Button>(R.id.register_button)
        val loginLink = findViewById<TextView>(R.id.login_link)

        setupPasswordToggle(passwordEdit, passwordToggle)
        setupPasswordToggle(confirmPasswordEdit, confirmPasswordToggle)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countries)
        countrySpinner.adapter = adapter

        phoneEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && phoneEdit.text.isEmpty()) {
                requestPhoneHint()
            }
        }

        loginLink.setOnClickListener {
            finish()
        }

        registerButton.setOnClickListener {
            val country = countrySpinner.selectedItem as Country
            val phoneRaw = phoneEdit.text.toString()
            val emailRaw = emailEdit.text.toString()
            val password = passwordEdit.text.toString().trim()
            val confirmPassword = confirmPasswordEdit.text.toString().trim()

            if (phoneRaw.trim().isEmpty()) {
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

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val normalizedEmail = emailRaw.trim().lowercase()
                    val existingEmail = ZetFlixAuthPrefs.getStoredEmail(this@ZetFlixRegisterActivity)
                    
                    if (existingEmail != null && existingEmail.equals(normalizedEmail, ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ZetFlixRegisterActivity, "Account already exists. Please login.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    val digitsOnly = phoneRaw.filter { it.isDigit() }
                    val countryDigits = country.code.filter { it.isDigit() }
                    val nationalNumber = if (digitsOnly.startsWith(countryDigits) && countryDigits.isNotEmpty()) {
                        digitsOnly.removePrefix(countryDigits)
                    } else {
                        digitsOnly
                    }

                    ZetFlixAuthPrefs.saveCredentials(
                        this@ZetFlixRegisterActivity,
                        normalizedEmail,
                        country.code,
                        nationalNumber,
                        password
                    )

                    ZetFlixAuthPrefs.setZetFlixAuthenticated(this@ZetFlixRegisterActivity, true)

                    withContext(Dispatchers.Main) {
                        setKey("HAS_DONE_SETUP", true)
                        if (BiometricAuthenticator.isBiometricHardwareAvailable(this@ZetFlixRegisterActivity)) {
                            startActivity(Intent(this@ZetFlixRegisterActivity, BiometricSetupActivity::class.java))
                            finish()
                        } else {
                            navigateToMain()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during registration", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ZetFlixRegisterActivity, "Error saving credentials", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity::class.java)
        intent.putExtra(com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity.EXTRA_FIRST_SETUP, true)
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
