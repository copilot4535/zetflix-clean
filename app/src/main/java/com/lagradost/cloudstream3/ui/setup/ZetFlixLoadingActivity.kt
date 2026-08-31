package com.lagradost.cloudstream3.ui.setup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ZetFlixLoadingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIRST_SETUP = "is_first_setup"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zetflix_loading)

        val isFirstSetup = intent.getBooleanExtra(EXTRA_FIRST_SETUP, false)
        val logo = findViewById<ImageView>(R.id.loading_logo)
        val status = findViewById<TextView>(R.id.loading_status)
        val progress = findViewById<android.view.View>(R.id.loading_progress)

        // Perfect Netflix-style entrance animation
        logo.animate()
            .alpha(1f)
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(1200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                logo.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(800)
                    .start()
            }
            .start()

        // Reveal loading indicators slowly
        status.animate().alpha(1f).setStartDelay(1000).setDuration(500).start()
        progress.animate().alpha(1f).setStartDelay(1000).setDuration(500).start()

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            
            // On first setup, show the branding for at least 4 seconds to allow plugin installation
            // On normal starts, show it for at least 2 seconds for visual continuity
            val minDisplayTime = if (isFirstSetup) 4000L else 2000L

            try {
                // Update and load all online plugins
                withTimeoutOrNull(60000L) {
                    PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem(this@ZetFlixLoadingActivity)
                }
            } catch (e: Exception) {
                Log.e("ZetFlixPluginSetup", "Failed to setup plugins", e)
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < minDisplayTime) {
                delay(minDisplayTime - elapsedTime)
            }

            // Smooth fade out before entering MainActivity
            findViewById<android.view.View>(android.R.id.content).animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    navigateToMain()
                }
                .start()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
