package com.example.fuelify.home

import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.WorkoutItem
import kotlinx.coroutines.*

class WorkoutListActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var allWorkouts = listOf<WorkoutItem>()
    private var selectedDifficulty = ""
    private val category by lazy { intent.getStringExtra("category") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_list)

        val title = category ?: "All Workouts"
        findViewById<TextView>(R.id.tvWorkoutListTitle).text = title
        findViewById<ImageButton>(R.id.btnWorkoutListBack).setOnClickListener { finish() }

        // Search
        val etSearch = findViewById<EditText>(R.id.etWorkoutListSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { searchWorkouts(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Difficulty filter chips
        listOf("All" to "", "Beginner" to "Beginner", "Medium" to "Medium", "Advanced" to "Advanced")
            .forEach { (label, value) ->
                val chip = LayoutInflater.from(this)
                    .inflate(R.layout.item_filter_chip, null, false) as TextView
                chip.text = label
                chip.setOnClickListener {
                    selectedDifficulty = value
                    updateChipStates(chip)
                    filterWorkouts()
                }
                if (value.isEmpty()) { // "All" starts selected
                    chip.setBackgroundResource(R.drawable.light_green_rectangle)
                    chip.setTextColor(0xFF4A6200.toInt())
                }
                findViewById<LinearLayout>(R.id.containerDifficultyFilters).addView(chip)
            }

        loadWorkouts()
    }

    private fun updateChipStates(selected: TextView) {
        val container = findViewById<LinearLayout>(R.id.containerDifficultyFilters)
        for (i in 0 until container.childCount) {
            val chip = container.getChildAt(i) as? TextView ?: continue
            if (chip == selected) {
                chip.setBackgroundResource(R.drawable.light_orange_rectangle)
                chip.setTextColor(0xFF4A6200.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_recommended_card)
                chip.setTextColor(0xFF374151.toInt())
            }
        }
    }

    private fun loadWorkouts() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getWorkouts(category = category)
                }
                if (resp.isSuccessful) {
                    allWorkouts = resp.body()?.data ?: emptyList()
                    filterWorkouts()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WorkoutListActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var searchQuery = ""

    private fun searchWorkouts(query: String) {
        searchQuery = query
        filterWorkouts()
    }

    private fun filterWorkouts() {
        var filtered = if (selectedDifficulty.isEmpty()) allWorkouts
            else allWorkouts.filter { it.difficulty == selectedDifficulty }
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.workoutName.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.equipment.contains(searchQuery, ignoreCase = true)
            }
        }
        bindWorkouts(filtered)
    }

    private fun bindWorkouts(workouts: List<WorkoutItem>) {
        val container = findViewById<LinearLayout>(R.id.containerWorkoutList)
        container.removeAllViews()
        val tvCount = findViewById<TextView>(R.id.tvWorkoutListCount)
        tvCount.text = "${workouts.size} workouts"

        workouts.forEach { workout ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_workout_list_row, container, false)

            val img = card.findViewById<ImageView>(R.id.imgWorkoutListRow)
            if (workout.imageUrl.isNotEmpty()) {
                Glide.with(this).load(workout.imageUrl).centerCrop().into(img)
            }
            card.findViewById<TextView>(R.id.tvWorkoutListRowName).text       = workout.workoutName
            card.findViewById<TextView>(R.id.tvWorkoutListRowDifficulty).text = workout.difficulty
            card.findViewById<TextView>(R.id.tvWorkoutListRowDuration).text   = "${workout.durationMinutes} min"
            card.findViewById<TextView>(R.id.tvWorkoutListRowCal).text        = "${workout.caloriesBurnedEstimate} kcal"
            card.findViewById<TextView>(R.id.tvWorkoutListRowExercises).text  = "${workout.exerciseCount} exercises"

            if (workout.isPremium) {
                card.findViewById<TextView>(R.id.tvWorkoutListRowPremium).visibility = android.view.View.VISIBLE
            }

            card.setOnClickListener {
                startActivity(Intent(this, WorkoutDetailActivity::class.java).apply {
                    putExtra("workout_id", workout.workoutId)
                })
            }
            container.addView(card)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
