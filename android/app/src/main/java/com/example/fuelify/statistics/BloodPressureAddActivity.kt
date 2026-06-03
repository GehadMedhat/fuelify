package com.example.fuelify.statistics

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * BloodPressureAddActivity
 *
 * Allows the user to log a new blood pressure reading.
 * Layout: blood_pressure_add.xml
 *
 * Fields:
 *  - Systolic  (mmHg)  — et_systolic   inside til_systolic
 *  - Diastolic (mmHg)  — et_diastolic  inside til_diastolic
 *  - Pulse     (bpm)   — et_pulse      inside til_pulse
 *  - Notes     (free)  — et_notes      inside til_notes
 *
 * On save: validates → persists via BloodPressureTrackerRepository (API) → finishes.
 */
class BloodPressureAddActivity : AppCompatActivity() {

    // ── Input layouts (for error display) ────────────────────────────────────
    private lateinit var tilSystolic: TextInputLayout
    private lateinit var tilDiastolic: TextInputLayout
    private lateinit var tilPulse: TextInputLayout

    // ── Edit texts ────────────────────────────────────────────────────────────
    private lateinit var etSystolic: TextInputEditText
    private lateinit var etDiastolic: TextInputEditText
    private lateinit var etPulse: TextInputEditText
    private lateinit var etNotes: TextInputEditText

    // ── Action button ─────────────────────────────────────────────────────────
    private lateinit var btnSave: MaterialButton

    // ── Bottom Navigation ─────────────────────────────────────────────────────
    private lateinit var bottomNavigation: BottomNavigationView

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blood_pressure_add)

        bindViews()
        setupClickListeners()
    }

    // ── View Binding ──────────────────────────────────────────────────────────

    private fun bindViews() {
        findViewById<android.widget.ImageView>(R.id.btn_back)
            .setOnClickListener { finish() }

        tilSystolic  = findViewById(R.id.til_systolic)
        tilDiastolic = findViewById(R.id.til_diastolic)
        tilPulse     = findViewById(R.id.til_pulse)

        etSystolic   = findViewById(R.id.et_systolic)
        etDiastolic  = findViewById(R.id.et_diastolic)
        etPulse      = findViewById(R.id.et_pulse)
        etNotes      = findViewById(R.id.et_notes)

        btnSave      = findViewById(R.id.btn_save)

//        bottomNavigation = findViewById(R.id.bottom_navigation)
    }

    // ── Click Listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            hideKeyboard()
            if (validate()) {
                saveReading()
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validate(): Boolean {
        var valid = true

        val systolicText  = etSystolic.text?.toString()?.trim() ?: ""
        val diastolicText = etDiastolic.text?.toString()?.trim() ?: ""
        val pulseText     = etPulse.text?.toString()?.trim() ?: ""

        tilSystolic.error = null
        if (systolicText.isEmpty()) {
            tilSystolic.error = "Please enter systolic pressure"
            valid = false
        } else {
            val v = systolicText.toIntOrNull()
            if (v == null || v !in 60..300) {
                tilSystolic.error = "Enter a value between 60 and 300 mmHg"
                valid = false
            }
        }

        tilDiastolic.error = null
        if (diastolicText.isEmpty()) {
            tilDiastolic.error = "Please enter diastolic pressure"
            valid = false
        } else {
            val v = diastolicText.toIntOrNull()
            if (v == null || v !in 40..200) {
                tilDiastolic.error = "Enter a value between 40 and 200 mmHg"
                valid = false
            }
        }

        tilPulse.error = null
        if (pulseText.isEmpty()) {
            tilPulse.error = "Please enter pulse rate"
            valid = false
        } else {
            val v = pulseText.toIntOrNull()
            if (v == null || v !in 30..250) {
                tilPulse.error = "Enter a value between 30 and 250 bpm"
                valid = false
            }
        }

        if (valid) {
            val sys = systolicText.toInt()
            val dia = diastolicText.toInt()
            if (sys <= dia) {
                tilSystolic.error  = "Systolic must be greater than diastolic"
                tilDiastolic.error = "Diastolic must be less than systolic"
                valid = false
            }
        }

        return valid
    }

    // ── Save via API ──────────────────────────────────────────────────────────

    private fun saveReading() {
        val systolic  = etSystolic.text.toString().trim().toInt()
        val diastolic = etDiastolic.text.toString().trim().toInt()
        val pulse     = etPulse.text.toString().trim().toInt()
        val notes     = etNotes.text?.toString()?.trim() ?: ""

        btnSave.isEnabled = false

        lifecycleScope.launch {
            val saved = BloodPressureTrackerRepository.addBpReading(systolic, diastolic, pulse, notes)
            if (saved != null) {
                Toast.makeText(
                    this@BloodPressureAddActivity,
                    "Saved! Status: ${saved.categoryLabel} (${saved.systolic}/${saved.diastolic} mmHg)",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } else {
                Toast.makeText(
                    this@BloodPressureAddActivity,
                    "Failed to save. Check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
                btnSave.isEnabled = true
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}