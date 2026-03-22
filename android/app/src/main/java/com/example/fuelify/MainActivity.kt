package com.example.fuelify

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.home.HomeActivity
import com.example.fuelify.onboarding.OnboardingActivity
import com.example.fuelify.utils.UserPreferences

/**
 * Entry point — decides whether to show onboarding or the home screen.
 * Never shows any UI itself; just redirects immediately.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (UserPreferences.isLoggedIn(this)) {
            // Returning user → go straight to home
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            // First time → go through onboarding
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        finish()
    }
}
