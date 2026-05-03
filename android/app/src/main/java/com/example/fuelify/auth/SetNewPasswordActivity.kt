package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.ResetPasswordRequest
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.VerifyResetCodeRequest
import kotlinx.coroutines.launch

class SetNewPasswordActivity : AppCompatActivity() {

    private lateinit var resetCodeInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var resetButton: View
    private lateinit var backArrow: ImageView
    private lateinit var backToLogin: View
    private lateinit var eyeNew: ImageView
    private lateinit var eyeConfirm: ImageView

    private var userEmail            = ""
    private var isNewPasswordVisible = false
    private var isConfirmVisible     = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_new_password)

        userEmail = intent.getStringExtra("email") ?: ""

        resetCodeInput       = findViewById(R.id.resetCodeInput)
        newPasswordInput     = findViewById(R.id.rehhqp4u7ksb)
        confirmPasswordInput = findViewById(R.id.rqtdpu3cg5vd)
        resetButton          = findViewById(R.id.r6yvcdehx5wg)
        backArrow            = findViewById(R.id.rgbkz6k1gjao)
        backToLogin          = findViewById(R.id.rcbvopfqpspp)
        eyeNew               = findViewById(R.id.rti8b274681a)
        eyeConfirm           = findViewById(R.id.rew5jukqcgn4)

        newPasswordInput.inputType     = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        confirmPasswordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        backArrow.setOnClickListener { finish() }

        backToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        eyeNew.setOnClickListener {
            isNewPasswordVisible = !isNewPasswordVisible
            newPasswordInput.inputType = if (isNewPasswordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            newPasswordInput.setSelection(newPasswordInput.text.length)
            eyeNew.setImageResource(
                if (isNewPasswordVisible) R.drawable.eye else R.drawable.eye_off_icon
            )
        }

        eyeConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            confirmPasswordInput.inputType = if (isConfirmVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            confirmPasswordInput.setSelection(confirmPasswordInput.text.length)
            eyeConfirm.setImageResource(
                if (isConfirmVisible) R.drawable.eye else R.drawable.eye_off_icon
            )
        }

        resetButton.setOnClickListener {
            val code            = resetCodeInput.text.toString().trim()
            val newPassword     = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (code.isEmpty() || code.length != 6) {
                resetCodeInput.error = "Enter the 6-digit code from your email"
                resetCodeInput.requestFocus()
                return@setOnClickListener
            }
            if (newPassword.isEmpty()) {
                newPasswordInput.error = "Password is required"
                newPasswordInput.requestFocus()
                return@setOnClickListener
            }
            if (newPassword.length < 8) {
                newPasswordInput.error = "Password must be at least 8 characters"
                newPasswordInput.requestFocus()
                return@setOnClickListener
            }
            if (!newPassword.any { it.isUpperCase() }) {
                newPasswordInput.error = "Password must contain at least one uppercase letter"
                newPasswordInput.requestFocus()
                return@setOnClickListener
            }
            if (!newPassword.any { it.isDigit() }) {
                newPasswordInput.error = "Password must contain at least one number"
                newPasswordInput.requestFocus()
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                confirmPasswordInput.error = "Passwords do not match"
                confirmPasswordInput.requestFocus()
                return@setOnClickListener
            }

            resetButton.isEnabled = false
            verifyAndReset(code, newPassword, confirmPassword)
        }
    }

    private fun verifyAndReset(code: String, newPassword: String, confirmPassword: String) {
        lifecycleScope.launch {
            try {
                val verifyResponse = RetrofitClient.instance.verifyResetCode(
                    VerifyResetCodeRequest(email = userEmail, code = code)
                )

                if (verifyResponse.isSuccessful && verifyResponse.body()?.success == true) {
                    val resetToken = verifyResponse.body()?.data?.get("resetToken") ?: ""

                    val resetResponse = RetrofitClient.instance.resetPassword(
                        ResetPasswordRequest(
                            resetToken      = resetToken,
                            newPassword     = newPassword,
                            confirmPassword = confirmPassword
                        )
                    )

                    if (resetResponse.isSuccessful && resetResponse.body()?.success == true) {
                        Toast.makeText(
                            this@SetNewPasswordActivity,
                            "Password reset successfully! Please login.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(this@SetNewPasswordActivity, LoginActivity::class.java))
                        finishAffinity()
                    } else {
                        Toast.makeText(
                            this@SetNewPasswordActivity,
                            resetResponse.body()?.message ?: "Reset failed",
                            Toast.LENGTH_LONG
                        ).show()
                        resetButton.isEnabled = true
                    }

                } else {
                    Toast.makeText(
                        this@SetNewPasswordActivity,
                        verifyResponse.body()?.message ?: "Invalid or expired code",
                        Toast.LENGTH_LONG
                    ).show()
                    resetButton.isEnabled = true
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@SetNewPasswordActivity,
                    "Connection error.",
                    Toast.LENGTH_LONG
                ).show()
                resetButton.isEnabled = true
            }
        }
    }
}