package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.ForgotPasswordRequest
import com.example.fuelify.auth.network.RetrofitClient
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var sendCodeButton: View
    private lateinit var backArrow: ImageView
    private lateinit var backToLogin: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        emailInput     = findViewById(R.id.r7kagbk3dpnj)
        sendCodeButton = findViewById(R.id.rtnp02mf2r4g)
        backArrow      = findViewById(R.id.rca589uz20jt)
        backToLogin    = findViewById(R.id.r7q5ezuyvx7k)

        backArrow.setOnClickListener { finish() }

        backToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        sendCodeButton.setOnClickListener {
            val email = emailInput.text.toString().trim()

            // ── Validation ────────────────────────────────────────────────────
            if (email.isEmpty()) {
                emailInput.error = "Email is required"
                emailInput.requestFocus()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Enter a valid email address"
                emailInput.requestFocus()
                return@setOnClickListener
            }

            sendCodeButton.isEnabled = false
            sendResetCode(email)
        }
    }

    private fun sendResetCode(email: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.forgotPassword(ForgotPasswordRequest(email))

                if (response.isSuccessful) {
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Code sent! Check your email.",
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(this@ForgotPasswordActivity, SetNewPasswordActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        response.body()?.message ?: "Failed to send code",
                        Toast.LENGTH_LONG
                    ).show()
                    sendCodeButton.isEnabled = true
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Connection error.",
                    Toast.LENGTH_LONG
                ).show()
                sendCodeButton.isEnabled = true
            }
        }
    }
}