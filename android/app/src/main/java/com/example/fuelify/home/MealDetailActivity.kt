package com.example.fuelify.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.MealDetailData
import kotlinx.coroutines.*

class MealDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEAL_ID = "extra_meal_id"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_detail)

        val mealId = intent.getIntExtra(EXTRA_MEAL_ID, -1)
        if (mealId == -1) { finish(); return }

        findViewById<ImageButton>(R.id.btnMealDetailBack).setOnClickListener { finish() }

        loadMealDetail(mealId)
    }

    private fun loadMealDetail(mealId: Int) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getMealDetail(mealId)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()!!.data?.let { bindDetail(it) }
                } else {
                    Toast.makeText(this@MealDetailActivity,
                        "Could not load meal details", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MealDetailActivity,
                    "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindDetail(d: MealDetailData) {
        // Hero image
        val img = findViewById<ImageView>(R.id.imgMealDetail)
        if (d.imageUrl.isNotEmpty()) {
            Glide.with(this).load(d.imageUrl)
                .placeholder(R.drawable.bg_image_placeholder)
                .centerCrop().into(img)
        }

        // Meal name + prep info
        findViewById<TextView>(R.id.tvMealDetailName).text = d.mealName
        findViewById<TextView>(R.id.tvMealDetailPrep).text = "${d.prepTimeMinutes} min"
        findViewById<TextView>(R.id.tvMealDetailDifficulty).text = d.difficulty

        // Calorie quality
        val calQuality = d.calorieQualityScore
        val qualityLabel = when {
            calQuality >= 9.0 -> "Excellent"
            calQuality >= 7.5 -> "Good"
            calQuality >= 6.0 -> "Average"
            else              -> "Low"
        }
        findViewById<TextView>(R.id.tvCalorieQuality).text = "${calQuality}/10"
        // find the "Excellent" label below it
        val calCard = (findViewById<TextView>(R.id.tvCalorieQuality)).parent as? LinearLayout
        (calCard?.getChildAt(2) as? TextView)?.text = qualityLabel

        // Eco grade
        findViewById<TextView>(R.id.tvEcoGrade).text = d.ecoGrade

        // Video button
        if (d.hasVideo && d.videoUrl.isNotEmpty()) {
            val btn = findViewById<LinearLayout>(R.id.btnWatchVideo)
            btn.visibility = View.VISIBLE
            btn.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.videoUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open video", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Ingredients
        val ingContainer = findViewById<LinearLayout>(R.id.containerIngredients)
        ingContainer.removeAllViews()
        d.ingredients.forEach { ing ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_ingredient_row, ingContainer, false)
            row.findViewById<TextView>(R.id.tvIngName).text     = ing.name
            row.findViewById<TextView>(R.id.tvIngQty).text      = ing.quantity
            ingContainer.addView(row)

            // Divider
            val divider = View(this)
            divider.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1)
            divider.setBackgroundColor(0xFFE5E7EB.toInt())
            ingContainer.addView(divider)
        }

        // Instructions
        val insContainer = findViewById<LinearLayout>(R.id.containerInstructions)
        insContainer.removeAllViews()
        d.instructions.forEachIndexed { idx, step ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_instruction_step, insContainer, false)
            row.findViewById<TextView>(R.id.tvStepNumber).text = "${idx + 1}"
            row.findViewById<TextView>(R.id.tvStepText).text   = step
            insContainer.addView(row)
        }

        // Nutrition facts
        findViewById<TextView>(R.id.tvNutCalories).text = "${d.calories} kcal"
        findViewById<TextView>(R.id.tvNutProtein).text  = "${d.proteinG.toInt()}g"
        findViewById<TextView>(R.id.tvNutCarbs).text    = "${d.carbsG.toInt()}g"
        findViewById<TextView>(R.id.tvNutFat).text      = "${d.fatG.toInt()}g"
        findViewById<TextView>(R.id.tvNutFiber).text    = "${d.fiberG}g"
        findViewById<TextView>(R.id.tvNutSugar).text    = "${d.sugarG}g"
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
