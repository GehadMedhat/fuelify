package com.example.fuelify.statistics

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.R
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class WaterIntakeActivity : AppCompatActivity() {

    private lateinit var etWaterAmount : EditText
    private lateinit var btnAddWater   : AppCompatButton
    private lateinit var btn250ml      : LinearLayout
    private lateinit var btn500ml      : LinearLayout
    private lateinit var btn750ml      : LinearLayout
    private lateinit var btn1000ml     : LinearLayout
    private lateinit var rvLogs        : RecyclerView
    private lateinit var logsAdapter   : LogsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.water_intake)
        bindViews()
        setupClickListeners()
        setupLogsRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun bindViews() {
        etWaterAmount = findViewById(R.id.etWaterAmount)
        btnAddWater   = findViewById(R.id.btnAddWater)
        btn250ml      = findViewById(R.id.btn250ml)
        btn500ml      = findViewById(R.id.btn500ml)
        btn750ml      = findViewById(R.id.btn750ml)
        btn1000ml     = findViewById(R.id.btn1000ml)
        rvLogs        = findViewById(R.id.rvLogs)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        btnAddWater.setOnClickListener { addWater() }
        btn250ml.setOnClickListener    { quickAdd(250) }
        btn500ml.setOnClickListener    { quickAdd(500) }
        btn750ml.setOnClickListener    { quickAdd(750) }
        btn1000ml.setOnClickListener   { quickAdd(1000) }
    }

    private fun addWater() {
        val text = etWaterAmount.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = text.toIntOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        commitAdd(amount)
        etWaterAmount.setText("")
    }

    private fun quickAdd(ml: Int) {
        commitAdd(ml)
    }

    private fun commitAdd(ml: Int) {
        lifecycleScope.launch {
            val log = WaterTrackerRepository.addLog(ml)
            if (log != null) {
                Toast.makeText(this@WaterIntakeActivity, "+$ml ml added!", Toast.LENGTH_SHORT).show()
                refreshLogs()
                rvLogs.scrollToPosition(0)
            }
        }
    }

    private fun refreshLogs() {
        lifecycleScope.launch {
            val logs = WaterTrackerRepository.getTodayLogs()
            logsAdapter.setLogs(logs.toMutableList())
        }
    }

    private fun setupLogsRecyclerView() {
        logsAdapter = LogsAdapter()
        rvLogs.layoutManager = LinearLayoutManager(this)
        rvLogs.adapter = logsAdapter
        rvLogs.isNestedScrollingEnabled = true
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    inner class LogsAdapter : RecyclerView.Adapter<LogsAdapter.LogViewHolder>() {

        private var logs: MutableList<WaterLog> = mutableListOf()

        fun setLogs(newLogs: MutableList<WaterLog>) {
            logs = newLogs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_water_log, parent, false)
            return LogViewHolder(view)
        }

        override fun getItemCount() = logs.size

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) =
            holder.bind(logs[position])

        inner class LogViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            private val tvAmount  : TextView  = v.findViewById(R.id.tvLogAmount)
            private val tvTime    : TextView  = v.findViewById(R.id.tvLogTime)
            private val btnDelete : ImageView = v.findViewById(R.id.btnDeleteLog)

            fun bind(log: WaterLog) {
                tvAmount.text = "${log.amountMl} ml"
                tvTime.text   = log.timeFormatted

                btnDelete.setOnClickListener {
                    lifecycleScope.launch {
                        WaterTrackerRepository.deleteLog(log.timestamp)
                        refreshLogs()
                        Toast.makeText(itemView.context, "Log deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}