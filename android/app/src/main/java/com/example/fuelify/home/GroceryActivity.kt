package com.example.fuelify.home

import android.app.Dialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GroceryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1
    private var allCatalogItems = listOf<CatalogItem>()
    private var activeFilter = "All"
    private val filters = listOf("All", "Protein", "Fruits", "Vegetables", "Dairy", "Grains", "Fats")
    private var isListMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grocery)

        userId = UserPreferences.getUserId(this)

        findViewById<ImageButton>(R.id.btnGroceryBack).setOnClickListener { finish() }
        setupBottomNav()
        buildFilterTabs()

        findViewById<LinearLayout>(R.id.btnListMode).setOnClickListener {
            isListMode = !isListMode
            bindCatalog()
        }

        findViewById<LinearLayout>(R.id.btnViewGroceryList).setOnClickListener {
            showGroceryListSheet()
        }

        loadCatalog()
    }

    // ── Load catalog from grocery_catalog table ───────────────────────────────

    private fun loadCatalog() {
        showLoading(true)
        scope.launch {
            try {
                val cat = if (activeFilter == "All") null else activeFilter
                val response = withContext(Dispatchers.IO) {
                    // Always personalized — recommended flag is dynamic per user goal/allergies
                    RetrofitClient.api.getPersonalizedCatalog(userId, category = cat)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    allCatalogItems = response.body()!!.data ?: emptyList()
                    bindCatalog()
                } else {
                    Toast.makeText(this@GroceryActivity, "Failed to load catalog", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroceryActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.pbGrocery)?.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ── Filter tabs ───────────────────────────────────────────────────────────

    private fun buildFilterTabs() {
        val container = findViewById<LinearLayout>(R.id.containerGroceryFilters)
        container.removeAllViews()
        filters.forEach { filter ->
            val tab = TextView(this).apply {
                text = filter
                textSize = 12f
                setPadding(36, 20, 36, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 8 }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeFilter = filter
                    buildFilterTabs()
                    loadCatalog()
                }
            }
            if (filter == activeFilter) {
                tab.setBackgroundResource(R.drawable.bg_badge_completed)
                tab.setTextColor(0xFF4A6200.toInt())
            } else {
                tab.setBackgroundResource(R.drawable.bg_recommended_card)
                tab.setTextColor(0xFF404040.toInt())
            }
            container.addView(tab)
        }
    }

    // ── Bind catalog cards ────────────────────────────────────────────────────

    private fun bindCatalog() {
        val container = findViewById<LinearLayout>(R.id.containerAllProducts)
        container.removeAllViews()

        // Recommended section
        val recommended = allCatalogItems.filter { it.isRecommended }
        val recSection = findViewById<View>(R.id.sectionRecommended)
        val recContainer = findViewById<LinearLayout>(R.id.containerRecommended)

        if (recommended.isNotEmpty()) {
            recSection.visibility = View.VISIBLE
            recContainer.removeAllViews()
            if (isListMode) recommended.forEach { recContainer.addView(buildListCard(it)) }
            else addGridRows(recContainer, recommended)
        } else {
            recSection.visibility = View.GONE
        }

        // All items
        if (allCatalogItems.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No items found."
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 48)
                setTextColor(0xFF737373.toInt())
            })
            return
        }

        if (isListMode) allCatalogItems.forEach { container.addView(buildListCard(it)) }
        else addGridRows(container, allCatalogItems)
    }

    // ── Grid (2 columns) ──────────────────────────────────────────────────────

    private fun addGridRows(container: LinearLayout, items: List<CatalogItem>) {
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 10.dp }
            }
            val card1 = buildGridCard(items[i])
            card1.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = 8.dp }
            row.addView(card1)
            if (i + 1 < items.size) {
                val card2 = buildGridCard(items[i + 1])
                card2.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(card2)
            } else {
                row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
            }
            container.addView(row)
            i += 2
        }
    }

    private fun buildGridCard(item: CatalogItem): LinearLayout {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_ingredient_grid, null, false) as LinearLayout

        // Load image if available
        val imgView = card.findViewById<ImageView?>(R.id.imgIngredientGrid)
        if (imgView != null && item.imageUrl.isNotEmpty()) {
            imgView.visibility = View.VISIBLE
            com.bumptech.glide.Glide.with(this).load(item.imageUrl)
                .placeholder(R.drawable.bg_image_placeholder).centerCrop().into(imgView)
        }

        card.findViewById<TextView>(R.id.tvIngredientGridEmoji).text = categoryEmoji(item.category)
        card.findViewById<TextView>(R.id.tvIngredientGridName).text = item.displayName
        card.findViewById<TextView>(R.id.tvIngredientGridUnit).text = item.description.ifEmpty { item.unit ?: "" }
        card.findViewById<TextView>(R.id.tvIngredientGridCal).text = "$${String.format("%.2f", item.price)}"
        card.findViewById<TextView>(R.id.tvIngredientGridMacros).text =
            if (item.caloriesPerUnit != null)
                "P:${item.proteinPerUnit?.toInt()}g  C:${item.carbsPerUnit?.toInt()}g  F:${item.fatPerUnit?.toInt()}g"
            else ""
        card.findViewById<View>(R.id.tvAllergenBadge).visibility =
            if (item.allergenFlag == true) View.VISIBLE else View.GONE

        card.findViewById<LinearLayout>(R.id.btnAddToPantryGrid).setOnClickListener {
            if (item.ingredientId != null) showAddToPantryDialog(item)
            else Toast.makeText(this, "No nutritional data for this item", Toast.LENGTH_SHORT).show()
        }
        card.findViewById<LinearLayout>(R.id.btnAddToListGrid).setOnClickListener {
            addToGroceryList(item)
        }
        return card
    }

    private fun buildListCard(item: CatalogItem): LinearLayout {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_ingredient_list, null, false) as LinearLayout

        val imgView = card.findViewById<ImageView?>(R.id.imgIngredientList)
        if (imgView != null && item.imageUrl.isNotEmpty()) {
            imgView.visibility = View.VISIBLE
            com.bumptech.glide.Glide.with(this).load(item.imageUrl)
                .placeholder(R.drawable.bg_image_placeholder).centerCrop().into(imgView)
        }

        card.findViewById<TextView>(R.id.tvIngredientListEmoji).text = categoryEmoji(item.category)
        card.findViewById<TextView>(R.id.tvIngredientListName).text = item.displayName
        card.findViewById<TextView>(R.id.tvIngredientListSub).text =
            "${item.category}  •  ${item.description.ifEmpty { "" }}"
        card.findViewById<TextView>(R.id.tvIngredientListMacros).text =
            "$${String.format("%.2f", item.price)}"
        card.findViewById<View>(R.id.tvAllergenBadgeList).visibility =
            if (item.allergenFlag == true) View.VISIBLE else View.GONE

        card.findViewById<LinearLayout>(R.id.btnAddToPantryList).setOnClickListener {
            if (item.ingredientId != null) showAddToPantryDialog(item)
            else Toast.makeText(this, "No nutritional data for this item", Toast.LENGTH_SHORT).show()
        }
        card.findViewById<LinearLayout>(R.id.btnAddToListList).setOnClickListener {
            addToGroceryList(item)
        }
        return card
    }

    // ── Add to Grocery List (DB) ──────────────────────────────────────────────

    private fun addToGroceryList(item: CatalogItem) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.addToGroceryList(
                        userId,
                        AddGroceryItemRequest(
                            itemName      = item.displayName,
                            category      = item.category,
                            quantity      = 1.0,
                            unit          = item.unit ?: "",
                            price         = item.price,
                            ingredientId  = item.ingredientId,
                            isRecommended = item.isRecommended
                        )
                    )
                }
                Toast.makeText(this@GroceryActivity, "✓ ${item.displayName} added to list", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@GroceryActivity, "Failed to add to list", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Add to Pantry bottom sheet ────────────────────────────────────────────

    private fun showAddToPantryDialog(item: CatalogItem) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_add_to_pantry)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        dialog.findViewById<TextView>(R.id.tvPantryDialogName).text = item.displayName

        var qty = 100.0
        var selectedDays = 7

        val tvQty = dialog.findViewById<TextView>(R.id.tvQtyValue)
        val etDays = dialog.findViewById<EditText>(R.id.etPantryDays)

        // Qty stepper
        dialog.findViewById<TextView>(R.id.btnQtyMinus).setOnClickListener {
            if (qty > 10) { qty -= 10; tvQty.text = qty.toInt().toString() }
        }
        dialog.findViewById<TextView>(R.id.btnQtyPlus).setOnClickListener {
            qty += 10; tvQty.text = qty.toInt().toString()
        }

        // Expiry chips
        fun selectChip(days: Int) {
            selectedDays = days
            etDays.setText("")
            listOf(
                dialog.findViewById<TextView>(R.id.chip3)  to 3,
                dialog.findViewById<TextView>(R.id.chip7)  to 7,
                dialog.findViewById<TextView>(R.id.chip14) to 14,
                dialog.findViewById<TextView>(R.id.chip30) to 30
            ).forEach { (chip, chipDays) ->
                if (chipDays == days) {
                    chip.setBackgroundResource(R.drawable.bg_badge_completed)
                    chip.setTextColor(0xFF4A6200.toInt())
                    chip.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_recommended_card)
                    chip.setTextColor(0xFF374151.toInt())
                    chip.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
        }
        selectChip(7) // default

        dialog.findViewById<TextView>(R.id.chip3).setOnClickListener  { selectChip(3)  }
        dialog.findViewById<TextView>(R.id.chip7).setOnClickListener  { selectChip(7)  }
        dialog.findViewById<TextView>(R.id.chip14).setOnClickListener { selectChip(14) }
        dialog.findViewById<TextView>(R.id.chip30).setOnClickListener { selectChip(30) }

        // Confirm button
        dialog.findViewById<LinearLayout>(R.id.btnConfirmAddPantry).setOnClickListener {
            val customDays = etDays.text.toString().toIntOrNull()
            val finalDays  = customDays ?: selectedDays
            val expiry = LocalDate.now().plusDays(finalDays.toLong())
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            dialog.dismiss()
            addToPantry(item.ingredientId!!, qty, expiry, item.displayName)
        }

        dialog.show()
    }

    private fun addToPantry(ingredientId: Int, quantity: Double, expiryDate: String, name: String) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.addPantryItem(
                        userId,
                        AddPantryRequest(
                            ingredientId = ingredientId,
                            quantity     = quantity,
                            expiryDate   = expiryDate
                        )
                    )
                }
                if (response.isSuccessful) {
                    Toast.makeText(this@GroceryActivity, "✓ $name added to Pantry", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@GroceryActivity, "Failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GroceryActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Grocery List Sheet ────────────────────────────────────────────────────

    private fun showGroceryListSheet() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.bottom_sheet_grocery_list)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod
        }

        val listContainer = dialog.findViewById<LinearLayout>(R.id.containerGroceryListItems)
        val btnClear = dialog.findViewById<TextView>(R.id.btnClearChecked)

        fun loadList() {
            scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.api.getGroceryList(userId)
                    }
                    val items = if (response.isSuccessful) response.body()?.data ?: emptyList() else emptyList()
                    listContainer.removeAllViews()

                    if (items.isEmpty()) {
                        listContainer.addView(TextView(this@GroceryActivity).apply {
                            text = "Your list is empty.\nTap + List on any item."
                            textSize = 13f
                            gravity = android.view.Gravity.CENTER
                            setPadding(0, 40, 0, 40)
                            setTextColor(0xFF737373.toInt())
                        })
                        return@launch
                    }

                    items.forEach { item ->
                        val row = LayoutInflater.from(this@GroceryActivity)
                            .inflate(R.layout.item_grocery_list_row, listContainer, false)
                        val cb     = row.findViewById<CheckBox>(R.id.cbGroceryItem)
                        val tvName = row.findViewById<TextView>(R.id.tvGroceryItemName)
                        val tvSub  = row.findViewById<TextView>(R.id.tvGroceryItemPrice)

                        cb.isChecked = item.checked
                        tvName.text  = item.itemName
                        tvSub.text   = "$${String.format("%.2f", item.price)}  •  ${item.category}"

                        fun applyStyle(checked: Boolean) {
                            if (checked) {
                                tvName.paintFlags = tvName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                                tvName.setTextColor(0xFF9CA3AF.toInt())
                            } else {
                                tvName.paintFlags = tvName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                                tvName.setTextColor(0xFF171717.toInt())
                            }
                        }
                        applyStyle(item.checked)

                        cb.setOnCheckedChangeListener { _, isChecked ->
                            applyStyle(isChecked)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    RetrofitClient.api.checkGroceryItem(userId, item.groceryItemId, isChecked)
                                }
                            }
                        }
                        listContainer.addView(row)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@GroceryActivity, "Error loading list", Toast.LENGTH_SHORT).show()
                }
            }
        }

        loadList()

        btnClear.setOnClickListener {
            scope.launch {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.clearCheckedItems(userId)
                }
                loadList()
            }
        }

        dialog.show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun categoryEmoji(category: String) = when (category) {
        "Protein"    -> "🥩"
        "Fruits"     -> "🍎"
        "Vegetables" -> "🥦"
        "Dairy"      -> "🥛"
        "Grains"     -> "🌾"
        "Fats"       -> "🫒"
        "Condiments" -> "🧂"
        else         -> "🍽️"
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

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
