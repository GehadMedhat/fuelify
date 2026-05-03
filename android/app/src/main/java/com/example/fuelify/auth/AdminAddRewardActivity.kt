package com.example.fuelify.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.example.fuelify.auth.network.AdminCreateRewardRequest
class AdminAddRewardActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etRewardName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etPointsRequired: EditText
    private lateinit var etTerms: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var rewardImagePreview: ShapeableImageView
    private lateinit var cameraIconOverlay: ImageView
    private lateinit var changePhotoText: TextView
    private lateinit var btnAddReward: LinearLayout
    private lateinit var tvBtnLabel: TextView

    private val PICK_IMAGE_REQUEST = 1001
    private var selectedImageUri: Uri? = null

    private val categories = listOf("Gym", "Lifestyle", "All")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_add_reward)

        btnBack            = findViewById(R.id.btnBack)
        etRewardName       = findViewById(R.id.etRewardName)
        etDescription      = findViewById(R.id.etDescription)
        etPointsRequired   = findViewById(R.id.etPointsRequired)
        etTerms            = findViewById(R.id.etTerms)
        spinnerCategory    = findViewById(R.id.spinnerCategory)
        rewardImagePreview = findViewById(R.id.rewardImagePreview)
        cameraIconOverlay  = findViewById(R.id.cameraIconOverlay)
        changePhotoText    = findViewById(R.id.changePhotoText)
        btnAddReward       = findViewById(R.id.btnAddReward)
        tvBtnLabel         = findViewById(R.id.tvBtnLabel)

        // Set up category dropdown
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerCategory.adapter = adapter

        btnBack.setOnClickListener { finish() }

        val pickImage = {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
        cameraIconOverlay.setOnClickListener { pickImage() }
        changePhotoText.setOnClickListener { pickImage() }
        rewardImagePreview.setOnClickListener { pickImage() }

        btnAddReward.setOnClickListener { validateAndSubmit() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            Glide.with(this)
                .load(selectedImageUri)
                .centerCrop()
                .into(rewardImagePreview)
            changePhotoText.text = "Tap to change image"
        }
    }

    private fun validateAndSubmit() {
        val name     = etRewardName.text.toString().trim()
        val points   = etPointsRequired.text.toString().trim()
        val category = spinnerCategory.selectedItem.toString()

        if (name.isEmpty()) {
            etRewardName.error = "Reward name is required"
            etRewardName.requestFocus()
            return
        }
        if (points.isEmpty()) {
            etPointsRequired.error = "Points required"
            etPointsRequired.requestFocus()
            return
        }

        btnAddReward.isEnabled = false
        tvBtnLabel.text = "Uploading..."

        lifecycleScope.launch {
            try {
                var photoUrl: String? = null

                // Step 1: Upload image if selected
                if (selectedImageUri != null) {
                    val file = getFileFromUri(selectedImageUri!!)
                    if (file != null) {
                        val requestBody = file.asRequestBody("image/jpeg".toMediaType())
                        val part = MultipartBody.Part.createFormData("photo", file.name, requestBody)
                        val uploadResponse = RetrofitClient.instance.uploadImage(
                            token  = SessionManager.getBearerToken(),
                            folder = "reward_images",
                            image  = part
                        )
                        if (uploadResponse.isSuccessful && uploadResponse.body()?.success == true) {
                            photoUrl = uploadResponse.body()!!.data?.url
                        } else {
                            Toast.makeText(this@AdminAddRewardActivity,
                                "Image upload failed", Toast.LENGTH_SHORT).show()
                            btnAddReward.isEnabled = true
                            tvBtnLabel.text = "Add Reward"
                            return@launch
                        }
                    }
                }

                tvBtnLabel.text = "Saving..."

                // Step 2: Create reward
                val response = RetrofitClient.instance.createReward(
                    SessionManager.getBearerToken(),
                    AdminCreateRewardRequest(
                        rewardName         = name,
                        description        = etDescription.text.toString().trim().ifEmpty { null },
                        pointsRequired     = points.toInt(),
                        category           = category,
                        imageUrl           = photoUrl,
                        termsAndConditions = etTerms.text.toString().trim().ifEmpty { null }
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@AdminAddRewardActivity,
                        "✅ Reward added successfully!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@AdminAddRewardActivity,
                        response.body()?.message ?: "Failed to add reward",
                        Toast.LENGTH_LONG).show()
                    btnAddReward.isEnabled = true
                    tvBtnLabel.text = "Add Reward"
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminAddRewardActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnAddReward.isEnabled = true
                tvBtnLabel.text = "Add Reward"
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "reward_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { inputStream.copyTo(it) }
            file
        } catch (e: Exception) { null }
    }
}