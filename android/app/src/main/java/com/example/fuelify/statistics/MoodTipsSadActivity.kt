package com.example.fuelify.statistics

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MoodTipsSadActivity : AppCompatActivity() {

    private val allTips = listOf(
        "It's okay to feel sad — allow yourself to feel it without judgment.",
        "Reach out to someone you trust; a short conversation can lift your spirits.",
        "Step outside for a short walk. Fresh air and movement ease a heavy heart.",
        "Put on a comforting movie or show and give yourself permission to rest.",
        "Write down your thoughts in a journal — getting them out can bring relief.",
        "Listen to music that matches your mood, then slowly shift to uplifting songs.",
        "Do one small act of self-care: a warm drink, a shower, or a quiet moment.",
        "Remind yourself: emotions are temporary. This feeling will pass.",
        "Try gentle stretching or yoga — even 10 minutes can shift your mood.",
        "Look at photos that bring back happy memories to remind yourself of joy."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mood_tips_sad)
        loadDynamicTips()
        setupNavBar()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        setupNavBar()
    }

    private fun loadDynamicTips() {
        val shuffled = allTips.shuffled().take(3)
        findViewById<TextView>(R.id.tv_tip_1).text = shuffled[0]
        findViewById<TextView>(R.id.tv_tip_2).text = shuffled[1]
        findViewById<TextView>(R.id.tv_tip_3).text = shuffled[2]
    }

    /**
     * This screen is neither Home nor Stats — both icons are grey, none selected.
     */
    private fun setupNavBar() {
        val green = Color.parseColor("#C3E66E")
        val grey  = Color.parseColor("#737373")
        val tintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(green, grey)
        )

    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btn_back_to_home).setOnClickListener {
            val intent = Intent(this, MoodHomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_view_progress).setOnClickListener {
            startActivity(Intent(this, MoodStatsActivity::class.java))
        }

    }
}