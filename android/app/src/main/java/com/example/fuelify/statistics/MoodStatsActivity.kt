package com.example.fuelify.statistics

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MoodStatsActivity : AppCompatActivity() {

    private lateinit var tvTotalLogs:     TextView
    private lateinit var tvMostCommon:    TextView
    private lateinit var tvCalendarTitle: TextView
    private lateinit var calendarGrid:    GridLayout
    private lateinit var moodBreakdown:   LinearLayout

    private val moodColors = mapOf(
        MoodType.AMAZING to Color.parseColor("#FFF9E6"),
        MoodType.OKAY    to Color.parseColor("#E8F5E9"),
        MoodType.SAD     to Color.parseColor("#E3F2FD"),
        MoodType.ANGRY   to Color.parseColor("#FCE4EC")
    )
    private val moodProgress = mapOf(
        MoodType.AMAZING to Color.parseColor("#FFD600"),
        MoodType.OKAY    to Color.parseColor("#66BB6A"),
        MoodType.SAD     to Color.parseColor("#42A5F5"),
        MoodType.ANGRY   to Color.parseColor("#EF5350")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mood_stats)
        bindViews()
        setupNavBar()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun bindViews() {
        tvTotalLogs   = findViewById(R.id.tv_total_logs_stat)
        tvMostCommon  = findViewById(R.id.tv_most_common_emoji)
        calendarGrid  = findViewById(R.id.calendar_grid)
//        bottomNav     = findViewById(R.id.bottom_navigation)

        tvCalendarTitle = findTextViewWithText("November 2025")
            ?: findFirstTextViewInCard(R.id.calendar_card)
                    ?: TextView(this)

        moodBreakdown = findLinearLayoutIn(R.id.mood_breakdown_card)
            ?: LinearLayout(this)

        findViewById<android.widget.ImageView>(R.id.btn_back)
            .setOnClickListener { finish() }
    }

    private fun setupNavBar() {
        val green = Color.parseColor("#C3E66E")
        val grey  = Color.parseColor("#737373")
        val tintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(green, grey)
        )

    }

    // ── Fetch all data from the server in one coroutine ───────────────────────
    private fun refreshAll() {
        lifecycleScope.launch {
            val stats = MoodRepository.getStatsData() ?: return@launch

            tvTotalLogs.text  = stats.totalLogs.toString()
            tvMostCommon.text = stats.mostCommonEmoji ?: "—"

            val calendarEntries = stats.calendarEntries
                .mapValues { (_, v) -> runCatching { MoodType.valueOf(v) }.getOrElse { MoodType.OKAY } }

            val counts = stats.moodCounts
                .mapKeys { (k, _) -> runCatching { MoodType.valueOf(k) }.getOrElse { MoodType.OKAY } }

            buildCalendar(stats.calendarMonth, stats.calendarLabel, calendarEntries)

            buildMoodBreakdown(counts)
        }
    }

    // ── Calendar ──────────────────────────────────────────────────────────────
    private fun buildCalendar(
        monthKey: String,
        monthLabel: String,
        entries: Map<String, MoodType>
    ) {
        tvCalendarTitle.text = monthLabel

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            time = sdf.parse("$monthKey-01")!!
        }
        val firstDow    = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayKey    = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        calendarGrid.removeAllViews()
        calendarGrid.columnCount = 7

        val cellSize = (resources.displayMetrics.widthPixels - dpToPx(80)) / 7

        repeat(firstDow) { calendarGrid.addView(makeBlankCell(cellSize)) }

        for (day in 1..daysInMonth) {
            val dayKey = "$monthKey-${String.format("%02d", day)}"
            val mood   = entries[dayKey]
            calendarGrid.addView(makeDayCell(day, dayKey, mood, todayKey, cellSize))
        }
    }

    private fun makeDayCell(
        day: Int, dayKey: String, mood: MoodType?, todayKey: String, cellSize: Int
    ): TextView {
        val tv = TextView(this)
        tv.layoutParams = GridLayout.LayoutParams().apply { width = cellSize; height = cellSize }
        tv.gravity  = Gravity.CENTER
        tv.textSize = if (mood != null) 18f else 13f

        return when {
            mood != null -> {
                tv.text = mood.emoji
                tv.setBackgroundColor(moodColors[mood] ?: Color.TRANSPARENT)
                tv
            }
            dayKey == todayKey -> {
                tv.text = day.toString()
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextColor(Color.WHITE)
                tv.setBackgroundResource(android.R.drawable.btn_default_small)
                tv.background?.setTint(Color.parseColor("#C3E66E"))
                tv
            }
            else -> {
                tv.text = day.toString()
                tv.setTextColor(Color.parseColor("#333333"))
                tv
            }
        }
    }

    private fun makeBlankCell(size: Int): android.view.View {
        val v = android.view.View(this)
        v.layoutParams = GridLayout.LayoutParams().apply { width = size; height = size }
        return v
    }

    // ── Mood breakdown ────────────────────────────────────────────────────────
    private fun buildMoodBreakdown(counts: Map<MoodType, Int>) {
        val titleView = if (moodBreakdown.childCount > 0) moodBreakdown.getChildAt(0) else null
        moodBreakdown.removeAllViews()
        titleView?.let { moodBreakdown.addView(it) }

        val total = counts.values.sum().coerceAtLeast(1)

        MoodType.values().forEach { mood ->
            val count = counts[mood] ?: 0
            if (count == 0) return@forEach

            val pct      = count * 100 / total
            val dayLabel = if (count == 1) "1 day" else "$count days"

            val row = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                gravity      = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(20) }
            }

            val tvEmoji = TextView(this).apply {
                text         = mood.emoji
                textSize     = 24f
                gravity      = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            }
            val tvLabel = TextView(this).apply {
                text         = mood.label
                textSize     = 16f
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dpToPx(12) }
            }
            val tvDays = TextView(this).apply {
                text     = dayLabel
                textSize = 14f
                setTextColor(Color.parseColor("#757575"))
            }

            row.addView(tvEmoji); row.addView(tvLabel); row.addView(tvDays)
            moodBreakdown.addView(row)

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max      = 100
                progress = pct
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8)
                ).apply { topMargin = dpToPx(12) }
                val drawable = android.graphics.drawable.ClipDrawable(
                    android.graphics.drawable.ColorDrawable(moodProgress[mood] ?: Color.GRAY),
                    android.view.Gravity.START,
                    android.graphics.drawable.ClipDrawable.HORIZONTAL
                )
                progressDrawable = android.graphics.drawable.LayerDrawable(arrayOf(
                    android.graphics.drawable.ColorDrawable(Color.parseColor("#EEEEEE")),
                    drawable
                ))
            }
            moodBreakdown.addView(pb)
        }

        if (counts.values.sum() == 0) {
            moodBreakdown.addView(TextView(this).apply {
                text     = "No moods logged yet.\nTap 'Add Mood' to get started!"
                textSize = 14f
                setTextColor(Color.parseColor("#757575"))
                gravity  = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(16) }
            })
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun findTextViewWithText(text: String): TextView? {
        fun search(vg: android.view.ViewGroup): TextView? {
            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i)
                if (child is TextView && child.text.toString() == text) return child
                if (child is android.view.ViewGroup) search(child)?.let { return it }
            }
            return null
        }
        return (window.decorView.rootView as? android.view.ViewGroup)?.let { search(it) }
    }

    private fun findFirstTextViewInCard(cardId: Int): TextView? {
        val card = findViewById<CardView>(cardId) ?: return null
        fun search(vg: android.view.ViewGroup): TextView? {
            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i)
                if (child is TextView) return child
                if (child is android.view.ViewGroup) search(child)?.let { return it }
            }
            return null
        }
        return search(card)
    }

    private fun findLinearLayoutIn(cardId: Int): LinearLayout? {
        val card = findViewById<CardView>(cardId) ?: return null
        fun search(vg: android.view.ViewGroup): LinearLayout? {
            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i)
                if (child is LinearLayout) return child
                if (child is android.view.ViewGroup) search(child)?.let { return it }
            }
            return null
        }
        return search(card)
    }
}