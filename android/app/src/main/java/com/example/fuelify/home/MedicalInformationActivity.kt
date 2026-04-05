package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class MedicalInformationActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    private val allConditions = listOf("Diabetes", "PCOS", "Thyroid",
        "Hypertension", "High Cholesterol", "IBS", "Asthma")
    private val selectedConditions = mutableSetOf<String>()
    private val allergies   = mutableListOf<String>()
    private val medications = mutableListOf<String>()

    // Checkbox view map
    private val conditionCheckBoxes = mutableMapOf<String, LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_information)

        userId = UserPreferences.getUserId(this)
        findViewById<ImageButton>(R.id.btnMedicalBack).setOnClickListener { finish() }

        buildConditionUI()
        loadMedicalInfo()

        // Add allergy button — bottom sheet with predefined allergens
        findViewById<LinearLayout>(R.id.btnAddAllergy).setOnClickListener {
            showAllergyPicker()
        }

        // Add medication button
        findViewById<LinearLayout>(R.id.btnAddMedication).setOnClickListener {
            showAddItemDialog("Add Medication", "e.g. Metformin 500mg - Morning") { item ->
                if (item.isNotBlank()) {
                    medications.add(item)
                    refreshMedicationList()
                }
            }
        }

        // Save button
        findViewById<LinearLayout>(R.id.btnSaveMedical).setOnClickListener { saveMedicalInfo() }
    }

    private fun buildConditionUI() {
        val container = findViewById<LinearLayout>(R.id.containerConditions)
        container.removeAllViews()

        allConditions.forEach { condition ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_condition_checkbox, container, false) as LinearLayout
            val tvName = row.findViewById<TextView>(R.id.tvConditionName)
            val checkbox = row.findViewById<ImageView>(R.id.ivConditionCheck)
            tvName.text = condition

            updateCheckboxState(checkbox, condition in selectedConditions)

            row.setOnClickListener {
                if (condition in selectedConditions) {
                    selectedConditions.remove(condition)
                } else {
                    selectedConditions.add(condition)
                }
                updateCheckboxState(checkbox, condition in selectedConditions)
            }
            conditionCheckBoxes[condition] = row
            container.addView(row)
        }
    }

    private fun updateCheckboxState(checkbox: ImageView, checked: Boolean) {
        checkbox.setImageResource(
            if (checked) android.R.drawable.checkbox_on_background
            else android.R.drawable.checkbox_off_background
        )
        checkbox.setColorFilter(
            if (checked) 0xFFF97316.toInt() else 0xFFD1D5DB.toInt()
        )
    }

    private fun refreshAllergyChips() {
        val container = findViewById<LinearLayout>(R.id.containerAllergyChips)
        container.removeAllViews()
        allergies.forEach { allergy ->
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_allergy_chip, container, false)
            chip.findViewById<TextView>(R.id.tvAllergyChipName).text = allergy
            chip.findViewById<TextView>(R.id.tvAllergyChipRemove).setOnClickListener {
                allergies.remove(allergy)
                refreshAllergyChips()
            }
            container.addView(chip)
        }
    }

    private fun refreshMedicationList() {
        val container = findViewById<LinearLayout>(R.id.containerMedications)
        container.removeAllViews()
        medications.forEach { med ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_medication_row, container, false)
            row.findViewById<TextView>(R.id.tvMedicationName).text = med
            row.findViewById<TextView>(R.id.tvMedicationRemove).setOnClickListener {
                medications.remove(med)
                refreshMedicationList()
            }
            container.addView(row)
        }
    }

    private val commonAllergens = listOf(
        "🥛 Dairy", "🌾 Gluten", "🥜 Peanuts", "🌰 Tree Nuts", "🥚 Eggs",
        "🐟 Fish", "🦐 Shellfish", "🫘 Soy", "🌽 Corn", "🍓 Strawberries",
        "🍑 Stone Fruits", "🍋 Citrus", "🌿 Sesame", "🌻 Sunflower Seeds",
        "🧅 Onion", "🧄 Garlic", "🍄 Mushrooms", "🥑 Latex-Fruit"
    )

    private fun showAllergyPicker() {
        val cleanNames = commonAllergens.map { it.substringAfter(" ") }
        val currentChecked = cleanNames.map { name ->
            allergies.any { it.equals(name, ignoreCase = true) }
        }.toBooleanArray()

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_allergy_picker, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.containerAllergenList)
        val btnDone   = view.findViewById<LinearLayout>(R.id.btnAllergyPickerDone)
        val selected  = allergies.toMutableList()

        commonAllergens.forEachIndexed { i, allergen ->
            val row = layoutInflater.inflate(R.layout.item_allergy_picker_row, container, false)
            val tvName = row.findViewById<TextView>(R.id.tvAllergenName)
            val ivCheck = row.findViewById<ImageView>(R.id.ivAllergenCheck)
            val cleanName = allergen.substringAfter(" ")
            tvName.text = allergen
            val checked = currentChecked[i]
            ivCheck.setImageResource(if (checked) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background)
            ivCheck.setColorFilter(if (checked) 0xFFF97316.toInt() else 0xFFD1D5DB.toInt())
            if (checked) selected.add(cleanName)

            row.setOnClickListener {
                if (selected.contains(cleanName)) {
                    selected.remove(cleanName)
                    ivCheck.setImageResource(android.R.drawable.checkbox_off_background)
                    ivCheck.setColorFilter(0xFFD1D5DB.toInt())
                } else {
                    selected.add(cleanName)
                    ivCheck.setImageResource(android.R.drawable.checkbox_on_background)
                    ivCheck.setColorFilter(0xFFF97316.toInt())
                }
            }
            container.addView(row)
        }

        btnDone.setOnClickListener {
            allergies.clear()
            allergies.addAll(selected.distinct())
            refreshAllergyChips()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showAddItemDialog(title: String, hint: String, onAdd: (String) -> Unit) {
        val et = EditText(this).apply {
            this.hint = hint
            setPadding(40, 20, 40, 20)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("Add") { _, _ -> onAdd(et.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadMedicalInfo() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getMedicalInfo(userId) }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val info = resp.body()!!.data!!
                    selectedConditions.clear()
                    selectedConditions.addAll(info.conditions)
                    allergies.clear()
                    allergies.addAll(info.allergies)
                    medications.clear()
                    medications.addAll(info.medications)

                    // Rebuild UI with loaded data
                    buildConditionUI()
                    refreshAllergyChips()
                    refreshMedicationList()
                }
            } catch (e: Exception) {
                // First time — no data yet, that's fine
            }
        }
    }

    private fun saveMedicalInfo() {
        val btnText = (findViewById<LinearLayout>(R.id.btnSaveMedical).getChildAt(0) as? TextView)
        btnText?.text = "Saving..."

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.saveMedicalInfo(
                        userId,
                        SaveMedicalInfoRequest(
                            conditions  = selectedConditions.toList(),
                            allergies   = allergies.toList(),
                            medications = medications.toList()
                        )
                    )
                }
                if (resp.isSuccessful) {
                    btnText?.text = "✓ Saved!"
                    Toast.makeText(this@MedicalInformationActivity,
                        "Medical information saved! Alerts updated.", Toast.LENGTH_SHORT).show()

                    // Go to smart plan to show adjustments
                    delay(800)
                    startActivity(Intent(this@MedicalInformationActivity, SmartPlanActivity::class.java))
                } else {
                    btnText?.text = "Save Medical Information"
                    Toast.makeText(this@MedicalInformationActivity,
                        "Failed to save, please try again", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btnText?.text = "Save Medical Information"
                Toast.makeText(this@MedicalInformationActivity,
                    "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
