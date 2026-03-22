package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

class CloudKitchenActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    private var selectedPlan    = "daily"
    private var selectedPortion = "regular"
    private var selectedSpice   = "medium"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_kitchen)
        userId = UserPreferences.getUserId(this)

        findViewById<ImageButton>(R.id.btnKitchenBack).setOnClickListener { finish() }

        setupPlanSelection()
        setupPortionSelection()
        setupSpiceSelection()
        loadAllergies()

        // Default selections
        selectPlan("daily")
        selectPortion("regular")
        selectSpice("medium")

        findViewById<TextView>(R.id.btnSendToKitchen).setOnClickListener {
            sendToKitchen()
        }
    }

    // ── Plan selection ────────────────────────────────────────────────────────

    private fun setupPlanSelection() {
        findViewById<LinearLayout>(R.id.cardDailyPlan).setOnClickListener { selectPlan("daily") }
        findViewById<LinearLayout>(R.id.cardWeeklyPlan).setOnClickListener { selectPlan("weekly") }
    }

    private fun selectPlan(plan: String) {
        selectedPlan = plan
        val dailyBg  = if (plan == "daily") R.drawable.bg_tag_green else R.drawable.bg_recommended_card
        val weeklyBg = if (plan == "weekly") R.drawable.bg_tag_green else R.drawable.bg_recommended_card
        findViewById<LinearLayout>(R.id.cardDailyPlan).setBackgroundResource(dailyBg)
        findViewById<LinearLayout>(R.id.cardWeeklyPlan).setBackgroundResource(weeklyBg)
        findViewById<TextView>(R.id.checkDailyPlan).visibility =
            if (plan == "daily") android.view.View.VISIBLE else android.view.View.GONE
        findViewById<TextView>(R.id.checkWeeklyPlan).visibility =
            if (plan == "weekly") android.view.View.VISIBLE else android.view.View.GONE
    }

    // ── Portion selection ─────────────────────────────────────────────────────

    private fun setupPortionSelection() {
        mapOf("small" to R.id.btnPortionSmall, "regular" to R.id.btnPortionRegular, "large" to R.id.btnPortionLarge)
            .forEach { (size, id) ->
                findViewById<TextView>(id).setOnClickListener { selectPortion(size) }
            }
    }

    private fun selectPortion(size: String) {
        selectedPortion = size
        listOf("small" to R.id.btnPortionSmall, "regular" to R.id.btnPortionRegular, "large" to R.id.btnPortionLarge)
            .forEach { (s, id) -> styleToggleBtn(id, s == size) }
    }

    // ── Spice selection ───────────────────────────────────────────────────────

    private fun setupSpiceSelection() {
        mapOf("mild" to R.id.btnSpiceMild, "medium" to R.id.btnSpiceMedium, "hot" to R.id.btnSpiceHot)
            .forEach { (level, id) ->
                findViewById<TextView>(id).setOnClickListener { selectSpice(level) }
            }
    }

    private fun selectSpice(level: String) {
        selectedSpice = level
        listOf("mild" to R.id.btnSpiceMild, "medium" to R.id.btnSpiceMedium, "hot" to R.id.btnSpiceHot)
            .forEach { (s, id) -> styleToggleBtn(id, s == level) }
    }

    private fun styleToggleBtn(viewId: Int, selected: Boolean) {
        val tv = findViewById<TextView>(viewId)
        if (selected) {
            tv.setBackgroundResource(R.drawable.bg_tag_green)
            tv.setTextColor(0xFF4A6200.toInt())
        } else {
            tv.setBackgroundResource(R.drawable.bg_recommended_card)
            tv.setTextColor(0xFF374151.toInt())
        }
    }

    // ── Load user allergies ───────────────────────────────────────────────────

    private fun loadAllergies() {
        val allergiesStr = UserPreferences.getAllergies(this)
        val container    = findViewById<LinearLayout>(R.id.containerAllergies)
        container.removeAllViews()

        if (allergiesStr.isEmpty()) {
            val tv = TextView(this)
            tv.text = "None"
            tv.setTextColor(0xFF888888.toInt())
            tv.textSize = 12f
            container.addView(tv)
            return
        }

        allergiesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { allergy ->
            val tag = TextView(this)
            tag.text = allergy
            tag.textSize = 11f
            tag.setTextColor(0xFF374151.toInt())
            tag.setBackgroundResource(R.drawable.bg_recommended_card)
            tag.setPadding(20, 8, 20, 8)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = 8
            tag.layoutParams = params
            container.addView(tag)
        }
    }

    // ── Send to kitchen ───────────────────────────────────────────────────────

    private fun sendToKitchen() {
        // Navigate to status screen (Preparing state)
        val intent = Intent(this, CloudKitchenStatusActivity::class.java)
        intent.putExtra(CloudKitchenStatusActivity.EXTRA_PLAN,    selectedPlan)
        intent.putExtra(CloudKitchenStatusActivity.EXTRA_PORTION, selectedPortion)
        intent.putExtra(CloudKitchenStatusActivity.EXTRA_SPICE,   selectedSpice)
        startActivity(intent)
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
