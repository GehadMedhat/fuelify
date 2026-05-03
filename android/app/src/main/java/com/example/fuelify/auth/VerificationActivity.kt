package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.VerifyEmailRequest
import com.example.fuelify.auth.network.ResendVerificationRequest
import kotlinx.coroutines.launch

class VerificationActivity : AppCompatActivity() {

    private lateinit var otp1: EditText
    private lateinit var otp2: EditText
    private lateinit var otp3: EditText
    private lateinit var otp4: EditText
    private lateinit var otp5: EditText
    private lateinit var otp6: EditText
    private lateinit var continueButton: android.view.View
    private lateinit var sendAgain: TextView
    private lateinit var backArrow: ImageView
    private lateinit var backToLogin: android.view.View
    private lateinit var emailLabel: TextView

    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        email = intent.getStringExtra("email") ?: ""

        otp1           = findViewById(R.id.otp1)
        otp2           = findViewById(R.id.otp2)
        otp3           = findViewById(R.id.otp3)
        otp4           = findViewById(R.id.otp4)
        otp5           = findViewById(R.id.otp5)
        otp6           = findViewById(R.id.otp6)
        continueButton = findViewById(R.id.rngayjooszt)
        sendAgain      = findViewById(R.id.tvSendAgain)
        backArrow      = findViewById(R.id.r0rvttdbbaec)
        backToLogin    = findViewById(R.id.rolc2qeghal)
        emailLabel     = findViewById(R.id.rozgsyo8bbg9)

        if (email.isNotEmpty()) {
            emailLabel.text = "We sent a code to\n$email"
        }

        setupOtpInputs()

        backArrow.setOnClickListener { finish() }

        backToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        sendAgain.setOnClickListener { resendCode() }

        continueButton.setOnClickListener {
            val code = getOtpCode()
            if (code.length < 6) {
                Toast.makeText(this, "Please enter the full 6-digit code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyEmail(code)
        }
    }

    private fun setupOtpInputs() {
        val inputs = listOf(otp1, otp2, otp3, otp4, otp5, otp6)

        inputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < inputs.size - 1) {
                        inputs[index + 1].requestFocus()
                    }
                    if (s?.length == 0 && index > 0) {
                        inputs[index - 1].requestFocus()
                    }
                }
            })
        }
    }

    private fun getOtpCode(): String {
        return "${otp1.text}${otp2.text}${otp3.text}${otp4.text}${otp5.text}${otp6.text}"
    }

    private fun verifyEmail(code: String) {
        continueButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.verifyEmail(
                    VerifyEmailRequest(email = email, code = code)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(
                        this@VerificationActivity,
                        "Email verified! Please login.",
                        Toast.LENGTH_SHORT
                    ).show()
                    val intent = Intent(this@VerificationActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    val message = response.body()?.message ?: "Invalid code. Please try again."
                    Toast.makeText(this@VerificationActivity, message, Toast.LENGTH_LONG).show()
                    clearOtpFields()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@VerificationActivity,
                    "Connection error. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                continueButton.isEnabled = true
            }
        }
    }

    private fun resendCode() {
        if (email.isEmpty()) {
            Toast.makeText(this, "Email not found. Please go back and try again.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.resendVerification(
                    ResendVerificationRequest(email = email)
                )
                val message = response.body()?.message ?: "Code sent!"
                Toast.makeText(this@VerificationActivity, message, Toast.LENGTH_SHORT).show()
                clearOtpFields()
                otp1.requestFocus()

            } catch (e: Exception) {
                Toast.makeText(
                    this@VerificationActivity,
                    "Connection error. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun clearOtpFields() {
        otp1.text.clear(); otp2.text.clear(); otp3.text.clear()
        otp4.text.clear(); otp5.text.clear(); otp6.text.clear()
        otp1.requestFocus()
    }
}