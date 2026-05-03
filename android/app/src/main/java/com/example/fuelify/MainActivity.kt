package com.example.fuelify

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.auth.WelcomeActivity
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.home.HomeActivity
import com.example.fuelify.onboarding.OnboardingActivity
import com.example.fuelify.utils.UserPreferences

/**
 * App entry point.
 *  - Not auth-logged-in              → WelcomeActivity (friend's Login/SignUp)
 *  - Auth done, no fuelify onboarding → OnboardingActivity (health questions)
 *  - Fully set up                     → HomeActivity
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init auth session manager
        SessionManager.init(this)

        // Detect fresh install → wipe fuelify prefs
        val prefs = getSharedPreferences("fuelify_prefs", MODE_PRIVATE)
        val installTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
        val savedInstallTime = prefs.getLong("install_time", -1L)
        if (savedInstallTime != installTime) {
            UserPreferences.clear(this)
            prefs.edit().putLong("install_time", installTime).apply()
        }

        val destination = when {
            !SessionManager.isLoggedIn()        -> WelcomeActivity::class.java
            !UserPreferences.isLoggedIn(this)   -> OnboardingActivity::class.java
            else                                -> HomeActivity::class.java
        }

        startActivity(Intent(this, destination))
        finish()
    }
}
