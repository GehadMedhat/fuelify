package com.example.fuelify.statistics

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MoodFeelingSelectionActivity : AppCompatActivity() {

    private var selectedMood: MoodType? = null

    private lateinit var cardAmazing: CardView
    private lateinit var cardOkay:    CardView
    private lateinit var cardSad:     CardView
    private lateinit var cardAngry:   CardView
    private lateinit var btnContinue: MaterialButton

    private val colorDefault  = Color.TRANSPARENT
    private val colorSelected = Color.parseColor("#C3E66E")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mood_feeling_selection)
        bindViews()
        setupNavBar()
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        btnContinue.alpha     = 0.6f
        btnContinue.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        setupNavBar()
    }

    private fun bindViews() {
        cardAmazing = findViewById(R.id.card_amazing)
        cardOkay    = findViewById(R.id.card_okay)
        cardSad     = findViewById(R.id.card_sad)
        cardAngry   = findViewById(R.id.card_angry)
        btnContinue = findViewById(R.id.btn_continue)
//        bottomNav   = findViewById(R.id.bottom_navigation)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupNavBar() {
        val green = Color.parseColor("#C3E66E")
        val grey  = Color.parseColor("#737373")
        val tintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(green, grey)
        )
    }

    private fun setupListeners() {
        cardAmazing.setOnClickListener { selectMood(MoodType.AMAZING) }
        cardOkay.setOnClickListener    { selectMood(MoodType.OKAY) }
        cardSad.setOnClickListener     { selectMood(MoodType.SAD) }
        cardAngry.setOnClickListener   { selectMood(MoodType.ANGRY) }

        btnContinue.setOnClickListener {
            val mood = selectedMood
            if (mood == null) {
                Toast.makeText(this, "Please select how you're feeling", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button to prevent double-tap while request is in flight
            btnContinue.isEnabled = false
            btnContinue.alpha     = 0.6f

            lifecycleScope.launch {
                val result = MoodRepository.addEntry(mood)
                if (result == null) {
                    // Server unreachable — show error and re-enable button
                    Toast.makeText(
                        this@MoodFeelingSelectionActivity,
                        "Could not save mood. Check your connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                    btnContinue.isEnabled = true
                    btnContinue.alpha     = 1f
                    return@launch
                }

                val destination: Class<*> = when (mood) {
                    MoodType.AMAZING -> MoodTipsHappyActivity::class.java
                    MoodType.OKAY    -> MoodTipsOkayActivity::class.java
                    MoodType.SAD     -> MoodTipsSadActivity::class.java
                    MoodType.ANGRY   -> MoodTipsAngryActivity::class.java
                }
                startActivity(Intent(this@MoodFeelingSelectionActivity, destination))
                finish()
            }
        }

    }

    private fun selectMood(mood: MoodType) {
        selectedMood = mood
        cardAmazing.setCardBackgroundColor(colorDefault)
        cardOkay.setCardBackgroundColor(colorDefault)
        cardSad.setCardBackgroundColor(colorDefault)
        cardAngry.setCardBackgroundColor(colorDefault)
        when (mood) {
            MoodType.AMAZING -> cardAmazing.setCardBackgroundColor(colorSelected)
            MoodType.OKAY    -> cardOkay.setCardBackgroundColor(colorSelected)
            MoodType.SAD     -> cardSad.setCardBackgroundColor(colorSelected)
            MoodType.ANGRY   -> cardAngry.setCardBackgroundColor(colorSelected)
        }
        btnContinue.alpha     = 1f
        btnContinue.isEnabled = true
    }
}