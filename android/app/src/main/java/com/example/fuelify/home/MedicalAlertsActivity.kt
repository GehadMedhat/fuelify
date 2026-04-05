package com.example.fuelify.home

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class MedicalAlertsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_alerts)
        userId = UserPreferences.getUserId(this)
        findViewById<ImageButton>(R.id.btnAlertsBack).setOnClickListener { finish() }
        loadAlerts()
    }

    override fun onResume() { super.onResume(); loadAlerts() }

    private fun loadAlerts() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getMedicalAlerts(userId) }

                if (resp.isSuccessful) {
                    val body = resp.body()

                    // Log for debugging
                    android.util.Log.d("MedicalAlerts", "success=${body?.success} data=${body?.data}")

                    if (body?.success == true) {
                        val data         = body.data
                        val alerts       = data?.alerts       ?: emptyList()
                        val alertsToday  = data?.alertsToday  ?: alerts.size
                        val swapsApplied = data?.swapsApplied ?: 0

                        android.util.Log.d("MedicalAlerts", "alerts count=${alerts.size}")

                        if (alerts.isEmpty()) {
                            // Alerts exist in DB but came back empty — Gson parse issue
                            // Try re-parsing raw body as fallback
                            android.util.Log.w("MedicalAlerts", "Alerts list empty — check Gson deserialization")
                        }

                        bindAlerts(alerts, alertsToday, swapsApplied)
                    } else {
                        bindAlerts(emptyList(), 0, 0)
                    }
                } else {
                    val errorBody = resp.errorBody()?.string()
                    android.util.Log.e("MedicalAlerts", "HTTP ${resp.code()}: $errorBody")
                    bindAlerts(emptyList(), 0, 0)
                    if (resp.code() != 200) {
                        Toast.makeText(this@MedicalAlertsActivity,
                            "Error ${resp.code()}: $errorBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MedicalAlerts", "Exception: ${e.message}", e)
                Toast.makeText(this@MedicalAlertsActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindAlerts(alerts: List<MedicalAlert>, alertsToday: Int, swapsApplied: Int) {
        val container = findViewById<LinearLayout>(R.id.containerAlerts)
        container.removeAllViews()

        if (alerts.isEmpty()) {
            val tv = TextView(this).apply {
                text = "✅ No active alerts today! Your plan looks great."
                textSize = 14f
                setTextColor(0xFF374151.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 60, 0, 60)
            }
            container.addView(tv)
        } else {
            alerts.forEach { alert -> container.addView(buildAlertCard(alert)) }
        }

        // Summary
        findViewById<TextView>(R.id.tvAlertsToday).text  = "$alertsToday"
        findViewById<TextView>(R.id.tvSwapsApplied).text = "$swapsApplied"
    }

    private fun buildAlertCard(alert: MedicalAlert): View {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_medical_alert_card, null, false)

        // Border color by severity
        val borderColor = when (alert.severity) {
            "danger"  -> 0xFFEF4444.toInt()
            "warning" -> 0xFFF97316.toInt()
            else      -> 0xFFC3E66E.toInt()
        }
        card.background?.setTint(0xFFFFFFFF.toInt())

        // Icon
        val tvIcon = card.findViewById<TextView>(R.id.tvAlertIcon)
        tvIcon.text = when (alert.alertType) {
            "high_sodium"  -> "⚠️"
            "high_sugar"   -> "⚠️"
            "exercise_mod" -> "💙"
            "meal_swap"    -> "🔄"
            "allergy"      -> "⚠️"
            else           -> "ℹ️"
        }
        val iconBg = when (alert.severity) {
            "danger"  -> 0xFFFEE2E2.toInt()
            "warning" -> 0xFFFFF7ED.toInt()
            else      -> 0xFFDCFCE7.toInt()
        }
        (tvIcon.parent as? LinearLayout)?.setBackgroundColor(iconBg)

        card.findViewById<TextView>(R.id.tvAlertTitle).text   = alert.title
        card.findViewById<TextView>(R.id.tvAlertMessage).text = alert.message

        if (alert.suggestion.isNotEmpty()) {
            card.findViewById<TextView>(R.id.tvAlertSuggestion).text = "🔄  ${alert.suggestion}"
        }

        // Dismiss
        card.findViewById<LinearLayout>(R.id.btnAlertDismiss).setOnClickListener {
            performAlertAction(alert.alertId, "dismiss")
            card.animate().alpha(0f).setDuration(300).withEndAction {
                (card.parent as? LinearLayout)?.removeView(card)
                updateSummaryCount(-1, 0)
            }.start()
        }

        // Apply Swap
        val btnApply = card.findViewById<LinearLayout>(R.id.btnAlertApply)
        btnApply.setOnClickListener {
            performAlertAction(alert.alertId, "apply")
            btnApply.setBackgroundColor(0xFF22C55E.toInt())
            (btnApply.getChildAt(0) as? TextView)?.text = "✓ Applied"
            card.postDelayed({
                (card.parent as? LinearLayout)?.removeView(card)
                updateSummaryCount(-1, 1)
            }, 600)
        }

        // Set card border color via tag (we use a colored side bar)
        val sideBar = card.findViewById<View>(R.id.viewAlertBorder)
        sideBar?.setBackgroundColor(borderColor)

        return card
    }

    private fun updateSummaryCount(alertDelta: Int, swapDelta: Int) {
        val tvAlerts = findViewById<TextView>(R.id.tvAlertsToday)
        val tvSwaps  = findViewById<TextView>(R.id.tvSwapsApplied)
        val currentAlerts = tvAlerts.text.toString().toIntOrNull() ?: 0
        val currentSwaps  = tvSwaps.text.toString().toIntOrNull() ?: 0
        tvAlerts.text = "${(currentAlerts + alertDelta).coerceAtLeast(0)}"
        tvSwaps.text  = "${currentSwaps + swapDelta}"
    }

    private fun performAlertAction(alertId: Int, action: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.updateMedicalAlert(userId, alertId, AlertActionRequest(action))
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
