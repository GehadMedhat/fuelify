package com.example.fuelify.statistics

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SleepAddAlarmActivity : AppCompatActivity() {

    private lateinit var tvBedtimeValue:   TextView
    private lateinit var tvHoursValue:     TextView
    private lateinit var tvRepeatValue:    TextView
    private lateinit var switchVibrate:    SwitchCompat
    private lateinit var btnAdd:           Button

    private var dayOfWeek:      Int       = 1
    private var bedtimeHour:    Int       = 21
    private var bedtimeMinute:  Int       = 0
    private var hoursOfSleep:   Int       = 8
    private var minutesOfSleep: Int       = 0
    private var repeatDays:     List<Int> = listOf(1, 2, 3, 4, 5)
    private var vibrateEnabled: Boolean   = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sleep_add_alarm)

        // Get the day passed from SleepScheduleActivity
        dayOfWeek = intent.getIntExtra("day_of_week", 1)

        bindViews()
        setupListeners()

        // Pre-fill from server schedule for this day
        loadScheduleFromServer()
    }

    private fun loadScheduleFromServer() {
        lifecycleScope.launch {
            val existing = SleepRepository.getScheduleForDay(dayOfWeek)
            if (existing != null) {
                bedtimeHour    = existing.bedtimeHour
                bedtimeMinute  = existing.bedtimeMinute
                // استخرج hours و minutes من sleepDuration (مثلاً "8h 0min")
                parseSleepDuration(existing.sleepDuration)
                vibrateEnabled = true // السيرفر مش بيرجع vibrateEnabled، نحتفظ بالـ default
                repeatDays     = listOf(dayOfWeek)
            }
            refreshUI()
        }
    }

    private fun parseSleepDuration(duration: String) {
        // Format: "8h 0min"
        try {
            val hPart = duration.substringBefore("h").trim().toIntOrNull() ?: 8
            val mPart = duration.substringAfter("h").substringBefore("min").trim().toIntOrNull() ?: 0
            hoursOfSleep   = hPart
            minutesOfSleep = mPart
        } catch (e: Exception) {
            hoursOfSleep   = 8
            minutesOfSleep = 0
        }
    }

    private fun bindViews() {
        tvBedtimeValue   = findViewById(R.id.tvBedtimeValue)
        tvHoursValue     = findViewById(R.id.tvHoursValue)
        tvRepeatValue    = findViewById(R.id.tvRepeatValue)
        switchVibrate    = findViewById(R.id.switchVibrate)
        btnAdd           = findViewById(R.id.btnAdd)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun refreshUI() {
        tvBedtimeValue.text     = formatTime12h(bedtimeHour, bedtimeMinute)
        tvHoursValue.text       = "${hoursOfSleep}hours ${minutesOfSleep}minutes"
        tvRepeatValue.text      = repeatLabel()
        switchVibrate.isChecked = vibrateEnabled
        setSwitchState(switchVibrate, vibrateEnabled)
    }

    // Green when ON, gray when OFF
    private fun setSwitchState(switch: SwitchCompat, enabled: Boolean) {
        val trackColor = if (enabled) android.graphics.Color.parseColor("#C3E66E")
        else         android.graphics.Color.parseColor("#CCCCCC")
        switch.trackTintList = android.content.res.ColorStateList.valueOf(trackColor)
        switch.thumbTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
    }

    private fun setupListeners() {
        findViewById<CardView>(R.id.cardBedtime).setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                bedtimeHour = h; bedtimeMinute = m; refreshUI()
            }, bedtimeHour, bedtimeMinute, false).show()
        }

        findViewById<CardView>(R.id.cardHoursOfSleep).setOnClickListener {
            showHoursPicker()
        }

        findViewById<CardView>(R.id.cardRepeat).setOnClickListener {
            showRepeatPicker()
        }

        switchVibrate.setOnCheckedChangeListener { _, checked ->
            vibrateEnabled = checked
            setSwitchState(switchVibrate, checked)
        }

        btnAdd.setOnClickListener { saveAndExit() }
    }

    private fun showHoursPicker() {
        val options = (4..12).flatMap { h ->
            listOf(0, 15, 30, 45).map { m -> "${h}h ${m}min" }
        }.toTypedArray()

        val current = options.indexOfFirst {
            it == "${hoursOfSleep}h ${minutesOfSleep}min"
        }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Hours of Sleep")
            .setSingleChoiceItems(options, current) { dialog, which ->
                val parts = options[which].replace("h", "").replace("min", "").trim().split(" ")
                hoursOfSleep   = parts[0].trim().toInt()
                minutesOfSleep = parts[1].trim().toInt()
                refreshUI()
                dialog.dismiss()
            }
            .show()
    }

    private fun showRepeatPicker() {
        val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dayNums  = listOf(1, 2, 3, 4, 5, 6, 7)
        val checked  = dayNums.map { repeatDays.contains(it) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Repeat")
            .setMultiChoiceItems(dayNames, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                repeatDays = dayNums.filterIndexed { i, _ -> checked[i] }
                refreshUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAndExit() {
        val request = UpdateSleepRequest(
            bedtimeHour    = bedtimeHour,
            bedtimeMinute  = bedtimeMinute,
            hoursOfSleep   = hoursOfSleep,
            minutesOfSleep = minutesOfSleep,
            repeatDays     = repeatDays,
            vibrateEnabled = vibrateEnabled,
            bedtimeEnabled = true,
            alarmEnabled   = true
        )

        lifecycleScope.launch {
            val result = SleepRepository.updateSchedule(dayOfWeek, request)
            if (result != null) {
                Toast.makeText(this@SleepAddAlarmActivity, "Schedule saved ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SleepAddAlarmActivity, "Failed to save ❌", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    private fun repeatLabel(): String {
        if (repeatDays.size == 7) return "Every day"
        if (repeatDays.toSet() == setOf(1, 2, 3, 4, 5)) return "Mon to Fri"
        if (repeatDays.toSet() == setOf(6, 7)) return "Weekends"
        val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
            5 to "Fri", 6 to "Sat", 7 to "Sun")
        return repeatDays.mapNotNull { names[it] }.joinToString(", ")
    }

    private fun formatTime12h(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val h12  = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        return String.format("%02d:%02d %s", h12, minute, amPm)
    }

}