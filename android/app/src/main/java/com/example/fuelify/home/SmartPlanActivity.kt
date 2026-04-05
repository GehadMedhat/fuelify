package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class SmartPlanActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_plan)
        userId = UserPreferences.getUserId(this)
        findViewById<ImageButton>(R.id.btnSmartPlanBack).setOnClickListener { finish() }

        // Apply Smart Plan to DB
        findViewById<LinearLayout>(R.id.btnApplySmartPlan).setOnClickListener {
            applySmartPlan()
        }

        // View My Updated Plan
        findViewById<LinearLayout>(R.id.btnViewUpdatedPlan).setOnClickListener {
            startActivity(Intent(this, WorkoutHomeActivity::class.java))
            finish()
        }

        loadSmartPlan()
        loadPreview()
    }

    private fun loadPreview() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getSmartPlanPreview(userId) }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val preview = resp.body()!!.data!!
                    bindPreview(preview)
                }
            } catch (e: Exception) { /* silent — preview is bonus */ }
        }
    }

    private fun bindPreview(preview: com.example.fuelify.data.api.models.SmartPlanPreview) {
        val container = findViewById<LinearLayout>(R.id.containerPreview)
        container.removeAllViews()

        if (preview.totalChanges == 0) {
            container.addView(TextView(this).apply {
                text = "✅ Your current plan already matches your health conditions. No changes needed!"
                textSize = 13f
                setTextColor(0xFF22C55E.toInt())
                setPadding(0, 8, 0, 8)
            })
            return
        }

        // Meal swaps
        if (preview.mealSwaps.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = "🥗 Meal Changes (${preview.mealSwaps.size})"
                textSize = 14f
                setTextColor(0xFF000000.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 4, 0, 8)
            })
            preview.mealSwaps.forEach { swap ->
                val row = android.view.LayoutInflater.from(this)
                    .inflate(R.layout.item_preview_swap_row, container, false)
                row.findViewById<TextView>(R.id.tvSwapOld).text    = swap.oldMealName
                row.findViewById<TextView>(R.id.tvSwapNew).text    = "→ ${swap.newMealName}"
                row.findViewById<TextView>(R.id.tvSwapReason).text = swap.reason
                row.findViewById<TextView>(R.id.tvSwapDate).text   = swap.planDate
                container.addView(row)
            }
        }

        // Workout swaps
        if (preview.workoutSwaps.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = "💪 Workout Changes (${preview.workoutSwaps.size})"
                textSize = 14f
                setTextColor(0xFF000000.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 16, 0, 8)
            })
            preview.workoutSwaps.forEach { swap ->
                val row = android.view.LayoutInflater.from(this)
                    .inflate(R.layout.item_preview_swap_row, container, false)
                row.findViewById<TextView>(R.id.tvSwapOld).text    = swap.oldWorkoutName
                row.findViewById<TextView>(R.id.tvSwapNew).text    = "→ ${swap.newWorkoutName}"
                row.findViewById<TextView>(R.id.tvSwapReason).text = swap.reason
                row.findViewById<TextView>(R.id.tvSwapDate).text   = swap.scheduledDate
                container.addView(row)
            }
        }

        // Show count badge
        val tvCount = findViewById<TextView>(R.id.tvPreviewCount)
        tvCount?.text = "${preview.totalChanges} changes will be made"
        tvCount?.visibility = android.view.View.VISIBLE
    }

    private fun loadSmartPlan() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getSmartPlan(userId) }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    bindSmartPlan(resp.body()!!.data!!)
                } else {
                    Toast.makeText(this@SmartPlanActivity,
                        "Could not load plan. Save your medical info first.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SmartPlanActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindSmartPlan(plan: SmartPlan) {
        // Condition badges
        val badgeContainer = findViewById<LinearLayout>(R.id.containerConditionBadges)
        badgeContainer.removeAllViews()
        if (plan.conditions.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No medical conditions added"
                textSize = 13f
                setTextColor(0xFF737373.toInt())
            }
            badgeContainer.addView(tv)
        } else {
            plan.conditions.forEach { cond ->
                val badge = LayoutInflater.from(this)
                    .inflate(R.layout.item_condition_badge, badgeContainer, false)
                badge.findViewById<TextView>(R.id.tvConditionBadge).text = cond
                badgeContainer.addView(badge)
            }
        }

        // ── Diet Plan ─────────────────────────────────────────────────────────

        // Removed items
        val dietRemovedContainer = findViewById<LinearLayout>(R.id.containerDietRemoved)
        dietRemovedContainer.removeAllViews()
        if (plan.dietRemovedItems.isEmpty()) {
            dietRemovedContainer.addView(emptyStateText("No items to remove"))
        } else {
            plan.dietRemovedItems.take(3).forEach { item ->
                dietRemovedContainer.addView(buildPlanItemRow(item, removed = true))
            }
        }

        // Added items
        val dietAddedContainer = findViewById<LinearLayout>(R.id.containerDietAdded)
        dietAddedContainer.removeAllViews()
        if (plan.dietAddedItems.isEmpty()) {
            dietAddedContainer.addView(emptyStateText("No items to add"))
        } else {
            plan.dietAddedItems.take(3).forEach { item ->
                dietAddedContainer.addView(buildPlanItemRow(item, removed = false))
            }
        }

        // Doctor recommendations
        val drContainer = findViewById<LinearLayout>(R.id.containerDoctorRecs)
        drContainer.removeAllViews()
        val topRec = plan.doctorRecommendations.firstOrNull()
        if (topRec != null) {
            val tv = TextView(this).apply {
                text = "💡 $topRec"
                textSize = 12f
                setTextColor(0xFF92400E.toInt())
                setPadding(0, 0, 0, 0)
            }
            drContainer.addView(tv)
        }

        // ── Workout Plan ──────────────────────────────────────────────────────

        val wkStatusBadge = findViewById<TextView>(R.id.tvWorkoutPlanStatus)
        wkStatusBadge.text = if (plan.workoutRemovedItems.isNotEmpty()) "Restricted / Recovery Mode" else "Optimized"
        wkStatusBadge.setTextColor(
            if (plan.workoutRemovedItems.isNotEmpty()) 0xFFEF4444.toInt() else 0xFF22C55E.toInt()
        )

        // Removed exercises
        val wkRemovedContainer = findViewById<LinearLayout>(R.id.containerWorkoutRemoved)
        wkRemovedContainer.removeAllViews()
        if (plan.workoutRemovedItems.isEmpty()) {
            wkRemovedContainer.addView(emptyStateText("No exercises restricted"))
        } else {
            plan.workoutRemovedItems.take(3).forEach { item ->
                wkRemovedContainer.addView(buildPlanItemRow(item, removed = true))
            }
        }

        // Added exercises
        val wkAddedContainer = findViewById<LinearLayout>(R.id.containerWorkoutAdded)
        wkAddedContainer.removeAllViews()
        if (plan.workoutAddedItems.isEmpty()) {
            wkAddedContainer.addView(emptyStateText("Continue with your current plan"))
        } else {
            plan.workoutAddedItems.take(3).forEach { item ->
                wkAddedContainer.addView(buildPlanItemRow(item, removed = false))
            }
        }

        // Recovery recommendations
        val recContainer = findViewById<LinearLayout>(R.id.containerRecoveryRecs)
        recContainer.removeAllViews()
        val topRecovery = plan.recoveryRecommendations.firstOrNull()
        if (topRecovery != null) {
            val tv = TextView(this).apply {
                text = "💡 $topRecovery"
                textSize = 12f
                setTextColor(0xFF92400E.toInt())
            }
            recContainer.addView(tv)
        }
    }

    private fun buildPlanItemRow(item: PlanItem, removed: Boolean): View {
        // Compact chip-style row
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val bullet = TextView(this).apply {
            text = if (removed) "✕ " else "✓ "
            textSize = 13f
            setTextColor(if (removed) 0xFFEF4444.toInt() else 0xFF22C55E.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val label = TextView(this).apply {
            text = "${item.name}  —  ${item.reason}"
            textSize = 13f
            setTextColor(0xFF374151.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        ll.addView(bullet)
        ll.addView(label)
        return ll
    }

    private fun emptyStateText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(0, 8, 0, 8)
        }
    }

    private fun applySmartPlan() {
        val btn = findViewById<LinearLayout>(R.id.btnApplySmartPlan)
        val tv  = btn.getChildAt(0) as? TextView
        tv?.text = "Applying..."

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.applySmartPlan(userId)
                }
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val msg = resp.body()?.data?.message ?: resp.body()?.message ?: "Plan applied!"
                    tv?.text = "✓ Applied!"
                    btn.setBackgroundColor(0xFF22C55E.toInt())
                    Toast.makeText(this@SmartPlanActivity, msg, Toast.LENGTH_LONG).show()
                    // Show view button now
                    findViewById<LinearLayout>(R.id.btnViewUpdatedPlan).visibility = android.view.View.VISIBLE
                } else {
                    tv?.text = "Apply to My Plan"
                    Toast.makeText(this@SmartPlanActivity, "Failed to apply plan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                tv?.text = "Apply to My Plan"
                Toast.makeText(this@SmartPlanActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
