package com.example.fuelify.statistics

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity

class BloodPressureHomeActivity : AppCompatActivity() {

    private lateinit var btnAddBloodPressure: com.google.android.material.button.MaterialButton
    private lateinit var btnBpStatistics: com.google.android.material.button.MaterialButton
    private lateinit var btnAddBloodSugar: com.google.android.material.button.MaterialButton
    private lateinit var btnBsStatistics: com.google.android.material.button.MaterialButton

    private lateinit var navHome: LinearLayout
    private lateinit var navWorkouts: LinearLayout
    private lateinit var navDiet: LinearLayout
    private lateinit var navStats: LinearLayout
    private lateinit var navProfile: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blood_pressure_home)
        bindViews()
        setupClickListeners()
        setupBottomNavigation()
    }

    private fun bindViews() {
        findViewById<android.widget.ImageView>(R.id.btn_back)
            .setOnClickListener { finish() }

        btnAddBloodPressure = findViewById(R.id.btn_add_blood_pressure)
        btnBpStatistics     = findViewById(R.id.btn_bp_statistics)
        btnAddBloodSugar    = findViewById(R.id.btn_add_blood_sugar)
        btnBsStatistics     = findViewById(R.id.btn_bs_statistics)

        navHome     = findViewById(R.id.navHome)
        navWorkouts = findViewById(R.id.navWorkouts)
        navDiet     = findViewById(R.id.navDiet)
        navStats    = findViewById(R.id.navStats)
        navProfile  = findViewById(R.id.navProfile)
    }

    private fun setupClickListeners() {
        btnAddBloodPressure.setOnClickListener {
            startActivity(Intent(this, BloodPressureAddActivity::class.java))
        }

        btnBpStatistics.setOnClickListener {
            startActivity(Intent(this, BloodPressureStatsActivity::class.java).apply {
                putExtra(BloodPressureStatsActivity.EXTRA_TYPE,
                    BloodPressureStatsActivity.TYPE_BLOOD_PRESSURE)
            })
        }

        btnAddBloodSugar.setOnClickListener {
            startActivity(Intent(this, BloodSugarAddActivity::class.java))
        }

        btnBsStatistics.setOnClickListener {
            startActivity(Intent(this, BloodPressureStatsActivity::class.java).apply {
                putExtra(BloodPressureStatsActivity.EXTRA_TYPE,
                    BloodPressureStatsActivity.TYPE_BLOOD_SUGAR)
            })
        }
    }

    private fun setupBottomNavigation() {
        navHome.setOnClickListener {
            startActivity(Intent(this, com.example.fuelify.home.HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        navWorkouts.setOnClickListener {
            startActivity(Intent(this, com.example.fuelify.home.WorkoutHomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        navDiet.setOnClickListener {
            startActivity(Intent(this, com.example.fuelify.home.DietActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        navStats.setOnClickListener {
            startActivity(Intent(this, WaterHomeActivity::class.java).apply {
                putExtra(BloodPressureStatsActivity.EXTRA_TYPE,
                    BloodPressureStatsActivity.TYPE_BLOOD_PRESSURE)
            })
        }

        navProfile.setOnClickListener {
            startActivity(Intent(this, com.example.fuelify.auth.ProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()          }
    }
}