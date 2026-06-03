package com.example.fuelify.statistics

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.util.*

class SleepScheduleActivity : AppCompatActivity() {

    private lateinit var tvBedtimeTime      : TextView
    private lateinit var tvBedtimeCountdown : TextView
    private lateinit var tvAlarmTime        : TextView
    private lateinit var tvAlarmCountdown   : TextView
    private lateinit var switchBedtime      : SwitchCompat
    private lateinit var switchAlarm        : SwitchCompat
    private lateinit var tvSleepInfo        : TextView
    private lateinit var sleepProgress      : ProgressBar
    private lateinit var tvProgressPct      : TextView
    private lateinit var fabAdd             : FloatingActionButton

    private val dayEntries = listOf(
        R.id.dayMon to 1, R.id.dayTue to 2, R.id.dayWed to 3,
        R.id.dayThu to 4, R.id.dayFri to 5, R.id.daySat to 6, R.id.daySun to 7
    )

    private val dayNames = mapOf(
        1 to "Mon", 2 to "Tue", 3 to "Wed",
        4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun"
    )

    private var selectedDay: Int = run {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sleep_schedule)
        bindViews()
        setupDayClickListeners()

        fabAdd.setOnClickListener {
            val intent = Intent(this, SleepAddAlarmActivity::class.java)
            intent.putExtra("day_of_week", selectedDay)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        renderDays()
        loadDaySchedule()
    }

    private fun bindViews() {
        tvBedtimeTime      = findViewById(R.id.tvBedtimeTime)
        tvBedtimeCountdown = findViewById(R.id.tvBedtimeCountdown)
        tvAlarmTime        = findViewById(R.id.tvAlarmTime)
        tvAlarmCountdown   = findViewById(R.id.tvAlarmCountdown)
        switchBedtime      = findViewById(R.id.switchBedtime)
        switchAlarm        = findViewById(R.id.switchAlarm)
        sleepProgress      = findViewById(R.id.sleepProgress)
        fabAdd             = findViewById(R.id.fabAdd)

        tvSleepInfo   = findFirstTvStartingWith("You will") ?: TextView(this)
        tvProgressPct = findFirstTvStartingWith("98") ?: TextView(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<CardView>(R.id.cardBedtime).setOnClickListener {
            startActivity(Intent(this, SleepAddAlarmActivity::class.java).apply {
                putExtra("day_of_week", selectedDay) })
        }
        findViewById<CardView>(R.id.cardAlarm).setOnClickListener {
            startActivity(Intent(this, SleepAddAlarmActivity::class.java).apply {
                putExtra("day_of_week", selectedDay) })
        }
    }

    private fun loadDaySchedule() {
        lifecycleScope.launch {
            val s = SleepRepository.getScheduleForDay(selectedDay) ?: return@launch

            tvBedtimeTime.text      = ", ${s.bedtimeFormatted}"
            tvBedtimeCountdown.text = s.countdownBedtime
            tvAlarmTime.text        = ", ${s.alarmFormatted}"
            tvAlarmCountdown.text   = s.countdownAlarm

            setSwitchState(switchBedtime, s.bedtimeEnabled)
            setSwitchState(switchAlarm,   s.alarmEnabled)

            switchBedtime.setOnCheckedChangeListener(null)
            switchAlarm.setOnCheckedChangeListener(null)
            switchBedtime.isChecked = s.bedtimeEnabled
            switchAlarm.isChecked   = s.alarmEnabled

            switchBedtime.setOnCheckedChangeListener { _, checked ->
                setSwitchState(switchBedtime, checked)
                lifecycleScope.launch { SleepRepository.toggleBedtime(selectedDay, checked) }
            }
            switchAlarm.setOnCheckedChangeListener { _, checked ->
                setSwitchState(switchAlarm, checked)
                lifecycleScope.launch { SleepRepository.toggleAlarm(selectedDay, checked) }
            }

            val pct = s.sleepQualityPct
            sleepProgress.progress = pct
            tvSleepInfo.text   = "You will get ${s.sleepDuration}\nfor tonight"
            tvProgressPct.text = "$pct%"
        }
    }

    private fun setSwitchState(switch: SwitchCompat, enabled: Boolean) {
        val color = if (enabled) Color.parseColor("#C3E66E") else Color.parseColor("#CCCCCC")
        switch.trackTintList = android.content.res.ColorStateList.valueOf(color)
        switch.thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
    }

    private fun renderDays() {
        dayEntries.forEach { (viewId, dow) ->
            val container = findViewById<View>(viewId)
            val tvs = mutableListOf<TextView>()
            collectTextViews(container, tvs)
            val isSelected = dow == selectedDay
            if (tvs.isNotEmpty()) {
                tvs[0].text = dayNames[dow] ?: ""
                tvs[0].setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#333333"))
            }
            if (tvs.size >= 2) tvs[1].visibility = View.GONE
            container.setBackgroundColor(
                if (isSelected) Color.parseColor("#C3E66E") else Color.parseColor("#F5F5F5")
            )
        }
    }

    private fun setupDayClickListeners() {
        dayEntries.forEach { (viewId, dow) ->
            findViewById<View>(viewId).setOnClickListener {
                selectedDay = dow
                renderDays()
                loadDaySchedule()
            }
        }
    }

    private fun collectTextViews(view: View, result: MutableList<TextView>) {
        if (view is TextView) { result.add(view); return }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectTextViews(view.getChildAt(i), result)
        }
    }

    private fun findFirstTvStartingWith(prefix: String): TextView? {
        fun search(vg: ViewGroup): TextView? {
            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i)
                if (child is TextView && child.text.toString().startsWith(prefix)) return child
                if (child is ViewGroup) search(child)?.let { return it }
            }
            return null
        }
        return (window.decorView.rootView as? ViewGroup)?.let { search(it) }
    }

}