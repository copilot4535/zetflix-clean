package com.lagradost.cloudstream3.ui.setup

import android.animation.*
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.facebook.shimmer.ShimmerFrameLayout
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ZetFlixLoadingActivity : AppCompatActivity() {

    private var breathingAnimator: AnimatorSet? = null

    companion object {
        const val EXTRA_FIRST_SETUP = "is_first_setup"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zetflix_loading)

        val isFirstSetup = intent.getBooleanExtra(EXTRA_FIRST_SETUP, false)
        val logo = findViewById<ImageView>(R.id.loading_logo)
        val glow = findViewById<View>(R.id.logo_glow)
        val shimmer = findViewById<ShimmerFrameLayout>(R.id.shimmer_container)
        val status = findViewById<TextView>(R.id.loading_status)
        val progress = findViewById<View>(R.id.loading_progress)

        // Cinematic Entrance
        logo.alpha = 0f
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f
        glow.alpha = 0f
        glow.scaleX = 0.5f
        glow.scaleY = 0.5f

        val entranceLogo = ObjectAnimator.ofPropertyValuesHolder(
            logo,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f)
        ).apply {
            duration = 1200
            interpolator = AnticipateOvershootInterpolator(1.2f)
        }

        val entranceGlow = ObjectAnimator.ofPropertyValuesHolder(
            glow,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f)
        ).apply {
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(entranceLogo, entranceGlow)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shimmer.startShimmer()
                    startBreathingAnimation(logo, glow)
                }
            })
            start()
        }

        // Reveal loading indicators slowly
        status.animate().alpha(1f).setStartDelay(1000).setDuration(800).start()
        progress.animate().alpha(1f).setStartDelay(1000).setDuration(800).start()

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

            performHeroExit {
                navigateToMain()
            }
        }
    }

    private fun startBreathingAnimation(logo: View, glow: View) {
        val logoPulse = ObjectAnimator.ofPropertyValuesHolder(
            logo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.03f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.03f)
        ).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val glowPulse = ObjectAnimator.ofPropertyValuesHolder(
            glow,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 0.6f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.1f, 1.3f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.1f, 1.3f)
        ).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        breathingAnimator = AnimatorSet().apply {
            playTogether(logoPulse, glowPulse)
            start()
        }
    }

    private fun performHeroExit(onComplete: () -> Unit) {
        val logo = findViewById<ImageView>(R.id.loading_logo)
        val glow = findViewById<View>(R.id.logo_glow)
        val root = findViewById<View>(android.R.id.content)
        val status = findViewById<TextView>(R.id.loading_status)
        val progress = findViewById<View>(R.id.loading_progress)

        breathingAnimator?.cancel()

        status.animate().alpha(0f).setDuration(300).start()
        progress.animate().alpha(0f).setDuration(300).start()

        val zoomLogo = ObjectAnimator.ofPropertyValuesHolder(
            logo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 12f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f)
        ).apply {
            duration = 800
            interpolator = AccelerateInterpolator(1.5f)
        }

        val fadeGlow = ObjectAnimator.ofFloat(glow, View.ALPHA, 0f).apply {
            duration = 400
        }

        val fadeOutRoot = ObjectAnimator.ofFloat(root, View.ALPHA, 0f).apply {
            duration = 500
            startDelay = 300
        }

        AnimatorSet().apply {
            playTogether(zoomLogo, fadeGlow, fadeOutRoot)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
            })
            start()
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
