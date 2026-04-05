package com.example.fuelify.doctor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.DoctorMessage
import com.example.fuelify.data.api.models.DoctorRespondRequest
import com.example.fuelify.utils.DoctorPreferences
import kotlinx.coroutines.*

class DoctorCaseActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var doctorId = -1
    private var caseId   = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_case)

        doctorId = DoctorPreferences.getDoctorId(this)
        caseId   = intent.getIntExtra("case_id", -1)
        val patientName = intent.getStringExtra("patient_name") ?: "Patient"

        findViewById<TextView>(R.id.tvCasePatientHeader).text = patientName
        findViewById<ImageButton>(R.id.btnDoctorCaseBack).setOnClickListener { finish() }

        // Quick response chips
        setupQuickResponses()

        // Send response
        findViewById<LinearLayout>(R.id.btnDoctorSendResponse).setOnClickListener {
            sendResponse()
        }

        loadCase()
    }

    private fun setupQuickResponses() {
        val chips = listOf(
            "Rest 48–72 hrs" to "Based on your symptoms, I recommend rest and avoiding heavy activity for 48–72 hours.",
            "Ice the area"   to "Please apply ice to the affected area for 15 minutes every 2–3 hours.",
            "Anti-inflam diet" to "Focus on anti-inflammatory foods: salmon, leafy greens, and turmeric.",
            "Reduce carbs"   to "Reduce processed carbohydrates and increase protein to 1.6g per kg body weight.",
            "Light stretching" to "For recovery, light stretching and 10–15 min flat-surface walks are safe to start."
        )
        val container = findViewById<LinearLayout?>(R.id.containerQuickReplies) ?: return

        chips.forEach { (label, fullText) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 11f
                isClickable = true
                isFocusable = true
                setTextColor(0xFFF97316.toInt())
                setBackgroundColor(0xFFFFF7ED.toInt())
                setPadding(20, 10, 20, 10)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 8
                layoutParams = lp
            }

            // Set touch listener instead of click listener to bypass scroll interception
            chip.setOnTouchListener { v, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    (v.parent?.parent as? android.widget.HorizontalScrollView)
                        ?.requestDisallowInterceptTouchEvent(true)
                    val et = findViewById<EditText>(R.id.etDoctorResponse) ?: return@setOnTouchListener false
                    val current = et.text.toString().trim()
                    et.setText(if (current.isEmpty()) fullText else "$current\n\n$fullText")
                    et.setSelection(et.text.length)
                    v.performClick()
                }
                true
            }

            container.addView(chip)
        }
    }

    private fun loadCase() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getDoctorCase(doctorId, caseId)
                }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val case = resp.body()!!.data!!
                    bindCase(case.conditionName, case.affectedArea, case.symptoms, case.limitations, case.status, case.messages)
                } else {
                    Toast.makeText(this@DoctorCaseActivity, "Failed to load case", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DoctorCaseActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindCase(
        condition: String, area: String, symptoms: String,
        limitations: String, status: String, messages: List<DoctorMessage>
    ) {
        // Case overview
        findViewById<TextView>(R.id.tvDoctorCaseCondition).text = condition
        findViewById<TextView>(R.id.tvDoctorCaseArea).text      = "Area: $area"
        findViewById<TextView>(R.id.tvDoctorCaseSymptoms).text  = symptoms
        if (limitations.isNotBlank()) {
            findViewById<TextView>(R.id.tvDoctorCaseLimitations).text    = limitations
            findViewById<TextView>(R.id.tvDoctorCaseLimitations).visibility = View.VISIBLE
        }

        // Status badge
        val tvStatus = findViewById<TextView>(R.id.tvDoctorCaseStatus)
        tvStatus.text = status.replaceFirstChar { it.uppercase() }
        tvStatus.setBackgroundColor(if (status == "responded") 0xFFDCFCE7.toInt() else 0xFFFFF7ED.toInt())
        tvStatus.setTextColor(if (status == "responded") 0xFF22C55E.toInt() else 0xFFF97316.toInt())

        // Always show respond form — doctor can send multiple messages
        val alreadyResponded = messages.any {
            it.senderType == "doctor" && it.senderName.startsWith("Dr.")
        }
        // Show "already responded" info banner (wallet credited) but keep form visible
        if (alreadyResponded) {
            findViewById<LinearLayout>(R.id.layoutAlreadyResponded).visibility = View.VISIBLE
        }
        // Update button label for follow-ups
        val btnLabel = findViewById<LinearLayout>(R.id.btnDoctorSendResponse)
            .getChildAt(0) as? TextView
        btnLabel?.text = if (alreadyResponded) "Send Follow-up Message" else "✓  Send Response (+25 EGP)"
        // Form always visible
        findViewById<LinearLayout>(R.id.layoutDoctorRespond).visibility = View.VISIBLE

        // Messages
        val container = findViewById<LinearLayout>(R.id.containerDoctorMessages)
        container.removeAllViews()
        messages.forEach { msg -> container.addView(buildMessageBubble(msg)) }

        // Scroll to bottom
        val scrollView = findViewById<ScrollView>(R.id.scrollDoctorCase)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun buildMessageBubble(msg: DoctorMessage): View {
        val isPatient = msg.senderType == "user"
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            layoutParams = lp
            setPadding(0, 0, 0, 0)
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isPatient) 0xFFF3F4F6.toInt() else 0xFFFFF7ED.toInt())
            setPadding(16, 12, 16, 12)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (isPatient) {
                lp.marginEnd = (60 * resources.displayMetrics.density).toInt()
            } else {
                lp.marginStart = (60 * resources.displayMetrics.density).toInt()
            }
            layoutParams = lp
        }

        val tvName = TextView(this).apply {
            text = msg.senderName
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (isPatient) 0xFF374151.toInt() else 0xFFF97316.toInt())
            setPadding(0, 0, 0, 6)
        }

        val tvContent = TextView(this).apply {
            text = msg.content
            textSize = 13f
            setTextColor(0xFF374151.toInt())
            setPadding(0, 0, 0, 6)
        }

        val tvTime = TextView(this).apply {
            text = msg.sentAt
            textSize = 10f
            setTextColor(0xFF9CA3AF.toInt())
        }

        bubble.addView(tvName)
        bubble.addView(tvContent)
        bubble.addView(tvTime)
        ll.addView(bubble)
        return ll
    }

    private fun sendResponse() {
        val content = findViewById<EditText>(R.id.etDoctorResponse).text.toString().trim()
        if (content.isBlank()) {
            Toast.makeText(this, "Please write a response", Toast.LENGTH_SHORT).show()
            return
        }

        val btnSend = findViewById<LinearLayout>(R.id.btnDoctorSendResponse)
        val btnTv   = btnSend.getChildAt(0) as? TextView
        btnTv?.text = "Sending..."
        btnSend.isEnabled = false

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.doctorRespond(doctorId, caseId, DoctorRespondRequest(content))
                }
                if (resp.isSuccessful) {
                    val msg = resp.body()?.message ?: "Response sent!"
                    Toast.makeText(this@DoctorCaseActivity, msg, Toast.LENGTH_LONG).show()
                    // Show wallet credited snackbar
                    if (msg.contains("EGP")) {
                        com.google.android.material.snackbar.Snackbar.make(
                            btnSend, "💰 $msg", com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                    }
                    findViewById<EditText>(R.id.etDoctorResponse).setText("")
                    btnSend.isEnabled = true          // ← re-enable before loadCase
                    btnTv?.text = "Send Follow-up Message"   // ← update label
                    loadCase()
                } else {
                    btnTv?.text = "Send Response"
                    btnSend.isEnabled = true
                    Toast.makeText(this@DoctorCaseActivity,
                        "Failed: ${resp.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnTv?.text = "Send Response"
                btnSend.isEnabled = true
                Toast.makeText(this@DoctorCaseActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
