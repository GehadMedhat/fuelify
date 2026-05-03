package com.example.fuelify.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelify.R
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.*
import com.example.fuelify.utils.UserPreferences
import kotlinx.coroutines.*


class DoctorConsultationActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var userId = -1
    private var currentCaseId = -1

    // ── Screens ───────────────────────────────────────────────────────────────
    private lateinit var screenList:    LinearLayout  // cases list + new case form
    private lateinit var screenChat:    LinearLayout  // chat for a selected case

    // ── List screen views ─────────────────────────────────────────────────────
    private lateinit var containerCases:    LinearLayout
    private lateinit var tvNoCases:         TextView
    private lateinit var btnNewCaseToggle:  LinearLayout
    private lateinit var layoutNewCaseForm: LinearLayout

    // ── New case form ─────────────────────────────────────────────────────────
    private lateinit var etCondition:   EditText
    private lateinit var etArea:        EditText
    private lateinit var etSymptoms:    EditText
    private lateinit var etLimitations: EditText

    // ── Chat screen views ─────────────────────────────────────────────────────
    private lateinit var tvChatTitle:         TextView
    private lateinit var tvChatStatus:        TextView
    private lateinit var tvChatCondition:     TextView
    private lateinit var tvChatArea:          TextView
    private lateinit var containerMessages:   LinearLayout
    private lateinit var etMessage:           EditText
    private lateinit var btnSend:             LinearLayout
    private lateinit var btnAcknowledge:      LinearLayout
    private lateinit var btnCloseCase:        LinearLayout
    private lateinit var btnViewAdjustments:  LinearLayout
    private lateinit var containerFileChips:  LinearLayout

    private val attachedFileNames = mutableListOf<String>()
    private var filePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val name = getFileName(uri)
            if (name != null && !attachedFileNames.contains(name)) attachedFileNames.add(name)
        }
        updateFileChips()
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_consultation)
        userId = UserPreferences.getUserId(this)
        bindViews()
        showListScreen()
        loadCasesList()
    }

    private fun bindViews() {
        screenList    = findViewById(R.id.screenList)
        screenChat    = findViewById(R.id.screenChat)

        // list screen
        containerCases    = findViewById(R.id.containerCases)
        tvNoCases         = findViewById(R.id.tvNoCases)
        btnNewCaseToggle  = findViewById(R.id.btnNewCaseToggle)
        layoutNewCaseForm = findViewById(R.id.layoutNewCaseForm)
        etCondition       = findViewById(R.id.etNewCondition)
        etArea            = findViewById(R.id.etNewArea)
        etSymptoms        = findViewById(R.id.etNewSymptoms)
        etLimitations     = findViewById(R.id.etNewLimitations)

        // chat screen
        tvChatTitle        = findViewById(R.id.tvChatTitle)
        tvChatStatus       = findViewById(R.id.tvChatStatus)
        tvChatCondition    = findViewById(R.id.tvChatCondition)
        tvChatArea         = findViewById(R.id.tvChatArea)
        containerMessages  = findViewById(R.id.containerMessages)
        etMessage          = findViewById(R.id.etConsultMessage)
        btnSend            = findViewById(R.id.btnSendMessage)
        btnAcknowledge     = findViewById(R.id.btnAcknowledgeMedical)
        btnCloseCase       = findViewById(R.id.btnCloseCase)
        btnViewAdjustments = findViewById(R.id.btnViewAdjustments)
        containerFileChips = findViewById(R.id.containerFileChips)

        // back buttons
        findViewById<ImageButton>(R.id.btnConsultBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnChatBack).setOnClickListener { showListScreen(); loadCasesList() }

        // toggle new case form
        btnNewCaseToggle.setOnClickListener {
            val visible = layoutNewCaseForm.visibility == View.VISIBLE
            layoutNewCaseForm.visibility = if (visible) View.GONE else View.VISIBLE
            (btnNewCaseToggle.getChildAt(0) as? TextView)?.text =
                if (visible) "+ New Consultation" else "✕ Cancel"
        }

        // submit new case
        findViewById<LinearLayout>(R.id.btnSubmitCase).setOnClickListener { submitNewCase() }

        // chat actions
        btnSend.setOnClickListener { sendMessage() }
        findViewById<LinearLayout>(R.id.btnAttachFiles).setOnClickListener { filePickerLauncher.launch("*/*") }
        btnViewAdjustments.setOnClickListener { startActivity(Intent(this, SmartPlanActivity::class.java)) }
        btnAcknowledge.setOnClickListener { updateCase("acknowledged") }
        btnCloseCase.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Close Case?")
                .setMessage("Mark this consultation as complete (Recovery Complete)?")
                .setPositiveButton("Close") { _, _ -> updateCase("closed") }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<LinearLayout>(R.id.btnDownloadSummary).setOnClickListener { downloadSummary() }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun showListScreen() {
        screenList.visibility = View.VISIBLE
        screenChat.visibility = View.GONE
    }

    private fun showChatScreen(caseId: Int) {
        currentCaseId = caseId
        screenList.visibility = View.GONE
        screenChat.visibility = View.VISIBLE
        loadChatMessages(caseId)
    }

    // ── Cases list ────────────────────────────────────────────────────────────

    private fun loadCasesList() {
        containerCases.removeAllViews()
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getConsultationList(userId)
                }
                val cases = resp.body()?.data ?: emptyList()
                if (cases.isEmpty()) {
                    tvNoCases.visibility = View.VISIBLE
                } else {
                    tvNoCases.visibility = View.GONE
                    cases.forEach { summary -> containerCases.addView(buildCaseCard(summary)) }
                }
            } catch (e: Exception) {
                tvNoCases.visibility = View.VISIBLE
                tvNoCases.text = "Could not load cases: ${e.message}"
            }
        }
    }

    private fun buildCaseCard(summary: ConsultationCaseSummary): View {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * dp).toInt()
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener { showChatScreen(summary.caseId) }
        }

        // Row 1: condition + status badge
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val tvCondition = TextView(this).apply {
            text = summary.conditionName.ifBlank { "Unnamed Case" }
            textSize = 15f
            setTextColor(0xFF111827.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val (badgeBg, badgeFg) = when (summary.status) {
            "closed"      -> Pair(0xFFD1FAE5.toInt(), 0xFF065F46.toInt())
            "responded"   -> Pair(0xFFDCFCE7.toInt(), 0xFF15803D.toInt())
            "acknowledged"-> Pair(0xFFDCFCE7.toInt(), 0xFF15803D.toInt())
            else          -> Pair(0xFFFEF9C3.toInt(), 0xFF92400E.toInt())
        }
        val tvStatus = TextView(this).apply {
            text = summary.status.replaceFirstChar { it.uppercase() }
            textSize = 11f
            setTextColor(badgeFg)
            setBackgroundColor(badgeBg)
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
        }
        row1.addView(tvCondition)
        row1.addView(tvStatus)
        card.addView(row1)

        // Area
        if (summary.affectedArea.isNotBlank()) {
            val tvArea = TextView(this).apply {
                text = "📍 ${summary.affectedArea}"
                textSize = 12f
                setTextColor(0xFF6B7280.toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = (4 * dp).toInt()
                layoutParams = lp
            }
            card.addView(tvArea)
        }

        // Date + messages count
        val tvMeta = TextView(this).apply {
            text = "${summary.createdAt}  ·  ${summary.messageCount} messages  ›"
            textSize = 11f
            setTextColor(0xFF9CA3AF.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (6 * dp).toInt()
            layoutParams = lp
        }
        card.addView(tvMeta)

        return card
    }

    // ── New case form ─────────────────────────────────────────────────────────

    private fun submitNewCase() {
        val condition   = etCondition.text.toString().trim()
        val area        = etArea.text.toString().trim()
        val symptoms    = etSymptoms.text.toString().trim()
        val limitations = etLimitations.text.toString().trim()

        if (condition.isBlank() || symptoms.isBlank()) {
            Toast.makeText(this, "Please fill in condition and symptoms", Toast.LENGTH_SHORT).show()
            return
        }

        val btnTv = (findViewById<LinearLayout>(R.id.btnSubmitCase).getChildAt(0) as? TextView)
        btnTv?.text = "Submitting..."

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.createConsultation(userId, CreateConsultationRequest(
                        conditionName = condition,
                        affectedArea  = area.ifBlank { "Not specified" },
                        symptoms      = symptoms,
                        limitations   = limitations
                    ))
                }
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val newCaseId = resp.body()!!.data!!
                    etCondition.setText("")
                    etArea.setText("")
                    etSymptoms.setText("")
                    etLimitations.setText("")
                    layoutNewCaseForm.visibility = View.GONE
                    (btnNewCaseToggle.getChildAt(0) as? TextView)?.text = "+ New Consultation"
                    Toast.makeText(this@DoctorConsultationActivity,
                        "Case submitted! Doctor response generated.", Toast.LENGTH_LONG).show()
                    showChatScreen(newCaseId)
                } else {
                    Toast.makeText(this@DoctorConsultationActivity,
                        "Failed: ${resp.body()?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DoctorConsultationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            btnTv?.text = "Submit to Medical Team"
        }
    }

    // ── Chat screen ───────────────────────────────────────────────────────────

    private fun loadChatMessages(caseId: Int) {
        containerMessages.removeAllViews()
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getConsultationCase(userId, caseId)
                }
                val case = resp.body()?.data ?: return@launch

                tvChatTitle.text    = case.conditionName.ifBlank { "Consultation" }
                tvChatCondition.text = case.conditionName
                tvChatArea.text     = case.affectedArea

                val (statusBg, statusFg) = when (case.status) {
                    "closed"       -> Pair(0xFFD1FAE5.toInt(), 0xFF065F46.toInt())
                    "responded"    -> Pair(0xFFDCFCE7.toInt(), 0xFF15803D.toInt())
                    "acknowledged" -> Pair(0xFFDCFCE7.toInt(), 0xFF15803D.toInt())
                    else           -> Pair(0xFFFEF9C3.toInt(), 0xFF92400E.toInt())
                }
                tvChatStatus.text = case.status.replaceFirstChar { it.uppercase() }
                tvChatStatus.setTextColor(statusFg)
                tvChatStatus.setBackgroundColor(statusBg)

                // Show/hide action buttons
                val isClosed = case.status == "closed"
                btnAcknowledge.visibility     = if (case.status == "responded") View.VISIBLE else View.GONE
                btnCloseCase.visibility       = if (!isClosed) View.VISIBLE else View.GONE
                btnViewAdjustments.visibility = if (case.adjustmentsApplied) View.VISIBLE else View.GONE
                etMessage.isEnabled           = !isClosed
                btnSend.isEnabled             = !isClosed
                btnSend.alpha                 = if (isClosed) 0.5f else 1f

                case.messages.forEach { msg -> containerMessages.addView(buildMessageBubble(msg)) }

                val scroll = findViewById<ScrollView>(R.id.scrollChat)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

            } catch (e: Exception) {
                Toast.makeText(this@DoctorConsultationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildMessageBubble(msg: ConsultationMessage): View {
        val isUser = msg.senderType == "user"
        val dp = resources.displayMetrics.density

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * dp).toInt()
            layoutParams = lp
            gravity = if (isUser) android.view.Gravity.END else android.view.Gravity.START
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            minimumWidth = (100 * dp).toInt()
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        if (isUser) {
            lp.marginStart = (60 * dp).toInt()
            bubble.setBackgroundColor(0xFFF97316.toInt())
        } else {
            lp.marginEnd = (60 * dp).toInt()
            bubble.setBackgroundColor(0xFFF3F4F6.toInt())
        }
        bubble.layoutParams = lp

        val tvName = TextView(this).apply {
            text = if (isUser) "You" else "🩺 Medical Team"
            textSize = 11f
            setTextColor(if (isUser) 0xFFFFE0C0.toInt() else 0xFF6B7280.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // This is the ONLY place tvContent should be configured
        val tvContent = TextView(this).apply {
            text = msg.content
            textSize = 13f
            // Correct way to set maxWidth:
            maxWidth = (resources.displayMetrics.widthPixels * 0.72).toInt()

            setTextColor(if (isUser) 0xFFFFFFFF.toInt() else 0xFF111827.toInt())
            val contentLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            contentLp.topMargin = (4 * dp).toInt()
            layoutParams = contentLp
        }

        val tvTime = TextView(this).apply {
            text = msg.sentAt
            textSize = 10f
            setTextColor(if (isUser) 0xFFFFD4A3.toInt() else 0xFF9CA3AF.toInt())
            val timeLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            timeLp.topMargin = (4 * dp).toInt()
            layoutParams = timeLp
        }

        bubble.addView(tvName)
        bubble.addView(tvContent)

        if (msg.fileNames.isNotEmpty()) {
            val tvFiles = TextView(this).apply {
                text = msg.fileNames.joinToString("\n") { "📎 $it" }
                textSize = 11f
                setTextColor(if (isUser) 0xFFFFE0C0.toInt() else 0xFF6B7280.toInt())
                val fileLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                fileLp.topMargin = (4 * dp).toInt()
                layoutParams = fileLp
            }
            bubble.addView(tvFiles)
        }

        bubble.addView(tvTime)
        wrapper.addView(bubble)
        return wrapper
    }

    // ── Send message ──────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "Write a message first", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentCaseId == -1) return

        val btnTv = (btnSend.getChildAt(0) as? TextView)
        btnTv?.text = "..."

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.api.sendConsultationMessage(
                        userId, currentCaseId,
                        SendMessageRequest(content = text, fileNames = attachedFileNames.toList())
                    )
                }
                if (resp.isSuccessful) {
                    etMessage.setText("")
                    attachedFileNames.clear()
                    updateFileChips()
                    loadChatMessages(currentCaseId)
                } else {
                    Toast.makeText(this@DoctorConsultationActivity, "Failed to send", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DoctorConsultationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            btnTv?.text = "Send"
        }
    }

    // ── Update case ───────────────────────────────────────────────────────────

    private fun updateCase(status: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.updateConsultationCase(
                        userId, currentCaseId, UpdateCaseRequest(status)
                    )
                }
                val msg = when (status) {
                    "acknowledged" -> "✓ Advice acknowledged! Smart plan adjustments applied."
                    "closed"       -> "Case closed. Recovery complete! 💪"
                    else           -> "Case updated."
                }
                Toast.makeText(this@DoctorConsultationActivity, msg, Toast.LENGTH_SHORT).show()
                loadChatMessages(currentCaseId)
            } catch (e: Exception) {
                Toast.makeText(this@DoctorConsultationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── File picker ───────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    }

    private fun updateFileChips() {
        containerFileChips.removeAllViews()
        val dp = resources.displayMetrics.density
        attachedFileNames.forEach { name ->
            val chip = TextView(this).apply {
                text = "📎 $name  ✕"
                textSize = 11f
                setTextColor(0xFF374151.toInt())
                setBackgroundColor(0xFFE5E7EB.toInt())
                setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = (8 * dp).toInt()
                layoutParams = lp
                setOnClickListener { attachedFileNames.remove(name); updateFileChips() }
            }
            containerFileChips.addView(chip)
        }
    }

    // ── Download summary ──────────────────────────────────────────────────────

    private fun downloadSummary() {
        scope.launch {
            val case = try {
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.getConsultationCase(userId, currentCaseId).body()?.data
                }
            } catch (e: Exception) { null } ?: return@launch

            try {
                val pdf = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                val bold   = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                val normal = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 12f }
                val gray   = android.graphics.Paint().apply { color = android.graphics.Color.GRAY;  textSize = 11f }

                var y = 60f
                canvas.drawText("Fuelify — Medical Consultation Summary", 40f, y, bold); y += 30f
                canvas.drawText("Status: ${case.status.uppercase()}  |  ${case.createdAt}", 40f, y, gray); y += 30f
                canvas.drawLine(40f, y, 555f, y, gray); y += 20f
                canvas.drawText("Condition: ${case.conditionName}", 40f, y, bold.apply { textSize = 14f }); y += 22f
                canvas.drawText("Affected Area: ${case.affectedArea}", 40f, y, normal); y += 22f
                canvas.drawText("Symptoms: ${case.symptoms.take(200)}", 40f, y, normal); y += 22f
                if (case.limitations.isNotBlank()) {
                    canvas.drawText("Limitations: ${case.limitations.take(150)}", 40f, y, normal); y += 22f
                }
                y += 10f
                canvas.drawLine(40f, y, 555f, y, gray); y += 20f
                case.messages.forEach { msg ->
                    if (msg.senderType == "doctor") {
                        canvas.drawText("Medical Team Response:", 40f, y, bold.apply { textSize = 13f }); y += 20f
                        msg.content.chunked(85).take(20).forEach { line ->
                            canvas.drawText(line, 40f, y, normal.apply { textSize = 11f }); y += 16f
                        }
                        y += 10f
                    }
                }
                y += 10f
                canvas.drawLine(40f, y, 555f, y, gray); y += 16f
                canvas.drawText("Generated by Fuelify. Not a substitute for professional medical advice.", 40f, y, gray)
                pdf.finishPage(page)

                val fileName = "Consultation_Summary_${case.caseId}.pdf"
                val dir  = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = java.io.File(dir, fileName)
                java.io.FileOutputStream(file).use { pdf.writeTo(it) }
                pdf.close()

                Toast.makeText(this@DoctorConsultationActivity, "✓ Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@DoctorConsultationActivity, "${packageName}.provider", file)
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (e: Exception) {
                    startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                }
            } catch (e: Exception) {
                Toast.makeText(this@DoctorConsultationActivity, "PDF error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
