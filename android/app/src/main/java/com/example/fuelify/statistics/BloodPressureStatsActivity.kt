package com.example.fuelify.statistics

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.example.fuelify.R
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * BloodPressureStatsActivity
 *
 * Shared statistics screen driven by the EXTRA_TYPE intent extra.
 * TYPE_BLOOD_PRESSURE → blood_pressure_stats.xml
 * TYPE_BLOOD_SUGAR    → blood_sugar_stats.xml
 *
 * Data is fetched from the Ktor server via BloodPressureTrackerRepository.
 */
class BloodPressureStatsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE          = "extra_type"
        const val TYPE_BLOOD_PRESSURE = "blood_pressure"
        const val TYPE_BLOOD_SUGAR    = "blood_sugar"
    }

    private var displayType: String = TYPE_BLOOD_PRESSURE

    // ── Common Views ──────────────────────────────────────────────────────────
    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var spinnerMonth: Spinner

    // ── Monthly Overview ──────────────────────────────────────────────────────
    private lateinit var tvAverageValue: TextView
    private lateinit var tvAverageLabel: TextView

    // ── Trend indicators (BP) ─────────────────────────────────────────────────
    private var ivSystolicTrend: ImageView?  = null
    private var tvSystolicChange: TextView?  = null
    private var ivDiastolicTrend: ImageView? = null
    private var tvDiastolicChange: TextView? = null

    // ── Trend indicators (BS) ─────────────────────────────────────────────────
    private var ivFastingTrend: ImageView?   = null
    private var tvFastingChange: TextView?   = null
    private var ivAfterMealTrend: ImageView? = null
    private var tvAfterMealChange: TextView? = null

    // ── Recent Readings grid ──────────────────────────────────────────────────
    private lateinit var tvLatestReading: TextView
    private lateinit var tvLatestReadingUnit: TextView
    private lateinit var tvLatestReadingTime: TextView

    private lateinit var tvAverageWeek: TextView
    private lateinit var tvAverageWeekUnit: TextView
    private lateinit var tvAverageWeekTime: TextView

    private lateinit var tvHighest: TextView
    private lateinit var tvHighestUnit: TextView
    private lateinit var tvHighestTime: TextView

    private lateinit var tvLowest: TextView
    private lateinit var tvLowestUnit: TextView
    private lateinit var tvLowestTime: TextView

    // ── Month spinner state ───────────────────────────────────────────────────
    private val monthNames: List<String> by lazy {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        (0 until 12).map { i ->
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -i)
            sdf.format(c.time)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        displayType = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_BLOOD_PRESSURE

        if (displayType == TYPE_BLOOD_PRESSURE) {
            setContentView(R.layout.blood_pressure_stats)
        } else {
            setContentView(R.layout.blood_sugar_stats)
        }

        bindViews()
        setupMonthSpinner()
        refreshStats(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH)
        )
    }

    // ── View Binding ──────────────────────────────────────────────────────────

    private fun bindViews() {
        btnBack          = findViewById(R.id.btnBack)
        tvTitle          = findViewById(R.id.tvTitle)
        spinnerMonth     = findViewById(R.id.spinnerMonth)

        tvAverageValue = findViewById(R.id.tvAverageValue)
        tvAverageLabel = findViewById(R.id.tvAverageLabel)

        if (displayType == TYPE_BLOOD_PRESSURE) {
            tvTitle.text      = "Blood Pressure Stats"
            ivSystolicTrend   = findViewById(R.id.ivSystolicTrend)
            tvSystolicChange  = findViewById(R.id.tvSystolicChange)
            ivDiastolicTrend  = findViewById(R.id.ivDiastolicTrend)
            tvDiastolicChange = findViewById(R.id.tvDiastolicChange)
            tvHighest         = findViewById(R.id.tvHighestSystolic)
        } else {
            tvTitle.text      = "Blood Sugar Stats"
            ivFastingTrend    = findViewById(R.id.ivFastingTrend)
            tvFastingChange   = findViewById(R.id.tvFastingChange)
            ivAfterMealTrend  = findViewById(R.id.ivAfterMealTrend)
            tvAfterMealChange = findViewById(R.id.tvAfterMealChange)
            tvHighest         = findViewById(R.id.tvHighestReading)
        }

        tvLatestReading     = findViewById(R.id.tvLatestReading)
        tvLatestReadingUnit = findViewById(R.id.tvLatestReadingUnit)
        tvLatestReadingTime = findViewById(R.id.tvLatestReadingTime)

        tvAverageWeek     = findViewById(R.id.tvAverageWeek)
        tvAverageWeekUnit = findViewById(R.id.tvAverageWeekUnit)
        tvAverageWeekTime = findViewById(R.id.tvAverageWeekTime)

        tvHighestUnit = findViewById(R.id.tvHighestUnit)
        tvHighestTime = findViewById(R.id.tvHighestTime)

        tvLowest     = findViewById(R.id.tvLowestReading)
        tvLowestUnit = findViewById(R.id.tvLowestUnit)
        tvLowestTime = findViewById(R.id.tvLowestTime)

        btnBack.setOnClickListener { finish() }
    }

    // ── Month Spinner ─────────────────────────────────────────────────────────

    private fun setupMonthSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = adapter

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -pos)
                refreshStats(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Data Refresh ──────────────────────────────────────────────────────────

    private fun refreshStats(year: Int, month: Int) {
        if (displayType == TYPE_BLOOD_PRESSURE) {
            refreshBpStats(year, month)
        } else {
            refreshBsStats(year, month)
        }
    }

    // ── Blood Pressure Stats (from API) ───────────────────────────────────────

    private fun refreshBpStats(year: Int, month: Int) {
        lifecycleScope.launch {
            val stats = BloodPressureTrackerRepository.getBpStats(year, month)

            if (stats != null && stats.monthlyAvgSystolic != null && stats.monthlyAvgDiastolic != null) {
                tvAverageValue.text = "${stats.monthlyAvgSystolic}/${stats.monthlyAvgDiastolic}"
                tvAverageLabel.text = "Monthly Average (mmHg)"
            } else {
                tvAverageValue.text = "--/--"
                tvAverageLabel.text = "No data for this month"
            }

            // Trends
            applyTrendView(ivSystolicTrend,  tvSystolicChange,  stats?.systolicTrend)
            applyTrendView(ivDiastolicTrend, tvDiastolicChange, stats?.diastolicTrend)

            // Latest reading
            val latest = stats?.latestReading
            if (latest != null) {
                tvLatestReading.text     = "${latest.systolic}/${latest.diastolic}"
                tvLatestReadingUnit.text = "mmHg"
                tvLatestReadingTime.text = latest.formattedTime
            } else {
                tvLatestReading.text     = "--/--"
                tvLatestReadingUnit.text = "mmHg"
                tvLatestReadingTime.text = "No readings yet"
            }

            // 7-day average
            if (stats?.weeklyAvgSystolic != null && stats.weeklyAvgDiastolic != null) {
                tvAverageWeek.text     = "${stats.weeklyAvgSystolic}/${stats.weeklyAvgDiastolic}"
                tvAverageWeekUnit.text = "mmHg"
                tvAverageWeekTime.text = "Last 7 days"
            } else {
                tvAverageWeek.text     = "--/--"
                tvAverageWeekUnit.text = "mmHg"
                tvAverageWeekTime.text = "No data"
            }

            // Highest
            val highest = stats?.highestReading
            if (highest != null) {
                tvHighest.text     = "${highest.systolic}/${highest.diastolic}"
                tvHighestUnit.text = "mmHg"
                tvHighestTime.text = highest.formattedTime
            } else {
                tvHighest.text     = "--/--"
                tvHighestUnit.text = "mmHg"
                tvHighestTime.text = "No data"
            }

            // Lowest
            val lowest = stats?.lowestReading
            if (lowest != null) {
                tvLowest.text     = "${lowest.systolic}/${lowest.diastolic}"
                tvLowestUnit.text = "mmHg"
                tvLowestTime.text = lowest.formattedTime
            } else {
                tvLowest.text     = "--/--"
                tvLowestUnit.text = "mmHg"
                tvLowestTime.text = "No data"
            }
        }
    }

    // ── Blood Sugar Stats (from API) ──────────────────────────────────────────

    private fun refreshBsStats(year: Int, month: Int) {
        lifecycleScope.launch {
            val stats = BloodPressureTrackerRepository.getBsStats(year, month)

            if (stats?.monthlyAvg != null) {
                tvAverageValue.text = "${stats.monthlyAvg}"
                tvAverageLabel.text = "Monthly Average (mg/dL)"
            } else {
                tvAverageValue.text = "--"
                tvAverageLabel.text = "No data for this month"
            }

            // Trends
            applyTrendView(ivFastingTrend,   tvFastingChange,   stats?.fastingTrend)
            applyTrendView(ivAfterMealTrend, tvAfterMealChange, stats?.afterMealTrend)

            // Latest reading
            val latest = stats?.latestReading
            if (latest != null) {
                tvLatestReading.text     = "${latest.glucose}"
                tvLatestReadingUnit.text = "mg/dL"
                tvLatestReadingTime.text = latest.formattedTime
            } else {
                tvLatestReading.text     = "--"
                tvLatestReadingUnit.text = "mg/dL"
                tvLatestReadingTime.text = "No readings yet"
            }

            // 7-day average
            if (stats?.weeklyAvg != null) {
                tvAverageWeek.text     = "${stats.weeklyAvg}"
                tvAverageWeekUnit.text = "mg/dL"
                tvAverageWeekTime.text = "Last 7 days"
            } else {
                tvAverageWeek.text     = "--"
                tvAverageWeekUnit.text = "mg/dL"
                tvAverageWeekTime.text = "No data"
            }

            // Highest
            val highest = stats?.highestReading
            if (highest != null) {
                tvHighest.text     = "${highest.glucose}"
                tvHighestUnit.text = "mg/dL"
                tvHighestTime.text = highest.formattedTime
            } else {
                tvHighest.text     = "--"
                tvHighestUnit.text = "mg/dL"
                tvHighestTime.text = "No data"
            }

            // Lowest
            val lowest = stats?.lowestReading
            if (lowest != null) {
                tvLowest.text     = "${lowest.glucose}"
                tvLowestUnit.text = "mg/dL"
                tvLowestTime.text = lowest.formattedTime
            } else {
                tvLowest.text     = "--"
                tvLowestUnit.text = "mg/dL"
                tvLowestTime.text = "No data"
            }
        }
    }

    // ── Trend Helper ──────────────────────────────────────────────────────────

    private fun applyTrendView(arrow: ImageView?, label: TextView?, trend: BpTrendInfo?) {
        if (arrow == null || label == null || trend == null) return
        when (trend.direction) {
            "UP"   -> arrow.setImageResource(R.drawable.ic_trending_up)
            "DOWN" -> arrow.setImageResource(R.drawable.ic_trending_down)
            else   -> arrow.setImageResource(R.drawable.ic_trending_flat)
        }
        arrow.setColorFilter(Color.parseColor(trend.color))
        label.text = trend.delta
        label.setTextColor(Color.parseColor(trend.color))
    }
}