package com.example.fuelify.home

import android.content.Intent
import com.example.fuelify.home.DietActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CloudKitchenStatusActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAN    = "extra_plan"
        const val EXTRA_PORTION = "extra_portion"
        const val EXTRA_SPICE   = "extra_spice"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_kitchen_status)

        val userId  = UserPreferences.getUserId(this)
        val plan    = intent.getStringExtra(EXTRA_PLAN)    ?: "daily"
        val portion = intent.getStringExtra(EXTRA_PORTION) ?: "regular"
        val spice   = intent.getStringExtra(EXTRA_SPICE)   ?: "medium"

        findViewById<ImageButton>(R.id.btnStatusBack).setOnClickListener { finish() }

        // Start with "Preparing" state
        showPreparingState()

        // Save order to DB then simulate preparation → delivery transition
        saveOrderAndSimulate(userId, plan, portion, spice)

        findViewById<TextView>(R.id.btnBackToHome).setOnClickListener {
            val intent = Intent(this, DietActivity::class.java)
            startActivity(intent)
            finish() // closes this screen so user doesn't come back to it
        }
    }

    private fun showPreparingState() {
        findViewById<ProgressBar>(R.id.statusSpinner).visibility    = View.VISIBLE
        findViewById<TextView>(R.id.statusCheckmark).visibility     = View.GONE
        findViewById<LinearLayout>(R.id.cardDeliveryTime).visibility= View.GONE
        findViewById<TextView>(R.id.tvStatusTitle).text    = "Preparing Your Meals"
        findViewById<TextView>(R.id.tvStatusSubtitle).text = "Our kitchen is working on your order"
    }

    private fun showDeliveredState() {
        findViewById<ProgressBar>(R.id.statusSpinner).visibility    = View.GONE
        findViewById<TextView>(R.id.statusCheckmark).visibility     = View.VISIBLE
        findViewById<LinearLayout>(R.id.cardDeliveryTime).visibility= View.VISIBLE
        findViewById<TextView>(R.id.tvStatusTitle).text    = "Out for Delivery!"
        findViewById<TextView>(R.id.tvStatusSubtitle).text = "Your meals will arrive soon"

        // Calculate delivery window: now + 1hr to now + 2hr
        val fmt      = DateTimeFormatter.ofPattern("h:mm a")
        val start    = LocalTime.now().plusHours(1)
        val end      = LocalTime.now().plusHours(2)
        val timeText = "Today, ${start.format(fmt)} - ${end.format(fmt)}"
        findViewById<TextView>(R.id.tvDeliveryTime).text = timeText
    }

    private fun saveOrderAndSimulate(userId: Int, plan: String, portion: String, spice: String) {
        scope.launch {
            try {
                // Save to kitchen_order table
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.createKitchenOrder(userId,
                        com.example.fuelify.data.api.models.KitchenOrderRequest(
                            planType    = plan,
                            portionSize = portion,
                            spiceLevel  = spice,
                            notes        = ""
                        )
                    )
                }
            } catch (e: Exception) {
                // Silent — UI still proceeds even if DB save fails
            }

            // After 3 seconds, transition to "Out for Delivery" state
            // (simulated — in production this would be a webhook/push from kitchen)
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing) showDeliveredState()
            }, 3000)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
