package com.example.fuelify.home

import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.ExerciseItem
import kotlinx.coroutines.*

class ExerciseListActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var allExercises = listOf<ExerciseItem>()
    private var selectedMuscle = ""

    private val muscleGroups = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Core", "Arms", "Glutes", "Full Body")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_list)

        findViewById<ImageButton>(R.id.btnExerciseListBack).setOnClickListener { finish() }

        // Muscle group filter chips
        val chipContainer = findViewById<LinearLayout>(R.id.containerMuscleFilters)
        muscleGroups.forEach { muscle ->
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_filter_chip, chipContainer, false) as TextView
            chip.text = muscle
            chip.setOnClickListener {
                selectedMuscle = if (muscle == "All") "" else muscle
                updateChipStates(chip)
                filterExercises()
            }
            if (muscle == "All") {
                chip.setBackgroundResource(R.drawable.light_orange_rectangle)
                chip.setTextColor(0xFF4A6200.toInt())
            }
            chipContainer.addView(chip)
        }

        // Search
        val etSearch = findViewById<EditText>(R.id.etExerciseListSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { searchQuery = s.toString(); filterExercises() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadExercises()
    }

    private fun updateChipStates(selected: TextView) {
        val container = findViewById<LinearLayout>(R.id.containerMuscleFilters)
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

    private fun loadExercises() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getExercises()
                }
                if (resp.isSuccessful) {
                    allExercises = resp.body()?.data ?: emptyList()
                    filterExercises()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ExerciseListActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var searchQuery = ""

    private fun filterExercises() {
        var filtered = if (selectedMuscle.isEmpty()) allExercises
            else allExercises.filter { it.muscleGroup.contains(selectedMuscle, ignoreCase = true) }
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.exerciseName.contains(searchQuery, ignoreCase = true) ||
                it.muscleGroup.contains(searchQuery, ignoreCase = true) ||
                it.equipmentNeeded.contains(searchQuery, ignoreCase = true)
            }
        }

        val container = findViewById<LinearLayout>(R.id.containerExerciseList)
        container.removeAllViews()
        findViewById<TextView>(R.id.tvExerciseListCount).text = "${filtered.size} exercises"

        filtered.forEach { exercise ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_exercise_row, container, false)

            val img = row.findViewById<ImageView>(R.id.imgExerciseRow)
            if (exercise.imageUrl.isNotEmpty()) {
                Glide.with(this).load(exercise.imageUrl).centerCrop().into(img)
            }
            row.findViewById<TextView>(R.id.tvExerciseRowName).text   = exercise.exerciseName
            row.findViewById<TextView>(R.id.tvExerciseRowMuscle).text = exercise.muscleGroup

            row.setOnClickListener { showExerciseDetail(exercise) }
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
        dialog.findViewById<TextView>(R.id.tvExerciseDetailName).text   = exercise.exerciseName
        dialog.findViewById<TextView>(R.id.tvExerciseDetailMuscle).text = exercise.muscleGroup
        val container = dialog.findViewById<LinearLayout>(R.id.containerExerciseEquipment)
        container.removeAllViews()

        val equipList = exercise.equipmentNeeded.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "None" }

        for (item in equipList) {
            val itemView = layoutInflater.inflate(R.layout.item_equipment_chip, container, false)

            val img = itemView.findViewById<ImageView>(R.id.imgEquipment)
            val txt = itemView.findViewById<TextView>(R.id.tvEquipmentChipName)

            txt.text = item
            img.setImageResource(getEquipmentImage(item))

            container.addView(itemView)
        }

        dialog.findViewById<TextView>(R.id.tvExerciseDetailDesc).text   = exercise.description
        dialog.findViewById<TextView>(R.id.btnCloseExerciseDetail).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
