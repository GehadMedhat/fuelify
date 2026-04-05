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
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.DashboardData
import com.example.fuelify.data.api.models.MealItem
import com.example.fuelify.data.api.models.WorkoutItem
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import java.text.SimpleDateFormat
import java.util.*
import com.bumptech.glide.Glide
import kotlinx.coroutines.*


class HomeActivity : AppCompatActivity() {

    private val vm: HomeViewModel by viewModels()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    private var allTodayMeals   = listOf<MealItem>()
    private var allRecommended  = listOf<MealItem>()
    private var suggestedWorkouts = listOf<WorkoutItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

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
        loadWorkoutData()
    }

    // ── Load workout data separately from dashboard ────────────────────────────
    private fun loadWorkoutData() {
        scope.launch {
            try {
                // 1. Extra RECOMMENDED workouts (different from weekly plan, for home screen only)
                val recResp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getRecommendedWorkouts(userId)
                }
                if (recResp.isSuccessful && recResp.body()?.data != null) {
                    suggestedWorkouts = recResp.body()!!.data!!.workouts
                }

                // 2. Workout progress → sessions counter (weekly view)
                val progressResp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getWorkoutProgress(userId)
                }
                if (progressResp.isSuccessful && progressResp.body()?.data != null) {
                    val prog = progressResp.body()!!.data!!
                    val exerciseDaysPerWeek = UserPreferences.getExerciseDays(this@HomeActivity)
                    val weekTarget = exerciseDaysPerWeek.coerceAtLeast(1)
                    // "2/5 this week" — how many workouts done vs recommended per week
                    findViewById<TextView>(R.id.tvSessions).text =
                        "${prog.weekSessions}/$weekTarget this week"
                    setProgressBar(R.id.progressSessions, prog.weekSessions, weekTarget)
                }

                // 3. Refresh home screen sections now that we have workout data
                if (allTodayMeals.isNotEmpty() || allRecommended.isNotEmpty()) {
                    bindHighlights(allTodayMeals)
                    bindRecommended(allRecommended)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeActivity", "Workout data error: ${e.message}")
            }
        }
    }

    // ── View All buttons ──────────────────────────────────────────────────────
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

    // ── Static UI ─────────────────────────────────────────────────────────────
    private fun setupStaticUI() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
        findViewById<TextView>(R.id.tvDate).text = dateFormat.format(Date())
        val name = UserPreferences.getName(this)
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        findViewById<TextView>(R.id.tvAvatar).text = initial
        findViewById<TextView>(R.id.tvGreeting).text = "Hi, $name"
    }

    // ── ViewModel observation ─────────────────────────────────────────────────
    private fun observeViewModel() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        vm.state.observe(this) { state ->
            when (state) {
                is DashboardState.Loading -> progressBar.visibility = View.VISIBLE
                is DashboardState.Success -> {
                    progressBar.visibility = View.GONE
                    allTodayMeals  = state.data.todayMeals
                    allRecommended = state.data.recommendedMeals
                    bindDashboard(state.data)
                }
                is DashboardState.Error -> {
                    progressBar.visibility = View.GONE
                    bindFromPrefs()
                }
            }
        }
    }

    // ── Bind dashboard ────────────────────────────────────────────────────────
    private fun bindDashboard(d: DashboardData) {
        val initial = d.name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        findViewById<TextView>(R.id.tvGreeting).text = "Hi, ${d.name}"
        findViewById<TextView>(R.id.tvAvatar).text = initial

        // Calories
        findViewById<TextView>(R.id.tvCaloriesEaten).text = "${d.caloriesEaten}"
        findViewById<TextView>(R.id.tvCaloriesGoal).text  = "of ${d.dailyCaloriesGoal} kcal"
        setProgressBar(R.id.progressCalories, d.caloriesEaten, d.dailyCaloriesGoal)

        // Show sessions as week progress until workout progress loads
        // workoutsGoal = recommended days per week (from exerciseDays)
        findViewById<TextView>(R.id.tvSessions).text = "${d.workoutsDone}/${d.workoutsGoal} this week"
        setProgressBar(R.id.progressSessions, d.workoutsDone, d.workoutsGoal)

        // Water
        findViewById<TextView>(R.id.tvWater).text = "${d.waterGlasses}/${d.waterGoal}"
        setProgressBar(R.id.progressWater, d.waterGlasses, d.waterGoal)

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

        // Bind highlights (meal + workout mixed)
        bindHighlights(d.todayMeals)

        // Bind recommended (meal + workout mixed)
        bindRecommended(d.recommendedMeals)
    }

    // ── Today's Highlights: first meal card + first suggested workout ─────────
    private fun bindHighlights(meals: List<MealItem>) {
        // Card 1 — Today's meal
        meals.getOrNull(0)?.let { meal ->
            val img1 = findViewById<ImageView>(R.id.imgHighlight1)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(img1)
            }
            findViewById<TextView>(R.id.tvHighlight1Name).text = meal.mealName
            try {
                findViewById<TextView>(R.id.tvHighlight1Sub).text =
                    "${meal.mealType} • ${meal.calories} cal"
            } catch (e: Exception) {}
            findViewById<LinearLayout>(R.id.cardHighlight1).setOnClickListener {
                openMealDetail(meal.mealId)
            }
        }

        // Card 2 — Suggested workout (replaces second meal)
        val workout = suggestedWorkouts.firstOrNull()
        if (workout != null) {
            val img2 = try { findViewById<ImageView>(R.id.imgHighlight2) } catch (e: Exception) { null }
            if (img2 != null && workout.imageUrl.isNotEmpty()) {
                Glide.with(this).load(workout.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(img2)
            }
            try {
                findViewById<TextView>(R.id.tvHighlight2Name).text  = workout.workoutName
                findViewById<TextView>(R.id.tvHighlight2Sub).text   =
                    "${workout.category} • ${workout.durationMinutes} min"
                findViewById<TextView>(R.id.tvHighlight2Time).text  =
                    "${workout.caloriesBurnedEstimate} kcal"
                // Add 💪 badge
// CORRECT
                findViewById<TextView>(R.id.tvHighlight2Badge)?.text = "💪 Workout"
            } catch (e: Exception) {}
            try {
                findViewById<LinearLayout>(R.id.cardHighlight2).setOnClickListener {
                    startActivity(Intent(this, WorkoutDetailActivity::class.java).apply {
                        putExtra("workout_id", workout.workoutId)
                    })
                }
            } catch (e: Exception) {}
        } else {
            // Fallback: show second meal if no workout loaded yet
            meals.getOrNull(1)?.let { meal ->
                try {
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
                } catch (e: Exception) {}
            }
        }
    }

    // ── Recommended: meals from dashboard + SUGGESTED workouts (extra picks, not the plan)
    // Interleaved: meal → workout → meal → workout
    private fun bindRecommended(meals: List<MealItem>) {
        val container = findViewById<LinearLayout>(R.id.containerRecommended)
        container.removeAllViews()

        val mealList    = meals.take(3)
        val workoutList = suggestedWorkouts.take(2)  // extra workout suggestions

        var mi = 0; var wi = 0
        while ((mi < mealList.size || wi < workoutList.size) && (mi + wi) < 4) {
            if (mi <= wi && mi < mealList.size) {
                container.addView(buildMealRow(mealList[mi++]))
            } else if (wi < workoutList.size) {
                container.addView(buildWorkoutRow(workoutList[wi++]))
            } else if (mi < mealList.size) {
                container.addView(buildMealRow(mealList[mi++]))
            } else break
        }
    }

    private fun buildMealRow(meal: MealItem): View {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_meal_recommended, null, false) // Fixed container param
        row.findViewById<TextView>(R.id.tvMealName).text   = meal.mealName
        row.findViewById<TextView>(R.id.tvMealSub).text    =
            "${meal.mealType} · P:${meal.proteinG}g C:${meal.carbsG}g F:${meal.fatG}g"
        row.findViewById<TextView>(R.id.tvMealCalTag).text = "${meal.calories} cal"
        row.findViewById<TextView>(R.id.tvMealTime).text   = "· ${meal.scheduledTime}"

        // FIX THIS LINE:
        val imgView = row.findViewById<ImageView>(R.id.imgMeal)

        if (meal.imageUrl.isNotEmpty()) {
            Glide.with(this).load(meal.imageUrl)
                .placeholder(R.drawable.bg_image_placeholder)
                .centerCrop().into(imgView)
        }
        row.setOnClickListener { openMealDetail(meal.mealId) }
        return row
    }

    private fun buildWorkoutRow(workout: WorkoutItem): View {
        // 1. Fixed the inflate call: removed "container =" and "attachToRoot ="
        // labels which can cause issues if not used perfectly in Kotlin
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_meal_recommended, null, false)

        // 2. Map Workout data to the Meal layout IDs
        row.findViewById<TextView>(R.id.tvMealName).text = workout.workoutName
        row.findViewById<TextView>(R.id.tvMealSub).text =
            "${workout.category} · ${workout.difficulty} · ${workout.exerciseCount} exercises"

        row.findViewById<TextView>(R.id.tvMealCalTag).text = "${workout.caloriesBurnedEstimate} kcal"
        row.findViewById<TextView>(R.id.tvMealTime).text = "· ${workout.durationMinutes} min"

        val imgView = row.findViewById<ImageView>(R.id.imgMeal)
        if (workout.imageUrl.isNotEmpty()) {
            Glide.with(this).load(workout.imageUrl)
                .placeholder(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(imgView)
        }

        // 3. Apply styling to distinguish it from a meal
        row.setBackgroundColor(0xFFF0FDF4.toInt()) // Light green background
        row.findViewById<TextView>(R.id.tvMealCalTag).setTextColor(0xFF22C55E.toInt())

        row.setOnClickListener {
            startActivity(Intent(this, WorkoutDetailActivity::class.java).apply {
                putExtra("workout_id", workout.workoutId)
            })
        }
        return row
    }

    // ── Fallback when offline ─────────────────────────────────────────────────
    private fun bindFromPrefs() {
        val dailyCal    = UserPreferences.getDailyCalories(this)
        val workoutDays = UserPreferences.getExerciseDays(this)
        val name        = UserPreferences.getName(this)
        val initial     = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        val dailyTarget = maxOf(1, (workoutDays + 6) / 7)
        findViewById<TextView>(R.id.tvGreeting).text      = "Hi, $name"
        findViewById<TextView>(R.id.tvAvatar).text        = initial
        findViewById<TextView>(R.id.tvCaloriesEaten).text = "0"
        findViewById<TextView>(R.id.tvCaloriesGoal).text  = "of $dailyCal kcal"
        findViewById<TextView>(R.id.tvSessions).text      = "0/$dailyTarget"
        findViewById<TextView>(R.id.tvWater).text         = "0/8"
        findViewById<TextView>(R.id.tvStreakTitle).text   = "0 Day Streak!"
        findViewById<TextView>(R.id.tvStreakSub).text     = "Start your journey today!"
    }

    // ── Progress bar helper ───────────────────────────────────────────────────
    private fun setProgressBar(viewId: Int, current: Int, max: Int) {
        val bar    = try { findViewById<View>(viewId) } catch (e: Exception) { return }
        val parent = bar.parent as? FrameLayout ?: return
        parent.post {
            val pct    = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
            val params = bar.layoutParams
            params.width = (parent.width * pct).toInt().coerceAtLeast(0)
            bar.layoutParams = params
        }
    }

    private fun openMealDetail(mealId: Int) {
        startActivity(Intent(this, MealDetailActivity::class.java).apply {
            putExtra(MealDetailActivity.EXTRA_MEAL_ID, mealId)
        })
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────
    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener { }
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener {
            startActivity(Intent(this, DietActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navWorkouts).setOnClickListener {
            startActivity(Intent(this, WorkoutHomeActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navStats).setOnClickListener {
            Toast.makeText(this, "Statistics coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadWorkoutData()    // Refresh workout data when returning from workout screen
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
