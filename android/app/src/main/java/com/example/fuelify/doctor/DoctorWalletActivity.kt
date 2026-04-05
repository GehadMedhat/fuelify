package com.example.fuelify.doctor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.WalletTransaction
import com.example.fuelify.utils.DoctorPreferences
import kotlinx.coroutines.*

class DoctorWalletActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var doctorId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_wallet)
        doctorId = DoctorPreferences.getDoctorId(this)
        findViewById<ImageButton>(R.id.btnWalletBack).setOnClickListener { finish() }
        loadWallet()
    }

    private fun loadWallet() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getDoctorWallet(doctorId) }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val wallet = resp.body()!!.data!!
                    bindWallet(wallet.balance, wallet.transactions)
                }
            } catch (e: Exception) {
                Toast.makeText(this@DoctorWalletActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindWallet(balance: Double, transactions: List<WalletTransaction>) {
        // Balance display
        findViewById<TextView>(R.id.tvWalletBalance).text = "%.2f EGP".format(balance)

        // Total earned
        val totalEarned = transactions.filter { it.type == "credit" }.sumOf { it.amount }
        val totalCases  = transactions.filter { it.type == "credit" }.size
        findViewById<TextView>(R.id.tvWalletTotalEarned).text  = "%.0f EGP".format(totalEarned)
        findViewById<TextView>(R.id.tvWalletTotalCases).text   = "$totalCases cases"

        // Transaction list
        val container = findViewById<LinearLayout>(R.id.containerTransactions)
        container.removeAllViews()

        if (transactions.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No transactions yet. Start responding to cases!"
                textSize = 13f
                setTextColor(0xFF9CA3AF.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 0)
            }
            container.addView(tv)
            return
        }

        transactions.forEach { tx ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_wallet_transaction, container, false)

            val isCredit = tx.type == "credit"
            row.findViewById<TextView>(R.id.tvTxPatient).text     = tx.patientName.ifBlank { "Transaction" }
            row.findViewById<TextView>(R.id.tvTxDescription).text = tx.description
            row.findViewById<TextView>(R.id.tvTxDate).text        = tx.createdAt

            val tvAmount = row.findViewById<TextView>(R.id.tvTxAmount)
            tvAmount.text = "${if (isCredit) "+" else "-"}%.0f EGP".format(tx.amount)
            tvAmount.setTextColor(if (isCredit) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())

            row.findViewById<TextView>(R.id.tvTxIcon).text =
                if (tx.caseId != null) "💬" else "💸"

            container.addView(row)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
