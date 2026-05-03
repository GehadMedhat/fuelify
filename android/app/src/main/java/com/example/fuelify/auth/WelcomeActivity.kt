package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.home.HomeActivity
import com.example.fuelify.R
import com.example.fuelify.doctor.DoctorHomeActivity
import com.example.fuelify.doctor.DoctorOnboardingActivity
import com.example.fuelify.onboarding.OnboardingActivity
import com.example.fuelify.utils.DoctorPreferences
import com.example.fuelify.utils.UserPreferences

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // If already logged in via auth AND onboarding done → go straight to home
        if (SessionManager.isLoggedIn()) {
            val dest = if (UserPreferences.isLoggedIn(this)) HomeActivity::class.java else OnboardingActivity::class.java
            startActivity(Intent(this, dest))
            finish()
            return
        }

        // Create Account → SignUp
        findViewById<android.view.View>(R.id.rh64s5rbxntn).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        // Already have an account → Login
        findViewById<android.view.View>(R.id.rqtw80eh3x9).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Continue as guest → Login
        findViewById<android.view.View>(R.id.rtcilofvmf29).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Continue as doctor → Login
        findViewById<android.view.View>(R.id.tvContinueAsDoctor).setOnClickListener {
            if (DoctorPreferences.isLoggedIn(this)) {
                startActivity(Intent(this, DoctorHomeActivity::class.java))
            } else {
                startActivity(Intent(this, DoctorOnboardingActivity::class.java))
            }        }
    }
}
