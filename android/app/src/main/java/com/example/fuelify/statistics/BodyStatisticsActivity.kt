package com.example.fuelify.statistics

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class BodyStatisticsActivity : AppCompatActivity() {

    private lateinit var btnBack                 : ImageButton
    private lateinit var tvMainPercentage        : TextView
    private lateinit var lineChart               : com.github.mikephil.charting.charts.LineChart
    private lateinit var tvMonthlyBreakdownTitle : TextView
    private lateinit var layoutMonthlyList       : LinearLayout

    private val fmt = DecimalFormat("0.0")

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.body_statistics)
        bindViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // Fetch everything from the server
        lifecycleScope.launch {
            val latest  = BodyScanRepository.getLatestRecord()
            val history = BodyScanRepository.getMonthlyBodyFatHistory()

            populateHeader(latest)
            setupChart(history)
            populateMonthlyBreakdown(history)
        }
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private fun bindViews() {
        btnBack               = findViewById(R.id.btnBack)
        tvMainPercentage      = findViewById(R.id.tvMainPercentage)
        lineChart             = findViewById(R.id.lineChart)
        tvMonthlyBreakdownTitle = findViewById(R.id.tvMonthlyBreakdownTitle)

        layoutMonthlyList = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleParent = tvMonthlyBreakdownTitle.parent as LinearLayout
        val titleIndex  = titleParent.indexOfChild(tvMonthlyBreakdownTitle)
        titleParent.addView(layoutMonthlyList, titleIndex + 1)

        for (i in titleIndex + 2 until titleParent.childCount) {
            titleParent.getChildAt(i)?.visibility = View.GONE
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun populateHeader(latest: BodyScanRecord?) {
        if (latest != null) {
            tvMainPercentage.text = fmt.format(latest.bodyFatPercent)
        }
    }

    // ── BarChart ──────────────────────────────────────────────────────────────

    private fun setupChart(history: List<MonthlyBodyFatEntry>) {

        val chartParent = lineChart.parent as? android.view.ViewGroup ?: return
        val chartIndex  = chartParent.indexOfChild(lineChart)

        val barChart = BarChart(this).apply {
            layoutParams = lineChart.layoutParams
        }
        chartParent.removeViewAt(chartIndex)
        chartParent.addView(barChart, chartIndex)

        // If no real data exists, show nothing — never show fake hardcoded values
        if (history.isEmpty()) {
            barChart.setNoDataText("No body scan data yet")
            barChart.setNoDataTextColor(Color.parseColor("#9E9E9E"))
            barChart.invalidate()
            return
        }

        val pairs: List<Pair<String, Float>> =
            history.map { it.monthLabel.split(" ").first() to it.bodyFatAvg.toFloat() }

        val entries = pairs.mapIndexed { i, (_, v) -> BarEntry(i.toFloat(), v) }
        val labels  = pairs.map { it.first }

        val barColors = if (history.size > 1) {
            history.map { entry ->
                if (entry.changeFromPrev <= 0) Color.parseColor("#C3E66E")
                else                           Color.parseColor("#FF9800")
            }
        } else {
            List(pairs.size) { Color.parseColor("#C3E66E") }
        }

        val dataSet = BarDataSet(entries, "Body Fat %").apply {
            colors         = barColors
            setDrawValues(true)
            valueTextSize  = 10f
            valueTextColor = Color.parseColor("#424242")
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry) =
                    "${fmt.format(barEntry.y)}%"
            }
        }

        barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }

            xAxis.apply {
                valueFormatter        = IndexAxisValueFormatter(labels)
                position              = XAxis.XAxisPosition.BOTTOM
                granularity           = 1f
                setDrawGridLines(false)
                textColor             = Color.parseColor("#424242")
                textSize              = 11f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor             = Color.parseColor("#E0E0E0")
                textColor             = Color.parseColor("#424242")
                textSize              = 10f
                axisMinimum           = ((pairs.minOfOrNull { it.second } ?: 0f) - 3f).coerceAtLeast(0f)
            }
            axisRight.isEnabled   = false
            legend.isEnabled      = false
            description.isEnabled = false
            setFitBars(true)
            animateY(800)
            invalidate()
        }
    }

    // ── Monthly breakdown list ────────────────────────────────────────────────

    private fun populateMonthlyBreakdown(history: List<MonthlyBodyFatEntry>) {
        layoutMonthlyList.removeAllViews()
        if (history.isEmpty()) return

        history.reversed().forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Month label
            val tvMonth = TextView(this).apply {
                text         = entry.monthLabel
                textSize     = 13f
                setTextColor(Color.parseColor("#424242"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            }

            // Always show the real monthly average — never substitute with latest record
            val displayFat = entry.bodyFatAvg

            val tvFat = TextView(this).apply {
                text         = "${fmt.format(displayFat)}%"
                textSize     = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#212121"))
                gravity      = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Delta arrow
            val tvDelta = TextView(this).apply {
                val delta = entry.changeFromPrev
                text = when {
                    delta < 0  -> "↓ ${fmt.format(-delta)}%"
                    delta > 0  -> "↑ ${fmt.format(delta)}%"
                    else       -> "–"
                }
                textSize     = 12f
                setTextColor(
                    if (entry.changeFromPrev <= 0) Color.parseColor("#66BB6A")
                    else                           Color.parseColor("#FF9800")
                )
                gravity      = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(tvMonth)
            row.addView(tvFat)
            row.addView(tvDelta)
            layoutMonthlyList.addView(row)

            // Divider
            val divider = View(this).apply {
                setBackgroundColor(Color.parseColor("#F0F0F0"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                )
            }
            layoutMonthlyList.addView(divider)
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    // ── Bottom nav ────────────────────────────────────────────────────────────


}