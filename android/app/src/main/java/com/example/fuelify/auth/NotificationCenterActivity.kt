package com.example.fuelify.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import com.example.fuelify.R
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch

class NotificationCenterActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var notificationsContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var markAllReadBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_center)

        btnBack                = findViewById(R.id.btnBack)
        notificationsContainer = findViewById(R.id.notificationsContainer)
        emptyView              = findViewById(R.id.emptyView)
        markAllReadBtn         = findViewById(R.id.markAllReadBtn)

        btnBack.setOnClickListener { finish() }

        markAllReadBtn.setOnClickListener { markAllRead() }

        loadNotifications()
    }

    private fun loadNotifications() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getNotifications(
                    SessionManager.getBearerToken()
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()!!.data
                    val notifications = data?.notifications ?: emptyList()

                    notificationsContainer.removeAllViews()

                    if (notifications.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        return@launch
                    }

                    emptyView.visibility = View.GONE
                    notifications.forEach { notif ->
                        addNotificationRow(
                            id      = notif.id,
                            title   = notif.title,
                            body    = notif.body,
                            isRead  = notif.isRead,
                            sentAt  = notif.sentAt
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@NotificationCenterActivity,
                    "Failed to load notifications", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addNotificationRow(
        id: Int, title: String, body: String, isRead: Boolean, sentAt: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
            layoutParams = params
            setBackgroundColor(
                if (isRead) android.graphics.Color.WHITE
                else android.graphics.Color.parseColor("#F0FFF0")
            )
        }

        val titleView = TextView(this).apply {
            text      = title
            textSize  = 16f
            setTextColor(android.graphics.Color.BLACK)
            if (!isRead) setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val bodyView = TextView(this).apply {
            text      = body
            textSize  = 14f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            setPadding(0, 8, 0, 0)
        }

        val timeView = TextView(this).apply {
            text      = sentAt.take(16).replace("T", " ")
            textSize  = 12f
            setTextColor(android.graphics.Color.parseColor("#999999"))
            setPadding(0, 4, 0, 0)
        }

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = 16 }
            setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
        }

        row.addView(titleView)
        row.addView(bodyView)
        row.addView(timeView)
        row.addView(divider)

        // Mark as read on tap
        if (!isRead) {
            row.setOnClickListener { markAsRead(id, row) }
        }

        notificationsContainer.addView(row)
    }

    private fun markAsRead(notifId: Int, row: LinearLayout) {
        lifecycleScope.launch {
            try {
                RetrofitClient.instance.markNotificationRead(
                    SessionManager.getBearerToken(), notifId
                )
                row.setBackgroundColor(android.graphics.Color.WHITE)
                row.setOnClickListener(null)
            } catch (e: Exception) { /* silent */ }
        }
    }

    private fun markAllRead() {
        lifecycleScope.launch {
            try {
                RetrofitClient.instance.markAllNotificationsRead(
                    SessionManager.getBearerToken()
                )
                Toast.makeText(this@NotificationCenterActivity,
                    "All marked as read", Toast.LENGTH_SHORT).show()
                loadNotifications()
            } catch (e: Exception) {
                Toast.makeText(this@NotificationCenterActivity,
                    "Failed to mark all as read", Toast.LENGTH_SHORT).show()
            }
        }
    }
}