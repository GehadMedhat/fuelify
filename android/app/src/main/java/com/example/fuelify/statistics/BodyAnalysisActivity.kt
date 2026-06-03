package com.example.fuelify.statistics

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.example.fuelify.R
import android.os.Environment
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BodyAnalysisActivity : AppCompatActivity() {

    private lateinit var btnBack             : ImageButton
    private lateinit var ivCameraIcon        : ImageView
    private lateinit var tvUploadTitle       : TextView
    private lateinit var tvUploadDesc        : TextView
    private lateinit var tvUploadInstruction : TextView
    private lateinit var btnChoosePhoto      : CardView
    private lateinit var cardTodayDetails    : CardView
    private lateinit var cardStatistics      : CardView

    private var cameraPhotoUri  : Uri? = null
    private var selectedPhotoUri: Uri? = null

    // ── Gallery ───────────────────────────────────────────────────────────────
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePhotoSelected(it) }
        }

    // ── Camera ────────────────────────────────────────────────────────────────
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraPhotoUri?.let { handlePhotoSelected(it) }
            }
            // إذا ألغى المستخدم أو فشل التصوير: لا نفعل شيئاً
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
        }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) galleryLauncher.launch("image/*")
        }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.body_analysis)
        bindViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // FIX #1: نُعيد الأيقونة فقط إذا لم تُختر أي صورة بعد
        // هذا يمنع الـ onResume من مسح thumbnail الصورة المختارة
        if (selectedPhotoUri == null) {
            showDefaultCameraIcon()
        }
        // إذا كان هناك صورة مختارة → لا نلمس الـ ImageView نهائياً

        // FIX: Always reset the bottom nav selection to Home when this screen is visible.
        // This handles the case where the user navigates here from BodyStatisticsActivity
        // (which leaves nav_statistics highlighted) via CLEAR_TOP/SINGLE_TOP, causing
        isSettingNavItem = true
        isSettingNavItem = false
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private fun bindViews() {
        btnBack              = findViewById(R.id.btnBack)
        ivCameraIcon         = findViewById(R.id.ivCameraIcon)
        tvUploadTitle        = findViewById(R.id.tvUploadTitle)
        tvUploadDesc         = findViewById(R.id.tvUploadDescription)
        tvUploadInstruction  = findViewById(R.id.tvUploadInstruction)
        btnChoosePhoto       = findViewById(R.id.btnChoosePhoto)
        cardTodayDetails     = findViewById(R.id.cardTodayDetails)
        cardStatistics       = findViewById(R.id.cardStatistics)
    }

    // FIX #1: دالة منفصلة لإظهار أيقونة الكاميرا الافتراضية بشكل صحيح
    // نستخدم setImageDrawable بدل setImageResource لتفادي مشكلة إعادة الرسم الغلط
    private fun showDefaultCameraIcon() {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_camera) ?: return
        // نطبق اللون على الـ drawable مباشرة قبل وضعه في الـ ImageView
        val tintedDrawable = drawable.mutate()
        tintedDrawable.setTint(ContextCompat.getColor(this, R.color.dark_green))
        ivCameraIcon.clearColorFilter()          // نتأكد من إزالة أي filter قديم
        ivCameraIcon.setImageDrawable(tintedDrawable)
        ivCameraIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    // ── Clicks ────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        btnChoosePhoto.setOnClickListener { showPhotoSourceDialog() }

        // View Today's Body Details → يفتح قائمة بسجلات اليوم
        cardTodayDetails.setOnClickListener {
            startActivity(Intent(this, BodyRecognitionActivity::class.java).apply {
                putExtra(BodyRecognitionActivity.EXTRA_FROM_TODAY, true)
            })
        }

        cardStatistics.setOnClickListener {
            startActivity(Intent(this, BodyStatisticsActivity::class.java))
        }
    }

    // ── Photo source dialog ───────────────────────────────────────────────────

    private fun showPhotoSourceDialog() {
        val options = arrayOf("📷  Take New Photo", "🖼️  Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Photo Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> checkGalleryPermissionAndLaunch()
                }
            }
            .show()
    }

    private fun checkGalleryPermissionAndLaunch() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            galleryLauncher.launch("image/*")
        else
            storagePermissionLauncher.launch(permission)
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED)
            launchCamera()
        else
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val photoFile = createTempImageFile()
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
        cameraPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    private fun createTempImageFile(): File {
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("BODY_${ts}_", ".jpg", dir)
    }

    // ── Photo selected ────────────────────────────────────────────────────────

    private fun handlePhotoSelected(uri: Uri) {
        selectedPhotoUri = uri

        // FIX #1: نُصفّر الـ ImageView تماماً قبل وضع الصورة الجديدة
        // setImageURI(null) ثم setImageURI(uri) يُجبر الـ view على إعادة القياس والرسم
        ivCameraIcon.clearColorFilter()
        ivCameraIcon.setImageDrawable(null)
        ivCameraIcon.setImageURI(null)
        ivCameraIcon.setImageURI(uri)
        ivCameraIcon.scaleType = ImageView.ScaleType.CENTER_CROP

        tvUploadInstruction.visibility = View.GONE
        tvUploadTitle.text = "Photo Selected"
        tvUploadDesc.text  = "Tap 'View Today\u0027s Body Details' to analyse"

        // انتقل لشاشة التحليل مع الصورة الجديدة
        startActivity(Intent(this, BodyRecognitionActivity::class.java).apply {
            putExtra(BodyRecognitionActivity.EXTRA_PHOTO_URI, uri.toString())
            putExtra(BodyRecognitionActivity.EXTRA_FROM_TODAY, false)
        })
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    // Guard flag: prevents setSelectedItemId from re-triggering the listener
    // and causing a StackOverflowError infinite loop.
    private var isSettingNavItem = false


}