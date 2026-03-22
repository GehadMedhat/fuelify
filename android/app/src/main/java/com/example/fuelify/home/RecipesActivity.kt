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
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.MealItem
import com.example.fuelify.data.api.models.SearchMealItem
import com.example.fuelify.data.api.models.SwitchMealRequest
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class RecipesActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1
    private var searchJob: Job? = null

    // Today's meals loaded once — used in the switch bottom sheet
    private var todayMeals = listOf<MealItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipes)

        userId = UserPreferences.getUserId(this)

        loadTodayMeals()   // load today's plan in background for switch sheet
        setupSearch()
        loadMeals("")
    }

    // ── Load today's plan silently ────────────────────────────────────────────

    private fun loadTodayMeals() {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getDashboard(userId)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    todayMeals = response.body()!!.data?.todayMeals ?: emptyList()
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        val et       = findViewById<EditText>(R.id.etRecipeSearch)
        val clearBtn = findViewById<TextView>(R.id.btnClearSearch)

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                clearBtn.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(400)
                    loadMeals(q)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        clearBtn.setOnClickListener { et.setText(""); loadMeals("") }
    }

    private fun loadMeals(query: String) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBarRecipes)
        val resultCount = findViewById<TextView>(R.id.tvResultCount)
        val container   = findViewById<LinearLayout>(R.id.containerRecipes)

        progressBar.visibility = View.VISIBLE
        container.removeAllViews()

        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.searchMeals(userId, query)
                }
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body()?.success == true) {
                    val meals = response.body()!!.data ?: emptyList()
                    resultCount.text = "${meals.size} recipes found"
                    resultCount.visibility = View.VISIBLE
                    bindMeals(meals, container)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@RecipesActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Bind meal cards ───────────────────────────────────────────────────────

    private fun bindMeals(meals: List<SearchMealItem>, container: LinearLayout) {
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

            // Difficulty color
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

            // Switch button — always visible, opens bottom sheet to pick which meal to replace
            val switchBtn = card.findViewById<TextView>(R.id.btnSwitchMeal)
            switchBtn.visibility = View.VISIBLE
            switchBtn.setOnClickListener {
                showSwitchSheet(meal)
            }

            // Tap card → meal detail
            card.setOnClickListener {
                val intent = Intent(this, MealDetailActivity::class.java)
                intent.putExtra(MealDetailActivity.EXTRA_MEAL_ID, meal.mealId)
                startActivity(intent)
            }

            container.addView(card)
        }
    }

    // ── Switch bottom sheet ───────────────────────────────────────────────────

    private fun showSwitchSheet(newMeal: SearchMealItem) {
        if (todayMeals.isEmpty()) {
            Toast.makeText(this, "No meals planned today to switch", Toast.LENGTH_SHORT).show()
            return
        }

        // Build bottom sheet dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_switch_meal)
        dialog.window?.apply {
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(android.view.Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        // Title
        dialog.findViewById<TextView>(R.id.tvSwitchTitle).text =
            "Switch with which meal?"
        dialog.findViewById<TextView>(R.id.tvSwitchSubtitle).text =
            "Replace one of today's meals with ${newMeal.mealName}"

        // Build meal options list
        val container = dialog.findViewById<LinearLayout>(R.id.containerSwitchMeals)
        todayMeals.forEach { existingMeal ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_switch_meal_row, container, false)

            // Load image
            val imgView = row.findViewById<ImageView>(R.id.imgSwitchMeal)
            if (existingMeal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(existingMeal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(imgView)
            }

            row.findViewById<TextView>(R.id.tvSwitchMealName).text = existingMeal.mealName
            row.findViewById<TextView>(R.id.tvSwitchMealInfo).text =
                "${existingMeal.mealType} · ${existingMeal.calories} cal"

            // Tap row → confirm switch
            row.setOnClickListener {
                dialog.dismiss()
                confirmSwitch(existingMeal, newMeal)
            }

            container.addView(row)
        }

        // Cancel button
        dialog.findViewById<TextView>(R.id.btnCancelSwitch).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Confirm and execute switch ────────────────────────────────────────────

    private fun confirmSwitch(oldMeal: MealItem, newMeal: SearchMealItem) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.switchMeal(
                        userId,
                        SwitchMealRequest(planId = oldMeal.id, newMealId = newMeal.mealId)
                    )
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(
                        this@RecipesActivity,
                        "✓ Switched to ${newMeal.mealName}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Refresh today's meals locally
                    todayMeals = todayMeals.map { m ->
                        if (m.id == oldMeal.id) m.copy(mealName = newMeal.mealName) else m
                    }
                    setResult(RESULT_OK)
                } else {
                    Toast.makeText(this@RecipesActivity, "Switch failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RecipesActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
