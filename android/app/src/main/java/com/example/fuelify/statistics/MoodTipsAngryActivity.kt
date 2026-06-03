package com.example.fuelify.statistics

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import com.example.fuelify.R
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MoodTipsAngryActivity : AppCompatActivity() {

    private val allTips = listOf(
        "Take 10 slow, deep breaths before reacting — it calms your nervous system.",
        "Remove yourself from the situation briefly; a short break changes perspective.",
        "Go for a brisk walk or do some exercise to release built-up tension safely.",
        "Write down exactly what triggered you — seeing it on paper often reduces its power.",
        "Count slowly from 1 to 20 before speaking or sending any messages.",
        "Try progressive muscle relaxation: tense each muscle group, then release.",
        "Listen to calming music or nature sounds to lower your heart rate.",
        "Splash cold water on your face — it activates the body's calming reflex.",
        "Ask yourself: will this matter in a week? A year? Gain some perspective.",
        "Talk to a trusted friend about what's bothering you — venting safely helps."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mood_tips_angry)
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