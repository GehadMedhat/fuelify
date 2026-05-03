package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var tvAdminName: TextView
    private lateinit var btnLogout: LinearLayout
    private lateinit var cardAddReward: LinearLayout
    private lateinit var cardUpdateReward: LinearLayout
    private lateinit var cardDeleteReward: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        tvAdminName    = findViewById(R.id.tvAdminName)
        btnLogout      = findViewById(R.id.btnLogout)
        cardAddReward  = findViewById(R.id.cardAddReward)
        cardUpdateReward = findViewById(R.id.cardUpdateReward)
        cardDeleteReward = findViewById(R.id.cardDeleteReward)

        // Show admin's name in header
        val firstName = SessionManager.getFirstName() ?: "Admin"
        tvAdminName.text = "Hi, $firstName"

        cardAddReward.setOnClickListener {
            startActivity(Intent(this, AdminAddRewardActivity::class.java))
        }

        cardUpdateReward.setOnClickListener {
            startActivity(Intent(this, AdminUpdateRewardActivity::class.java))
        }

        cardDeleteReward.setOnClickListener {
            startActivity(Intent(this, AdminDeleteRewardActivity::class.java))
        }

        btnLogout.setOnClickListener { performLogout() }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                RetrofitClient.instance.logout(SessionManager.getBearerToken())
            } catch (e: Exception) {
                // ignore network error on logout
            } finally {
                SessionManager.clear()
                startActivity(Intent(this@AdminDashboardActivity, WelcomeActivity::class.java))
                finishAffinity()
            }
        }
    }
}
