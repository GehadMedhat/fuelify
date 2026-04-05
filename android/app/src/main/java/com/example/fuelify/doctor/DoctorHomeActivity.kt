package com.example.fuelify.doctor

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.InboxCase
import com.example.fuelify.utils.DoctorPreferences
import kotlinx.coroutines.*

class DoctorHomeActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var doctorId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_home)

        doctorId = DoctorPreferences.getDoctorId(this)
        val profile = DoctorPreferences.getProfile(this)

        // Header
        val specialty = when (profile?.specialty) {
            "diet"    -> "Diet & Nutrition"
            "workout" -> "Sports & Fitness"
            else      -> "General Practice"
        }
        findViewById<TextView>(R.id.tvDoctorName).text = "Dr. ${profile?.fullName ?: "Doctor"}"
        findViewById<TextView>(R.id.tvDoctorSpecialty).text = specialty

        // Wallet icon
        findViewById<LinearLayout>(R.id.btnWallet).setOnClickListener {
            startActivity(Intent(this, DoctorWalletActivity::class.java))
        }

        // Filter tabs
        setupFilterTabs()

        // Refresh
        findViewById<LinearLayout>(R.id.btnRefreshInbox).setOnClickListener { loadInbox() }

        loadInbox()
    }

    override fun onResume() { super.onResume(); loadInbox() }

    private var currentFilter = "all"   // "all" | "pending" | "responded"

    private fun setupFilterTabs() {
        listOf(
            R.id.btnFilterAll       to "all",
            R.id.btnFilterPending   to "pending",
            R.id.btnFilterResponded to "responded"
        ).forEach { (btnId, filter) ->
            findViewById<TextView>(btnId).setOnClickListener {
                currentFilter = filter
                updateFilterUI()
                filterCases()
            }
        }
    }

    private fun updateFilterUI() {
        listOf(
            R.id.btnFilterAll       to "all",
            R.id.btnFilterPending   to "pending",
            R.id.btnFilterResponded to "responded"
        ).forEach { (btnId, filter) ->
            val tv = findViewById<TextView>(btnId)
            if (filter == currentFilter) {
                tv.setBackgroundColor(0xFFF97316.toInt())
                tv.setTextColor(0xFFFFFFFF.toInt())
            } else {
                tv.setBackgroundColor(0xFFE5E7EB.toInt())
                tv.setTextColor(0xFF374151.toInt())
            }
        }
    }

    private var allCases = listOf<InboxCase>()

    private fun filterCases() {
        val filtered = when (currentFilter) {
            "pending"   -> allCases.filter { !it.doctorResponded }
            "responded" -> allCases.filter { it.doctorResponded }
            else        -> allCases
        }
        bindCases(filtered)
    }

    private fun loadInbox() {
        val tvEmpty = findViewById<TextView>(R.id.tvInboxEmpty)
        val container = findViewById<LinearLayout>(R.id.containerInbox)
        tvEmpty.visibility = View.GONE
        container.removeAllViews()

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getDoctorInbox(doctorId) }
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val cases = resp.body()!!.data ?: emptyList()
                    allCases = cases

                    // Stats
                    val pendingCount    = cases.count { !it.doctorResponded }
                    val respondedCount  = cases.count { it.doctorResponded }
                    findViewById<TextView>(R.id.tvStatPending).text   = "$pendingCount"
                    findViewById<TextView>(R.id.tvStatResponded).text = "$respondedCount"
                    findViewById<TextView>(R.id.tvStatTotal).text     = "${cases.size}"

                    filterCases()
                } else {
                    tvEmpty.text = resp.body()?.message ?: "No cases yet"
                    tvEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                tvEmpty.text = "Network error. Pull to refresh."
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun bindCases(cases: List<InboxCase>) {
        val container = findViewById<LinearLayout>(R.id.containerInbox)
        val tvEmpty   = findViewById<TextView>(R.id.tvInboxEmpty)
        container.removeAllViews()

        if (cases.isEmpty()) {
            tvEmpty.text = when (currentFilter) {
                "pending"   -> "No pending cases 🎉 You're all caught up!"
                "responded" -> "You haven't responded to any cases yet."
                else        -> "No cases in your inbox yet."
            }
            tvEmpty.visibility = View.VISIBLE
            return
        }
        tvEmpty.visibility = View.GONE

        cases.forEach { case ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_doctor_case_card, container, false)

            card.findViewById<TextView>(R.id.tvCasePatientName).text  = case.patientName
            card.findViewById<TextView>(R.id.tvCaseCondition).text    = case.conditionName
            card.findViewById<TextView>(R.id.tvCaseSymptoms).text     = case.symptoms.take(80) + if (case.symptoms.length > 80) "..." else ""
            card.findViewById<TextView>(R.id.tvCaseDate).text         = case.createdAt
            card.findViewById<TextView>(R.id.tvCaseMsgCount).text     = "${case.messageCount} messages"

            val tvStatus = card.findViewById<TextView>(R.id.tvCaseCardStatus)
            if (case.doctorResponded) {
                tvStatus.text = "✓ Responded"
                tvStatus.setTextColor(0xFF22C55E.toInt())
                tvStatus.setBackgroundColor(0xFFDCFCE7.toInt())
            } else {
                tvStatus.text = "Needs Response"
                tvStatus.setTextColor(0xFFF97316.toInt())
                tvStatus.setBackgroundColor(0xFFFFF7ED.toInt())
            }

            val tvSpecialty = card.findViewById<TextView>(R.id.tvCaseSpecialty)
            tvSpecialty.text = when (case.specialtyNeeded) {
                "diet"    -> "🥗 Diet"
                "workout" -> "💪 Fitness"
                else      -> "🩺 General"
            }

            card.setOnClickListener {
                startActivity(Intent(this, DoctorCaseActivity::class.java).apply {
                    putExtra("case_id", case.caseId)
                    putExtra("patient_name", case.patientName)
                })
            }
            container.addView(card)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
