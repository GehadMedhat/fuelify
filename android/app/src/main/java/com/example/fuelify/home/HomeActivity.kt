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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
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

    private var allTodayMeals        = listOf<MealItem>()
    private var allRecommended       = listOf<MealItem>()
    private var suggestedWorkouts    = listOf<WorkoutItem>()
    // Today's scheduled workout from the real workout_plan (name + image + id)
    private var todayWorkoutFromPlan = Triple("", "", 0)

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
        loadWorkoutData()
    }

    // ── Load workout data separately from dashboard ────────────────────────────
    private fun loadWorkoutData() {
        scope.launch {
            try {
                // Extra recommended workouts (different from weekly plan)
                val recResp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getRecommendedWorkouts(userId)
                }
                if (recResp.isSuccessful && recResp.body()?.data != null) {
                    suggestedWorkouts = recResp.body()!!.data!!.workouts
                }

                // Real workout session count from workout_session table
                val progressResp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getWorkoutProgress(userId)
                }
                if (progressResp.isSuccessful && progressResp.body()?.data != null) {
                    val prog = progressResp.body()!!.data!!
                    val exerciseDaysPerWeek = UserPreferences.getExerciseDays(this@HomeActivity)
                        .coerceAtLeast(1)
                    // Show: "done this week / weekly target"
                    // exerciseDays IS the weekly target (e.g. 4 = "do 4 workouts this week")
                    findViewById<TextView>(R.id.tvSessions).text =
                        "${prog.weekSessions}/$exerciseDaysPerWeek"
                    setProgressBar(R.id.progressSessions, prog.weekSessions, exerciseDaysPerWeek)
                }

                // Refresh sections now that workout data is loaded
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
//    private fun setupViewAllButtons() {
//        findViewById<TextView>(R.id.tvViewAllHighlights).setOnClickListener {
//            openMealsList("Today's Meals", allTodayMeals)
//        }
//        findViewById<TextView>(R.id.tvViewAllRecommended).setOnClickListener {
//            openMealsList("Recommended For You", allRecommended)
//        }
//    }

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

        // Meals — week progress: "X / Y meals this week"
        val mealsEaten = d.weekMealsEaten
        val mealsTotal = d.weekMealsTotal.coerceAtLeast(1)
        findViewById<TextView>(R.id.tvCaloriesEaten).text = "$mealsEaten"
        findViewById<TextView>(R.id.tvCaloriesGoal).text  = "of $mealsTotal meals"
        setProgressBar(R.id.progressCalories, mealsEaten, mealsTotal)

        // Workouts — week progress: "X / Y sessions this week"
//        val wkDone = d.weekWorkoutsDone
//        val wkGoal = d.weekWorkoutsGoal.coerceAtLeast(1)
//        findViewById<TextView>(R.id.tvSessions).text = "$wkDone/$wkGoal sessions this week"
//        setProgressBar(R.id.progressSessions, wkDone, wkGoal)

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

        // Store today's workout from plan for the highlight card (name + image + id)
        todayWorkoutFromPlan = Triple(d.todayWorkoutName, d.todayWorkoutImage, d.todayWorkoutId)

        bindHighlights(d.todayMeals)
        bindRecommended(d.recommendedMeals)
    }

    // ── Today's Highlights: meal from today's plan + workout from today's plan ──
    private fun bindHighlights(meals: List<MealItem>) {
        // Card 1 — First meal from today's meal plan
        meals.getOrNull(0)?.let { meal ->
            val img1 = findViewById<ImageView>(R.id.imgHighlight1)
            img1.clipToOutline = true   // ensures card container clips image corners
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(img1)
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

        // Card 2 — Today's scheduled workout from workout_plan (the real plan, not suggestions)
        val (workoutName, workoutImage, workoutId) = todayWorkoutFromPlan
        if (workoutName.isNotBlank()) {
            try {
                val img2 = findViewById<ImageView>(R.id.imgHighlight2)
                img2.clipToOutline = true
                if (workoutImage.isNotEmpty()) {
                    Glide.with(this).load(workoutImage)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(img2)
                }
                findViewById<TextView>(R.id.tvHighlight2Name).text = workoutName
                findViewById<TextView>(R.id.tvHighlight2Sub).text  = "Today's Workout"
                try { findViewById<TextView>(R.id.tvHighlight2Badge)?.text = "💪 Planned" }
                    catch (e: Exception) {}
                // Tap → open the specific workout detail
                findViewById<LinearLayout>(R.id.cardHighlight2).setOnClickListener {
                    if (workoutId > 0) {
                        startActivity(Intent(this, WorkoutDetailActivity::class.java).apply {
                            putExtra("workout_id", workoutId)
                        })
                    } else {
                        startActivity(Intent(this, WorkoutListActivity::class.java))
                    }
                }
            } catch (e: Exception) {}
        } else {
            // No workout scheduled today — show second meal as fallback
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
//                    try { findViewById<TextView>(R.id.tvHighlight2Time).text = meal.scheduledTime }
//                        catch (e: Exception) {}
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
