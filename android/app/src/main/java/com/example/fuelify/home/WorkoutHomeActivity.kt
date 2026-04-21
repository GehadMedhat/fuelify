package com.example.fuelify.home

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class WorkoutHomeActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_home)
        userId = UserPreferences.getUserId(this)

        // Greeting
        val name = UserPreferences.getName(this)
        findViewById<TextView>(R.id.tvWorkoutGreeting).text = "Hi, $name"

        // Search
//        findViewById<LinearLayout>(R.id.layoutWorkoutSearch).setOnClickListener {
//            startActivity(Intent(this, WorkoutListActivity::class.java))
//        }

        // Category header + View All
        findViewById<TextView>(R.id.tvCategoryViewAll).setOnClickListener {
            startActivity(Intent(this, WorkoutCategoryActivity::class.java))
        }

        // Workouts View All
        findViewById<TextView>(R.id.tvWorkoutsViewAll).setOnClickListener {
            startActivity(Intent(this, WorkoutListActivity::class.java))
        }

        // Exercises View All
        findViewById<TextView>(R.id.tvExercisesViewAll).setOnClickListener {
            startActivity(Intent(this, ExerciseListActivity::class.java))
        }

        setupBottomNav()
        loadData()
    }

    override fun onResume() { super.onResume(); loadData() }

    private fun loadData() {
        scope.launch {
            try {

                // Show calendar immediately from prefs (no network wait)
                buildWeekCalendar(UserPreferences.getExerciseDays(this@WorkoutHomeActivity))

                val sugResp = withContext(Dispatchers.IO) { RetrofitClient.api.getSuggestedWorkouts(userId) }
                if (sugResp.isSuccessful && sugResp.body()?.data != null) {
                    val sugData = sugResp.body()!!.data!!
                    val sessionsPerDay = sugData.sessionsPerDay   // e.g. 2
                    val weekTotal      = sugData.weekTotal         // e.g. 8

                    // Save for HomeActivity
                    UserPreferences.saveWeekTotal(this@WorkoutHomeActivity, weekTotal)
                    UserPreferences.saveSessionsPerDay(this@WorkoutHomeActivity, sessionsPerDay)

                    // Rebuild calendar with accurate exerciseDays from API
                    buildWeekCalendar(sugData.exerciseDays)

                    bindSuggestedOptions(sugData.workouts)

                    val progressResp = withContext(Dispatchers.IO) { RetrofitClient.api.getWorkoutProgress(userId) }
                    if (progressResp.isSuccessful && progressResp.body()?.data != null) {
                        val prog = progressResp.body()!!.data!!
                        // Status text
                        findViewById<TextView>(R.id.tvTodayWorkoutStatus)?.text =
                            "${prog.todaySessions}/$sessionsPerDay sessions today"
                        // Progress bar fills based on TODAY's sessions vs daily target
                        findViewById<ProgressBar>(R.id.progressTodayWorkout)?.progress =
                            if (sessionsPerDay > 0) (prog.todaySessions * 100) / sessionsPerDay else 0
                        // The three stat bubbles: Sessions = today's, not week
                        findViewById<TextView>(R.id.tvWeekWorkouts)?.text  = "${prog.todaySessions}/$sessionsPerDay"
                        findViewById<TextView>(R.id.tvWeekCalories)?.text  = "${prog.todayCalories}"
                        findViewById<TextView>(R.id.tvWeekMinutes)?.text   = "${prog.todayMinutes}"
                    }
                }

                // Load popular workouts (limit 4)
                val wkResp = withContext(Dispatchers.IO) { RetrofitClient.api.getWorkouts(limit = 4) }
                if (wkResp.isSuccessful) bindPopularWorkouts(wkResp.body()?.data ?: emptyList())

                // Load this week's workout plan
                val planResp = withContext(Dispatchers.IO) { RetrofitClient.api.getWeekWorkoutPlan(userId) }
                if (planResp.isSuccessful && planResp.body()?.data != null) {
                    bindWeekPlan(planResp.body()!!.data!!)
                }

                // Load exercises (limit 4)
                val exResp = withContext(Dispatchers.IO) { RetrofitClient.api.getExercises(limit = 4) }
                if (exResp.isSuccessful) bindExercises(exResp.body()?.data ?: emptyList())

            } catch (e: Exception) {
                Toast.makeText(this@WorkoutHomeActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindWorkoutProgress(progress: WorkoutProgress) {
        val tvStatus = try { findViewById<TextView>(R.id.tvTodayWorkoutStatus) } catch (e: Exception) { return }
        val tvCalories = try { findViewById<TextView>(R.id.tvTodayWorkoutCalories) } catch (e: Exception) { null }
        val progressBar = try { findViewById<ProgressBar>(R.id.progressTodayWorkout) } catch (e: Exception) { null }
        val tvWeekWorkouts = try { findViewById<TextView>(R.id.tvWeekWorkouts) } catch (e: Exception) { null }
        val tvWeekCalories = try { findViewById<TextView>(R.id.tvWeekCalories) } catch (e: Exception) { null }
        val tvWeekMinutes  = try { findViewById<TextView>(R.id.tvWeekMinutes)  } catch (e: Exception) { null }

        tvStatus.text = when {
            progress.todayDone && progress.lastWorkoutName.isNotBlank() ->
                "✓ ${progress.lastWorkoutName} — ${progress.todayMinutes} min"
            progress.todayDone -> "✓ Workout done today!"
            else -> "No workout yet today"
        }
        tvStatus.setTextColor(if (progress.todayDone) 0xFF33000000.toInt() else 0xFF9CA3AF.toInt())

        if (progress.todayCalories > 0) {
            tvCalories?.text = "${progress.todayCalories} kcal"
        }

        progressBar?.progress = progress.todayProgressPct

        // Stat bubbles show TODAY's numbers (despite the "Week" prefix in IDs)
        tvWeekWorkouts?.text  = "${progress.todaySessions}"
        tvWeekCalories?.text  = "${progress.todayCalories}"
        tvWeekMinutes?.text   = "${progress.todayMinutes}"
    }

    private fun buildWeekCalendar(exerciseDays: Int) {
        val container = try { findViewById<android.widget.LinearLayout>(R.id.containerWeekCalendar) }
        catch (e: Exception) { return }
        container.removeAllViews()

        val today     = java.time.LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1) // Monday

        // Which day-of-week indices (0=Mon) are workout days
        val workoutDayIndices: Set<Int> = when (exerciseDays) {
            1    -> setOf(0)
            2    -> setOf(0, 3)
            3    -> setOf(0, 2, 4)
            4    -> setOf(0, 1, 3, 4)
            5    -> setOf(0, 1, 2, 3, 4)
            6    -> setOf(0, 1, 2, 3, 4, 5)
            else -> setOf(0, 1, 2, 3, 4, 5, 6)
        }

        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

        (0..6).forEach { offset ->
            val date      = weekStart.plusDays(offset.toLong())
            val isWorkout = offset in workoutDayIndices
            val isToday   = date == today
            val isPast    = date.isBefore(today)

            val cell = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                val lp = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }

            // Day letter
            val tvDay = android.widget.TextView(this).apply {
                text = dayLabels[offset]
                textSize = 10f
                setTextColor(if (isToday) 0xFFC3E66E.toInt() else 0xFF9CA3AF.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Day number circle
            val tvNum = android.widget.TextView(this).apply {
                text = "${date.dayOfMonth}"
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                val size = (36 * resources.displayMetrics.density).toInt()
                val lp = android.widget.LinearLayout.LayoutParams(size, size)
                lp.topMargin = (4 * resources.displayMetrics.density).toInt()
                layoutParams = lp

                // Colors based on state
                when {
                    isToday && isWorkout -> {
                        setBackgroundColor(0xFFC3E66E.toInt())
                        setTextColor(0xFFFFFFFF.toInt())
                    }
                    isToday -> {
                        setBackgroundColor(0xFFC3E66E.toInt())
                        setTextColor(0xFF000000.toInt())
                    }
                    isWorkout && isPast -> {
                        // Past workout day — show green (done or missed)
                        setBackgroundColor(0xFFDCFCE7.toInt())
                        setTextColor(0xFF22C55E.toInt())
                    }
                    isWorkout -> {
                        // Future workout day — orange outline style
                        setBackgroundColor(0xFFF5FDE6.toInt())
                        setTextColor(0xFFC3E66E.toInt())
                    }
                    else -> {
                        // Rest day
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(0xFF9CA3AF.toInt())
                    }
                }
            }

            // Small dot below for workout days
            val dot = android.widget.TextView(this).apply {
                text = if (isWorkout) "•" else " "
                textSize = 10f
                setTextColor(if (isWorkout) 0xFFC3E66E.toInt() else android.graphics.Color.TRANSPARENT)
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            cell.addView(tvDay)
            cell.addView(tvNum)
            cell.addView(dot)
            container.addView(cell)
        }

        // Update week range label
        try {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d")
            val weekEnd = weekStart.plusDays(6)
            findViewById<android.widget.TextView>(R.id.tvWeekRange).text =
                "${weekStart.format(fmt)} – ${weekEnd.format(fmt)}"
        } catch (e: Exception) {}
    }

    private fun bindSuggestedOptions(options: List<WorkoutItem>) {
        val container = findViewById<LinearLayout>(R.id.containerSuggestedWorkouts)
        container.removeAllViews()

        options.forEach { w ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_suggested_workout_card, container, false)

            val img = card.findViewById<ImageView>(R.id.imgSuggestedCard)
            if (w.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(w.imageUrl)
                    .apply(RequestOptions().transform(CenterCrop(), RoundedCorners(24)))
                    .into(img)
            }
            card.findViewById<TextView>(R.id.tvSuggestedCardName).text       = w.workoutName
            card.findViewById<TextView>(R.id.tvSuggestedCardCategory).text   = w.category
            card.findViewById<TextView>(R.id.tvSuggestedCardDuration).text   = "${w.durationMinutes} min"
            card.findViewById<TextView>(R.id.tvSuggestedCardCalories).text   = "${w.caloriesBurnedEstimate} kcal"
            card.findViewById<TextView>(R.id.tvSuggestedCardDifficulty).text = w.difficulty

            if (w.isPremium) {
                card.findViewById<TextView>(R.id.tvSuggestedCardPremium).visibility = android.view.View.VISIBLE
            }

            card.findViewById<LinearLayout>(R.id.btnSuggestedStart).setOnClickListener {
                startActivity(Intent(this, WorkoutDetailActivity::class.java).apply {
                    putExtra("workout_id", w.workoutId)
                })
            }
            container.addView(card)
        }
    }

    private fun bindWeekPlan(plan: List<WeekPlanEntry>) {
        val container = try { findViewById<android.widget.LinearLayout>(R.id.containerWeekPlan) }
        catch (e: Exception) { return }
        container.removeAllViews()

        if (plan.isEmpty()) {
            // No plan yet — prompt user to open suggested workouts to generate it
            val tv = android.widget.TextView(this).apply {
                text = "Open a workout to generate your personal plan!"
                textSize = 12f
                setTextColor(0xFF9CA3AF.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            container.addView(tv)
            return
        }

        plan.forEach { entry ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (1 * resources.displayMetrics.density).toInt()
                layoutParams = lp
                setPadding(0,
                    (10 * resources.displayMetrics.density).toInt(), 0,
                    (10 * resources.displayMetrics.density).toInt())
                setBackgroundColor(when {
                    entry.isToday -> 0xFFF5FDE6.toInt()
                    entry.status == "completed" -> 0xFFF0FDF4.toInt()
                    else -> android.graphics.Color.TRANSPARENT
                })
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(android.content.Intent(this@WorkoutHomeActivity,
                        WorkoutDetailActivity::class.java).apply {
                        putExtra("workout_id", entry.workoutId)
                    })
                }
            }

            // Day badge (e.g. "Mon
            val dayCol = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                val size = (52 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
                setBackgroundColor(when {
                    entry.isToday -> 0xFFC3E66E.toInt()
                    entry.status == "completed" -> 0xFF22C55E.toInt()
                    entry.isPast -> 0xFFE5E7EB.toInt()
                    else -> 0xFFF9FAFB.toInt()
                })
            }
            val tvDayLabel = android.widget.TextView(this).apply {
                text = entry.dayLabel
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(when {
                    entry.isToday || entry.status == "completed" -> 0xFFFFFFFF.toInt()
                    else -> 0xFF9CA3AF.toInt()
                })
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val tvDayNum = android.widget.TextView(this).apply {
                text = "${entry.dayNumber}"
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(when {
                    entry.isToday || entry.status == "completed" -> 0xFFFFFFFF.toInt()
                    else -> 0xFF374151.toInt()
                })
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            dayCol.addView(tvDayLabel)
            dayCol.addView(tvDayNum)

            // Workout info
            val infoCol = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val lp = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = (14 * resources.displayMetrics.density).toInt()
                layoutParams = lp
            }
            val tvName = android.widget.TextView(this).apply {
                text = entry.workoutName
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(if (entry.isToday) 0xFFC3E66E.toInt() else 0xFF171717.toInt())
            }
            val tvMeta = android.widget.TextView(this).apply {
                text = "${entry.category} · ${entry.durationMinutes} min · ${entry.caloriesBurnedEstimate} kcal"
                textSize = 11f
                setTextColor(0xFF9CA3AF.toInt())
            }
            infoCol.addView(tvName)
            infoCol.addView(tvMeta)

            // Status badge
            val tvStatus = android.widget.TextView(this).apply {
                text = when (entry.status) {
                    "completed" -> "✓"
                    else        -> if (entry.isToday) "Today" else "›"
                }
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(when {
                    entry.status == "completed" -> 0xFF22C55E.toInt()
                    entry.isToday -> 0xFFC3E66E.toInt()
                    else -> 0xFF9CA3AF.toInt()
                })
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginStart = (8 * resources.displayMetrics.density).toInt()
                layoutParams = lp
            }

            row.addView(dayCol)
            row.addView(infoCol)
            row.addView(tvStatus)
            container.addView(row)
        }
    }

    private fun bindPopularWorkouts(workouts: List<WorkoutItem>) {
        val container = findViewById<LinearLayout>(R.id.containerPopularWorkouts)
        container.removeAllViews()
        val total = findViewById<TextView>(R.id.tvWorkoutsCount)
        total.text = "Workouts: 70"

        workouts.forEach { workout ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_workout_card, container, false)

            val img = card.findViewById<ImageView>(R.id.imgWorkoutCard)
            if (workout.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(workout.imageUrl)
                    .apply(RequestOptions().transform(CenterCrop(), RoundedCorners(24)))
                    .into(img)
            }
            card.findViewById<TextView>(R.id.tvWorkoutCardName).text = workout.workoutName
            card.findViewById<TextView>(R.id.tvWorkoutCardDifficulty).text = workout.difficulty
            card.findViewById<TextView>(R.id.tvWorkoutCardDuration).text = "${workout.durationMinutes} min"

            card.setOnClickListener {
                startActivity(Intent(this, WorkoutDetailActivity::class.java).apply {
                    putExtra("workout_id", workout.workoutId)
                })
            }
            container.addView(card)
        }
    }

    private fun bindExercises(exercises: List<ExerciseItem>) {
        val container = findViewById<LinearLayout>(R.id.containerExercises)
        container.removeAllViews()
        val total = findViewById<TextView>(R.id.tvExercisesCount)
        total.text = "Exercises: 70"

        exercises.forEach { exercise ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_exercise_row, container, false)

            val img = row.findViewById<ImageView>(R.id.imgExerciseRow)
            if (exercise.imageUrl.isNotEmpty()) {
                Glide.with(this).load(exercise.imageUrl).centerCrop().into(img)
            }
            row.findViewById<TextView>(R.id.tvExerciseRowName).text = exercise.exerciseName
            row.findViewById<TextView>(R.id.tvExerciseRowMuscle).text = exercise.muscleGroup

            row.setOnClickListener {
                showExerciseDetail(exercise)
            }
            container.addView(row)
        }
    }

    private fun showExerciseDetail(exercise: ExerciseItem) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_exercise_detail)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        val img = dialog.findViewById<ImageView>(R.id.imgExerciseDetail)
        if (exercise.imageUrl.isNotEmpty()) {
            Glide.with(this).load(exercise.imageUrl).centerCrop().into(img)
        }
        dialog.findViewById<TextView>(R.id.tvExerciseDetailName).text  = exercise.exerciseName
        dialog.findViewById<TextView>(R.id.tvExerciseDetailMuscle).text = exercise.muscleGroup
        dialog.findViewById<TextView>(R.id.tvExerciseDetailEquip).text  = exercise.equipmentNeeded
        dialog.findViewById<TextView>(R.id.tvExerciseDetailDesc).text   = exercise.description
        dialog.findViewById<TextView>(R.id.btnCloseExerciseDetail).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener {
            startActivity(Intent(this, DietActivity::class.java)); finish()
        }
        listOf(R.id.navStats, R.id.navProfile).forEach { id ->
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}