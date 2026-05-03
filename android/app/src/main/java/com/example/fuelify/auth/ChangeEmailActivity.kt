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
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.auth.network.UpdateProfileRequest
import kotlinx.coroutines.launch

class ChangeEmailActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etNewEmail: EditText
    private lateinit var btnSendCode: LinearLayout
    private lateinit var tvBtnLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_email)

        btnBack     = findViewById(R.id.btnBack)
        etNewEmail  = findViewById(R.id.etNewEmail)
        btnSendCode = findViewById(R.id.btnSendCode)
        tvBtnLabel  = findViewById(R.id.tvBtnLabel)

        btnBack.setOnClickListener { finish() }

        btnSendCode.setOnClickListener {
            val newEmail = etNewEmail.text.toString().trim()
            when {
                newEmail.isEmpty() ->
                    etNewEmail.error = "Please enter an email address"
                !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() ->
                    etNewEmail.error = "Enter a valid email address"
                newEmail == (SessionManager.getEmail() ?: "") ->
                    etNewEmail.error = "This is already your current email"
                else ->
                    sendEmailChangeRequest(newEmail)
            }
        }
    }

    private fun sendEmailChangeRequest(newEmail: String) {
        btnSendCode.isEnabled = false
        tvBtnLabel.text = "Sending..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateProfile(
                    token   = SessionManager.getBearerToken(),
                    request = UpdateProfileRequest(email = newEmail)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(
                        this@ChangeEmailActivity,
                        "Code sent to $newEmail",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Navigate to OTP verification screen
                    val intent = Intent(this@ChangeEmailActivity, VerifyEmailChangeActivity::class.java)
                    intent.putExtra("pendingEmail", newEmail)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@ChangeEmailActivity,
                        response.body()?.message ?: "Failed to send code",
                        Toast.LENGTH_LONG
                    ).show()
                    btnSendCode.isEnabled = true
                    tvBtnLabel.text = "Send Verification Code"
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChangeEmailActivity,
                    "Connection error", Toast.LENGTH_SHORT).show()
                btnSendCode.isEnabled = true
                tvBtnLabel.text = "Send Verification Code"
            }
        }
    }
}