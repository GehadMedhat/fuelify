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

        val prefs = getSharedPreferences("fuelify_prefs", MODE_PRIVATE)
        val installTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
        val savedInstallTime = prefs.getLong("install_time", -1L)

        if (savedInstallTime != installTime) {
            // Fresh install detected — wipe everything
            UserPreferences.clear(this)
            prefs.edit().putLong("install_time", installTime).apply()
        }

        if (UserPreferences.isLoggedIn(this)) {
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        finish()
    }
}
