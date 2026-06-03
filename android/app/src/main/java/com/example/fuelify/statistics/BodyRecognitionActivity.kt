package com.example.fuelify.statistics

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

class BodyRecognitionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_URI  = "extra_photo_uri"
        const val EXTRA_FROM_TODAY = "extra_from_today"
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    private lateinit var btnBack          : ImageButton
    private lateinit var ivBodyAnatomy    : ImageView
    private lateinit var tvBodyFat        : TextView
    private lateinit var tvMuscleMass     : TextView
    private lateinit var tvWater          : TextView
    private lateinit var tvBMI            : TextView
    private lateinit var tvBodyType       : TextView
    private lateinit var btnSaveResult    : CardView

    private var photoUriString : String  = ""
    private var fromToday      : Boolean = false
    private lateinit var currentRecord   : BodyScanRecord

    private var todayAdapter: TodayRecordsAdapter? = null

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.body_recognition)

        photoUriString = intent.getStringExtra(EXTRA_PHOTO_URI) ?: ""
        fromToday      = intent.getBooleanExtra(EXTRA_FROM_TODAY, false)

        bindViews()
        loadData()
        setupClickListeners()
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private fun bindViews() {
        btnBack          = findViewById(R.id.btnBack)
        ivBodyAnatomy    = findViewById(R.id.ivBodyAnatomy)
        tvBodyFat        = findViewById(R.id.tvBodyFat)
        tvMuscleMass     = findViewById(R.id.tvMuscleMass)
        tvWater          = findViewById(R.id.tvWater)
        tvBMI            = findViewById(R.id.tvBMI)
        tvBodyType       = findViewById(R.id.tvBodyType)
        btnSaveResult    = findViewById(R.id.btnSaveResult)
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun loadData() {
        when {
            // Case A: new photo → analyse locally, show result, let user save
            photoUriString.isNotEmpty() -> {
                currentRecord = analysePhoto(photoUriString)
                renderRecord(currentRecord)
                btnSaveResult.visibility = View.VISIBLE
            }

            // Case B: View Today's Body Details → fetch from server
            fromToday -> {
                btnSaveResult.visibility = View.GONE
                lifecycleScope.launch {
                    val todayRecords = BodyScanRepository.getTodayRecords().toMutableList()
                    if (todayRecords.isEmpty()) {
                        Toast.makeText(
                            this@BodyRecognitionActivity,
                            "No body scan saved today.\nPlease take a photo first.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@launch
                    }
                    currentRecord  = todayRecords.first()
                    photoUriString = currentRecord.photoUri
                    renderRecord(currentRecord)
                    showTodayRecordsList(todayRecords)
                }
            }

            // Case C: Fallback → fetch latest from server
            else -> {
                btnSaveResult.visibility = View.GONE
                lifecycleScope.launch {
                    val latest = BodyScanRepository.getLatestRecord()
                    currentRecord  = latest ?: buildDefaultRecord("")
                    photoUriString = currentRecord.photoUri
                    renderRecord(currentRecord)
                }
            }
        }
    }

    // ── Today's records list (show + delete) ──────────────────────────────────

    private fun showTodayRecordsList(records: MutableList<BodyScanRecord>) {
        val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

        val card = CardView(this).apply {
            radius        = dpToPx(16).toFloat()
            cardElevation = dpToPx(4).toFloat()
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpToPx(16); it.bottomMargin = dpToPx(8) }
        }

        val tvTitle = TextView(this).apply {
            text     = "📋  Today's Scans"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(4))
        }

        val rv = RecyclerView(this).apply {
            layoutManager            = LinearLayoutManager(this@BodyRecognitionActivity)
            isNestedScrollingEnabled = false
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(8))
        }

        todayAdapter = TodayRecordsAdapter(
            records  = records,
            timeFmt  = timeFmt,
            onSelect = { selected ->
                currentRecord  = selected
                photoUriString = selected.photoUri
                renderRecord(selected)
            },
            onDelete = { record, position ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Scan")
                    .setMessage("Delete the scan saved at ${timeFmt.format(java.util.Date(record.timestamp))}?")
                    .setPositiveButton("Delete") { _, _ ->
                        // Delete on server
                        lifecycleScope.launch {
                            BodyScanRepository.deleteRecord(record.timestamp)
                        }
                        records.removeAt(position)
                        todayAdapter?.notifyItemRemoved(position)
                        tvTitle.text = "📋  Today's Scans (${records.size})"

                        if (record.timestamp == currentRecord.timestamp) {
                            if (records.isNotEmpty()) {
                                currentRecord  = records.first()
                                photoUriString = currentRecord.photoUri
                                renderRecord(currentRecord)
                            } else {
                                Toast.makeText(this, "All today's scans deleted.", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        rv.adapter = todayAdapter
        tvTitle.text = "📋  Today's Scans (${records.size})"

        val innerLL = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tvTitle)
            addView(rv)
        }
        card.addView(innerLL)

        val saveParent = btnSaveResult.parent as? ViewGroup ?: return
        saveParent.addView(card, saveParent.indexOfChild(btnSaveResult))
    }

    // ── Analyse (local, same logic as before) ─────────────────────────────────

    private fun analysePhoto(uriString: String): BodyScanRecord {
        val seed = abs(uriString.hashCode()).toLong()
        val rng  = Random(seed)

        val bodyFat    = 10.0 + rng.nextDouble() * 25.0
        val muscleMass = 40.0 + rng.nextDouble() * 30.0
        val water      = 50.0 + rng.nextDouble() * 15.0
        val bmi        = 17.5 + rng.nextDouble() * 15.0

        val bodyType = when {
            bodyFat < 15 && muscleMass > 60 -> "Athletic"
            bodyFat < 20                    -> "Fit"
            bodyFat < 25                    -> "Average"
            else                            -> "Overweight"
        }

        return BodyScanRecord(
            timestamp         = System.currentTimeMillis(),
            bodyFatPercent    = String.format("%.1f", bodyFat).toDouble(),
            muscleMassPercent = String.format("%.1f", muscleMass).toDouble(),
            waterPercent      = String.format("%.1f", water).toDouble(),
            bmi               = String.format("%.1f", bmi).toDouble(),
            bodyType          = bodyType,
            photoUri          = uriString
        )
    }

    private fun buildDefaultRecord(uri: String) = BodyScanRecord(
        bodyFatPercent = 14.8, muscleMassPercent = 65.2,
        waterPercent   = 59.3, bmi = 23.4,
        bodyType       = "Athletic", photoUri = uri
    )

    // ── Render ────────────────────────────────────────────────────────────────

    private fun renderRecord(record: BodyScanRecord) {
        tvBodyFat.text    = "${record.bodyFatPercent}%"
        tvMuscleMass.text = "${record.muscleMassPercent}%"
        tvWater.text      = "${record.waterPercent}%"
        tvBMI.text        = "${record.bmi}"
        tvBodyType.text   = record.bodyType

        val uriToShow = photoUriString.ifEmpty { record.photoUri }
        if (uriToShow.isNotEmpty()) {
            try {
                ivBodyAnatomy.setImageURI(null)
                ivBodyAnatomy.setImageURI(Uri.parse(uriToShow))
                ivBodyAnatomy.scaleType = ImageView.ScaleType.CENTER_CROP
            } catch (_: Exception) { }
        }
    }

    // ── Save → send to server ─────────────────────────────────────────────────

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        btnSaveResult.setOnClickListener {
            val toSave = currentRecord.copy(
                timestamp = System.currentTimeMillis(),
                photoUri  = photoUriString.ifEmpty { currentRecord.photoUri }
            )
            lifecycleScope.launch {
                val saved = BodyScanRepository.saveRecord(toSave)
                if (saved != null) {
                    Toast.makeText(
                        this@BodyRecognitionActivity,
                        "Body scan result saved!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@BodyRecognitionActivity,
                        "Saved locally — server unreachable.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                startActivity(Intent(this@BodyRecognitionActivity, BodyStatisticsActivity::class.java))
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    // ── Bottom nav ────────────────────────────────────────────────────────────



    // ── RecyclerView Adapter ──────────────────────────────────────────────────

    inner class TodayRecordsAdapter(
        private val records : MutableList<BodyScanRecord>,
        private val timeFmt : java.text.SimpleDateFormat,
        private val onSelect: (BodyScanRecord) -> Unit,
        private val onDelete: (BodyScanRecord, Int) -> Unit
    ) : RecyclerView.Adapter<TodayRecordsAdapter.VH>() {

        inner class VH(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
            val ivThumb : ImageView = row.findViewById(android.R.id.icon)
            val tvTime  : TextView  = row.findViewById(android.R.id.text1)
            val tvFat   : TextView  = row.findViewById(android.R.id.text2)
            val btnDel  : ImageButton = row.findViewById(android.R.id.closeButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )

                val thumb = ImageView(context).apply {
                    id          = android.R.id.icon
                    layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).also { it.marginEnd = dpToPx(12) }
                    scaleType   = ImageView.ScaleType.CENTER_CROP
                    background  = GradientDrawable().apply {
                        shape        = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#F5F5F5"))
                    }
                }

                val textCol = LinearLayout(context).apply {
                    orientation  = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val t1 = TextView(context).apply {
                        id        = android.R.id.text1
                        textSize  = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#212121"))
                    }
                    val t2 = TextView(context).apply {
                        id        = android.R.id.text2
                        textSize  = 11f
                        setTextColor(Color.parseColor("#757575"))
                    }
                    addView(t1)
                    addView(t2)
                }

                val delBtn = ImageButton(context).apply {
                    id         = android.R.id.closeButton
                    setImageResource(android.R.drawable.ic_menu_delete)
                    setColorFilter(Color.parseColor("#EF5350"))
                    background = null
                    layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
                }

                addView(thumb)
                addView(textCol)
                addView(delBtn)
            }
            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val rec = records[position]

            holder.tvTime.text = "Scan at ${timeFmt.format(java.util.Date(rec.timestamp))}"
            holder.tvFat.text  = "Body Fat: ${rec.bodyFatPercent}%  •  BMI: ${rec.bmi}"

            if (rec.photoUri.isNotEmpty()) {
                try {
                    holder.ivThumb.setImageURI(null)
                    holder.ivThumb.setImageURI(Uri.parse(rec.photoUri))
                } catch (_: Exception) {
                    holder.ivThumb.setImageResource(R.drawable.ic_camera)
                }
            } else {
                holder.ivThumb.setImageResource(R.drawable.ic_camera)
            }

            holder.row.setBackgroundColor(
                if (rec.timestamp == currentRecord.timestamp)
                    Color.parseColor("#F1F8E9")
                else
                    Color.WHITE
            )

            holder.row.setOnClickListener {
                onSelect(rec)
                notifyDataSetChanged()
            }

            holder.btnDel.setOnClickListener {
                onDelete(rec, holder.adapterPosition)
            }
        }

        override fun getItemCount() = records.size
    }
}