package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.PantryItemModel
import com.example.fuelify.data.api.models.PantryRecipeSuggestion
import com.example.fuelify.data.api.models.ScannedPantryItem
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class SmartPantryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_pantry)

        userId = UserPreferences.getUserId(this)

        findViewById<ImageButton>(R.id.btnPantryBack).setOnClickListener { finish() }
        setupBottomNav()

        // Add button → open Grocery screen
        findViewById<LinearLayout>(R.id.btnAddPantryItem).setOnClickListener {
            startActivity(Intent(this, GroceryActivity::class.java))
        }

        loadPantry()
    }

    override fun onResume() {
        super.onResume()
        loadPantry()
    }

    // ── Load pantry from DB ───────────────────────────────────────────────────

    private fun loadPantry() {
        showLoading(true)
        scope.launch {
            try {
                val pantryResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getPantry(userId)
                }
                val suggestionsResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getPantrySuggestions(userId)
                }

                val items = if (pantryResponse.isSuccessful && pantryResponse.body()?.success == true)
                    pantryResponse.body()!!.data ?: emptyList()
                else emptyList()

                val suggestions = if (suggestionsResponse.isSuccessful && suggestionsResponse.body()?.success == true)
                    suggestionsResponse.body()!!.data ?: emptyList()
                else emptyList()

                // Also load scanned pantry items
                val scannedResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getScannedPantry(userId)
                }
                val scannedItems = if (scannedResponse.isSuccessful && scannedResponse.body()?.success == true)
                    scannedResponse.body()!!.data ?: emptyList()
                else emptyList()

                bindPantryItems(items)
                bindScannedPantryItems(scannedItems)
                bindSuggestions(suggestions)
            } catch (e: Exception) {
                Toast.makeText(this@SmartPantryActivity, "Network error", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.pbPantry)?.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ── Pantry inventory ──────────────────────────────────────────────────────

    private fun bindPantryItems(items: List<PantryItemModel>) {
        val container = findViewById<LinearLayout>(R.id.containerPantryItems)
        container.removeAllViews()

        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Your pantry is empty.\nTap '+ Add' to shop for ingredients."
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 48)
                setTextColor(0xFF737373.toInt())
            })
            return
        }

        items.forEach { item ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_pantry_row, container, false)

            row.findViewById<TextView>(R.id.tvPantryItemIcon).text = categoryEmoji(item.foodCategory)
            row.findViewById<TextView>(R.id.tvPantryItemName).text = item.name
            row.findViewById<TextView>(R.id.tvPantryItemSub).text =
                "${item.quantity.toInt()} ${item.unit}  •  ${item.foodCategory}"

            val tvExpiry = row.findViewById<TextView>(R.id.tvPantryItemExpiry)
            tvExpiry.text = when {
                item.daysUntilExpiry < 0  -> "Expired!"
                item.daysUntilExpiry == 0 -> "Expires today!"
                item.daysUntilExpiry == 1 -> "Expires tomorrow"
                else                      -> "Expires in ${item.daysUntilExpiry} days"
            }
            tvExpiry.setTextColor(
                if (item.daysUntilExpiry <= 3) 0xFFF97316.toInt() else 0xFF374151.toInt()
            )

            // Long press → delete
            row.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Remove from Pantry")
                    .setMessage("Remove ${item.name}?")
                    .setPositiveButton("Remove") { _, _ ->
                        deletePantryItem(item.pantryItemId, item.name)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }

            container.addView(row)
        }
    }

    // ── Scanned Pantry Items ─────────────────────────────────────────────────

    private fun bindScannedPantryItems(items: List<ScannedPantryItem>) {
        // Find or create the scanned section header
        val container = findViewById<LinearLayout>(R.id.containerPantryItems)

        if (items.isEmpty()) return

        // Add section header
        val header = TextView(this).apply {
            text = "📦  Scanned Products"
            textSize = 14f
            setTextColor(0xFF171717.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }
        container.addView(header)

        items.forEach { item ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_pantry_row, container, false)

            row.findViewById<TextView>(R.id.tvPantryItemIcon).text = "🛒"
            row.findViewById<TextView>(R.id.tvPantryItemName).text = item.productName
            row.findViewById<TextView>(R.id.tvPantryItemSub).text =
                "${item.quantity.toInt()}${item.unit}  •  ${item.calories.toInt()} kcal  •  Nutri-${item.nutriScore}"

            val tvExpiry = row.findViewById<TextView>(R.id.tvPantryItemExpiry)
            tvExpiry.text = when {
                item.daysUntilExpiry < 0  -> "Expired!"
                item.daysUntilExpiry == 0 -> "Expires today!"
                item.daysUntilExpiry == 1 -> "Expires tomorrow"
                else                      -> "Expires in ${item.daysUntilExpiry} days"
            }
            tvExpiry.setTextColor(
                if (item.daysUntilExpiry <= 3) 0xFFF97316.toInt() else 0xFF374151.toInt()
            )

            // Long press → delete
            row.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Remove from Pantry")
                    .setMessage("Remove ${item.productName}?")
                    .setPositiveButton("Remove") { _, _ ->
                        deleteScannedPantryItem(item.itemId, item.productName)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }

            container.addView(row)
        }
    }

    private fun deleteScannedPantryItem(itemId: Int, name: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.deleteScannedPantryItem(userId, itemId)
                }
                Toast.makeText(this@SmartPantryActivity, "$name removed", Toast.LENGTH_SHORT).show()
                loadPantry()
            } catch (e: Exception) {
                Toast.makeText(this@SmartPantryActivity, "Failed to remove", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePantryItem(itemId: Int, name: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.deletePantryItem(userId, itemId)
                }
                Toast.makeText(this@SmartPantryActivity, "$name removed", Toast.LENGTH_SHORT).show()
                loadPantry()
            } catch (e: Exception) {
                Toast.makeText(this@SmartPantryActivity, "Failed to remove", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Recipe suggestions ────────────────────────────────────────────────────

    private fun bindSuggestions(suggestions: List<PantryRecipeSuggestion>) {
        val container = findViewById<LinearLayout>(R.id.containerRecipeSuggestions)
        container.removeAllViews()

        if (suggestions.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Add pantry items to get recipe suggestions based on what you have."
                textSize = 13f
                setPadding(0, 16, 0, 16)
                setTextColor(0xFF737373.toInt())
            })
            return
        }

        suggestions.forEach { s ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_recipe_suggestion, container, false)

            if (s.imageUrl.isNotEmpty()) {
                Glide.with(this).load(s.imageUrl)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(card.findViewById(R.id.imgRecipeSuggestion))
            }

            card.findViewById<TextView>(R.id.tvRecipeSuggestionName).text = s.mealName
            card.findViewById<TextView>(R.id.tvRecipeSuggestionBadge).text =
                "${s.matchCount} ingredient${if (s.matchCount > 1) "s" else ""} match"

            val missing = s.totalIngredients - s.matchCount
            val tvMissing = card.findViewById<TextView>(R.id.tvRecipeMissing)
            if (missing > 0) {
                tvMissing.visibility = View.VISIBLE
                tvMissing.text = "$missing missing"
            } else {
                tvMissing.visibility = View.GONE
            }

            // Tap → open meal detail
            card.setOnClickListener {
                val intent = Intent(this, MealDetailActivity::class.java)
                intent.putExtra("meal_id", s.mealId)
                startActivity(intent)
            }

            container.addView(card)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun categoryEmoji(category: String) = when (category) {
        "Protein"    -> "🥩"
        "Fruits"     -> "🍎"
        "Vegetables" -> "🥦"
        "Dairy"      -> "🥛"
        "Carbs"      -> "🌾"
        "Fats"       -> "🫒"
        "Condiments" -> "🧂"
        else         -> "🍽️"
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

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
