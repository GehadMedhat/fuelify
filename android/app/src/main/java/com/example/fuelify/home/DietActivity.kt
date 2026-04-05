package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.models.DashboardData
import com.example.fuelify.data.api.models.MealItem
import com.example.fuelify.utils.UserPreferences
import java.text.SimpleDateFormat
import java.util.*

class DietActivity : AppCompatActivity() {

    private val vm: HomeViewModel by viewModels()
    private var userId = -1
    private var allMeals = listOf<MealItem>()
    private var dailyCalGoal = 2000
    private var caloriesEaten = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diet_main)

        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, top, 0, 0)
            insets
        }

        userId = UserPreferences.getUserId(this)
        if (userId == -1) { finish(); return }
        dailyCalGoal = UserPreferences.getDailyCalories(this)

        setupStaticUI()
        setupBottomNav()
        setupQuickAccess()
        observeViewModel()
        vm.loadDashboard(userId)

        // Search bar → open Recipes screen
        findViewById<LinearLayout>(R.id.searchBar)?.setOnClickListener {
            startActivity(Intent(this, RecipesActivity::class.java))
        }
        findViewById<TextView>(R.id.tvViewAllMeals).setOnClickListener {
            val intent = Intent(this, MealsListActivity::class.java)
            intent.putExtra(MealsListActivity.EXTRA_TITLE, "Today's Meals")
            intent.putExtra(MealsListActivity.EXTRA_MEALS, ArrayList(allMeals))
            startActivity(intent)
        }
    }

    private fun setupStaticUI() {
        val name = UserPreferences.getName(this)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when { hour < 12 -> "Good morning,"; hour < 17 -> "Good afternoon,"; else -> "Good evening," }
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
        findViewById<TextView>(R.id.tvDietGreeting).text = greeting
        findViewById<TextView>(R.id.tvDietName).text = name
        findViewById<TextView>(R.id.tvDietDate).text = dateFormat.format(Date())
    }

    private fun observeViewModel() {
        vm.state.observe(this) { state ->
            when (state) {
                is DashboardState.Success -> {
                    allMeals      = state.data.todayMeals
                    dailyCalGoal  = state.data.dailyCaloriesGoal
                    caloriesEaten = state.data.caloriesEaten
                    bindDashboard(state.data)
                }
                is DashboardState.Error -> bindFromPrefs()
                else -> {}
            }
        }
    }

    private fun bindDashboard(d: DashboardData) {
        updateCalorieDisplay(d.caloriesEaten, d.dailyCaloriesGoal)

        val m = d.macros
        findViewById<TextView>(R.id.tvDietProtein).text = "${m.proteinG}g / ${m.proteinGoal}g"
        findViewById<TextView>(R.id.tvDietCarbs).text   = "${m.carbsG}g / ${m.carbsGoal}g"
        findViewById<TextView>(R.id.tvDietFat).text     = "${m.fatG}g / ${m.fatGoal}g"
        setMacroBar(R.id.progressProtein, m.proteinG, m.proteinGoal)
        setMacroBar(R.id.progressCarbs,   m.carbsG,   m.carbsGoal)
        setMacroBar(R.id.progressFat,     m.fatG,     m.fatGoal)

        bindMeals(d.todayMeals)
    }

    private fun updateCalorieDisplay(eaten: Int, goal: Int) {
        caloriesEaten = eaten
        val remaining = (goal - eaten).coerceAtLeast(0)
        findViewById<TextView>(R.id.tvDietCalEaten).text = "$eaten"
        findViewById<TextView>(R.id.tvDietCalGoal).text  = "/ $goal  calories consumed"

        val tvRemaining = findViewById<TextView>(R.id.tvCalRemaining)
        when {
            eaten >= goal -> {
                tvRemaining.text = "✅ Goal reached!"
                tvRemaining.setBackgroundResource(R.drawable.bg_badge_completed)
            }
            else -> {
                tvRemaining.text = "$remaining cal remaining"
                tvRemaining.setBackgroundResource(R.drawable.bg_badge_time)
            }
        }
    }

    private fun bindFromPrefs() {
        val dailyCal = UserPreferences.getDailyCalories(this)
        updateCalorieDisplay(0, dailyCal)
    }

    private fun setMacroBar(viewId: Int, current: Int, max: Int) {
        val bar    = findViewById<View>(viewId)
        val parent = bar.parent as? FrameLayout ?: return
        parent.post {
            val pct    = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
            val params = bar.layoutParams
            params.width = (parent.width * pct).toInt().coerceAtLeast(0)
            bar.layoutParams = params
        }
    }

    private fun bindMeals(meals: List<MealItem>) {
        val container = findViewById<LinearLayout>(R.id.containerDietMeals)
        container.removeAllViews()

        if (meals.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No meals planned yet"
            tv.setTextColor(0xFF999999.toInt())
            tv.setPadding(48, 24, 48, 24)
            container.addView(tv)
            return
        }

        // Show planned total info only if gap is small (< 300 cal)
        val plannedTotal = meals.sumOf { it.calories }
        val gap = dailyCalGoal - plannedTotal
        if (gap in 1..300) {
            val infoTv = TextView(this)
            infoTv.text = "Almost there! Add ${gap} more calories to hit your goal"
            infoTv.setTextColor(0xFF888888.toInt())
            infoTv.textSize = 11f
            infoTv.setPadding(48, 0, 48, 20)
            container.addView(infoTv)
        }

        meals.forEach { meal ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_diet_meal_row, container, false)

            val imgView = row.findViewById<ImageView>(R.id.imgDietMeal)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(imgView)
            }

            row.findViewById<TextView>(R.id.tvDietMealType).text = meal.mealType
            row.findViewById<TextView>(R.id.tvDietMealName).text = meal.mealName
            row.findViewById<TextView>(R.id.tvDietMealTime).text = meal.scheduledTime
            row.findViewById<TextView>(R.id.tvDietMealCal).text  = "${meal.calories} cal"

            // Check button — one-way only, stays green once tapped
            val checkBtn = row.findViewById<TextView>(R.id.btnMealCheck)
            updateCheckButton(checkBtn, meal.isCompleted)

            if (meal.isCompleted) {
                checkBtn.isClickable = false
            } else {
                checkBtn.setOnClickListener {
                    checkBtn.isClickable = false  // disable immediately — no double tap
                    updateCheckButton(checkBtn, true)
                    vm.logMeal(userId, meal.id)
                    caloriesEaten += meal.calories
                    updateCalorieDisplay(caloriesEaten.coerceAtLeast(0), dailyCalGoal)
                }
            }

            // Tap row → Meal Scheduling
            row.setOnClickListener {
                val intent = Intent(this, MealSchedulingActivity::class.java)
                intent.putExtra(MealSchedulingActivity.EXTRA_MEALS, ArrayList(meals))
                intent.putExtra(MealSchedulingActivity.EXTRA_CAL_EATEN, caloriesEaten)
                intent.putExtra(MealSchedulingActivity.EXTRA_CAL_GOAL, dailyCalGoal)
                startActivity(intent)
            }

            container.addView(row)
        }
    }

    private fun updateCheckButton(btn: TextView, eaten: Boolean) {
        if (eaten) {
            btn.background = getDrawable(R.drawable.bg_badge_completed)
            btn.setTextColor(0xFFFFFFFF.toInt())
        } else {
            btn.background = getDrawable(R.drawable.bg_notif)
            btn.setTextColor(0xFF999999.toInt())
        }
    }

    private fun setupQuickAccess() {
        findViewById<LinearLayout>(R.id.btnMealDelivery).setOnClickListener {
            startActivity(Intent(this, MealDeliveryActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnGroceries).setOnClickListener {
            startActivity(Intent(this, GroceryActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnSmartPantry).setOnClickListener {
            startActivity(Intent(this, SmartPantryActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnFamilyPlans).setOnClickListener {
            startActivity(Intent(this, FamilyDietActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnMealScan).setOnClickListener {
            startActivity(Intent(this, ProductScanActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnOpenEco).setOnClickListener {
            startActivity(Intent(this, EcoSustainabilityActivity::class.java))
        }
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navWorkouts).setOnClickListener {
            startActivity(Intent(this, WorkoutHomeActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener { }
        listOf(R.id.navStats, R.id.navProfile).forEach { id ->
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}