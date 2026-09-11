package com.lagradost.cloudstream3.ui.sports

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.databinding.ActivitySportsBinding
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat

class SportsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySportsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        binding = ActivitySportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnReturnToMovies.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
