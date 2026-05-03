package com.example.fuelify.auth

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SendMessageRequest
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var messagesScrollView: ScrollView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var sendIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        btnBack            = findViewById(R.id.btnBack)
        messagesScrollView = findViewById(R.id.messagesScrollView)
        messagesContainer  = findViewById(R.id.messagesContainer)
        messageInput       = findViewById(R.id.messageInput)
        sendIcon           = findViewById(R.id.sendIcon)

        btnBack.setOnClickListener { finish() }

        sendIcon.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                messageInput.text.clear()
            }
        }

        val initialMessage = intent.getStringExtra("initialMessage")
        if (initialMessage != null) {
            sendMessage(initialMessage)
        } else {
            loadChatHistory()
        }
    }

    private fun loadChatHistory() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getChatHistory(
                    SessionManager.getBearerToken()
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val messages = response.body()!!.data?.messages ?: emptyList()
                    messagesContainer.removeAllViews()
                    messages.forEach { msg ->
                        addMessageBubble(msg.message, msg.sender == "user")
                    }
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "loadChatHistory error: ${e.message}", e)
            }
        }
    }

    private fun sendMessage(text: String) {
        addMessageBubble(text, isUser = true)
        scrollToBottom()

        val typingView = TextView(this).apply {
            this.text = "Typing......"
            textSize = 14f
            setTextColor(0xFF4CAF50.toInt())
            setPadding(16, 8, 16, 8)
        }
        messagesContainer.addView(typingView)
        scrollToBottom()

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.sendMessage(
                    SessionManager.getBearerToken(),
                    SendMessageRequest(text)
                )

                messagesContainer.removeView(typingView)

                // ── Debug logs — remove after confirming it works ──
                Log.d("ChatActivity", "HTTP status : ${response.code()}")
                Log.d("ChatActivity", "success     : ${response.body()?.success}")
                Log.d("ChatActivity", "data        : ${response.body()?.data}")
                Log.d("ChatActivity", "auraReply   : ${response.body()?.data?.auraReply}")
                Log.d("ChatActivity", "reply msg   : ${response.body()?.data?.auraReply?.message}")
                if (!response.isSuccessful) {
                    Log.e("ChatActivity", "errorBody   : ${response.errorBody()?.string()}")
                }
                // ─────────────────────────────────────────────────

                if (response.isSuccessful) {
                    val reply = response.body()?.data?.auraReply?.message
                    if (!reply.isNullOrBlank()) {
                        addMessageBubble(reply, isUser = false)
                    } else {
                        addMessageBubble("Received an empty response. Please try again.", isUser = false)
                    }
                } else {
                    addMessageBubble("Server error (${response.code()}). Please try again.", isUser = false)
                }
                scrollToBottom()

            } catch (e: Exception) {
                Log.e("ChatActivity", "sendMessage exception: ${e.message}", e)
                try { messagesContainer.removeView(typingView) } catch (_: Exception) {}
                addMessageBubble("Connection error. Please try again.", isUser = false)
                scrollToBottom()
            }
        }
    }

    private fun addMessageBubble(text: String, isUser: Boolean) {
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (isUser) setPadding(12, 12, 12, 12) else setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity      = if (isUser) Gravity.END else Gravity.START
                it.bottomMargin = 12
                it.topMargin    = 4
                it.width        = resources.displayMetrics.widthPixels * 7 / 10
            }
            setBackgroundResource(
                if (isUser) R.drawable.message_user_background
                else        R.drawable.message_bot_background
            )
        }

        val textView = TextView(this).apply {
            this.text = text
            textSize  = 14f
            setTextColor(if (isUser) 0xFF000000.toInt() else 0xFF333333.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        bubble.addView(textView)
        messagesContainer.addView(bubble)
    }

    private fun scrollToBottom() {
        messagesScrollView.post {
            messagesScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}