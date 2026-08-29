package com.lagradost.cloudstream3.ui.setup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ZetFlixLoadingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zetflix_loading)

        lifecycleScope.launch {
            try {
                // Update and load all online plugins from the allowlisted repositories with a 60s timeout
                withTimeoutOrNull(60000L) {
                    PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem(this@ZetFlixLoadingActivity)
                }
            } catch (e: Exception) {
                Log.e("ZetFlixPluginSetup", "Failed to setup plugins", e)
            } finally {
                navigateToMain()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
