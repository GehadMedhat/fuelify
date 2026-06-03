package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fuelify.R
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.LoginRequest
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.doctor.DoctorHomeActivity
import com.example.fuelify.doctor.DoctorOnboardingActivity
import com.example.fuelify.home.HomeActivity
import com.example.fuelify.onboarding.OnboardingActivity
import com.example.fuelify.utils.DoctorPreferences
import com.example.fuelify.data.api.models.UserResponse as FuelifyUserResponse
import com.example.fuelify.data.api.models.ApiResponse as FuelifyApiResponse
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: View
    private lateinit var forgotPassword: TextView
    private lateinit var signupLink: TextView
    private lateinit var backArrow: ImageView
    private lateinit var eyeIcon: ImageView
    private lateinit var btnDoctorLogin: LinearLayout
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput     = findViewById(R.id.r7kagbk3dpnj)
        passwordInput  = findViewById(R.id.rrdxgaebywo9)
        loginButton    = findViewById(R.id.rtnp02mf2r4g)
        forgotPassword = findViewById(R.id.r1hht59hz75e)
        signupLink     = findViewById(R.id.rloginClickable)
        backArrow      = findViewById(R.id.rca589uz20jt)
        eyeIcon        = findViewById(R.id.rlhctwdh473)
        btnDoctorLogin = findViewById(R.id.btnDoctorLogin)

        passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

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

        backArrow.setOnClickListener { finish() }

        forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        signupLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        btnDoctorLogin.setOnClickListener {
            if (DoctorPreferences.isLoggedIn(this)) {
                startActivity(Intent(this, DoctorHomeActivity::class.java))
            } else {
                startActivity(Intent(this, DoctorOnboardingActivity::class.java))
            }
        }

        loginButton.setOnClickListener {
            val email    = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty()) { emailInput.error = "Email is required"; emailInput.requestFocus(); return@setOnClickListener }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailInput.error = "Enter a valid email address"; emailInput.requestFocus(); return@setOnClickListener }
            if (password.isEmpty()) { passwordInput.error = "Password is required"; passwordInput.requestFocus(); return@setOnClickListener }

            loginButton.isEnabled = false
            performLogin(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                // Step 1: Auth login (port 8081)
                val response = RetrofitClient.instance.login(LoginRequest(email, password))

                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()!!.data!!

                    SessionManager.saveToken(data.accessToken)
                    SessionManager.saveRefreshToken(data.refreshToken)
                    runCatching { SessionManager.saveUser(data.user) }

                    val isAdmin = isAdminFromToken(data.accessToken)
                    SessionManager.saveAdminStatus(isAdmin)

                    if (isAdmin) {
                        Toast.makeText(this@LoginActivity, "Welcome, Admin!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, AdminDashboardActivity::class.java))
                        finishAffinity()
                        return@launch
                    }

                    // Step 2: Check onboarding status on Fuelify backend (port 8080)
                    val destination = try {
                        val userResp = com.example.fuelify.data.api.RetrofitClient.api
                            .getUserByEmail(email)

                        android.util.Log.d("LOGIN_DEBUG", "getUserByEmail code: ${userResp.code()}")
                        android.util.Log.d("LOGIN_DEBUG", "getUserByEmail body: ${userResp.errorBody()?.string()}")
                        android.util.Log.d("LOGIN_DEBUG", "success: ${userResp.body()?.success}")
                        android.util.Log.d("LOGIN_DEBUG", "data: ${userResp.body()?.data}")

                        if (userResp.isSuccessful && userResp.body()?.success == true) {
                            val userData: FuelifyUserResponse? = userResp.body()!!.data
                            android.util.Log.d("LOGIN_DEBUG", "profileComplete: ${userData?.profileComplete}, userId: ${userData?.userId}")
                            if (userData != null && userData.profileComplete) {
                                UserPreferences.saveUserId(this@LoginActivity, userData.userId)
                                HomeActivity::class.java
                            } else {
                                OnboardingActivity::class.java
                            }
                        } else {
                            OnboardingActivity::class.java
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LOGIN_DEBUG", "Exception: ${e.message}", e)
                        OnboardingActivity::class.java
                    }

                    Toast.makeText(this@LoginActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, destination))
                    finishAffinity()

                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        response.body()?.message ?: "Login failed.",
                        Toast.LENGTH_LONG
                    ).show()
                    loginButton.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                loginButton.isEnabled = true
            }
        }
    }
    private fun isAdminFromToken(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return false
            val base64 = parts[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
            val payload = String(android.util.Base64.decode(base64, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            payload.contains("\"isAdmin\":true") || payload.contains("\"isAdmin\": true")
        } catch (e: Exception) { false }
    }
}
