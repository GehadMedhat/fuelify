package com.example.fuelify.home

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.SearchMealItem
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MealDeliveryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1
    private var allMeals = listOf<SearchMealItem>()
    private var activeFilter = "All"
    private val filters = listOf("All", "High Protein", "Vegan", "Low Carb", "Balanced")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_delivery)
        userId = UserPreferences.getUserId(this)
        findViewById<ImageButton>(R.id.btnDeliveryBack).setOnClickListener { finish() }
        setupBottomNav()
        buildFilterTabs()
        loadMeals()
    }

    // ── Filter tabs ───────────────────────────────────────────────────────────

    private fun buildFilterTabs() {
        val container = findViewById<LinearLayout>(R.id.containerFilterTabs)
        filters.forEach { filter ->
            val tab = TextView(this)
            tab.text = filter
            tab.textSize = 13f
            tab.setPadding(32, 20, 32, 20)
            tab.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 8 }
            tab.isClickable = true
            tab.isFocusable = true
            updateTabStyle(tab, filter == activeFilter)
            tab.setOnClickListener {
                activeFilter = filter
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i) as? TextView ?: continue
                    updateTabStyle(child, child.text == activeFilter)
                }
                filterAndBind()
            }
            container.addView(tab)
        }
    }

    private fun updateTabStyle(tab: TextView, selected: Boolean) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_badge_completed)
            tab.setTextColor(0xFF4A6200.toInt())
        } else {
            tab.setBackgroundResource(R.drawable.bg_recommended_card)
            tab.setTextColor(0xFF374151.toInt())
        }
    }

    // ── Load meals ────────────────────────────────────────────────────────────

    private fun loadMeals() {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.searchMeals(userId, "")
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    allMeals = response.body()!!.data ?: emptyList()
                    filterAndBind()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MealDeliveryActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterAndBind() {
        val filtered = if (activeFilter == "All") allMeals
        else allMeals.filter { it.dietType.equals(activeFilter, ignoreCase = true) }
        bindMeals(filtered)
    }

    // ── Bind meal cards ───────────────────────────────────────────────────────

    private fun bindMeals(meals: List<SearchMealItem>) {
        val container = findViewById<LinearLayout>(R.id.containerDeliveryMeals)
        container.removeAllViews()
        meals.forEach { meal ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_delivery_meal_card, container, false)

            val img = card.findViewById<ImageView>(R.id.imgDeliveryMeal)
            if (meal.imageUrl.isNotEmpty()) {
                Glide.with(this).load(meal.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop().into(img)
            }

            card.findViewById<TextView>(R.id.tvDeliveryMealName).text    = meal.mealName
            val price = String.format("$%.2f", 8.99 + (meal.calories / 100.0))
            card.findViewById<TextView>(R.id.tvDeliveryMealPrice).text   = price
            card.findViewById<TextView>(R.id.tvDeliveryMealCal).text     = "${meal.calories}"
            card.findViewById<TextView>(R.id.tvDeliveryMealProtein).text = "${(meal.calories * 0.08).toInt()}g"
            card.findViewById<TextView>(R.id.tvDeliveryMealCarbs).text   = "${(meal.calories * 0.12).toInt()}g"
            card.findViewById<TextView>(R.id.tvDeliveryMealFat).text     = "${(meal.calories * 0.03).toInt()}g"

            // Order Now → show bottom sheet with 2 options
            card.findViewById<TextView>(R.id.btnOrderNow).setOnClickListener {
                showOrderOptionsSheet(meal)
            }

            container.addView(card)
        }
    }

    // ── Order options bottom sheet ────────────────────────────────────────────

    private fun showOrderOptionsSheet(meal: SearchMealItem) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_order_options)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        dialog.findViewById<TextView>(R.id.tvOrderMealName).text = meal.mealName

        // Talabat button
        dialog.findViewById<LinearLayout>(R.id.btnOrderTalabat).setOnClickListener {
            dialog.dismiss()
            openTalabat(meal.mealName)
        }

        // Cloud Kitchen button
        dialog.findViewById<LinearLayout>(R.id.btnOrderCloudKitchen).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, CloudKitchenActivity::class.java))
        }

        dialog.findViewById<TextView>(R.id.btnOrderCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Google Custom Search — opens first result directly ───────────────────

    companion object {
        // Replace these with your actual keys from Google Console
        const val GOOGLE_API_KEY = "AIzaSyBlZe6ZgGjOypXL_pyZZpJ0bobhWQkNteQ"
        const val SEARCH_ENGINE_ID = "b71de9a88248243f6"
    }

    private fun openTalabat(mealName: String) {
        val query = Uri.encode(mealName)

        // Try opening Talabat app directly with search
        val talabatAppIntent = Intent(Intent.ACTION_VIEW,
            Uri.parse("talabat://search?q=$query"))
        talabatAppIntent.setPackage("com.talabat")

        if (talabatAppIntent.resolveActivity(packageManager) != null) {
            startActivity(talabatAppIntent)
        } else {
            // Fallback: open Talabat Egypt website search
            val webUrl = "https://www.talabat.com/egypt/search?q=$query"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
        }
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener {
            startActivity(Intent(this, DietActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navWorkouts).setOnClickListener {
            startActivity(Intent(this, WorkoutHomeActivity::class.java)); finish()
        }
        listOf(R.id.navStats, R.id.navProfile).forEach { id ->
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
