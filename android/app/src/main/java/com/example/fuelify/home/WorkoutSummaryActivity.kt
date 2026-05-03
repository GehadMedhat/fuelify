package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

class WorkoutSummaryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_summary)

        val workoutId      = intent.getIntExtra("workout_id", -1)
        val workoutName    = intent.getStringExtra("workout_name") ?: "Custom Workout"
        val durationSecs   = intent.getIntExtra("duration_seconds", 0)
        val caloriesBurned = intent.getIntExtra("calories_burned", 0)
        val exercisesDone  = intent.getIntExtra("exercises_done", 0)
        val workoutImage   = intent.getStringExtra("workout_image") ?: ""
        val userId         = UserPreferences.getUserId(this)

        // Back
        findViewById<ImageButton>(R.id.btnSummaryBack).setOnClickListener { finish() }

        // Workout info row
        val img = findViewById<ImageView>(R.id.imgSummaryWorkout)
        if (workoutImage.isNotEmpty()) {
            Glide.with(this).load(workoutImage).centerCrop().into(img)
        }
        findViewById<TextView>(R.id.tvSummaryWorkoutName).text = workoutName

        // Time range
        val now   = LocalDateTime.now()
        val start = now.minusSeconds(durationSecs.toLong())
        val fmt   = DateTimeFormatter.ofPattern("HH:mm")
        findViewById<TextView>(R.id.tvSummaryTimeRange).text =
            "${start.format(fmt)} - ${now.format(fmt)}"

        // Total time
        val hrs  = durationSecs / 3600
        val mins = (durationSecs % 3600) / 60
        val secs = durationSecs % 60
        val timeStr = if (hrs > 0)
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
        else
            String.format(Locale.getDefault(), "00:%02d:%02d", mins, secs)
        findViewById<TextView>(R.id.tvSummaryTotalTime).text = timeStr

        // Calories
        val activeCal = (caloriesBurned * 0.85).roundToInt()
        val totalCal  = caloriesBurned
        findViewById<TextView>(R.id.tvSummaryActiveCal).text = "$activeCal"
        findViewById<TextView>(R.id.tvSummaryTotalCal).text  = "$totalCal"

        // Simulated heart rate
        val avgHr = Random.nextInt(130, 175)
        findViewById<TextView>(R.id.tvSummaryAvgHR).text = "$avgHr"

        // Exercises done
        findViewById<TextView>(R.id.tvSummaryExercises).text = "$exercisesDone exercises"

        // Save workout button
        val btnSave = findViewById<LinearLayout>(R.id.btnSaveWorkout)
        btnSave.setOnClickListener {
            if (isSaved) {
                val intent = Intent(this, WorkoutHomeActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                saveWorkout(
                    workoutId,
                    workoutName,
                    durationSecs,
                    caloriesBurned,
                    exercisesDone,
                    userId
                )
            }
        }
    }

    private fun saveWorkout(
        workoutId: Int, workoutName: String,
        durationSecs: Int, caloriesBurned: Int, exercisesDone: Int, userId: Int
    ) {
        isSaved = true
        val btnSave = findViewById<LinearLayout>(R.id.btnSaveWorkout)
        val btnText = btnSave.getChildAt(0) as? TextView
        btnText?.text = "Saving..."

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.saveWorkoutSession(
                        userId,
                        com.example.fuelify.data.api.models.SaveWorkoutSessionRequest(
                            workoutId       = if (workoutId > 0) workoutId else null,
                            workoutName     = workoutName,
                            durationSeconds = durationSecs,
                            caloriesBurned  = caloriesBurned,
                            exercisesDone   = exercisesDone,
                            isCustom        = workoutId <= 0
                        )
                    )
                }
                if (resp.isSuccessful) {
                    isSaved = true

                    btnText?.text = "Go Back Home"
                    btnSave.setBackgroundColor(0xFFFFA157.toInt())

                    Toast.makeText(
                        this@WorkoutSummaryActivity,
                        "Great job! 💪 Workout saved!",
                        Toast.LENGTH_SHORT
                    ).show()
                }else {
                    isSaved = false
                    btnText?.text = "Save Workout"
                    val errorBody = resp.errorBody()?.string() ?: "Unknown error"
                    val code = resp.code()
                    Toast.makeText(this@WorkoutSummaryActivity,
                        "Failed ($code): $errorBody", Toast.LENGTH_LONG).show()
                    android.util.Log.e("WorkoutSave", "HTTP $code — $errorBody")
                }
            } catch (e: Exception) {
                isSaved = false
                btnText?.text = "Save Workout"
                android.util.Log.e("WorkoutSave", "Exception: ${e.message}", e)
                Toast.makeText(this@WorkoutSummaryActivity,
                    "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
