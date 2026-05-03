package com.example.fuelify.doctor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.DoctorOnboardRequest
import com.example.fuelify.utils.DoctorPreferences
import kotlinx.coroutines.*

class DoctorOnboardingActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Step views
    private lateinit var layoutStep1: LinearLayout
    private lateinit var layoutStep2: LinearLayout
    private lateinit var layoutStep3: LinearLayout
    private lateinit var layoutStep4: LinearLayout
    private lateinit var progressSteps: ProgressBar

    // Step 1 — Basic info (passed from login/register by friend)
    private var email    = ""
    private var password = ""

    // Step 2 — Specialty
    private var selectedSpecialty = ""

    // Step 3 — Professional info
    private lateinit var etFullName:      EditText
    private lateinit var etQualification: EditText
    private lateinit var etHospital:      EditText
    private lateinit var etYearsExp:      EditText
    private lateinit var etBio:           EditText
    private lateinit var etEmail:         EditText
    private lateinit var etPassword:      EditText

    // Step 4 — Document
    private var documentName = ""

    private var currentStep = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_onboarding)

        // Receive email + password from friend's auth screen
        email    = intent.getStringExtra("email") ?: ""
        password = intent.getStringExtra("password") ?: ""

        bindViews()
        showStep(1)
    }

    private fun bindViews() {
        layoutStep1   = findViewById(R.id.layoutOnboardStep1)
        layoutStep2   = findViewById(R.id.layoutOnboardStep2)
        layoutStep3   = findViewById(R.id.layoutOnboardStep3)
        layoutStep4   = findViewById(R.id.layoutOnboardStep4)
        progressSteps = findViewById(R.id.progressOnboardSteps)

        etFullName      = findViewById(R.id.etDoctorFullName)
        etQualification = findViewById(R.id.etDoctorQualification)
        etHospital      = findViewById(R.id.etDoctorHospital)
        etYearsExp      = findViewById(R.id.etDoctorYearsExp)
        etBio           = findViewById(R.id.etDoctorBio)
        etEmail         = findViewById(R.id.etDoctorEmail)
        etPassword      = findViewById(R.id.etDoctorPassword)

        // Step 1: continue to onboarding
        findViewById<LinearLayout>(R.id.btnStep1Next).setOnClickListener {
            showStep(2)
        }

        // Already have account → login directly
        findViewById<LinearLayout>(R.id.btnDoctorAlreadyLogin).setOnClickListener {
            showLoginDialog()
        }

        // Step 2: Specialty selection
        listOf(
            R.id.btnSpecialtyDiet    to "diet",
            R.id.btnSpecialtyWorkout to "workout",
            R.id.btnSpecialtyGeneral to "general"
        ).forEach { (btnId, spec) ->
            findViewById<LinearLayout>(btnId).setOnClickListener {
                selectedSpecialty = spec
                updateSpecialtyUI()
            }
        }
        findViewById<LinearLayout>(R.id.btnStep2Next).setOnClickListener {
            if (selectedSpecialty.isEmpty()) {
                Toast.makeText(this, "Please select your specialty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showStep(3)
        }

        // Step 2 back
        findViewById<ImageButton>(R.id.btnStep2Back).setOnClickListener { showStep(1) }

        // Step 3: Professional info
        findViewById<LinearLayout>(R.id.btnStep3Next).setOnClickListener {
            if (etFullName.text.isBlank()) {
                Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (etQualification.text.isBlank()) {
                Toast.makeText(this, "Please enter your qualification", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (etEmail.text.isBlank()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (etPassword.text.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Store for use in submit
            email    = etEmail.text.toString().trim()
            password = etPassword.text.toString().trim()
            showStep(4)
        }
        findViewById<ImageButton>(R.id.btnStep3Back).setOnClickListener { showStep(2) }

        // Step 4: Document upload
        val filePicker = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                documentName = getFileName(uri) ?: "document.pdf"
                findViewById<TextView>(R.id.tvDocumentName).text = "📎 $documentName"
                findViewById<TextView>(R.id.tvDocumentName).setTextColor(0xFF22C55E.toInt())
            }
        }

        findViewById<LinearLayout>(R.id.btnPickDocument).setOnClickListener {
            filePicker.launch("*/*")
        }

        findViewById<LinearLayout>(R.id.btnSubmitOnboarding).setOnClickListener {
            submitOnboarding()
        }
        findViewById<ImageButton>(R.id.btnStep4Back).setOnClickListener { showStep(3) }
    }

    private fun updateSpecialtyUI() {
        val dietBtn    = findViewById<LinearLayout>(R.id.btnSpecialtyDiet)
        val workoutBtn = findViewById<LinearLayout>(R.id.btnSpecialtyWorkout)
        val generalBtn = findViewById<LinearLayout>(R.id.btnSpecialtyGeneral)

        val selectedColor  = 0xFFF97316.toInt()
        val defaultColor   = 0xFFFFFFFF.toInt()
        val selectedBorder = 0xFFF97316.toInt()

        listOf(dietBtn to "diet", workoutBtn to "workout", generalBtn to "general").forEach { (btn, spec) ->
            val isSelected = spec == selectedSpecialty
            btn.setBackgroundColor(if (isSelected) 0xFFFFF7ED.toInt() else defaultColor)
            btn.alpha = if (isSelected) 1f else 0.7f
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        layoutStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        layoutStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        layoutStep3.visibility = if (step == 3) View.VISIBLE else View.GONE
        layoutStep4.visibility = if (step == 4) View.VISIBLE else View.GONE
        progressSteps.progress = step * 25
    }

    private fun showLoginDialog() {
        val dialog = android.app.AlertDialog.Builder(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val etEmail = android.widget.EditText(this).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val etPass = android.widget.EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etEmail)
        layout.addView(etPass)

        dialog.setTitle("Doctor Login")
            .setView(layout)
            .setPositiveButton("Login") { _, _ ->
                val em = etEmail.text.toString().trim()
                val pw = etPass.text.toString().trim()
                if (em.isBlank() || pw.isBlank()) {
                    android.widget.Toast.makeText(this, "Enter email and password", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                loginDoctor(em, pw)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loginDoctor(em: String, pw: String) {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.doctorLogin(
                        com.example.fuelify.data.api.models.DoctorLoginRequest(em, pw)
                    )
                }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val profile = resp.body()!!.data!!
                    com.example.fuelify.utils.DoctorPreferences.saveDoctor(this@DoctorOnboardingActivity, profile)
                    if (profile.isApproved) {
                        startActivity(android.content.Intent(this@DoctorOnboardingActivity,
                            DoctorHomeActivity::class.java))
                    } else {
                        startActivity(android.content.Intent(this@DoctorOnboardingActivity,
                            DoctorPendingActivity::class.java).apply {
                            putExtra("message", "Your account is still pending approval.")
                            putExtra("email", em)
                            putExtra("password", pw)
                        })
                    }
                    finish()
                } else {
                    android.widget.Toast.makeText(this@DoctorOnboardingActivity,
                        resp.body()?.message ?: "Login failed", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(this@DoctorOnboardingActivity,
                    "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitOnboarding() {
        val btnSubmit = findViewById<LinearLayout>(R.id.btnSubmitOnboarding)
        val btnTv = btnSubmit.getChildAt(0) as? TextView
        btnTv?.text = "Submitting..."
        btnSubmit.isEnabled = false

        val request = DoctorOnboardRequest(
            email         = email,
            password      = password,
            fullName      = etFullName.text.toString().trim(),
            specialty     = selectedSpecialty,
            qualification = etQualification.text.toString().trim(),
            hospital      = etHospital.text.toString().trim(),
            yearsExp      = etYearsExp.text.toString().toIntOrNull() ?: 0,
            bio           = etBio.text.toString().trim(),
            documentName  = documentName
        )

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.doctorRegister(request)
                }
                if (resp.isSuccessful && resp.body()?.success == true) {
                    // Go to pending screen
                    startActivity(Intent(this@DoctorOnboardingActivity, DoctorPendingActivity::class.java).apply {
                        putExtra("message", resp.body()!!.message)
                        putExtra("email", email)
                        putExtra("password", password)
                    })
                    finish()
                } else {
                    btnTv?.text = "Submit Application"
                    btnSubmit.isEnabled = true
                    val err = resp.errorBody()?.string() ?: resp.body()?.message ?: "Unknown error"
                    Toast.makeText(this@DoctorOnboardingActivity, err, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnTv?.text = "Submit Application"
                btnSubmit.isEnabled = true
                Toast.makeText(this@DoctorOnboardingActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
