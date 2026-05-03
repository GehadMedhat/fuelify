package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SignUpRequest
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var signUpButton: View
    private lateinit var loginLink: TextView
    private lateinit var backArrow: ImageView
    private lateinit var eyeIcon: ImageView
    private lateinit var eyeConfirm: ImageView
    private var isPasswordVisible = false
    private var isConfirmVisible  = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        firstNameInput       = findViewById(R.id.robbqera82jc)
        lastNameInput        = findViewById(R.id.ryf0llxe01df)
        emailInput           = findViewById(R.id.r7kagbk3dpnj)
        passwordInput        = findViewById(R.id.rrdxgaebywo9)
        confirmPasswordInput = findViewById(R.id.rp6kv6intxwf)
        signUpButton         = findViewById(R.id.rtnp02mf2r4g)
        loginLink            = findViewById(R.id.rloginClickable)
        backArrow            = findViewById(R.id.rca589uz20jt)
        eyeIcon              = findViewById(R.id.rlhctwdh473)
        eyeConfirm           = findViewById(R.id.reye_confirm)

        passwordInput.inputType        = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        confirmPasswordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        backArrow.setOnClickListener { finish() }

        loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Toggle password eye icon
        eyeIcon.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeIcon.setImageResource(R.drawable.eye_off_icon)
            } else {
                passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIcon.setImageResource(R.drawable.eye)
            }
            passwordInput.setSelection(passwordInput.text.length)
        }

        // Toggle confirm password eye icon
        eyeConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            if (isConfirmVisible) {
                confirmPasswordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeConfirm.setImageResource(R.drawable.eye_off_icon)
            } else {
                confirmPasswordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeConfirm.setImageResource(R.drawable.eye)
            }
            confirmPasswordInput.setSelection(confirmPasswordInput.text.length)
        }

        signUpButton.setOnClickListener {
            val firstName       = firstNameInput.text.toString().trim()
            val lastName        = lastNameInput.text.toString().trim()
            val email           = emailInput.text.toString().trim()
            val password        = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (firstName.isEmpty()) { firstNameInput.error = "First name is required"; firstNameInput.requestFocus(); return@setOnClickListener }
            if (lastName.isEmpty())  { lastNameInput.error  = "Last name is required";  lastNameInput.requestFocus();  return@setOnClickListener }
            if (email.isEmpty())     { emailInput.error     = "Email is required";      emailInput.requestFocus();     return@setOnClickListener }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailInput.error = "Enter a valid email address"; emailInput.requestFocus(); return@setOnClickListener }
            if (password.isEmpty())  { passwordInput.error  = "Password is required";   passwordInput.requestFocus();  return@setOnClickListener }
            if (password.length < 8) { passwordInput.error  = "Password must be at least 8 characters"; passwordInput.requestFocus(); return@setOnClickListener }
            if (!password.any { it.isUpperCase() }) { passwordInput.error = "Password must contain at least one uppercase letter"; passwordInput.requestFocus(); return@setOnClickListener }
            if (!password.any { it.isDigit() })     { passwordInput.error = "Password must contain at least one number"; passwordInput.requestFocus(); return@setOnClickListener }
            if (password != confirmPassword) { confirmPasswordInput.error = "Passwords do not match"; confirmPasswordInput.requestFocus(); return@setOnClickListener }

            signUpButton.isEnabled = false
            performSignUp(firstName, lastName, email, password, confirmPassword)
        }
    }

    private fun performSignUp(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        confirmPassword: String
    ) {
        lifecycleScope.launch {
            try {
                // Username generated from email prefix
                val username = email.substringBefore("@").replace(".", "_").replace("+", "_")

                val response = RetrofitClient.instance.signUp(
                    SignUpRequest(
                        firstName       = firstName,
                        lastName        = lastName,
                        email           = email,
                        username        = username,
                        password        = password,
                        confirmPassword = confirmPassword
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@SignUpActivity,
                        "Account created! Check your email for the verification code.",
                        Toast.LENGTH_LONG).show()
                    val intent = Intent(this@SignUpActivity, VerificationActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@SignUpActivity,
                        response.body()?.message ?: "Sign up failed",
                        Toast.LENGTH_LONG).show()
                    signUpButton.isEnabled = true
                }

            } catch (e: Exception) {
                Toast.makeText(this@SignUpActivity,
                    "Error: ${e.message}", Toast.LENGTH_LONG).show()
                signUpButton.isEnabled = true
            }
        }
    }
}