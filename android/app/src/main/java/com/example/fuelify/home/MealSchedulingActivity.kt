package com.example.fuelify.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.models.MealItem

class MealSchedulingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEALS     = "extra_meals"
        const val EXTRA_CAL_EATEN = "extra_cal_eaten"
        const val EXTRA_CAL_GOAL  = "extra_cal_goal"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_scheduling)

        val meals    = intent.getSerializableExtra(EXTRA_MEALS) as? ArrayList<MealItem> ?: arrayListOf()
        val calEaten = intent.getIntExtra(EXTRA_CAL_EATEN, 0)
        val calGoal  = intent.getIntExtra(EXTRA_CAL_GOAL, 2000)

        // Back button
        findViewById<ImageButton>(R.id.btnSchedulingBack).setOnClickListener { finish() }

        // Calorie progress
        findViewById<TextView>(R.id.tvSchedulingCalEaten).text = "$calEaten"
        findViewById<TextView>(R.id.tvSchedulingCalGoal).text  = "/ $calGoal"
        setCalProgress(calEaten, calGoal)

        // Group meals by time of day
        val morning = meals.filter { it.mealType.equals("Breakfast", ignoreCase = true) || it.scheduledTime.contains("AM") }
        val noon    = meals.filter { it.mealType.equals("Lunch", ignoreCase = true) || it.mealType.equals("Snack", ignoreCase = true) && it.scheduledTime.contains("PM") && !it.scheduledTime.startsWith("7") }
        val night   = meals.filter { it.mealType.equals("Dinner", ignoreCase = true) || it.scheduledTime.startsWith("7") }

        bindSection(R.id.tvMorningSectionTitle, R.id.containerMorningMeals, morning)
        bindSection(R.id.tvNoonSectionTitle,    R.id.containerNoonMeals,    noon)
        bindSection(R.id.tvNightSectionTitle,   R.id.containerNightMeals,   night)
    }

    private fun bindSection(titleId: Int, containerId: Int, meals: List<MealItem>) {
        if (meals.isEmpty()) return

        val titleView = findViewById<TextView>(titleId)
        val container = findViewById<LinearLayout>(containerId)
        titleView.visibility = View.VISIBLE

        meals.forEach { meal ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_scheduling_meal, container, false)

            val imgView = row.findViewById<ImageView>(R.id.imgSchedulingMeal)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(imgView)
            }

            row.findViewById<TextView>(R.id.tvSchedulingMealName).text = meal.mealName
            row.findViewById<TextView>(R.id.tvSchedulingMealCal).text  = "${meal.calories} kcal"

            // Bell alarm button — logic added when notifications are implemented
            val bellBtn = row.findViewById<TextView>(R.id.btnSchedulingAlarm)
            bellBtn.setOnClickListener {
                Toast.makeText(this, "Reminder set for ${meal.scheduledTime}!", Toast.LENGTH_SHORT).show()
            }

            container.addView(row)
        }
    }

    private fun setCalProgress(eaten: Int, goal: Int) {
        val bar    = findViewById<View>(R.id.progressSchedulingCal)
        val parent = bar.parent as? FrameLayout ?: return
        parent.post {
            val pct    = if (goal > 0) (eaten.toFloat() / goal).coerceIn(0f, 1f) else 0f
            val params = bar.layoutParams
            params.width = (parent.width * pct).toInt().coerceAtLeast(0)
            bar.layoutParams = params
        }
    }
}
