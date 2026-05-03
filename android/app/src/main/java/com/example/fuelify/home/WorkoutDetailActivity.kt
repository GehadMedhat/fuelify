package com.example.fuelify.home

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.WorkoutDetail
import com.example.fuelify.data.api.models.WorkoutExerciseItem
import kotlinx.coroutines.*

class WorkoutDetailActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val workoutId by lazy { intent.getIntExtra("workout_id", -1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_detail)

        findViewById<ImageButton>(R.id.btnWorkoutDetailBack).setOnClickListener { finish() }

        if (workoutId == -1) { finish(); return }
        loadWorkoutDetail()
    }
    private fun getEquipmentImage(name: String): Int {
        return when (name.lowercase()) {
            "dumbbells" -> R.drawable.dumbbells
            "barbell" -> R.drawable.barbell
            "yoga mat" -> R.drawable.yoga_mat
            "boxing gloves" -> R.drawable.boxing_gloves
            "jump rope" -> R.drawable.jump_rope
            "heavy bag" -> R.drawable.heavy_bag
            "bench" -> R.drawable.bench
            "pull-up bar" -> R.drawable.pullup_bar
            "cable machine" -> R.drawable.cable_machine
            "leg machine" -> R.drawable.leg_machine
            "kettlebell" -> R.drawable.kettlebell
            "plyo box" -> R.drawable.plyo_box
            else -> R.drawable.dumbbells
        }
    }
    private fun loadWorkoutDetail() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getWorkoutDetail(workoutId)
                }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    bindDetail(resp.body()!!.data!!)
                } else {
                    Toast.makeText(this@WorkoutDetailActivity, "Could not load workout", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WorkoutDetailActivity, "Network error", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun bindDetail(detail: WorkoutDetail) {
        // Hero image
        val img = findViewById<ImageView>(R.id.imgWorkoutDetailHero)
        if (detail.imageUrl.isNotEmpty()) {
            Glide.with(this).load(detail.imageUrl).centerCrop().into(img)
        }

        // Title & category
        findViewById<TextView>(R.id.tvWorkoutDetailName).text     = detail.workoutName
        findViewById<TextView>(R.id.tvWorkoutDetailCategory).text = detail.category

        // Stats chips
        findViewById<TextView>(R.id.tvWorkoutDetailDuration).text   = "${detail.durationMinutes} min"
        findViewById<TextView>(R.id.tvWorkoutDetailCalories).text   = "${detail.caloriesBurnedEstimate} kal"
        findViewById<TextView>(R.id.tvWorkoutDetailDifficulty).text = detail.difficulty

        // Equipment
        val equipContainer = findViewById<LinearLayout>(R.id.containerWorkoutEquipment)
        equipContainer.removeAllViews()

        val equipCount = findViewById<TextView>(R.id.tvEquipmentCount)

        val equipList = detail.equipment.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "None" }

        equipCount.text = if (equipList.isEmpty()) "No equipment" else "${equipList.size} items"

        for (item in equipList) {
            val itemView = layoutInflater.inflate(R.layout.item_equipment_chip, equipContainer, false)

            val img = itemView.findViewById<ImageView>(R.id.imgEquipment)
            val txt = itemView.findViewById<TextView>(R.id.tvEquipmentChipName)

            txt.text = item
            img.setImageResource(getEquipmentImage(item))

            equipContainer.addView(itemView)
        }

        // Premium badge
        if (detail.isPremium) {
            findViewById<TextView>(R.id.tvWorkoutDetailPremium).visibility = android.view.View.VISIBLE
        }

        // Exercises list
        val exContainer = findViewById<LinearLayout>(R.id.containerWorkoutExercises)
        exContainer.removeAllViews()

        detail.exercises.forEachIndexed { idx, exercise ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_workout_exercise_row, exContainer, false)

            val img2 = row.findViewById<ImageView>(R.id.imgWorkoutExerciseRow)
            if (exercise.imageUrl.isNotEmpty()) {
                Glide.with(this).load(exercise.imageUrl).centerCrop().into(img2)
            }

            row.findViewById<TextView>(R.id.tvWorkoutExerciseName).text =
                exercise.exerciseName
            row.findViewById<TextView>(R.id.tvWorkoutExerciseSets).text =
                if (exercise.sets > 1) "${exercise.sets}x${exercise.reps}" else "x${exercise.reps}"

            // Info button → show exercise detail sheet
            row.findViewById<View>(R.id.btnWorkoutExerciseInfo).setOnClickListener {
                showExerciseSheet(exercise)
            }

            // Rest row if needed
            if (exercise.restSeconds > 0 && idx < detail.exercises.size - 1) {
                val restRow = LayoutInflater.from(this).inflate(R.layout.item_rest_row, exContainer, false)
                restRow.findViewById<TextView>(R.id.tvRestDuration).text =
                    "${exercise.restSeconds / 60}:${String.format("%02d", exercise.restSeconds % 60)}"
                exContainer.addView(row)
                exContainer.addView(restRow)
            } else {
                exContainer.addView(row)
            }
        }

        // Custom Workout button
        findViewById<LinearLayout>(R.id.btnCustomWorkout).setOnClickListener {
            startActivity(Intent(this, CustomWorkoutActivity::class.java))
        }

        // Start Workout button → launch active workout timer
        findViewById<LinearLayout>(R.id.btnStartWorkout).setOnClickListener {
            val exerciseList = ArrayList(detail.exercises)
            val intent = Intent(this, ActiveWorkoutActivity::class.java).apply {
                putExtra("workout_id",        detail.workoutId)
                putExtra("workout_name",      detail.workoutName)
                putExtra("calories_estimate", detail.caloriesBurnedEstimate)
                putExtra("exercises",         exerciseList)
            }
            startActivity(intent)
        }
    }

    private fun showExerciseSheet(exercise: WorkoutExerciseItem) {
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

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
