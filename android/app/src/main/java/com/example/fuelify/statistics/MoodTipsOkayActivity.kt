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

class MoodTipsOkayActivity : AppCompatActivity() {

    private val allTips = listOf(
        "A steady mood is a great foundation — use it to make progress on a goal.",
        "Try something new today, even something small, to spark a little excitement.",
        "Check in with a friend; connecting with others often lifts a neutral mood.",
        "Get moving — even a 15-minute walk can shift 'okay' into 'good'.",
        "Tidy up one area of your space; a cleaner environment improves how you feel.",
        "Listen to an upbeat playlist while you go about your day.",
        "Set one intention for the rest of the day to give it a sense of purpose.",
        "Take a few mindful breaths and appreciate the calm steadiness you have right now.",
        "Read or watch something inspiring — a good story can shift your energy.",
        "Do one thing just for fun, with no productivity goal attached."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mood_tips_okay)
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