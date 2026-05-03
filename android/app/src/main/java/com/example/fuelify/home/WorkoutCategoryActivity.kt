package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.CategoryItem
import kotlinx.coroutines.*

class WorkoutCategoryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var allCategories = listOf<CategoryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_category)

        findViewById<ImageButton>(R.id.btnCategoryBack).setOnClickListener { finish() }

        // Search
        findViewById<EditText>(R.id.etCategorySearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterCategories(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadCategories()
    }

    private fun loadCategories() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getWorkoutCategories() }
                if (resp.isSuccessful) {
                    allCategories = resp.body()?.data ?: emptyList()
                    bindCategories(allCategories)
                }
            } catch (e: Exception) {
                Toast.makeText(this@WorkoutCategoryActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterCategories(query: String) {
        val filtered = if (query.isBlank()) allCategories
        else allCategories.filter { it.category.contains(query, ignoreCase = true) }
        bindCategories(filtered)
    }

    private fun bindCategories(categories: List<CategoryItem>) {
        val container = findViewById<LinearLayout>(R.id.containerCategories)
        container.removeAllViews()

        categories.forEach { cat ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_category_row, container, false)

            val iconBg = row.findViewById<LinearLayout>(R.id.layoutCategoryIcon)
            try {
                val color = android.graphics.Color.parseColor(cat.color)
                iconBg.setBackgroundColor(color)
            } catch (e: Exception) {}

            row.findViewById<TextView>(R.id.tvCategoryRowEmoji).text = cat.emoji
            row.findViewById<TextView>(R.id.tvCategoryRowName).text  = cat.category
            row.findViewById<TextView>(R.id.tvCategoryRowCount).text = "${cat.workoutCount} workouts"

            row.setOnClickListener {
                startActivity(Intent(this, WorkoutListActivity::class.java).apply {
                    putExtra("category", cat.category)
                })
            }
            container.addView(row)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
