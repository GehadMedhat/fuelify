package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.models.SearchMealItem
import com.example.fuelify.utils.SavedRecipesManager

class SavedRecipesActivity : AppCompatActivity() {

    private var allSaved = listOf<SearchMealItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_recipes)
        setupSearch()
        setupClearAll()
    }

    override fun onResume() {
        super.onResume()
        // Always reload from storage so additions from RecipesActivity are reflected
        allSaved = SavedRecipesManager.getAll(this)
        applyFilter(findViewById<EditText>(R.id.etSavedSearch).text.toString())
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        val et       = findViewById<EditText>(R.id.etSavedSearch)
        val clearBtn = findViewById<TextView>(R.id.btnClearSavedSearch)

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                clearBtn.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilter(q)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        clearBtn.setOnClickListener { et.setText("") }
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isBlank()) allSaved
        else allSaved.filter { it.mealName.contains(query, ignoreCase = true) }
        bindMeals(filtered)
    }

    // ── Clear all ─────────────────────────────────────────────────────────────

    private fun setupClearAll() {
        findViewById<TextView>(R.id.btnClearSaved).setOnClickListener {
            SavedRecipesManager.clearAll(this)
            allSaved = emptyList()
            applyFilter("")
            Toast.makeText(this, "All saved recipes cleared", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Bind cards (exact same style as RecipesActivity) ─────────────────────

    private fun bindMeals(meals: List<SearchMealItem>) {
        val container    = findViewById<LinearLayout>(R.id.containerSavedRecipes)
        val resultCount  = findViewById<TextView>(R.id.tvSavedResultCount)
        val emptyLayout  = findViewById<LinearLayout>(R.id.layoutSavedEmpty)
        val clearAllBtn  = findViewById<TextView>(R.id.btnClearSaved)

        container.removeAllViews()

        if (meals.isEmpty()) {
            emptyLayout.visibility  = View.VISIBLE
            resultCount.visibility  = View.GONE
            clearAllBtn.visibility  = View.GONE
            return
        }

        emptyLayout.visibility = View.GONE
        resultCount.text       = "${meals.size} saved recipes"
        resultCount.visibility = View.VISIBLE
        clearAllBtn.visibility = View.VISIBLE

        meals.forEach { meal ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_recipe_card, container, false)

            // Image
            val img = card.findViewById<ImageView>(R.id.imgRecipeCard)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(img)
            }

            // Calorie badge
            card.findViewById<TextView>(R.id.tvRecipeCalBadge).text = "${meal.calories} cal"

            // Suitability badge
            val suitBadge = card.findViewById<TextView>(R.id.tvSuitabilityBadge)
            suitBadge.visibility = View.VISIBLE
            if (meal.isSuitable) {
                suitBadge.text = "✓ Suitable"
                suitBadge.setBackgroundResource(R.drawable.bg_badge_completed)
            } else {
                suitBadge.text = "✕ Not suitable"
                val bg = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_badge_time)
                bg?.setTint(0xFFE53935.toInt())
                suitBadge.background = bg
            }

            // Info
            card.findViewById<TextView>(R.id.tvRecipeCardName).text       = meal.mealName
            card.findViewById<TextView>(R.id.tvRecipeCardPrep).text       = "${meal.prepTimeMinutes} min"
            card.findViewById<TextView>(R.id.tvRecipeCardDifficulty).text = meal.difficulty
            card.findViewById<TextView>(R.id.tvSuitabilityReason).text    = meal.suitabilityReason

            // Difficulty colour (same logic as RecipesActivity)
            val diffView = card.findViewById<TextView>(R.id.tvRecipeCardDifficulty)
            when (meal.difficulty) {
                "Medium" -> {
                    diffView.setBackgroundResource(R.drawable.bg_streak_card)
                    diffView.setTextColor(0xFFFF9800.toInt())
                }
                "Hard" -> {
                    val bg2 = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_badge_time)
                    bg2?.setTint(0xFFE53935.toInt())
                    diffView.background = bg2
                    diffView.setTextColor(0xFFFFFFFF.toInt())
                }
            }

            // Save/bookmark button — show as filled (already saved); tapping unsaves
            val saveBtn = card.findViewById<TextView>(R.id.btnSaveRecipe)
            saveBtn.visibility = View.VISIBLE
            saveBtn.text       = "🔖 Saved"
            saveBtn.setOnClickListener {
                SavedRecipesManager.remove(this, meal.mealId)
                allSaved = SavedRecipesManager.getAll(this)
                applyFilter(findViewById<EditText>(R.id.etSavedSearch).text.toString())
                Toast.makeText(this, "Removed from saved", Toast.LENGTH_SHORT).show()
            }

            // Hide switch button — not relevant in saved view
            card.findViewById<TextView>(R.id.btnSwitchMeal).visibility = View.GONE

            // Tap card → meal detail
            card.setOnClickListener {
                val intent = Intent(this, MealDetailActivity::class.java)
                intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.mealId)
                startActivity(intent)
            }

            container.addView(card)
        }
    }
}
