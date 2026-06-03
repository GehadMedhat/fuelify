package com.example.fuelify.statistics

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class WaterHomeActivity : AppCompatActivity() {

    private lateinit var progressCircle             : ProgressBar
    private lateinit var tvPercentage               : TextView
    private lateinit var tvProgress                 : TextView
    private lateinit var tvRemaining                : TextView
    private lateinit var tvDailyGoal                : TextView
    private lateinit var cardAddAmount              : CardView
    private lateinit var cardStatistics             : CardView
    private lateinit var cardReminder               : CardView
    private lateinit var cardSleepTracker           : CardView
    private lateinit var cardMoodTracker            : CardView
    private lateinit var cardBloodPressureTracker   : CardView
    private lateinit var cardBodyScanTracker        : CardView
    private lateinit var bottomNavigation           : BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.water_home)

        // ── تهيئة الـ Local Cache لكل الـ Repositories ──────────────────────

        // ────────────────────────────────────────────────────────────────────

        bindViews()
        setupClickListeners()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        bottomNavigation.selectedItemId = R.id.nav_home
    }

    private fun bindViews() {
        progressCircle            = findViewById(R.id.progressCircle)
        tvPercentage              = findViewById(R.id.tvPercentage)
        tvProgress                = findViewById(R.id.tvProgress)
        tvRemaining               = findViewById(R.id.tvRemaining)
        tvDailyGoal               = findViewById(R.id.tvDailyGoal)
        cardAddAmount             = findViewById(R.id.cardAddAmount)
        cardStatistics            = findViewById(R.id.cardStatistics)
        cardReminder              = findViewById(R.id.cardReminder)
        cardSleepTracker          = findViewById(R.id.cardSleepTracker)
        cardMoodTracker           = findViewById(R.id.cardMoodTracker)
        cardBloodPressureTracker  = findViewById(R.id.cardBloodPressureTracker)
        cardBodyScanTracker       = findViewById(R.id.cardBodyScanTracker)
        bottomNavigation          = findViewById(R.id.bottomNavigation)
        findViewById<android.widget.ImageView>(R.id.btnBack)
            .setOnClickListener { finish() }
    }

    private fun updateUI() {
        lifecycleScope.launch {
            val home = WaterTrackerRepository.getHomeData() ?: run {
                tvPercentage.text = "0 %"
                tvProgress.text   = "0 ml / 0 ml"
                tvRemaining.text  = "0 ml"
                tvDailyGoal.text  = "0 ml"
                return@launch
            }
            val percent   = home.todayProgressPercent.coerceAtMost(100)
            val remaining = (home.dailyGoalMl - home.todayTotalMl).coerceAtLeast(0)

            val animator = android.animation.ObjectAnimator.ofInt(
                progressCircle, "progress", progressCircle.progress, percent)
            animator.duration     = 800
            animator.interpolator = DecelerateInterpolator()
            animator.start()

            tvPercentage.text = "$percent %"
            tvProgress.text   = "${home.todayTotalMl} ml / ${home.dailyGoalMl} ml"
            tvRemaining.text  = "$remaining ml"
            tvDailyGoal.text  = "${home.dailyGoalMl} ml"
        }
    }

    private fun setupClickListeners() {
        cardAddAmount.setOnClickListener {
            startActivity(Intent(this, WaterIntakeActivity::class.java)) }
        cardStatistics.setOnClickListener {
            startActivity(Intent(this, WaterStatisticsActivity::class.java)) }
        cardReminder.setOnClickListener {
            startActivity(Intent(this, WaterRemindersActivity::class.java)) }
        cardSleepTracker.setOnClickListener {
            startActivity(Intent(this, SleepTrackerActivity::class.java)) }
        cardMoodTracker.setOnClickListener {
            startActivity(Intent(this, MoodHomeActivity::class.java)) }
        cardBloodPressureTracker.setOnClickListener {
            startActivity(Intent(this, BloodPressureHomeActivity::class.java)) }
        cardBodyScanTracker.setOnClickListener {
            startActivity(Intent(this, BodyAnalysisActivity::class.java)) }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, com.example.fuelify.home.HomeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_workouts -> {
                    startActivity(Intent(this, com.example.fuelify.home.WorkoutHomeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, com.example.fuelify.auth.ProfileActivity::class.java))
                    true
                }
                R.id.nav_diet -> {
                    startActivity(Intent(this, com.example.fuelify.home.DietActivity::class.java))
                    true
                }
                R.id.nav_statistics -> {
                    true
                }
                else -> {
                    true
                }
            }
        }
        bottomNavigation.setOnItemReselectedListener { }

    }
}