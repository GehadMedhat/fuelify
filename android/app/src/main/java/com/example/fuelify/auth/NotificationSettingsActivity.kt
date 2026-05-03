package com.example.fuelify.auth

import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import com.example.fuelify.R
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.NotificationSettingsRequest
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch
import java.util.Calendar

class NotificationSettingsActivity : AppCompatActivity() {

    // ── Toggle containers ─────────────────────────────────────────────────────
    private lateinit var hydrationToggle: LinearLayout
    private lateinit var hydrationKnob: View
    private lateinit var hydrationDropdowns: LinearLayout

    private lateinit var stepsToggle: LinearLayout
    private lateinit var stepsKnob: View
    private lateinit var stepsDropdowns: LinearLayout

    private lateinit var sleepToggle: LinearLayout
    private lateinit var sleepKnob: View
    private lateinit var sleepDropdowns: LinearLayout

    private lateinit var workoutToggle: LinearLayout
    private lateinit var workoutKnob: View
    private lateinit var workoutDropdowns: LinearLayout

    private lateinit var dndToggle: LinearLayout
    private lateinit var dndKnob: View
    private lateinit var dndTimeContainer: LinearLayout

    // ── Value TextViews ───────────────────────────────────────────────────────
    private lateinit var hydrationTimingValue: TextView
    private lateinit var hydrationFreqValue: TextView
    private lateinit var stepsTimingValue: TextView
    private lateinit var stepsFreqValue: TextView
    private lateinit var sleepTimingValue: TextView
    private lateinit var sleepFreqValue: TextView
    private lateinit var workoutTimingValue: TextView
    private lateinit var workoutFreqValue: TextView
    private lateinit var dndStartValue: TextView
    private lateinit var dndEndValue: TextView

    private lateinit var saveButton: LinearLayout
    private lateinit var btnBack: ImageView

    // ── State ─────────────────────────────────────────────────────────────────
    private var hydrationEnabled = true
    private var stepsEnabled     = true
    private var sleepEnabled     = true
    private var workoutEnabled   = true
    private var dndEnabled       = false

    private var hydrationTiming    = "EVERY_HOUR"
    private var hydrationFreqStart = "08:00"
    private var hydrationFreqEnd   = "20:00"

    private var stepsTiming    = "EVERY_2_HOURS"
    private var stepsFreqStart = "08:00"
    private var stepsFreqEnd   = "20:00"

    private var sleepBedtime = "22:00"
    private var sleepWakeup  = "07:00"

    private var workoutTiming    = "EVERY_HOUR"
    private var workoutFreqStart = "08:00"
    private var workoutFreqEnd   = "20:00"

    private var dndStart = "22:00"
    private var dndEnd   = "07:00"

    // ── Dropdown options — must match backend WorkoutTiming enum exactly ──────
    private val timingOptions = arrayOf(
        "Every 30 Minutes", "Every Hour", "Every 2 Hours", "Every 3 Hours"
    )
    private val timingValues = arrayOf(
        "EVERY_30_MIN", "EVERY_HOUR", "EVERY_2_HOURS", "EVERY_3_HOURS"
    )

    private val freqOptions = arrayOf(
        "6 AM – 6 PM", "7 AM – 7 PM", "8 AM – 8 PM", "9 AM – 9 PM", "All Day"
    )
    private val freqStartValues = arrayOf("06:00", "07:00", "08:00", "09:00", "00:00")
    private val freqEndValues   = arrayOf("18:00", "19:00", "20:00", "21:00", "23:59")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        bindViews()
        setupClickListeners()
        loadSettings()
    }

    private fun bindViews() {
        btnBack              = findViewById(R.id.btnBack)

        hydrationToggle      = findViewById(R.id.hydrationToggle)
        hydrationKnob        = findViewById(R.id.hydrationKnob)
        hydrationDropdowns   = findViewById(R.id.hydrationDropdowns)
        hydrationTimingValue = findViewById(R.id.hydrationTimingValue)
        hydrationFreqValue   = findViewById(R.id.hydrationFreqValue)

        stepsToggle          = findViewById(R.id.stepsToggle)
        stepsKnob            = findViewById(R.id.stepsKnob)
        stepsDropdowns       = findViewById(R.id.stepsDropdowns)
        stepsTimingValue     = findViewById(R.id.stepsTimingValue)
        stepsFreqValue       = findViewById(R.id.stepsFreqValue)

        sleepToggle          = findViewById(R.id.sleepToggle)
        sleepKnob            = findViewById(R.id.sleepKnob)
        sleepDropdowns       = findViewById(R.id.sleepDropdowns)
        sleepTimingValue     = findViewById(R.id.sleepTimingValue)
        sleepFreqValue       = findViewById(R.id.sleepFreqValue)

        workoutToggle        = findViewById(R.id.workoutToggle)
        workoutKnob          = findViewById(R.id.workoutKnob)
        workoutDropdowns     = findViewById(R.id.workoutDropdowns)
        workoutTimingValue   = findViewById(R.id.workoutTimingValue)
        workoutFreqValue     = findViewById(R.id.workoutFreqValue)

        dndToggle            = findViewById(R.id.dndToggle)
        dndKnob              = findViewById(R.id.dndKnob)
        dndTimeContainer     = findViewById(R.id.dndTimeContainer)
        dndStartValue        = findViewById(R.id.dndStartValue)
        dndEndValue          = findViewById(R.id.dndEndValue)

        saveButton           = findViewById(R.id.saveButton)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        // ── Toggles ───────────────────────────────────────────────────────────
        hydrationToggle.setOnClickListener {
            hydrationEnabled = !hydrationEnabled
            updateToggleUI(hydrationKnob, hydrationToggle, hydrationEnabled)
            hydrationDropdowns.visibility = if (hydrationEnabled) View.VISIBLE else View.GONE
        }
        stepsToggle.setOnClickListener {
            stepsEnabled = !stepsEnabled
            updateToggleUI(stepsKnob, stepsToggle, stepsEnabled)
            stepsDropdowns.visibility = if (stepsEnabled) View.VISIBLE else View.GONE
        }
        sleepToggle.setOnClickListener {
            sleepEnabled = !sleepEnabled
            updateToggleUI(sleepKnob, sleepToggle, sleepEnabled)
            sleepDropdowns.visibility = if (sleepEnabled) View.VISIBLE else View.GONE
        }
        workoutToggle.setOnClickListener {
            workoutEnabled = !workoutEnabled
            updateToggleUI(workoutKnob, workoutToggle, workoutEnabled)
            workoutDropdowns.visibility = if (workoutEnabled) View.VISIBLE else View.GONE
        }
        dndToggle.setOnClickListener {
            dndEnabled = !dndEnabled
            updateToggleUI(dndKnob, dndToggle, dndEnabled)
            dndTimeContainer.visibility = if (dndEnabled) View.VISIBLE else View.GONE
        }

        // ── Dropdowns ─────────────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.hydrationTimingBtn).setOnClickListener {
            showTimingDropdown { i ->
                hydrationTiming = timingValues[i]
                hydrationTimingValue.text = timingOptions[i]
            }
        }
        findViewById<LinearLayout>(R.id.hydrationFreqBtn).setOnClickListener {
            showFreqDropdown { i ->
                hydrationFreqStart = freqStartValues[i]
                hydrationFreqEnd   = freqEndValues[i]
                hydrationFreqValue.text = freqOptions[i]
            }
        }

        findViewById<LinearLayout>(R.id.stepsTimingBtn).setOnClickListener {
            showTimingDropdown { i ->
                stepsTiming = timingValues[i]
                stepsTimingValue.text = timingOptions[i]
            }
        }
        findViewById<LinearLayout>(R.id.stepsFreqBtn).setOnClickListener {
            showFreqDropdown { i ->
                stepsFreqStart = freqStartValues[i]
                stepsFreqEnd   = freqEndValues[i]
                stepsFreqValue.text = freqOptions[i]
            }
        }

        // Sleep uses time pickers
        findViewById<LinearLayout>(R.id.sleepTimingBtn).setOnClickListener {
            showTimePicker("Bedtime") { h, m ->
                sleepBedtime = "%02d:%02d".format(h, m)
                sleepTimingValue.text = formatTime12h(h, m)
            }
        }
        findViewById<LinearLayout>(R.id.sleepFreqBtn).setOnClickListener {
            showTimePicker("Wake Up") { h, m ->
                sleepWakeup = "%02d:%02d".format(h, m)
                sleepFreqValue.text = formatTime12h(h, m)
            }
        }

        findViewById<LinearLayout>(R.id.workoutTimingBtn).setOnClickListener {
            showTimingDropdown { i ->
                workoutTiming = timingValues[i]
                workoutTimingValue.text = timingOptions[i]
            }
        }
        findViewById<LinearLayout>(R.id.workoutFreqBtn).setOnClickListener {
            showFreqDropdown { i ->
                workoutFreqStart = freqStartValues[i]
                workoutFreqEnd   = freqEndValues[i]
                workoutFreqValue.text = freqOptions[i]
            }
        }

        // DND time pickers
        findViewById<LinearLayout>(R.id.dndStartBtn).setOnClickListener {
            showTimePicker("DND Start") { h, m ->
                dndStart = "%02d:%02d".format(h, m)
                dndStartValue.text = formatTime12h(h, m)
            }
        }
        findViewById<LinearLayout>(R.id.dndEndBtn).setOnClickListener {
            showTimePicker("DND End") { h, m ->
                dndEnd = "%02d:%02d".format(h, m)
                dndEndValue.text = formatTime12h(h, m)
            }
        }

        // Save
        saveButton.setOnClickListener { saveSettings() }
    }

    // ── Toggle UI ─────────────────────────────────────────────────────────────
    private fun updateToggleUI(knob: View, toggle: LinearLayout, isOn: Boolean) {
        val toggleWidth = toggle.width.takeIf { it > 0 } ?: 140
        val knobWidth   = knob.width.takeIf  { it > 0 } ?: 60
        val targetX     = if (isOn) (toggleWidth - knobWidth - 4).toFloat() else 0f
        knob.animate().translationX(targetX).setDuration(200).start()
        toggle.setBackgroundResource(
            if (isOn) R.drawable.green_toggle else R.drawable.grey_toggle
        )
    }

    // ── Dropdown helpers ──────────────────────────────────────────────────────
    private fun showTimingDropdown(onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Select Timing")
            .setItems(timingOptions) { _, i -> onSelected(i) }
            .show()
    }

    private fun showFreqDropdown(onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Select Frequency")
            .setItems(freqOptions) { _, i -> onSelected(i) }
            .show()
    }

    private fun showTimePicker(title: String, onSelected: (Int, Int) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m -> onSelected(h, m) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false
        ).apply { setTitle(title) }.show()
    }

    private fun formatTime12h(h: Int, m: Int): String {
        val ampm = if (h < 12) "AM" else "PM"
        val hour = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(hour, m, ampm)
    }

    // ── Load settings ─────────────────────────────────────────────────────────
    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getNotificationSettings(
                    SessionManager.getBearerToken()
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val s = response.body()!!.data!!
                    hydrationEnabled = s.hydrationEnabled
                    stepsEnabled     = s.stepsEnabled
                    sleepEnabled     = s.sleepEnabled
                    workoutEnabled   = s.workoutEnabled
                    dndEnabled       = s.dndEnabled
                    workoutTiming    = s.workoutTiming
                    workoutFreqStart = s.workoutFreqStart
                    workoutFreqEnd   = s.workoutFreqEnd
                    dndStart         = s.dndStartTime
                    dndEnd           = s.dndEndTime
                    applyUIState()
                }
            } catch (e: Exception) {
                applyUIState() // use defaults
            }
        }
    }

    private fun applyUIState() {
        hydrationToggle.post { updateToggleUI(hydrationKnob, hydrationToggle, hydrationEnabled) }
        hydrationToggle.post { updateToggleUI(stepsKnob,     stepsToggle,     stepsEnabled) }
        hydrationToggle.post { updateToggleUI(sleepKnob,     sleepToggle,     sleepEnabled) }
        hydrationToggle.post { updateToggleUI(workoutKnob,   workoutToggle,   workoutEnabled) }
        hydrationToggle.post { updateToggleUI(dndKnob,       dndToggle,       dndEnabled) }

        hydrationDropdowns.visibility = if (hydrationEnabled) View.VISIBLE else View.GONE
        stepsDropdowns.visibility     = if (stepsEnabled) View.VISIBLE else View.GONE
        sleepDropdowns.visibility     = if (sleepEnabled) View.VISIBLE else View.GONE
        workoutDropdowns.visibility   = if (workoutEnabled) View.VISIBLE else View.GONE
        dndTimeContainer.visibility   = if (dndEnabled) View.VISIBLE else View.GONE

        workoutTimingValue.text = timingOptions[timingValues.indexOf(workoutTiming).coerceAtLeast(0)]
        dndStartValue.text      = format24to12(dndStart)
        dndEndValue.text        = format24to12(dndEnd)
    }

    private fun format24to12(time: String): String {
        return try {
            val parts = time.split(":")
            formatTime12h(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) { time }
    }

    // ── Save settings ─────────────────────────────────────────────────────────
    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateNotificationSettings(
                    SessionManager.getBearerToken(),
                    NotificationSettingsRequest(
                        hydrationEnabled = hydrationEnabled,
                        stepsEnabled     = stepsEnabled,
                        sleepEnabled     = sleepEnabled,
                        workoutEnabled   = workoutEnabled,
                        workoutTiming    = workoutTiming,
                        workoutFreqStart = workoutFreqStart,
                        workoutFreqEnd   = workoutFreqEnd,
                        dndEnabled       = dndEnabled,
                        dndStartTime     = dndStart,
                        dndEndTime       = dndEnd
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@NotificationSettingsActivity,
                        "Settings saved!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@NotificationSettingsActivity,
                        response.body()?.message ?: "Failed to save",
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NotificationSettingsActivity,
                    "Connection error", Toast.LENGTH_SHORT).show()
            }
        }
    }
}