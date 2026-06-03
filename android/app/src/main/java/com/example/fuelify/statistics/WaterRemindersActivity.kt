package com.example.fuelify.statistics

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import com.example.fuelify.R
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.*

class WaterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("water_ch", "Water Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        nm.notify(
            intent.getIntExtra("notif_id", 1),
            NotificationCompat.Builder(context, "water_ch")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("💧 Time to Drink Water!")
                .setContentText("Stay hydrated — drink a glass of water now.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }
}

class WaterRemindersActivity : AppCompatActivity() {

    private lateinit var switchAutoReminder : SwitchCompat
    private lateinit var btnAddNew          : TextView
    private lateinit var rvReminders        : RecyclerView
    private lateinit var remindersAdapter   : RemindersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.water_reminders)
        requestNotificationPermission()
        createNotificationChannel()
        bindViews()
        setupAutoSwitch()
        btnAddNew.setOnClickListener { openTimePicker(null) }
        setupRecyclerView()
    }

    private fun bindViews() {
        switchAutoReminder = findViewById(R.id.switchAutoReminder)
        btnAddNew          = findViewById(R.id.btnAddNew)
        rvReminders        = findViewById(R.id.rvReminders)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel("water_ch", "Water Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun setupAutoSwitch() {
        lifecycleScope.launch {
            switchAutoReminder.isChecked = WaterTrackerRepository.isAutoReminderEnabled()
        }
        switchAutoReminder.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch {
                WaterTrackerRepository.setAutoReminderEnabled(checked)
                if (checked) {
                    scheduleAutoReminders()
                    Toast.makeText(this@WaterRemindersActivity, "Auto reminders enabled ✅", Toast.LENGTH_SHORT).show()
                } else {
                    cancelAutoReminders()
                    Toast.makeText(this@WaterRemindersActivity, "Auto reminders disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openTimePicker(existingItem: ReminderItem?) {
        val initH = existingItem?.hour   ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val initM = existingItem?.minute ?: Calendar.getInstance().get(Calendar.MINUTE)

        TimePickerDialog(this, { _, hour, minute ->
            val amPm  = if (hour < 12) "AM" else "PM"
            val h12   = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            val label = String.format("%02d:%02d %s", h12, minute, amPm)

            lifecycleScope.launch {
                if (existingItem != null) {
                    cancelReminderAlarm(existingItem)
                    val updated = existingItem.copy(timeLabel = label, hour = hour, minute = minute)
                    WaterTrackerRepository.editReminder(updated)
                    Toast.makeText(this@WaterRemindersActivity, "Reminder updated to $label ✅", Toast.LENGTH_SHORT).show()
                } else {
                    val newItem = ReminderItem(
                        id        = UUID.randomUUID().toString(),
                        timeLabel = label,
                        isEnabled = true,
                        hour      = hour,
                        minute    = minute
                    )
                    WaterTrackerRepository.addReminder(newItem)
                    Toast.makeText(this@WaterRemindersActivity, "Reminder added for $label ✅", Toast.LENGTH_SHORT).show()
                }
                refreshReminders()
            }
        }, initH, initM, false).show()
    }

    private fun refreshReminders() {
        lifecycleScope.launch {
            val list = WaterTrackerRepository.getReminders()
            remindersAdapter.setItems(list.toMutableList())
        }
    }

    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(
            onToggle = { item, enabled ->
                lifecycleScope.launch {
                    WaterTrackerRepository.toggleReminder(item.id, enabled)
                    if (enabled) scheduleReminderAlarm(item)
                    else cancelReminderAlarm(item)
                }
            },
            onDelete = { item ->
                lifecycleScope.launch {
                    WaterTrackerRepository.deleteReminder(item.id)
                    cancelReminderAlarm(item)
                    refreshReminders()
                    Toast.makeText(this@WaterRemindersActivity, "Reminder deleted", Toast.LENGTH_SHORT).show()
                }
            },
            onEdit = { item -> openTimePicker(item) }
        )
        rvReminders.layoutManager = LinearLayoutManager(this)
        rvReminders.adapter = remindersAdapter
        rvReminders.isNestedScrollingEnabled = false
        refreshReminders()
    }

    private fun alarmManager() = getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun buildPI(id: Int): PendingIntent = PendingIntent.getBroadcast(
        this, id,
        Intent(this, WaterReminderReceiver::class.java).apply { putExtra("notif_id", id) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleReminderAlarm(item: ReminderItem) {
        if (!item.isEnabled) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }
        val pi = buildPI(item.id.hashCode())
        val am = alarmManager()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun cancelReminderAlarm(item: ReminderItem) =
        alarmManager().cancel(buildPI(item.id.hashCode()))

    private fun scheduleAutoReminders() {
        listOf(8, 10, 12, 14, 16, 18, 20).forEach { hour ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
            }
            val pi = buildPI(900 + hour)
            val am = alarmManager()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                else am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }
    }

    private fun cancelAutoReminders() =
        listOf(8, 10, 12, 14, 16, 18, 20).forEach { alarmManager().cancel(buildPI(900 + it)) }



    inner class RemindersAdapter(
        private val onToggle : (ReminderItem, Boolean) -> Unit,
        private val onDelete : (ReminderItem) -> Unit,
        private val onEdit   : (ReminderItem) -> Unit
    ) : RecyclerView.Adapter<RemindersAdapter.VH>() {

        private var items = mutableListOf<ReminderItem>()

        fun setItems(newItems: MutableList<ReminderItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        )

        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) = h.bind(items[pos])

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val stripe : View         = v.findViewById(R.id.viewStripe)
            private val tvTime : TextView     = v.findViewById(R.id.tvReminderTime)
            private val toggle : SwitchCompat = v.findViewById(R.id.switchReminder)
            private val btnDel : ImageView    = v.findViewById(R.id.btnDeleteReminder)

            fun bind(item: ReminderItem) {
                tvTime.text      = item.timeLabel
                toggle.isChecked = item.isEnabled
                stripe.setBackgroundColor(if (item.isEnabled) Color.parseColor("#C3E66E") else Color.parseColor("#E0E0E0"))
                tvTime.setTextColor(if (item.isEnabled) Color.BLACK else Color.parseColor("#CCCCCC"))

                toggle.setOnCheckedChangeListener { _, checked ->
                    onToggle(item, checked)
                    stripe.setBackgroundColor(if (checked) Color.parseColor("#C3E66E") else Color.parseColor("#E0E0E0"))
                    tvTime.setTextColor(if (checked) Color.BLACK else Color.parseColor("#CCCCCC"))
                }
                tvTime.setOnClickListener { onEdit(item) }
                btnDel.setOnClickListener { onDelete(item) }
            }
        }
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