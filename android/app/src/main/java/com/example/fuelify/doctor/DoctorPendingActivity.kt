// ════════════════════════════════════════════════════════════════════════════
// DoctorPendingActivity.kt
// ════════════════════════════════════════════════════════════════════════════
package com.example.fuelify.doctor

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.DoctorLoginRequest
import com.example.fuelify.utils.DoctorPreferences
import kotlinx.coroutines.*

class DoctorPendingActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_pending)

        val message  = intent.getStringExtra("message") ?: "We will review your profile within 24–48 hours."
        val email    = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""

        findViewById<TextView>(R.id.tvPendingMessage).text = message

        // Check if approved and go to inbox
        findViewById<LinearLayout>(R.id.btnCheckApproval).setOnClickListener {
            checkApproval(email, password)
        }
    }

    private fun checkApproval(email: String, password: String) {
        val tv = (findViewById<LinearLayout>(R.id.btnCheckApproval).getChildAt(0) as? TextView)
        tv?.text = "Checking..."
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.doctorLogin(DoctorLoginRequest(email, password))
                }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val profile = resp.body()!!.data!!
                    DoctorPreferences.saveDoctor(this@DoctorPendingActivity, profile)
                    if (profile.isApproved) {
                        startActivity(Intent(this@DoctorPendingActivity, DoctorHomeActivity::class.java))
                        finish()
                    } else {
                        tv?.text = "Check Approval Status"
                        Toast.makeText(this@DoctorPendingActivity,
                            "Still pending approval. We'll email you when approved!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    tv?.text = "Check Approval Status"
                    Toast.makeText(this@DoctorPendingActivity, "Login failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                tv?.text = "Check Approval Status"
                Toast.makeText(this@DoctorPendingActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
