package com.example.fuelify.statistics

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.example.fuelify.R

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel("sleep_ch", "Sleep Reminders", android.app.NotificationManager.IMPORTANCE_HIGH)
            )
        }
        nm.notify(
            intent.getIntExtra("notif_id", 200),
            androidx.core.app.NotificationCompat.Builder(context, "sleep_ch")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(intent.getStringExtra("title") ?: "Sleep Reminder")
                .setContentText(intent.getStringExtra("body") ?: "Time to sleep!")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }
}

class SleepTrackerActivity : AppCompatActivity() {

    private lateinit var tvBedtimeTime      : TextView
    private lateinit var tvBedtimeCountdown : TextView
    private lateinit var tvAlarmTime        : TextView
    private lateinit var tvAlarmCountdown   : TextView
    private lateinit var switchBedtime      : SwitchCompat
    private lateinit var switchAlarm        : SwitchCompat
    private lateinit var cardBedtime        : CardView
    private lateinit var cardAlarm          : CardView
    private lateinit var cardDailySchedule  : CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sleep_tracker)
        requestNotificationPermission()
        bindViews()
    }

    override fun onResume() {
        super.onResume()
        loadSchedule()
    }

    private fun bindViews() {
        tvBedtimeTime      = findViewById(R.id.tvBedtimeTime)
        tvBedtimeCountdown = findViewById(R.id.tvBedtimeCountdown)
        tvAlarmTime        = findViewById(R.id.tvAlarmTime)
        tvAlarmCountdown   = findViewById(R.id.tvAlarmCountdown)
        switchBedtime      = findViewById(R.id.switchBedtime)
        switchAlarm        = findViewById(R.id.switchAlarm)
        cardBedtime        = findViewById(R.id.cardBedtime)
        cardAlarm          = findViewById(R.id.cardAlarm)
        cardDailySchedule  = findViewById(R.id.cardDailySchedule)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        cardBedtime.setOnClickListener { pickBedtime() }
        cardAlarm.setOnClickListener   { pickAlarm() }
        cardDailySchedule.setOnClickListener {
            startActivity(Intent(this, SleepScheduleActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnCheck).setOnClickListener {
            startActivity(Intent(this, SleepScheduleActivity::class.java))
        }
    }

    private fun loadSchedule() {
        lifecycleScope.launch {
            val s = SleepRepository.getTodaySchedule() ?: return@launch

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
                lifecycleScope.launch {
                    SleepRepository.toggleBedtime(s.dayOfWeek, checked)
                    if (checked) scheduleBedtimeAlarm(s.bedtimeHour, s.bedtimeMinute)
                    else cancelAlarm(1001)
                }
            }
            switchAlarm.setOnCheckedChangeListener { _, checked ->
                setSwitchState(switchAlarm, checked)
                lifecycleScope.launch {
                    SleepRepository.toggleAlarm(s.dayOfWeek, checked)
                    if (checked) scheduleWakeAlarm(s.wakeHour, s.wakeMinute)
                    else cancelAlarm(1002)
                }
            }

            if (s.bedtimeEnabled) scheduleBedtimeAlarm(s.bedtimeHour, s.bedtimeMinute)
            if (s.alarmEnabled)   scheduleWakeAlarm(s.wakeHour, s.wakeMinute)
        }
    }

    private fun setSwitchState(switch: SwitchCompat, enabled: Boolean) {
        val trackColor = if (enabled) Color.parseColor("#C3E66E") else Color.parseColor("#CCCCCC")
        switch.trackTintList = android.content.res.ColorStateList.valueOf(trackColor)
        switch.thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
    }

    private fun pickBedtime() {
        lifecycleScope.launch {
            val s = SleepRepository.getTodaySchedule() ?: return@launch
            TimePickerDialog(this@SleepTrackerActivity, { _, hour, minute ->
                lifecycleScope.launch {
                    SleepRepository.updateSchedule(s.dayOfWeek, UpdateSleepRequest(
                        bedtimeHour    = hour,
                        bedtimeMinute  = minute,
                        hoursOfSleep   = s.sleepDuration.substringBefore("h").trim().toIntOrNull() ?: 8,
                        minutesOfSleep = s.sleepDuration.substringAfter("h").substringBefore("min").trim().toIntOrNull() ?: 0,
                        bedtimeEnabled = s.bedtimeEnabled,
                        alarmEnabled   = s.alarmEnabled
                    ))
                    loadSchedule()
                }
            }, s.bedtimeHour, s.bedtimeMinute, false).show()
        }
    }

    private fun pickAlarm() {
        lifecycleScope.launch {
            val s = SleepRepository.getTodaySchedule() ?: return@launch
            TimePickerDialog(this@SleepTrackerActivity, { _, wakeHour, wakeMinute ->
                lifecycleScope.launch {
                    var diffMins = (wakeHour * 60 + wakeMinute) - (s.bedtimeHour * 60 + s.bedtimeMinute)
                    if (diffMins <= 0) diffMins += 24 * 60
                    SleepRepository.updateSchedule(s.dayOfWeek, UpdateSleepRequest(
                        bedtimeHour    = s.bedtimeHour,
                        bedtimeMinute  = s.bedtimeMinute,
                        hoursOfSleep   = diffMins / 60,
                        minutesOfSleep = diffMins % 60,
                        bedtimeEnabled = s.bedtimeEnabled,
                        alarmEnabled   = s.alarmEnabled
                    ))
                    loadSchedule()
                }
            }, s.wakeHour, s.wakeMinute, false).show()
        }
    }

    private fun scheduleBedtimeAlarm(hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }
        fireAlarm(1001, cal.timeInMillis, "🛌 Bedtime Reminder", "Time to go to bed!")
    }

    private fun scheduleWakeAlarm(hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }
        fireAlarm(1002, cal.timeInMillis, "⏰ Wake Up!", "Good morning! Time to start your day.")
    }

    private fun fireAlarm(id: Int, triggerMs: Long, title: String, body: String) {
        val pi = PendingIntent.getBroadcast(
            this, id,
            Intent(this, SleepAlarmReceiver::class.java).apply {
                putExtra("notif_id", id); putExtra("title", title); putExtra("body", body)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            else am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    private fun cancelAlarm(id: Int) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(PendingIntent.getBroadcast(
            this, id, Intent(this, SleepAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
    }
}