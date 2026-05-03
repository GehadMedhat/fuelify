package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.models.MealItem

class MealsListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MEALS = "extra_meals"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meals_list)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Meals"
        val meals = intent.getSerializableExtra(EXTRA_MEALS) as? ArrayList<MealItem> ?: arrayListOf()

        findViewById<TextView>(R.id.tvTitle).text = title
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.containerMeals)

        if (meals.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No meals available"
            tv.setTextColor(0xFF999999.toInt())
            tv.setPadding(16, 48, 16, 16)
            container.addView(tv)
            return
        }

        meals.forEach { meal ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_meal_card, container, false)

            // Load image with Glide
            val imgView = card.findViewById<ImageView>(R.id.imgMealCard)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(imgView)
            }

            card.findViewById<TextView>(R.id.tvMealTypeBadge).text    = meal.mealType
            card.findViewById<TextView>(R.id.tvMealTimeBadge).text     = meal.scheduledTime
            card.findViewById<TextView>(R.id.tvMealCardName).text      = meal.mealName
            card.findViewById<TextView>(R.id.tvMealCardCalories).text  = "${meal.calories} cal"
            card.findViewById<TextView>(R.id.tvMealCardProtein).text   = "P: ${meal.proteinG}g"
            card.findViewById<TextView>(R.id.tvMealCardCarbs).text     = "C: ${meal.carbsG}g"
            card.findViewById<TextView>(R.id.tvMealCardFat).text       = "F: ${meal.fatG}g"
            card.findViewById<TextView>(R.id.tvMealCardPrep).text      = "⏱ ${meal.prepTimeMinutes} min"
            card.findViewById<TextView>(R.id.tvMealCardDifficulty).text= meal.difficulty

            // Tap card → open meal detail
            card.setOnClickListener {
                val intent = Intent(this, MealDetailActivity::class.java)
                intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.mealId)
                startActivity(intent)
            }

            container.addView(card)
        }
    }
}
