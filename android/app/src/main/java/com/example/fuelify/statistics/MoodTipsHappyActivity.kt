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

class MoodTipsHappyActivity : AppCompatActivity() {

    private val allTips = listOf(
        "Share your happiness — call a friend or loved one and spread the joy!",
        "Channel this energy into a creative project you've been putting off.",
        "Write down 3 things you're grateful for to lock in this positive feeling.",
        "Go for a walk outside and soak in the world around you while you feel great.",
        "Use this mood boost to tackle a task you've been avoiding.",
        "Listen to your favourite upbeat playlist and let the music amplify your joy.",
        "Do something kind for a stranger — happiness multiplies when you share it.",
        "Take a moment to savour this feeling; mindfully notice what made today good.",
        "Set a new goal while your confidence is high — write it down and commit.",
        "Treat yourself to something small that you enjoy — you deserve it!"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mood_tips_happy)
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