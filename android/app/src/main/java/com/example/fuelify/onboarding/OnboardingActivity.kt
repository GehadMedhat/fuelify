package com.example.fuelify.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.fuelify.R
import com.example.fuelify.home.HomeActivity
import com.example.fuelify.onboarding.fragments.*
import com.example.fuelify.utils.UserPreferences

class OnboardingActivity : AppCompatActivity() {

    val viewModel: OnboardingViewModel by viewModels()

    private val steps: List<Fragment> by lazy {
        listOf(
            Step01NameFragment(),
            Step02GenderFragment(),
            Step03AgeFragment(),
            Step04HeightFragment(),
            Step05WeightFragment(),
            Step06GoalFragment(),
            Step07ActivityFragment(),
            Step08MotivationFragment(),
            Step09PushupsFragment(),
            Step10WorkoutDaysFragment(),
            Step11TrainingPlaceFragment(),
            Step12MealsNumFragment(),
            Step13LikeableFoodFragment(),
            Step14AllergiesFragment(),
            Step15BudgetFragment()
        )
    }

    var currentStep = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        if (savedInstanceState == null) loadStep(0)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentStep > 0) goBack() else finish()
            }
        })

        viewModel.submitState.observe(this) { state ->
            when (state) {
                is OnboardingState.Loading -> { /* show loader if needed */ }
                is OnboardingState.Success -> {
                    // Save user to SharedPreferences
                    val d = viewModel.userData
                    UserPreferences.saveUserId(this, state.userId)
                    UserPreferences.saveProfile(
                        ctx          = this,
                        name         = d.name,
                        goal         = d.goal,
                        weightKg     = d.weightKg,
                        heightCm     = d.heightCm,
                        age          = d.age,
                        gender       = d.gender,
                        activityLevel= d.activityLevel,
                        mealsPerDay  = d.mealsPerDay,
                        exerciseDays = d.exerciseDays
                    )
                    // Save for ProductScanActivity suitability check
                    val prefs = getSharedPreferences("fuelify_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("user_goal", d.goal)
                        .putString("user_allergies", org.json.JSONArray(d.allergies).toString())
                        .apply()

                    Toast.makeText(this, "Welcome to Fuelify! 🎉", Toast.LENGTH_LONG).show()
                    // Go to home, clear the back stack so user can't go back to onboarding
                    startActivity(
                        Intent(this, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                }
                is OnboardingState.Error -> {
                    Toast.makeText(this, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    fun goNext() {
        if (currentStep < steps.size - 1) {
            loadStep(currentStep + 1)
        } else {
            viewModel.submitOnboarding()
        }
    }

    fun goBack() {
        if (currentStep > 0) loadStep(currentStep - 1)
    }

    private fun loadStep(index: Int) {
        currentStep = index
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragmentContainer, steps[index])
            .commit()
    }

    fun setupDots(dotsContainer: LinearLayout) {
        dotsContainer.removeAllViews()
        val dp4  = (4  * resources.displayMetrics.density).toInt()
        val dp32 = (32 * resources.displayMetrics.density).toInt()
        val dp4m = (4  * resources.displayMetrics.density).toInt()
        for (i in steps.indices) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(
                if (i == currentStep) dp32 else dp4, dp4
            )
            params.marginEnd = dp4m
            dot.layoutParams = params
            dot.setBackgroundColor(
                if (i == currentStep) 0xFF000000.toInt() else 0xFFD4D4D4.toInt()
            )
            dotsContainer.addView(dot)
        }
    }

    private fun updateDotsInCurrentFragment() {
        val frag = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val dotsContainer = frag?.view?.findViewById<LinearLayout>(R.id.dotsContainer)
        if (dotsContainer != null) setupDots(dotsContainer)
    }
}
