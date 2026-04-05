package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.doctor.DoctorHomeActivity
import com.example.fuelify.doctor.DoctorOnboardingActivity
import com.example.fuelify.utils.DoctorPreferences

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        setupBottomNav()
    }

    private fun setupBottomNav() {

        findViewById<LinearLayout>(R.id.btnMedical).setOnClickListener {
            startActivity(Intent(this, MedicalInformationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnAlerts).setOnClickListener {
            startActivity(Intent(this, MedicalAlertsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnPlan).setOnClickListener {
            startActivity(Intent(this, SmartPlanActivity::class.java))
            finish()
        }

        findViewById<LinearLayout>(R.id.btnReport).setOnClickListener {
            startActivity(Intent(this, HealthReportActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnDoctor).setOnClickListener {
            startActivity(Intent(this, DoctorConsultationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnDoctorLogin).setOnClickListener {
            // If already logged in as doctor → go straight to inbox
            // Otherwise → go to onboarding
            if (DoctorPreferences.isLoggedIn(this)) {
                startActivity(Intent(this, DoctorHomeActivity::class.java))
            } else {
                startActivity(Intent(this, DoctorOnboardingActivity::class.java))
            }
        }
    }
}