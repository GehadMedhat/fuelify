package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.example.fuelify.R
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class QuickQuestionsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var messageInput: EditText
    private lateinit var sendIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot_suggestions)

        btnBack      = findViewById(R.id.btnBack)
        messageInput = findViewById(R.id.messageInput)
        sendIcon     = findViewById(R.id.sendIcon)

        btnBack.setOnClickListener { finish() }

        // Send typed message to ChatActivity
        sendIcon.setOnClickListener { sendToChat() }
        messageInput.setOnEditorActionListener { _, _, _ ->
            sendToChat()
            true
        }

        // Quick question buttons → open ChatActivity with that message
        setupQuickButton(R.id.btnDailyHealthSummary, "How much water should I drink daily?")
        setupQuickButton(R.id.btnMoodTracking,       "How can I improve my sleep quality?")
        setupQuickButton(R.id.btnWorkoutTips,        "What should I eat before a workout?")
        setupQuickButton(R.id.btnSymptomCheck,       "What are high protein meal ideas?")
        setupQuickButton(R.id.btnSleepData,          "What's a good beginner workout plan?")
        setupQuickButton(R.id.btnLog5kRun,           "How do I build muscle faster?")
        setupQuickButton(R.id.btnStretchReminder,    "How many calories should I burn per day?")
    }

    private fun setupQuickButton(id: Int, message: String) {
        try {
            findViewById<Button>(id)?.setOnClickListener {
                openChatWith(message)
            }
        } catch (e: Exception) { /* ignore if button not found */ }
    }

    private fun sendToChat() {
        val text = messageInput.text.toString().trim()
        if (text.isNotEmpty()) {
            openChatWith(text)
        }
    }

    private fun openChatWith(message: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("initialMessage", message)
        startActivity(intent)
    }
}