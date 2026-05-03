package com.example.fuelify.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.BingoCard
import com.example.fuelify.data.api.models.BingoCell
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*

class BingoActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bingo)

        userId = UserPreferences.getUserId(this)
        findViewById<ImageButton>(R.id.btnBingoBack).setOnClickListener { finish() }
        loadBingo()
    }

    override fun onResume() { super.onResume(); loadBingo() }

    private fun loadBingo() {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getBingoCard(userId)
                }
                if (resp.isSuccessful && resp.body()?.data != null) {
                    bindCard(resp.body()!!.data!!)
                } else {
                    Toast.makeText(this@BingoActivity,
                        "Couldn't load bingo card", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BingoActivity,
                    "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindCard(card: BingoCard) {
        // Header dates
        findViewById<TextView>(R.id.tvBingoWeek).text =
            "Week of ${card.weekStart} – ${card.weekEnd}"

        // Progress summary
        val done  = card.totalDone
        val total = card.cells.size
        findViewById<TextView>(R.id.tvBingoProgress).text = "$done / $total tasks completed"
        findViewById<ProgressBar>(R.id.progressBingo).progress = (done * 100) / total

        // Bingo banner
        val banner = findViewById<LinearLayout>(R.id.layoutBingoBanner)
        if (card.hasBingo) {
            banner.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvBingoBannerText).text = "🎉 BINGO!"
        } else {
            banner.visibility = View.GONE
        }

        // Reward message
        findViewById<TextView>(R.id.tvBingoReward).text = card.rewardMsg

        // Build 3×3 grid
        val grid = findViewById<GridLayout>(R.id.gridBingo)
        grid.removeAllViews()
        grid.columnCount = 3
        grid.rowCount    = 3

        card.cells.forEach { cell ->
            val cellView = buildCellView(cell)
            val params = GridLayout.LayoutParams().apply {
                width  = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec    = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(6, 6, 6, 6)
            }
            cellView.layoutParams = params
            grid.addView(cellView)
        }

        // Line completion badges
        bindLineBadges(card)
    }

    private fun buildCellView(cell: BingoCell): View {
        val root = LayoutInflater.from(this)
            .inflate(R.layout.item_bingo_cell, null, false)

        val cardView = root.findViewById<CardView>(R.id.bingoCellCard)
        val tvEmoji  = root.findViewById<TextView>(R.id.tvBingoCellEmoji)
        val tvTask   = root.findViewById<TextView>(R.id.tvBingoCellTask)
        val tvProg   = root.findViewById<TextView>(R.id.tvBingoCellProgress)
        val checkMark= root.findViewById<TextView>(R.id.tvBingoCellCheck)

        tvEmoji.text = cell.emoji
        tvTask.text  = cell.task

        if (cell.completed) {
            // Completed — green fill
            cardView.setCardBackgroundColor(0xFF4A6200.toInt())
            tvTask.setTextColor(0xFFFFFFFF.toInt())
            tvEmoji.alpha = 1f
            tvProg.visibility  = View.GONE
            checkMark.visibility = View.VISIBLE
            checkMark.text = "✓"
        } else {
            // In progress
            cardView.setCardBackgroundColor(0xFFFFFFFF.toInt())
            tvTask.setTextColor(0xFF374151.toInt())
            checkMark.visibility = View.GONE
            tvProg.visibility  = View.VISIBLE
            tvProg.text = "${cell.current}/${cell.target}"
            tvProg.setTextColor(
                if (cell.current > 0) 0xFF8BC220.toInt() else 0xFFAAAAAA.toInt()
            )
        }

        return root
    }

    private fun bindLineBadges(card: BingoCard) {
        val container = findViewById<LinearLayout>(R.id.containerBingoBadges)
        container.removeAllViews()

        fun badge(text: String, done: Boolean) {
            val tv = TextView(this).apply {
                this.text = text
                textSize = 11f
                setPadding(20, 8, 20, 8)
                setTextColor(if (done) 0xFFFFFFFF.toInt() else 0xFF888888.toInt())
                setBackgroundColor(
                    if (done) 0xFF4A6200.toInt() else 0xFFEEEEEE.toInt()
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 8
                layoutParams = lp
            }
            container.addView(tv)
        }

        repeat(card.rowsComplete) { badge("Row ✓", true) }
        repeat(card.colsComplete) { badge("Col ✓", true) }

        val linesLeft = 3 - card.rowsComplete + 3 - card.colsComplete
        if (linesLeft > 0 && !card.hasBingo) {
            badge("$linesLeft lines to go", false)
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
