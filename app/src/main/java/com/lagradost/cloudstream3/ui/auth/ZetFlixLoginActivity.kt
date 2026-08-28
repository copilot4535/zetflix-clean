package com.lagradost.cloudstream3.ui.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ZetFlixSessionManager
import java.util.*

class ZetFlixLoginActivity : AppCompatActivity() {

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
        val passwordEdit = findViewById<EditText>(R.id.password_edit)
        val passwordToggle = findViewById<ImageView>(R.id.password_visibility_toggle)
        val confirmPasswordLayout = findViewById<View>(R.id.confirm_password_layout)
        val confirmPasswordEdit = findViewById<EditText>(R.id.confirm_password_edit)
        val confirmPasswordToggle = findViewById<ImageView>(R.id.confirm_password_visibility_toggle)
        val privacyCheckbox = findViewById<CheckBox>(R.id.privacy_checkbox)
        val loginButton = findViewById<Button>(R.id.login_button)
        val modeSwitchLink = findViewById<TextView>(R.id.mode_switch_link)

        setupPasswordToggle(passwordEdit, passwordToggle)
        setupPasswordToggle(confirmPasswordEdit, confirmPasswordToggle)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, countries)
        countrySpinner.adapter = adapter

        phoneEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                requestPhoneHint()
            }
        }

        fun updateUI() {
            if (isRegisterMode) {
                loginButton.setText(R.string.zetflix_register_button)
                modeSwitchLink.setText(R.string.zetflix_login_toggle)
                confirmPasswordLayout.visibility = View.VISIBLE
            } else {
                loginButton.setText(R.string.zetflix_login_button)
                modeSwitchLink.setText(R.string.zetflix_register_toggle)
                confirmPasswordLayout.visibility = View.GONE
            }
        }

        updateUI()

        modeSwitchLink.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUI()
        }

        loginButton.setOnClickListener {
            val country = countrySpinner.selectedItem as Country
            val phone = phoneEdit.text.toString().trim()
            val email = emailEdit.text.toString().trim().lowercase()
            val password = passwordEdit.text.toString()
            val confirmPassword = confirmPasswordEdit.text.toString()

            if (phone.length != country.length) {
                Toast.makeText(this, R.string.zetflix_phone_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email.isEmpty() || !email.endsWith("@gmail.com")) {
                Toast.makeText(this, R.string.zetflix_email_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 8) {
                Toast.makeText(this, R.string.zetflix_password_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegisterMode && password != confirmPassword) {
                Toast.makeText(this, R.string.zetflix_password_mismatch, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!privacyCheckbox.isChecked) {
                Toast.makeText(this, "Please agree to the privacy policy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveAuthData(country.code, phone, email, password)
            ZetFlixAuthPrefs.setZetFlixAuthenticated(this, true)
            ZetFlixSessionManager.setLoginTimestamp(this)
            setKey("HAS_DONE_SETUP", true)
            
            startActivity(Intent(this, com.lagradost.cloudstream3.ui.setup.ZetFlixLoadingActivity::class.java))
            finish()
        }
    }

    private fun saveAuthData(countryCode: String, phone: String, email: String, password: String) {
        try {
            val mainKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                this,
                "zetflix_secure_prefs",
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val deviceId = sharedPreferences.getString("device_id", null) ?: UUID.randomUUID().toString()
            val deviceSecret = sharedPreferences.getString("device_secret", null) ?: (UUID.randomUUID().toString() + UUID.randomUUID().toString())

            sharedPreferences.edit().apply {
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
            Toast.makeText(this, "Error saving auth data", Toast.LENGTH_SHORT).show()
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
