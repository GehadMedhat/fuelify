package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.EcoSustainability
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class EcoSustainabilityActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eco_sustainability)

        userId = UserPreferences.getUserId(this)

        findViewById<ImageButton>(R.id.btnEcoBack).setOnClickListener { finish() }
        setupBottomNav()
        loadEcoData()
    }

    private fun loadEcoData() {
        showLoading(true)
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getEcoSustainability(userId)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()!!.data?.let { bindEcoData(it) }
                } else {
                    Toast.makeText(this@EcoSustainabilityActivity,
                        "Could not load eco data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EcoSustainabilityActivity,
                    "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.pbEco).visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun bindEcoData(eco: EcoSustainability) {
        // ── Weekly score card ─────────────────────────────────────────────────
        val tvGrade = findViewById<TextView>(R.id.tvEcoWeeklyGrade)
        val tvMsg   = findViewById<TextView>(R.id.tvEcoGradeMessage)
        val tvScore = findViewById<TextView>(R.id.tvEcoWeeklyScore)

        tvGrade.text = eco.weeklyGrade
        tvMsg.text   = eco.gradeMessage
        tvScore.text = if (eco.weeklyScore > 0) "${eco.weeklyScore}/10" else ""

        // Color the grade
        val gradeColor = when (eco.weeklyGrade) {
            "A"  -> 0xFF4A6200.toInt()
            "B"  -> 0xFF65A30D.toInt()
            "C"  -> 0xFFF97316.toInt()
            "D"  -> 0xFFEF4444.toInt()
            else -> 0xFF737373.toInt()
        }
        tvGrade.setTextColor(gradeColor)

        // ── Meal Eco Scores ───────────────────────────────────────────────────
        val container = findViewById<LinearLayout>(R.id.containerEcoMeals)
        container.removeAllViews()

        if (eco.mealScores.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No meals planned this week yet.\nPlan meals to see your eco impact!"
                textSize = 13f
                setTextColor(0xFF737373.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 40)
            }
            container.addView(tv)
        } else {
            eco.mealScores.forEach { meal ->
                val card = LayoutInflater.from(this)
                    .inflate(R.layout.item_eco_meal_score, container, false)

                // Meal image
                val img = card.findViewById<ImageView>(R.id.imgEcoMeal)
                if (meal.imageUrl.isNotEmpty()) {
                    Glide.with(this).load(meal.imageUrl)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop().into(img)
                }

                // Name + grade badge
                card.findViewById<TextView>(R.id.tvEcoMealName).text  = meal.mealName
                val tvGradeBadge = card.findViewById<TextView>(R.id.tvEcoMealGrade)
                tvGradeBadge.text = meal.ecoGrade

                val badgeBg = when (meal.ecoGrade) {
                    "A"  -> 0xFFD9F99D.toInt()
                    "B"  -> 0xFFBEF264.toInt()
                    "C"  -> 0xFFFED7AA.toInt()
                    else -> 0xFFFECACA.toInt()
                }
                val badgeText = when (meal.ecoGrade) {
                    "A"  -> 0xFF365314.toInt()
                    "B"  -> 0xFF3F6212.toInt()
                    "C"  -> 0xFF9A3412.toInt()
                    else -> 0xFF991B1B.toInt()
                }
                tvGradeBadge.setBackgroundColor(badgeBg)
                tvGradeBadge.setTextColor(badgeText)

                // Eco tags
                card.findViewById<TextView>(R.id.tvEcoCarbon).text   = meal.carbonLevel
                card.findViewById<TextView>(R.id.tvEcoOrigin).text   = meal.originType
                card.findViewById<TextView>(R.id.tvEcoPackaging).text = meal.packaging

                container.addView(card)
            }
        }

        // ── Suggestions ───────────────────────────────────────────────────────
        val sugContainer = findViewById<LinearLayout>(R.id.containerEcoSuggestions)
        sugContainer.removeAllViews()

        eco.suggestions.forEachIndexed { index, suggestion ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_eco_suggestion, sugContainer, false)
            row.findViewById<TextView>(R.id.tvSuggestionNumber).text = "${index + 1}"
            row.findViewById<TextView>(R.id.tvSuggestionText).text   = suggestion
            sugContainer.addView(row)
        }
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener {
            startActivity(Intent(this, DietActivity::class.java)); finish()
        }
        listOf(R.id.navWorkouts, R.id.navStats, R.id.navProfile).forEach { id ->
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
