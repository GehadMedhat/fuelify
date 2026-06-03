package com.example.fuelify.statistics

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.util.*

class WaterStatisticsActivity : AppCompatActivity() {

    private lateinit var btnDaily               : TextView
    private lateinit var btnWeekly              : TextView
    private lateinit var btnMonthly             : TextView
    private lateinit var tvTotalAmount          : TextView
    private lateinit var tvGoalCompletion       : TextView
    private lateinit var tvDailyAverage         : TextView
    private lateinit var barChart               : BarChart
    private lateinit var lineChart              : LineChart
    private lateinit var cardConsumptionAnalysis: CardView
    private lateinit var cardMonthlyPanel       : CardView
    private lateinit var monthlyPanelContent    : LinearLayout

    // الأشهر المختارة للـ quarterly (افتراضي: آخر 3 أشهر)
    private var selectedMonths: MutableList<Pair<Int, Int>> = mutableListOf()
    private var currentTab = "DAILY"

    private val monthNames = arrayOf(
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.water_daily_statistics)
        bindViews()
        setupTabListeners()

        // افتراضي: آخر 3 أشهر
        selectedMonths = (2 downTo 0).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
            Pair(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
        }.toMutableList()

        barChart.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                barChart.viewTreeObserver.removeOnGlobalLayoutListener(this)
                selectTab("DAILY")
            }
        })
    }

    private fun bindViews() {
        btnDaily                = findViewById(R.id.btnDaily)
        btnWeekly               = findViewById(R.id.btnWeekly)
        btnMonthly              = findViewById(R.id.btnMonthly)
        tvTotalAmount           = findViewById(R.id.tvTotalAmount)
        tvGoalCompletion        = findViewById(R.id.tvGoalCompletion)
        tvDailyAverage          = findViewById(R.id.tvDailyAverage)
        barChart                = findViewById(R.id.barChart)
        lineChart               = findViewById(R.id.lineChart)
        cardConsumptionAnalysis = findViewById(R.id.cardConsumptionAnalysis)
        cardMonthlyPanel        = findViewById(R.id.cardMonthlyPanel)
        monthlyPanelContent     = findViewById(R.id.monthlyPanelContent)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupTabListeners() {
        btnDaily.setOnClickListener   { selectTab("DAILY") }
        btnWeekly.setOnClickListener  { selectTab("WEEKLY") }
        btnMonthly.setOnClickListener { selectTab("MONTHLY") }
    }

    private fun selectTab(tab: String) {
        currentTab = tab
        updateTabStyles(tab)
        val isMonthly = tab == "MONTHLY"
        cardConsumptionAnalysis.visibility = if (isMonthly) View.GONE   else View.VISIBLE
        cardMonthlyPanel.visibility        = if (isMonthly) View.VISIBLE else View.GONE
        when (tab) {
            "DAILY"   -> loadDailyData()
            "WEEKLY"  -> loadWeeklyData()
            "MONTHLY" -> loadMonthlyData()
        }
    }

    private fun updateTabStyles(selected: String) {
        mapOf(btnDaily to "DAILY", btnWeekly to "WEEKLY", btnMonthly to "MONTHLY")
            .forEach { (tv, tab) ->
                val sel = selected == tab
                tv.setBackgroundResource(if (sel) R.drawable.bg_tab_selected else android.R.color.transparent)
                tv.setTextColor(if (sel) Color.WHITE else Color.parseColor("#666666"))
            }
    }

    // ── Daily ─────────────────────────────────────────────────────────────────
    private fun loadDailyData() {
        lifecycleScope.launch {
            val stats = WaterTrackerRepository.getDailyStats() ?: return@launch

            tvTotalAmount.text        = "${stats.totalMl} ml"
            tvGoalCompletion.text     = "${stats.progressPercent}%"
            tvDailyAverage.visibility = View.VISIBLE

            // حساب متوسط 7 أيام من الـ home data
            val home = WaterTrackerRepository.getHomeData()
            val avg7 = home?.dailyAverageMl7Days ?: 0
            tvDailyAverage.text = if (avg7 >= 1000) "${"%.1f".format(avg7 / 1000f)}L" else "$avg7 ml"

            showBar()
            if (stats.hourlyBreakdown.isEmpty()) {
                barChart.clear(); barChart.invalidate()
            } else {
                val entries = stats.hourlyBreakdown.mapIndexed { i, e ->
                    BarEntry(i.toFloat(), e.amountMl.toFloat())
                }
                val labels = stats.hourlyBreakdown.map { it.label }
                drawBar(entries, labels, isMonthly = false, goalLine = stats.goalMl.toFloat())
            }
        }
    }

    // ── Weekly ────────────────────────────────────────────────────────────────
    private fun loadWeeklyData() {
        lifecycleScope.launch {
            val stats = WaterTrackerRepository.getWeeklyStats() ?: return@launch

            tvTotalAmount.text        = "${stats.totalMl} ml"
            tvGoalCompletion.text     = "${stats.goalCompletionPercent}%"
            tvDailyAverage.visibility = View.GONE

            showLine()
            if (stats.days.isEmpty()) {
                lineChart.clear(); lineChart.invalidate()
            } else {
                val entries = stats.days.mapIndexed { i, d ->
                    Entry(i.toFloat(), d.amountMl.toFloat())
                }
                val labels = stats.days.map { it.label }
                drawLine(entries, labels, stats.goalMl.toFloat())
            }
        }
    }

    // ── Monthly ───────────────────────────────────────────────────────────────
    private fun loadMonthlyData() {
        lifecycleScope.launch {
            val stats = WaterTrackerRepository.getMonthlyStats() ?: return@launch

            tvTotalAmount.text        = "${stats.totalMl} ml"
            tvGoalCompletion.text     = "${stats.goalCompletionPercent}%"
            tvDailyAverage.visibility = View.GONE

            refreshMonthlyPanel()
        }
    }

    // ── Monthly Panel (Quarterly) ─────────────────────────────────────────────
    private fun openMonthPicker() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear downTo currentYear - 4).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Year")
            .setItems(years) { _, yi ->
                val chosenYear = years[yi].toInt()
                val checked = BooleanArray(12) { idx ->
                    selectedMonths.any { it.first == chosenYear && it.second == idx }
                }
                AlertDialog.Builder(this)
                    .setTitle("Select up to 3 Months ($chosenYear)")
                    .setMultiChoiceItems(monthNames, checked) { _, which, isChecked -> checked[which] = isChecked }
                    .setPositiveButton("Apply") { _, _ ->
                        val picked = (0..11).filter { checked[it] }
                        when {
                            picked.isEmpty() -> Toast.makeText(this, "Select at least 1 month", Toast.LENGTH_SHORT).show()
                            picked.size > 3  -> Toast.makeText(this, "Select at most 3 months", Toast.LENGTH_SHORT).show()
                            else -> {
                                selectedMonths = picked.map { Pair(chosenYear, it) }.toMutableList()
                                refreshMonthlyPanel()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null).show()
            }.show()
    }

    private fun refreshMonthlyPanel() {
        lifecycleScope.launch {
            // جيب بيانات الأشهر المختارة من السيرفر
            val monthPairs = selectedMonths.map { MonthPair(it.first, it.second) }
            val quarterly  = WaterTrackerRepository.getCustomQuarterlyStats(monthPairs)
                ?: WaterTrackerRepository.getQuarterlyStats()
                ?: return@launch

            val data = quarterly.months
            val goal = WaterTrackerRepository.getDailyGoal()

            monthlyPanelContent.removeAllViews()

            // Header
            val headerRow = LinearLayout(this@WaterStatisticsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(48, 40, 48, 8)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            headerRow.addView(TextView(this@WaterStatisticsActivity).apply {
                text      = "📊  Consumption Analysis"
                textSize  = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1A1A2E"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            headerRow.addView(TextView(this@WaterStatisticsActivity).apply {
                text       = "Change ▾"
                textSize   = 13f
                setTextColor(Color.parseColor("#8BC34A"))
                setTypeface(null, Typeface.BOLD)
                setPadding(24, 16, 24, 16)
                background = roundBg(Color.parseColor("#F0F7E6"), 32f)
                setOnClickListener { openMonthPicker() }
            })
            monthlyPanelContent.addView(headerRow)

            monthlyPanelContent.addView(TextView(this@WaterStatisticsActivity).apply {
                text     = data.joinToString("  •  ") { it.monthLabel }
                textSize = 12f
                setTextColor(Color.parseColor("#999999"))
                setPadding(48, 0, 48, 24)
            })

            monthlyPanelContent.addView(buildQuarterlyBarChart(data, goal))

            val colors = listOf("#4CAF50", "#2196F3", "#FF9800")
            data.forEachIndexed { i, m ->
                monthlyPanelContent.addView(buildMonthCard(m, goal, colors.getOrElse(i) { "#607D8B" }))
            }
            monthlyPanelContent.addView(buildSummaryCard(data, goal))
            cardMonthlyPanel.visibility = View.VISIBLE
        }
    }

    // ── Chart Builders ────────────────────────────────────────────────────────

    private fun buildQuarterlyBarChart(data: List<QuarterlyMonthData>, goal: Int): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val barColors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#2196F3"), Color.parseColor("#FF9800"))
        val entries   = data.mapIndexed { i, m -> BarEntry(i.toFloat(), m.totalMl.toFloat()) }
        val labels    = data.map { it.monthLabel }
        val maxVal    = data.maxOfOrNull { it.totalMl.toFloat() } ?: 0f
        val mGoal     = goal * 30f
        val axisMax   = maxOf(maxVal, mGoal) * 1.3f

        val dataSet = BarDataSet(entries, "").apply {
            colors = barColors.take(data.size)
            setDrawValues(true); valueTextSize = 10f; valueTextColor = Color.parseColor("#555555")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    if (value >= 1000f) "${"%.1f".format(value / 1000f)}L" else "${value.toInt()}ml"
            }
        }
        val chart = BarChart(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 520).apply { setMargins(0, 8, 0, 8) }
            description.isEnabled = false; legend.isEnabled = false
            setDrawGridBackground(false); setTouchEnabled(false); setScaleEnabled(false)
            axisRight.isEnabled = false; setExtraTopOffset(16f); setExtraBottomOffset(8f)
            axisLeft.apply {
                axisMinimum = 0f; axisMaximum = if (axisMax <= 0f) 10000f else axisMax
                setDrawGridLines(true); gridColor = Color.parseColor("#F0F0F0")
                textColor = Color.parseColor("#999999"); textSize = 10f
                if (goal > 0) {
                    removeAllLimitLines()
                    addLimitLine(LimitLine(mGoal, "Monthly Goal").apply {
                        lineColor = Color.parseColor("#FF5252"); lineWidth = 1.5f
                        enableDashedLine(10f, 5f, 0f); textColor = Color.parseColor("#FF5252")
                        textSize = 9f; labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    })
                }
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM; valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f; isGranularityEnabled = true; setDrawGridLines(false)
                textColor = Color.parseColor("#555555"); textSize = 11f; labelCount = labels.size
            }
            this.data = BarData(dataSet).apply { barWidth = 0.55f }
            Handler(Looper.getMainLooper()).postDelayed({
                this.data.notifyDataChanged(); notifyDataSetChanged(); animateY(600); invalidate()
            }, 120)
        }
        container.addView(chart)
        return container
    }

    private fun buildMonthCard(m: QuarterlyMonthData, goal: Int, colorHex: String): View {
        val color   = Color.parseColor(colorHex)
        val lightBg = blendWithWhite(color, 0.08f)
        val goalPct = if (m.totalDays > 0 && goal > 0) minOf(m.totalMl * 100 / (goal * m.totalDays), 100) else 0
        val card = CardView(this).apply {
            radius = 40f; cardElevation = 2f; setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 8, 32, 8) }
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 32, 40, 32) }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        topRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(18, 18).apply { setMargins(0, 0, 16, 0) }; background = circleBg(color) })
        topRow.addView(TextView(this).apply { text = m.monthLabel; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1A1A2E")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        topRow.addView(TextView(this).apply { text = "$goalPct% goal"; textSize = 12f; setTextColor(color); setTypeface(null, Typeface.BOLD); setPadding(20, 8, 20, 8); background = roundBg(lightBg, 28f) })
        inner.addView(topRow)
        val track = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20).apply { setMargins(0, 16, 0, 0) }
            background = roundBg(Color.parseColor("#F0F0F0"), 10f)
        }
        track.addView(View(this).apply {
            background = roundBg(color, 10f)
            layoutParams = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
            scaleX = goalPct.coerceIn(0, 100) / 100f; pivotX = 0f
        })
        inner.addView(track)
        val statsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 } }
        val totalStr = if (m.totalMl >= 1000) "${"%.1f".format(m.totalMl / 1000f)} L" else "${m.totalMl} ml"
        val avgStr   = if (m.avgPerDay >= 1000) "${"%.1f".format(m.avgPerDay / 1000f)} L" else "${m.avgPerDay} ml"
        statsRow.addView(statItem("💧 Total", totalStr)); statsRow.addView(statItem("📅 Avg/Day", avgStr)); statsRow.addView(statItem("🎯 Days Met", "${m.daysMetGoal}/${m.totalDays}"))
        inner.addView(statsRow); card.addView(inner)
        return card
    }

    private fun buildSummaryCard(data: List<QuarterlyMonthData>, goal: Int): View {
        val total3       = data.sumOf { it.totalMl }
        val avg3         = if (data.isNotEmpty()) total3 / data.size else 0
        val totalGoalAll = data.sumOf { goal * it.totalDays }
        val overallPct   = if (totalGoalAll > 0) minOf(total3 * 100 / totalGoalAll, 100) else 0
        val bestMonth    = data.filter { it.totalMl > 0 }.maxByOrNull { it.totalMl }
        val card = CardView(this).apply {
            radius = 40f; cardElevation = 2f; setCardBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 8, 32, 40) }
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 36, 40, 36) }
        inner.addView(TextView(this).apply { text = "📈  Period Summary"; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 } })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
        val totalStr = if (total3 >= 1000) "${"%.1f".format(total3 / 1000f)} L" else "$total3 ml"
        val avgStr   = if (avg3   >= 1000) "${"%.1f".format(avg3 / 1000f)} L"   else "$avg3 ml"
        row.addView(sumItem("Total", totalStr)); row.addView(sumItem("Avg/Month", avgStr)); row.addView(sumItem("Overall Goal", "$overallPct%"))
        inner.addView(row)
        if (bestMonth != null) inner.addView(TextView(this).apply { text = "🏆  Best: ${bestMonth.monthLabel}"; textSize = 12f; setTextColor(Color.parseColor("#C3E66E")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 } })
        card.addView(inner)
        return card
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun statItem(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(this@WaterStatisticsActivity).apply { text = value; textSize = 14f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1A1A2E")) })
        addView(TextView(this@WaterStatisticsActivity).apply { text = label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#999999")) })
    }

    private fun sumItem(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(this@WaterStatisticsActivity).apply { text = value; textSize = 16f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        addView(TextView(this@WaterStatisticsActivity).apply { text = label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#AAAAAA")) })
    }

    private fun showBar()  { barChart.visibility = View.VISIBLE;  lineChart.visibility = View.GONE }
    private fun showLine() { lineChart.visibility = View.VISIBLE; barChart.visibility  = View.GONE }

    private fun drawBar(entries: List<BarEntry>, labels: List<String>, isMonthly: Boolean, goalLine: Float = 0f) {
        val maxVal  = entries.maxOfOrNull { it.y } ?: 0f
        val axisMax = if (maxVal <= 0f) 1000f else maxOf(maxVal, goalLine) * 1.3f
        val dataSet = BarDataSet(entries, "").apply { color = Color.parseColor("#C3E66E"); setDrawValues(false) }
        barChart.apply {
            clear(); description.isEnabled = false; legend.isEnabled = false
            setDrawGridBackground(false); setTouchEnabled(false); setPinchZoom(false); setScaleEnabled(false); axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f; axisMaximum = axisMax; setDrawGridLines(true)
                gridColor = Color.parseColor("#F0F0F0"); textColor = Color.parseColor("#999999"); textSize = 10f
                if (goalLine > 0f) {
                    removeAllLimitLines()
                    addLimitLine(LimitLine(goalLine, "Goal").apply {
                        lineColor = Color.parseColor("#FF7043"); lineWidth = 1.5f
                        textColor = Color.parseColor("#FF7043"); textSize = 9f
                        labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    })
                }
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM; granularity = 1f; isGranularityEnabled = true
                setDrawGridLines(false); textColor = Color.parseColor("#999999")
                if (isMonthly) {
                    valueFormatter = IndexAxisValueFormatter(labels.mapIndexed { i, l -> if (i % 5 == 0) l else "" })
                    textSize = 9f; labelRotationAngle = -45f; labelCount = labels.size
                } else {
                    valueFormatter = IndexAxisValueFormatter(labels)
                    textSize = 10f; labelRotationAngle = 0f; labelCount = labels.size
                }
            }
            setExtraBottomOffset(if (isMonthly) 16f else 4f)
            data = BarData(dataSet).apply { barWidth = if (isMonthly) 0.5f else 0.6f }
            Handler(Looper.getMainLooper()).postDelayed({
                data.notifyDataChanged(); notifyDataSetChanged(); animateY(500); invalidate()
            }, 100)
        }
    }

    private fun drawLine(entries: List<Entry>, labels: List<String>, goalLine: Float = 0f) {
        val maxVal  = entries.maxOfOrNull { it.y } ?: 0f
        val axisMax = if (maxVal <= 0f) 1000f else maxOf(maxVal, goalLine) * 1.3f
        val dataSet = LineDataSet(entries, "").apply {
            color = Color.parseColor("#8BC34A"); lineWidth = 2.5f; circleRadius = 4f
            setCircleColor(Color.parseColor("#C3E66E")); setDrawCircleHole(false); setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR; setDrawFilled(true); fillColor = Color.parseColor("#C3E66E"); fillAlpha = 50
        }
        lineChart.apply {
            clear(); description.isEnabled = false; legend.isEnabled = false
            setDrawGridBackground(false); setTouchEnabled(false); setPinchZoom(false); setScaleEnabled(false); axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f; axisMaximum = axisMax; setDrawGridLines(true)
                gridColor = Color.parseColor("#F0F0F0"); textColor = Color.parseColor("#999999"); textSize = 10f
                if (goalLine > 0f) {
                    removeAllLimitLines()
                    addLimitLine(LimitLine(goalLine, "Daily Goal").apply {
                        lineColor = Color.parseColor("#FF7043"); lineWidth = 1.5f
                        textColor = Color.parseColor("#FF7043"); textSize = 9f
                        labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    })
                }
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM; valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f; isGranularityEnabled = true; setDrawGridLines(false)
                textColor = Color.parseColor("#999999"); textSize = 11f; labelCount = labels.size
                axisMinimum = 0f; axisMaximum = (labels.size - 1).toFloat()
            }
            setExtraLeftOffset(8f); setExtraRightOffset(8f); setExtraBottomOffset(4f)
            data = LineData(dataSet)
            Handler(Looper.getMainLooper()).postDelayed({
                data.notifyDataChanged(); notifyDataSetChanged(); animateX(500); invalidate()
            }, 100)
        }
    }

    private fun roundBg(color: Int, r: Float) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE; cornerRadius = r; setColor(color)
    }
    private fun circleBg(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color)
    }
    private fun blendWithWhite(color: Int, alpha: Float): Int {
        val r = (Color.red(color)   * alpha + 255 * (1 - alpha)).toInt()
        val g = (Color.green(color) * alpha + 255 * (1 - alpha)).toInt()
        val b = (Color.blue(color)  * alpha + 255 * (1 - alpha)).toInt()
        return Color.rgb(r, g, b)
    }

}