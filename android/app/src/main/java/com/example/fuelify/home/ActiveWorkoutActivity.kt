package com.example.fuelify.home

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import java.util.Locale

class ActiveWorkoutActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Workout data
    private var exercises = listOf<WorkoutExerciseItem>()
    private var currentIndex = 0
    private var workoutId   = -1
    private var workoutName = ""
    private var caloriesBurnedEstimate = 0
    private var userId = -1

    // Timers
    private var elapsedTimer: CountDownTimer? = null
    private var exerciseTimer: CountDownTimer? = null
    private var totalElapsedSeconds = 0
    private var exerciseSecondsLeft = 0
    private var isPaused = false
    private var startTimeMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_workout)

        userId              = UserPreferences.getUserId(this)
        workoutId           = intent.getIntExtra("workout_id", -1)
        workoutName         = intent.getStringExtra("workout_name") ?: "Workout"
        caloriesBurnedEstimate = intent.getIntExtra("calories_estimate", 300)

        // Get exercises from intent
        @Suppress("UNCHECKED_CAST")
        val exList = intent.getSerializableExtra("exercises") as? ArrayList<WorkoutExerciseItem>
        if (exList.isNullOrEmpty()) { finish(); return }
        exercises = exList

        startTimeMillis = SystemClock.elapsedRealtime()
        startElapsedTimer()
        loadExercise(0)

        // Pause / Resume
        findViewById<LinearLayout>(R.id.btnPauseWorkout).setOnClickListener {
            if (isPaused) resumeWorkout() else pauseWorkout()
        }

        // Next Exercise — skip current, move to next
        findViewById<LinearLayout>(R.id.btnNextExercise).setOnClickListener {
            exerciseTimer?.cancel()
            moveToNext()
        }

        // Finish early
        findViewById<LinearLayout>(R.id.btnFinishWorkout).setOnClickListener {
            showFinishConfirmation()
        }
    }

    // ── Total elapsed timer (counts up) ───────────────────────────────────────

    private fun startElapsedTimer() {
        elapsedTimer?.cancel()
        elapsedTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused) {
                    totalElapsedSeconds++
                    val mins = totalElapsedSeconds / 60
                    val secs = totalElapsedSeconds % 60
                    val display = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                    runOnUiThread {
                        try {
                            findViewById<TextView>(R.id.tvWorkoutElapsed).text = display
                        } catch (_: Exception) {}
                    }
                }
            }
            override fun onFinish() {}
        }.start()
    }

    // ── Load exercise at index ─────────────────────────────────────────────────

    private fun loadExercise(index: Int) {
        currentIndex = index
        val exercise = exercises[index]

        // Progress label
        findViewById<TextView>(R.id.tvExerciseProgress).text =
            "Exercise ${index + 1}/${exercises.size}"

        // Exercise image
        val img = findViewById<ImageView>(R.id.imgActiveExercise)
        if (exercise.imageUrl.isNotEmpty()) {
            Glide.with(this).load(exercise.imageUrl).centerCrop().into(img)
        }

        // Exercise name
        findViewById<TextView>(R.id.tvActiveExerciseName).text = exercise.exerciseName

        // Split sets and reps into separate views
        try {
            findViewById<TextView>(R.id.tvActiveSets).text = "${exercise.sets}"
            findViewById<TextView>(R.id.tvActiveReps).text = "${exercise.reps}"
        } catch (e: Exception) { /* fallback */ }

        // Update progress bar
        try {
            val progress = ((index + 1) * 100) / exercises.size
            findViewById<android.widget.ProgressBar>(R.id.progressExercise).progress = progress
        } catch (e: Exception) { /* ignore */ }

        // Exercise countdown (30 sec per exercise if no rest defined, else reps shown)
        // restSeconds = rest time after exercise; if 0, default to 30s
        val durationSecs = if (exercise.restSeconds > 0) exercise.restSeconds else 30
        startExerciseTimer(durationSecs)

        // Next exercise preview
        val nextContainer = findViewById<LinearLayout>(R.id.layoutNextExercise)
        if (index + 1 < exercises.size) {
            val next = exercises[index + 1]
            nextContainer.visibility = View.VISIBLE
            val nextImg = nextContainer.findViewById<ImageView>(R.id.imgNextExercise)
            if (next.imageUrl.isNotEmpty()) {
                Glide.with(this).load(next.imageUrl).centerCrop().into(nextImg)
            }
            nextContainer.findViewById<TextView>(R.id.tvNextExerciseName).text  = next.exerciseName
            nextContainer.findViewById<TextView>(R.id.tvNextExerciseSets).text  =
                if (next.sets > 1) "${next.sets}×${next.reps}" else "×${next.reps}"

            nextContainer.findViewById<View>(R.id.btnNextExerciseInfo).setOnClickListener {
                showExerciseInfo(next)
            }
        } else {
            nextContainer.visibility = View.GONE
        }

        // Info button for current
        findViewById<View>(R.id.btnCurrentExerciseInfo).setOnClickListener {
            showExerciseInfo(exercise)
        }
    }

    private fun startExerciseTimer(seconds: Int) {
        exerciseTimer?.cancel()
        exerciseSecondsLeft = seconds
        updateExerciseTimerDisplay(seconds)

        exerciseTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused) {
                    exerciseSecondsLeft = (millisUntilFinished / 1000).toInt()
                    updateExerciseTimerDisplay(exerciseSecondsLeft)
                }
            }
            override fun onFinish() {
                if (!isPaused) moveToNext()
            }
        }.start()
    }

    private fun updateExerciseTimerDisplay(seconds: Int) {
        val mins = seconds / 60
        val secs = seconds % 60
        try {
            findViewById<TextView>(R.id.tvExerciseTimer).text =
                String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
        } catch (_: Exception) {}
    }

    private fun moveToNext() {
        if (currentIndex + 1 < exercises.size) {
            loadExercise(currentIndex + 1)
        } else {
            finishWorkout()
        }
    }

    // ── Pause / Resume ────────────────────────────────────────────────────────

    private fun pauseWorkout() {
        isPaused = true
        exerciseTimer?.cancel()
        elapsedTimer?.cancel()
        val btnText = findViewById<TextView>(R.id.tvPauseLabel)
        btnText.text = "Resume"
    }

    private fun resumeWorkout() {
        isPaused = false
        startElapsedTimer()
        startExerciseTimer(exerciseSecondsLeft)
        val btnText = findViewById<TextView>(R.id.tvPauseLabel)
        btnText.text = "Pause"
    }

    // ── Exercise info sheet ───────────────────────────────────────────────────

    private fun showExerciseInfo(exercise: WorkoutExerciseItem) {
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
        dialog.findViewById<TextView>(R.id.tvExerciseDetailName).text   = exercise.exerciseName
        dialog.findViewById<TextView>(R.id.tvExerciseDetailMuscle).text = exercise.muscleGroup
        dialog.findViewById<TextView>(R.id.tvExerciseDetailEquip).text  = exercise.equipmentNeeded
        dialog.findViewById<TextView>(R.id.tvExerciseDetailDesc).text   = exercise.description
        dialog.findViewById<TextView>(R.id.btnCloseExerciseDetail).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ── Finish ────────────────────────────────────────────────────────────────

    private fun showFinishConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Finish Workout?")
            .setMessage("You've completed ${currentIndex + 1} of ${exercises.size} exercises.")
            .setPositiveButton("Finish") { _, _ -> finishWorkout() }
            .setNegativeButton("Continue", null)
            .show()
    }

    private fun finishWorkout() {
        exerciseTimer?.cancel()
        elapsedTimer?.cancel()

        val exercisesDone = (currentIndex + 1).coerceAtMost(exercises.size)
        val totalExercises = exercises.size.coerceAtLeast(1)

        // Scale calories by: how many exercises completed vs total
        // If all done → full estimate. If half done → half.
        // Also scale slightly by actual duration vs expected (durationMinutes * 60)
        val completionRatio = exercisesDone.toFloat() / totalExercises.toFloat()
        val calories = (caloriesBurnedEstimate * completionRatio).toInt()
            .coerceAtLeast(caloriesBurnedEstimate / 4)  // at least 25% if any exercises done

        // Launch summary
        val intent = Intent(this, WorkoutSummaryActivity::class.java).apply {
            putExtra("workout_id",        workoutId)
            putExtra("workout_name",      workoutName)
            putExtra("duration_seconds",  totalElapsedSeconds)
            putExtra("calories_burned",   calories)
            putExtra("exercises_done",    exercisesDone)
            putExtra("total_exercises",   totalExercises)
            putExtra("workout_image",     exercises.firstOrNull()?.imageUrl ?: "")
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        exerciseTimer?.cancel()
        elapsedTimer?.cancel()
        scope.cancel()
    }
}
