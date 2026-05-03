package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.DeleteAccountRequest
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch

class DeleteAccountActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etPassword: EditText
    private lateinit var btnDeleteAccount: LinearLayout
    private lateinit var btnCancel: LinearLayout
    private lateinit var tvBtnLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_account)

        btnBack         = findViewById(R.id.btnBack)
        etPassword      = findViewById(R.id.etPassword)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)
        btnCancel       = findViewById(R.id.btnCancel)
        tvBtnLabel      = findViewById(R.id.tvBtnLabel)

        btnBack.setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }

        btnDeleteAccount.setOnClickListener {
            val password = etPassword.text.toString().trim()
            if (password.isBlank()) {
                etPassword.error = "Password is required"
                etPassword.requestFocus()
            } else {
                deleteAccount(password)
            }
        }
    }

    private fun deleteAccount(password: String) {
        btnDeleteAccount.isEnabled = false
        tvBtnLabel.text = "Deleting..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.deleteAccount(
                    SessionManager.getBearerToken(),
                    DeleteAccountRequest(password)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@DeleteAccountActivity,
                        "Account deleted.", Toast.LENGTH_SHORT).show()
                    SessionManager.clear()
                    startActivity(Intent(this@DeleteAccountActivity, WelcomeActivity::class.java))
                    finishAffinity()
                } else {
                    Toast.makeText(this@DeleteAccountActivity,
                        response.body()?.message ?: "Failed to delete account",
                        Toast.LENGTH_LONG).show()
                    btnDeleteAccount.isEnabled = true
                    tvBtnLabel.text = "Delete My Account"
                }
            } catch (e: Exception) {
                Toast.makeText(this@DeleteAccountActivity,
                    "Connection error", Toast.LENGTH_SHORT).show()
                btnDeleteAccount.isEnabled = true
                tvBtnLabel.text = "Delete My Account"
            }
        }
    }
}