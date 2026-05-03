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
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.auth.network.VerifyEmailChangeRequest
import kotlinx.coroutines.launch

class VerifyEmailChangeActivity : AppCompatActivity() {

    private lateinit var otp1: EditText
    private lateinit var otp2: EditText
    private lateinit var otp3: EditText
    private lateinit var otp4: EditText
    private lateinit var otp5: EditText
    private lateinit var otp6: EditText
    private lateinit var continueButton: android.view.View
    private lateinit var backArrow: ImageView
    private lateinit var emailLabel: TextView

    private var pendingEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        pendingEmail = intent.getStringExtra("pendingEmail") ?: ""

        otp1           = findViewById(R.id.otp1)
        otp2           = findViewById(R.id.otp2)
        otp3           = findViewById(R.id.otp3)
        otp4           = findViewById(R.id.otp4)
        otp5           = findViewById(R.id.otp5)
        otp6           = findViewById(R.id.otp6)
        continueButton = findViewById(R.id.rngayjooszt)
        backArrow      = findViewById(R.id.r0rvttdbbaec)
        emailLabel     = findViewById(R.id.rozgsyo8bbg9)

        // Hide "Back to Login" — not relevant here
        try {
            findViewById<android.view.View>(R.id.rolc2qeghal)?.visibility = android.view.View.GONE
        } catch (_: Exception) {}

        // Hide "Send Again" — email change resend not needed for now
        try {
            findViewById<TextView>(R.id.tvSendAgain)?.visibility = android.view.View.GONE
        } catch (_: Exception) {}

        if (pendingEmail.isNotEmpty()) {
            emailLabel.text = "We sent a code to\n$pendingEmail"
        }

        setupOtpInputs()

        backArrow.setOnClickListener { finish() }

        continueButton.setOnClickListener {
            val code = getOtpCode()
            if (code.length < 6) {
                Toast.makeText(this, "Please enter the full 6-digit code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyEmailChange(code)
        }
    }

    private fun setupOtpInputs() {
        val inputs = listOf(otp1, otp2, otp3, otp4, otp5, otp6)
        inputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < inputs.size - 1) inputs[index + 1].requestFocus()
                    if (s?.length == 0 && index > 0) inputs[index - 1].requestFocus()
                }
            })
        }
    }

    private fun getOtpCode(): String {
        return "${otp1.text}${otp2.text}${otp3.text}${otp4.text}${otp5.text}${otp6.text}"
    }

    private fun verifyEmailChange(code: String) {
        continueButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.verifyEmailChange(
                    token   = SessionManager.getBearerToken(),
                    request = VerifyEmailChangeRequest(code = code)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    // Update session with new email
                    response.body()!!.data?.let { SessionManager.saveUser(it) }

                    Toast.makeText(
                        this@VerifyEmailChangeActivity,
                        "Email updated successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Go back to profile, clear the back stack up to it
                    val intent = Intent(this@VerifyEmailChangeActivity, ProfileActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()

                } else {
                    val message = response.body()?.message ?: "Invalid code. Please try again."
                    Toast.makeText(this@VerifyEmailChangeActivity, message, Toast.LENGTH_LONG).show()
                    clearOtpFields()
                    continueButton.isEnabled = true
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@VerifyEmailChangeActivity,
                    "Connection error. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
                continueButton.isEnabled = true
            }
        }
    }

    private fun clearOtpFields() {
        otp1.text.clear(); otp2.text.clear(); otp3.text.clear()
        otp4.text.clear(); otp5.text.clear(); otp6.text.clear()
        otp1.requestFocus()
    }
}