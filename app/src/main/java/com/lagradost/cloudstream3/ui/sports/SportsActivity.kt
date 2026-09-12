package com.lagradost.cloudstream3.ui.sports

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivitySportsBinding
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import androidx.navigation.fragment.NavHostFragment
import com.lagradost.cloudstream3.ui.home.LiveStreamViewModel
import com.lagradost.cloudstream3.ui.home.LiveStreamViewModel.SportCategory

class SportsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySportsBinding
    private val viewModel: LiveStreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        binding = ActivitySportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fixSystemBarsPadding(binding.sportsHeader)

        setupBackHandler()
        setupCategoryChips()

        binding.sportsSearch.setOnClickListener {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.sports_nav_host_fragment) as? NavHostFragment
            val fragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull() as? ZetSportsFragment
            fragment?.onSearchTriggered()
        }

        binding.btnReturnToMovies.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupCategoryChips() {
        binding.sportsCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            
            val category = when (checkedId) {
                R.id.chip_live -> SportCategory.Live
                R.id.chip_football -> SportCategory.Football
                R.id.chip_cricket -> SportCategory.Cricket
                R.id.chip_more -> SportCategory.More
                else -> SportCategory.Live
            }
            viewModel.setSportCategory(category)
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.sports_nav_host_fragment) as? NavHostFragment
                val navController = navHostFragment?.navController
                
                // If we are at the start destination, move task to back (stay alive)
                if (navController?.currentDestination?.id == navController?.graph?.startDestinationId) {
                    moveTaskToBack(true)
                } else {
                    // Otherwise let the NavController handle it
                    if (navController?.popBackStack() != true) {
                        moveTaskToBack(true)
                    }
                }
            }
        })
    }
}
