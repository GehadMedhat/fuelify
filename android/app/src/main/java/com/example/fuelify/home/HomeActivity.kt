package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fuelify.R
import com.example.fuelify.data.api.models.DashboardData
import com.example.fuelify.data.api.models.MealItem
import com.example.fuelify.utils.UserPreferences
import java.text.SimpleDateFormat
import java.util.*
import com.bumptech.glide.Glide

class HomeActivity : AppCompatActivity() {

    private val vm: HomeViewModel by viewModels()
    private var userId = -1

    // Store full data for "View All" dialogs
    private var allTodayMeals   = listOf<MealItem>()
    private var allRecommended  = listOf<MealItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // ── Fix Issue 1: handle status bar insets properly ────────────────────
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        userId = UserPreferences.getUserId(this)
        if (userId == -1) {
            startActivity(Intent(this,
                com.example.fuelify.onboarding.OnboardingActivity::class.java))
            finish()
            return
        }

        setupStaticUI()
        observeViewModel()
        vm.loadDashboard(userId)
        setupBottomNav()
        setupViewAllButtons()
    }

    // ── Issue 3: View All buttons ─────────────────────────────────────────────

    private fun setupViewAllButtons() {
        findViewById<TextView>(R.id.tvViewAllHighlights).setOnClickListener {
            openMealsList("Today's Meals", allTodayMeals)
        }
        findViewById<TextView>(R.id.tvViewAllRecommended).setOnClickListener {
            openMealsList("Recommended For You", allRecommended)
        }
    }

    private fun openMealsList(title: String, meals: List<MealItem>) {
        if (meals.isEmpty()) {
            Toast.makeText(this, "No meals available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, MealsListActivity::class.java)
        intent.putExtra(MealsListActivity.EXTRA_TITLE, title)
        intent.putExtra(MealsListActivity.EXTRA_MEALS, ArrayList(meals))
        startActivity(intent)
    }

    // ── Static UI (name, date) ────────────────────────────────────────────────

    private fun setupStaticUI() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
        findViewById<TextView>(R.id.tvDate).text = dateFormat.format(Date())

        val name = UserPreferences.getName(this)
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        findViewById<TextView>(R.id.tvAvatar).text = initial
        findViewById<TextView>(R.id.tvGreeting).text = "Hi, $name"
    }

    // ── Observe ViewModel ─────────────────────────────────────────────────────

    private fun observeViewModel() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        vm.state.observe(this) { state ->
            when (state) {
                is DashboardState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                }
                is DashboardState.Success -> {
                    progressBar.visibility = View.GONE
                    allTodayMeals  = state.data.todayMeals
                    allRecommended = state.data.recommendedMeals
                    bindDashboard(state.data)
                }
                is DashboardState.Error -> {
                    progressBar.visibility = View.GONE
                    // Issue 2: show fallback from SharedPreferences silently
                    // Only show toast if it's a real error, not just first load
                    bindFromPrefs()
                }
            }
        }
    }

    // ── Bind live API data ────────────────────────────────────────────────────

    private fun bindDashboard(d: DashboardData) {
        // Greeting
        val initial = d.name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        findViewById<TextView>(R.id.tvGreeting).text = "Hi, ${d.name}"
        findViewById<TextView>(R.id.tvAvatar).text = initial

        // Calories
        findViewById<TextView>(R.id.tvCaloriesEaten).text = "${d.caloriesEaten}"
        findViewById<TextView>(R.id.tvCaloriesGoal).text  = "of ${d.dailyCaloriesGoal} kcal"

        // Sessions
        findViewById<TextView>(R.id.tvSessions).text = "${d.workoutsDone}/${d.workoutsGoal}"

        // Water
        findViewById<TextView>(R.id.tvWater).text = "${d.waterGlasses}/${d.waterGoal}"

        // Progress bars
        setProgressBar(R.id.progressCalories, d.caloriesEaten, d.dailyCaloriesGoal)
        setProgressBar(R.id.progressSessions, d.workoutsDone,  d.workoutsGoal)
        setProgressBar(R.id.progressWater,    d.waterGlasses,  d.waterGoal)

        // Streak
        val streakMsg = when {
            d.streakDays == 0 -> "Start your journey today!"
            d.streakDays == 1 -> "Great start! Keep going."
            d.streakDays < 7  -> "You're building momentum!"
            d.streakDays < 30 -> "You're on fire! Keep going."
            else               -> "Incredible! ${d.streakDays} days strong 💪"
        }
        findViewById<TextView>(R.id.tvStreakTitle).text = "${d.streakDays} Day Streak!"
        findViewById<TextView>(R.id.tvStreakSub).text   = streakMsg

        // Today's highlights — show first 2 meals, tap opens meal detail
        d.todayMeals.getOrNull(0)?.let { meal ->
            val img1 = findViewById<ImageView>(R.id.imgHighlight1)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(img1)
            }
            findViewById<TextView>(R.id.tvHighlight1Name).text = meal.mealName
            findViewById<TextView>(R.id.tvHighlight1Sub).text  =
                "${meal.mealType} • ${meal.calories} cal"
            findViewById<LinearLayout>(R.id.cardHighlight1).setOnClickListener {
                openMealDetail(meal.mealId)
            }
        }
        d.todayMeals.getOrNull(1)?.let { meal ->
            val img2 = findViewById<ImageView>(R.id.imgHighlight2)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(img2)
            }
            findViewById<TextView>(R.id.tvHighlight2Name).text = meal.mealName
            findViewById<TextView>(R.id.tvHighlight2Sub).text  =
                "${meal.mealType} • ${meal.calories} cal"
            findViewById<TextView>(R.id.tvHighlight2Time).text = meal.scheduledTime
            findViewById<LinearLayout>(R.id.cardHighlight2).setOnClickListener {
                openMealDetail(meal.mealId)
            }
        }

        // Recommended
        bindRecommended(d.recommendedMeals)
    }

    // ── Fallback when offline ─────────────────────────────────────────────────

    private fun bindFromPrefs() {
        val dailyCal    = UserPreferences.getDailyCalories(this)
        val workoutDays = UserPreferences.getExerciseDays(this)
        val name        = UserPreferences.getName(this)
        val initial     = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

        // Issue 1 fix also: make sure name shows correctly from prefs
        findViewById<TextView>(R.id.tvGreeting).text  = "Hi, $name"
        findViewById<TextView>(R.id.tvAvatar).text    = initial
        findViewById<TextView>(R.id.tvCaloriesEaten).text = "0"
        findViewById<TextView>(R.id.tvCaloriesGoal).text  = "of $dailyCal kcal"
        findViewById<TextView>(R.id.tvSessions).text      = "0/$workoutDays"
        findViewById<TextView>(R.id.tvWater).text         = "0/8"
        findViewById<TextView>(R.id.tvStreakTitle).text   = "0 Day Streak!"
        // Issue 2 fix: don't say "Connect to internet" — just say start journey
        findViewById<TextView>(R.id.tvStreakSub).text     = "Start your journey today!"
    }

    // ── Progress bar helper ───────────────────────────────────────────────────

    private fun setProgressBar(viewId: Int, current: Int, max: Int) {
        val bar    = findViewById<View>(viewId)
        val parent = bar.parent as? FrameLayout ?: return
        parent.post {
            val pct    = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
            val params = bar.layoutParams
            params.width = (parent.width * pct).toInt().coerceAtLeast(0)
            bar.layoutParams = params
        }
    }

    // ── Recommended meals list ────────────────────────────────────────────────

    private fun bindRecommended(meals: List<MealItem>) {
        val container = findViewById<LinearLayout>(R.id.containerRecommended)
        container.removeAllViews()

        if (meals.isEmpty()) return

        // Show first 4, tap opens meal detail
        meals.take(4).forEach { meal ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_meal_recommended, container, false)
            row.findViewById<TextView>(R.id.tvMealName).text   = meal.mealName
            row.findViewById<TextView>(R.id.tvMealSub).text    =
                "${meal.mealType} · P:${meal.proteinG}g C:${meal.carbsG}g F:${meal.fatG}g"
            row.findViewById<TextView>(R.id.tvMealCalTag).text = "${meal.calories} cal"
            row.findViewById<TextView>(R.id.tvMealTime).text   = "· ${meal.scheduledTime}"
            val imgView = row.findViewById<ImageView>(R.id.imgMeal)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(imgView)
            }
            row.setOnClickListener { openMealDetail(meal.mealId) }
            container.addView(row)
        }
    }


    private fun openMealDetail(mealId: Int) {
        val intent = Intent(this, MealDetailActivity::class.java)
        intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, mealId)
        startActivity(intent)
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener { /* already here */ }
        
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener {
            startActivity(Intent(this, DietActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navWorkouts).setOnClickListener {
            Toast.makeText(this, "Workouts coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.navStats).setOnClickListener {
            Toast.makeText(this, "Statistics coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            Toast.makeText(this, "Profile coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()

    }
}
