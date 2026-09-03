package com.lagradost.cloudstream3.ui.music

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivityMusicBinding
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat

class MusicActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMusicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.musicBottomNav.setupWithNavController(navController)
    }
}
