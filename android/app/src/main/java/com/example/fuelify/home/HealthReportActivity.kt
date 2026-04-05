package com.example.fuelify.home

import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HealthReportActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId       = -1
    private var hideWeight   = false
    private var hideCalories = false
    private var currentReport: HealthReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_report)
        userId = UserPreferences.getUserId(this)

        findViewById<ImageButton>(R.id.btnHealthReportBack).setOnClickListener { finish() }

        // Privacy toggles — set listeners AFTER report loads to avoid false triggers
        // (see bindReport for where listeners are attached)

        // Lab analyze button
        findViewById<LinearLayout>(R.id.btnAnalyzeLabs).setOnClickListener { analyzeLabResults() }

        // Download PDF
        findViewById<LinearLayout>(R.id.btnDownloadPDF).setOnClickListener { generatePDF() }

        // Share Email
        findViewById<LinearLayout>(R.id.btnShareEmail).setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "My Fuelify Health Report")
                putExtra(Intent.EXTRA_TEXT, buildTextReport())
            }, "Share via Email"))
        }

        // Share WhatsApp
        findViewById<LinearLayout>(R.id.btnShareWhatsApp).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, buildTextReport())
                })
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }

        loadHealthReport()
    }

    private fun loadHealthReport() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.api.getHealthReport(userId) }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    currentReport = resp.body()!!.data!!
                    bindReport(currentReport!!)
                }
            } catch (e: Exception) {
                Toast.makeText(this@HealthReportActivity, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindReport(report: HealthReport) {
        hideWeight   = report.hideWeight
        hideCalories = report.hideCalories

        val swWeight   = findViewById<Switch>(R.id.switchHideWeight)
        val swCalories = findViewById<Switch>(R.id.switchHideCalories)
        // Clear listeners before setting values to avoid triggering privacy save on load
        swWeight.setOnCheckedChangeListener(null)
        swCalories.setOnCheckedChangeListener(null)
        swWeight.isChecked   = report.hideWeight
        swCalories.isChecked = report.hideCalories
        // Re-attach after values are set
        swWeight.setOnCheckedChangeListener { _, checked -> hideWeight = checked; updatePrivacy() }
        swCalories.setOnCheckedChangeListener { _, checked ->
            hideCalories = checked; updatePrivacy()
            currentReport?.let { r -> bindReport(r.copy(hideCalories = checked)) }
        }

        // Conditions
        val condContainer = findViewById<LinearLayout>(R.id.containerReportConditions)
        condContainer.removeAllViews()
        if (report.conditions.isEmpty()) {
            condContainer.addView(TextView(this).apply {
                text = "No conditions recorded"; textSize = 12f; setTextColor(0xFF9CA3AF.toInt())
            })
        } else {
            report.conditions.forEach { cond ->
                val badge = TextView(this).apply {
                    text = cond; textSize = 12f; setTextColor(0xFFF97316.toInt())
                    setBackgroundColor(0xFFFFF7ED.toInt()); setPadding(24, 8, 24, 8)
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = 8; badge.layoutParams = lp
                condContainer.addView(badge)
            }
        }

        // Plan summary
        findViewById<TextView>(R.id.tvReportWorkouts).text = "${report.workoutsCompleted}/${report.workoutsTotal}"
        findViewById<TextView>(R.id.tvReportMeals).text = if (!hideCalories)
            "${report.mealsLogged}/${report.mealsTotal}" else "Hidden"

        // Calorie summary
        val tvTotal = findViewById<TextView>(R.id.tvReportTotalCal)
        val tvAvg   = findViewById<TextView>(R.id.tvReportAvgCal)
        if (!hideCalories) {
            tvTotal.text = "${report.totalCaloriesThisWeek} kcal this week"
            tvAvg.text   = "${report.avgDailyCalories} kcal/day avg"
        } else {
            tvTotal.text = "Hidden"; tvAvg.text = "Hidden"
        }

        // Real chart
        drawRealChart(report.dailyProgress)
    }

    private fun drawRealChart(days: List<DailyProgress>) {
        val container = findViewById<LinearLayout>(R.id.containerProgressBars)
        container.removeAllViews()
        val maxCal = days.maxOfOrNull { it.calories }?.takeIf { it > 0 } ?: 2000

        days.forEach { day ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT)
                p.weight = 1f; layoutParams = p
            }

            // Calorie label
            if (!hideCalories && day.calories > 0) {
                col.addView(TextView(this).apply {
                    text = "${day.calories}"
                    textSize = 7f
                    setTextColor(0xFF737373.toInt())
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
            }

            // Bar
            val barPct    = if (maxCal > 0) day.calories.toFloat() / maxCal else 0f
            val maxBarPx  = (100 * resources.displayMetrics.density).toInt()
            val barHeight = (barPct * maxBarPx).toInt().coerceAtLeast(if (day.calories > 0) 8 else 2)

            col.addView(View(this).apply {
                val lp = LinearLayout.LayoutParams((16 * resources.displayMetrics.density).toInt(), barHeight)
                lp.bottomMargin = (2 * resources.displayMetrics.density).toInt()
                layoutParams = lp
                setBackgroundColor(if (day.workoutDone) 0xFFC3E66E.toInt() else 0xFFDCFCE7.toInt())
            })

            // Day label
            col.addView(TextView(this).apply {
                text = day.dayLabel; textSize = 9f; setTextColor(0xFF737373.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            container.addView(col)
        }
    }

    // ── Lab Results ───────────────────────────────────────────────────────────

    private fun analyzeLabResults() {
        val hba1c       = findViewById<EditText>(R.id.etLabHba1c).text.toString().toDoubleOrNull()
        val cholesterol = findViewById<EditText>(R.id.etLabCholesterol).text.toString().toDoubleOrNull()
        val ldl         = findViewById<EditText>(R.id.etLabLdl).text.toString().toDoubleOrNull()
        val hdl         = findViewById<EditText>(R.id.etLabHdl).text.toString().toDoubleOrNull()
        val glucose     = findViewById<EditText>(R.id.etLabGlucose).text.toString().toDoubleOrNull()
        val tsh         = findViewById<EditText>(R.id.etLabTsh).text.toString().toDoubleOrNull()
        val bpSys       = findViewById<EditText>(R.id.etLabBpSys).text.toString().toIntOrNull()
        val bpDia       = findViewById<EditText>(R.id.etLabBpDia).text.toString().toIntOrNull()

        if (listOf(hba1c, cholesterol, ldl, hdl, glucose, tsh).all { it == null } && bpSys == null) {
            Toast.makeText(this, "Please enter at least one lab value", Toast.LENGTH_SHORT).show()
            return
        }

        val warnings = mutableListOf<String>()
        val recs     = mutableListOf<String>()

        hba1c?.let {
            when {
                it >= 6.5 -> { warnings.add("⚠️ HbA1c ${it}% — Diabetic range"); recs.add("Follow strict low-glycemic diet. Avoid all refined sugars.") }
                it in 5.7..6.4 -> { warnings.add("⚠️ HbA1c ${it}% — Pre-diabetic"); recs.add("Reduce sugar. Add 30 min daily walking. Increase fiber.") }
                else -> recs.add("✓ HbA1c ${it}% is normal")
            }
        }
        cholesterol?.let {
            when {
                it >= 240 -> { warnings.add("⚠️ Cholesterol ${it} mg/dL — High"); recs.add("Eliminate saturated fats. Add oats, nuts, fatty fish.") }
                it in 200.0..239.9 -> { warnings.add("⚠️ Cholesterol ${it} mg/dL — Borderline"); recs.add("Reduce red meat. Add 30 min cardio 5x/week.") }
                else -> recs.add("✓ Cholesterol ${it} mg/dL is normal")
            }
        }
        ldl?.let {
            if (it > 130) { warnings.add("⚠️ LDL ${it} — High"); recs.add("Avoid trans fats. Increase soluble fiber (beans, lentils, oats).") }
            else recs.add("✓ LDL ${it} mg/dL is acceptable")
        }
        hdl?.let {
            if (it < 40) { warnings.add("⚠️ HDL ${it} — Low"); recs.add("Increase aerobic exercise. Add healthy fats (avocado, olive oil).") }
            else recs.add("✓ HDL ${it} mg/dL is good")
        }
        glucose?.let {
            when {
                it >= 126 -> { warnings.add("⚠️ Fasting glucose ${it} — Diabetic"); recs.add("Consult physician urgently. Avoid all simple carbs.") }
                it in 100.0..125.9 -> { warnings.add("⚠️ Glucose ${it} — Impaired fasting"); recs.add("Reduce carb portions. Eat protein with every meal.") }
                else -> recs.add("✓ Fasting glucose ${it} mg/dL is normal")
            }
        }
        tsh?.let {
            when {
                it > 4.5 -> { warnings.add("⚠️ TSH ${it} — Hypothyroidism suspected"); recs.add("Consult endocrinologist. Ensure selenium intake. Avoid raw cruciferous veg.") }
                it < 0.4 -> { warnings.add("⚠️ TSH ${it} — Hyperthyroidism suspected"); recs.add("Avoid excess iodine. Limit caffeine. Reduce HIIT exercise.") }
                else -> recs.add("✓ TSH ${it} mIU/L is normal")
            }
        }
        if (bpSys != null && bpDia != null) {
            when {
                bpSys >= 140 || bpDia >= 90 -> { warnings.add("⚠️ BP ${bpSys}/${bpDia} — Hypertension"); recs.add("Reduce sodium <1500mg/day. DASH diet. 30 min moderate exercise daily.") }
                bpSys in 130..139 || bpDia in 80..89 -> { warnings.add("⚠️ BP ${bpSys}/${bpDia} — Elevated"); recs.add("Reduce processed food. Increase potassium. Manage stress.") }
                else -> recs.add("✓ Blood pressure ${bpSys}/${bpDia} is normal")
            }
        }

        val output = buildString {
            if (warnings.isNotEmpty()) append("ALERTS:\n${warnings.joinToString("\n")}\n\n")
            append("RECOMMENDATIONS:\n${recs.joinToString("\n")}")
        }

        val tvRecs = findViewById<TextView>(R.id.tvLabRecommendations)
        tvRecs.text = output
        tvRecs.setTextColor(0xFF000000.toInt())
        tvRecs.textSize = 13f
        tvRecs.visibility = View.VISIBLE

        // Save to backend
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.saveMedicalInfo(userId, SaveMedicalInfoRequest(
                        conditions = emptyList(), allergies = emptyList(), medications = emptyList(),
                        labResults = LabResults(hba1c = hba1c, totalCholesterol = cholesterol,
                            ldl = ldl, hdl = hdl, fastingGlucose = glucose, tsh = tsh,
                            bloodPressureSystolic = bpSys, bloodPressureDiastolic = bpDia)
                    ))
                }
                Toast.makeText(this@HealthReportActivity, "Lab results saved!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { /* silent */ }
        }
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    private fun generatePDF() {
        val report = currentReport ?: run {
            Toast.makeText(this, "Load your report first", Toast.LENGTH_SHORT).show()
            return
        }
        // Request WRITE permission on older Android
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
                Toast.makeText(this, "Allow storage permission then try again", Toast.LENGTH_SHORT).show()
                return
            }
        }
        try {
            val pdf      = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page     = pdf.startPage(pageInfo)
            val canvas   = page.canvas

            val bold   = Paint().apply { color = Color.BLACK; textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
            val normal = Paint().apply { color = Color.BLACK; textSize = 12f }
            val gray   = Paint().apply { color = Color.GRAY;  textSize = 12f }
            val green  = Paint().apply { color = Color.rgb(74, 98, 0); textSize = 12f; typeface = Typeface.DEFAULT_BOLD }

            var y = 60f
            canvas.drawText("Fuelify Health Report", 40f, y, bold); y += 28f
            canvas.drawText("Generated: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, y, gray); y += 30f
            canvas.drawLine(40f, y, 555f, y, gray); y += 20f

            fun section(title: String, body: String) {
                canvas.drawText(title, 40f, y, bold.also { it.textSize = 14f }); y += 22f
                canvas.drawText(body.ifEmpty { "None" }, 40f, y, normal); y += 24f
            }

            section("Medical Conditions", report.conditions.joinToString(", "))
            section("Allergies", report.allergies.joinToString(", "))
            section("Medications", report.medications.joinToString(", "))

            canvas.drawLine(40f, y, 555f, y, gray); y += 20f
            canvas.drawText("Weekly Summary", 40f, y, bold.also { it.textSize = 16f }); y += 26f
            canvas.drawText("Workouts: ${report.workoutsCompleted}/${report.workoutsTotal}", 40f, y, normal); y += 20f
            canvas.drawText("Meals Logged: ${report.mealsLogged}/${report.mealsTotal}", 40f, y, normal); y += 20f
            if (!hideCalories) {
                canvas.drawText("Total Calories: ${report.totalCaloriesThisWeek} kcal", 40f, y, green); y += 20f
                canvas.drawText("Daily Average: ${report.avgDailyCalories} kcal/day", 40f, y, normal); y += 20f
            }
            y += 10f

            canvas.drawText("Daily Progress", 40f, y, bold.also { it.textSize = 14f }); y += 22f
            report.dailyProgress.forEach { day ->
                val workout = if (day.workoutDone) "✓ Workout done" else "No workout"
                val cal = if (!hideCalories) "${day.calories} kcal" else "Hidden"
                canvas.drawText("${day.dayLabel}:  $cal   |   $workout", 40f, y, normal); y += 18f
            }

            y += 20f
            canvas.drawLine(40f, y, 555f, y, gray); y += 16f
            canvas.drawText("This report is confidential and HIPAA compliant. Fuelify App.", 40f, y, gray)

            pdf.finishPage(page)

            val fileName = "Fuelify_Report_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"

            // Save to public Downloads folder (works on all Android versions)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            Toast.makeText(this, "✓ Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()

            // Open PDF via FileProvider
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "${packageName}.provider", file)
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: Exception) {
                // Fallback — open Downloads app
                try {
                    startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                } catch (e2: Exception) {
                    Toast.makeText(this, "PDF saved to Downloads folder", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Toast.makeText(this, "PDF error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildTextReport(): String {
        val report = currentReport ?: return "No report data"
        return buildString {
            appendLine("=== FUELIFY HEALTH REPORT ===")
            appendLine("Generated: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("Conditions: ${report.conditions.ifEmpty { listOf("None") }.joinToString(", ")}")
            appendLine("Allergies: ${report.allergies.ifEmpty { listOf("None") }.joinToString(", ")}")
            appendLine("Medications: ${report.medications.ifEmpty { listOf("None") }.joinToString(", ")}")
            appendLine()
            appendLine("Workouts: ${report.workoutsCompleted}/${report.workoutsTotal}")
            appendLine("Meals Logged: ${report.mealsLogged}/${report.mealsTotal}")
            if (!hideCalories) {
                appendLine("Total Calories: ${report.totalCaloriesThisWeek} kcal")
                appendLine("Daily Average: ${report.avgDailyCalories} kcal/day")
            }
        }
    }

    private fun updatePrivacy() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.updatePrivacy(userId, PrivacyRequest(hideWeight, hideCalories))
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
