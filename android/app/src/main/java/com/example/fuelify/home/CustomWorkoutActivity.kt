package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class CustomWorkoutActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1
    private var selectedLevel = "Beginner"
    private var includesWarmUp = true
    private var includesStretching = true
    private var selectedEquipment = mutableListOf<String>()
    private var selectedFocusAreas = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_workout)

        userId = UserPreferences.getUserId(this)
        setupLevelSelection()
        setupToggles()

        // Equipment row
        findViewById<LinearLayout>(R.id.rowChooseEquipment).setOnClickListener {
            showMultiSelectDialog("Choose Equipment",
                listOf("Dumbbells", "Barbell", "Pull-up Bar", "Yoga Mat", "Kettlebell",
                    "Resistance Bands", "Bench", "None"),
                selectedEquipment) { selected ->
                selectedEquipment = selected.toMutableList()
                findViewById<TextView>(R.id.tvEquipmentSelection).text =
                    if (selected.isEmpty()) "None" else selected.joinToString(", ")
            }
        }

        // Focus area row
        findViewById<LinearLayout>(R.id.rowChooseFocus).setOnClickListener {
            showMultiSelectDialog("Choose Focus Area",
                listOf("Legs", "Core muscles", "Chest", "Back", "Shoulders",
                    "Arms", "Glutes", "Full Body", "Cardio"),
                selectedFocusAreas) { selected ->
                selectedFocusAreas = selected.toMutableList()
                findViewById<TextView>(R.id.tvFocusSelection).text =
                    if (selected.isEmpty()) "Any" else selected.joinToString(", ")
            }
        }

        // Create workout button
        findViewById<LinearLayout>(R.id.btnCreateWorkout).setOnClickListener {
            createCustomWorkout()
        }

        // Back
        findViewById<ImageButton>(R.id.btnCustomBack).setOnClickListener { finish() }
    }

    private fun setupLevelSelection() {
        val levels = mapOf(
            R.id.layoutBeginner     to "Beginner",
            R.id.layoutIrregular    to "Irregular",
            R.id.layoutMedium       to "Medium",
            R.id.layoutAdvanced     to "Advanced"
        )

        levels.forEach { (viewId, level) ->
            val layout = findViewById<LinearLayout>(viewId)
            layout.setOnClickListener {
                selectedLevel = level
                updateLevelUI(levels)
            }
        }
        updateLevelUI(levels)
    }

    private fun updateLevelUI(levels: Map<Int, String>) {
        levels.forEach { (viewId, level) ->
            val layout = findViewById<LinearLayout>(viewId)
            if (level == selectedLevel) {
                layout.setBackgroundResource(R.drawable.bg_picker_selected)
            } else {
                layout.setBackgroundResource(R.drawable.bg_recommended_card)
            }
        }
    }

    private fun setupToggles() {
        val toggleWarmUp   = findViewById<Switch>(R.id.switchWarmUp)
        val toggleStretch  = findViewById<Switch>(R.id.switchStretching)

        toggleWarmUp.isChecked   = includesWarmUp
        toggleStretch.isChecked  = includesStretching

        toggleWarmUp.setOnCheckedChangeListener  { _, checked -> includesWarmUp = checked }
        toggleStretch.setOnCheckedChangeListener { _, checked -> includesStretching = checked }
    }

    private fun showMultiSelectDialog(
        title: String,
        options: List<String>,
        current: List<String>,
        onConfirm: (List<String>) -> Unit
    ) {
        val selected = current.toMutableList()
        val checkedItems = options.map { it in selected }.toBooleanArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(options.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (isChecked) selected.add(options[which])
                else selected.remove(options[which])
            }
            .setPositiveButton("Done") { _, _ -> onConfirm(selected) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCustomWorkout() {
        val nameField = findViewById<EditText>(R.id.etWorkoutName)
        val name = nameField.text.toString().trim().ifEmpty { "My Custom Workout" }

        // Map level to difficulty
        val difficulty = when (selectedLevel) {
            "Irregular" -> "Beginner"
            "Advanced"  -> "Advanced"
            "Medium"    -> "Medium"
            else        -> "Beginner"
        }

        // Build category from focus areas
        val category = when {
            selectedFocusAreas.any { it.contains("Leg", true) || it.contains("Glute", true) } -> "Gym"
            selectedFocusAreas.any { it.contains("Chest", true) || it.contains("Back", true) || it.contains("Shoulder", true) } -> "Upper Body"
            selectedFocusAreas.any { it.contains("Cardio", true) } -> "Running"
            selectedFocusAreas.any { it.contains("Core", true) } -> "Gym"
            else -> "Gym"
        }

        // Query backend for matching workouts
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getWorkouts(category = category, difficulty = difficulty, limit = 1)
                }

                if (resp.isSuccessful && !resp.body()?.data.isNullOrEmpty()) {
                    val workout = resp.body()!!.data!!.first()
                    // Load full detail and launch
                    val detailResp = withContext(Dispatchers.IO) {
                        RetrofitClient.api.getWorkoutDetail(workout.workoutId)
                    }
                    if (detailResp.isSuccessful && detailResp.body()?.data != null) {
                        val detail = detailResp.body()!!.data!!
                        launchWorkout(detail, name)
                    }
                } else {
                    Toast.makeText(this@CustomWorkoutActivity,
                        "No matching workout found, try different options", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CustomWorkoutActivity,
                    "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchWorkout(detail: com.example.fuelify.data.api.models.WorkoutDetail, customName: String) {
        val exerciseList = ArrayList(detail.exercises)
        val intent = Intent(this, ActiveWorkoutActivity::class.java).apply {
            putExtra("workout_id",        detail.workoutId)
            putExtra("workout_name",      customName)
            putExtra("calories_estimate", detail.caloriesBurnedEstimate)
            putExtra("exercises",         exerciseList)
        }
        startActivity(intent)
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
