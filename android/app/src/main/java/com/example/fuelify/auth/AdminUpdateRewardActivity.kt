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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.example.fuelify.auth.network.AdminUpdateRewardRequest
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.RewardResponse
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class AdminUpdateRewardActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var rvRewards: RecyclerView
    private lateinit var formContainer: View
    private lateinit var rewardListContainer: View

    private lateinit var etRewardName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etPointsRequired: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var rewardImagePreview: ShapeableImageView
    private lateinit var cameraIconOverlay: ImageView
    private lateinit var changePhotoText: TextView
    private lateinit var etTerms: EditText
    private lateinit var btnUpdateReward: LinearLayout
    private lateinit var tvBtnLabel: TextView
    private lateinit var btnBackToList: TextView

    private var loadedRewardId: Int = -1
    private var selectedImageUri: Uri? = null
    private var existingImageUrl: String? = null

    private val PICK_IMAGE_REQUEST = 1001
    private val categories = listOf("Gym", "Lifestyle", "All")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_update_reward)

        btnBack             = findViewById(R.id.btnBack)
        rvRewards           = findViewById(R.id.rvRewards)
        formContainer       = findViewById(R.id.formContainer)
        rewardListContainer = findViewById(R.id.rewardListContainer)
        etRewardName        = findViewById(R.id.etRewardName)
        etDescription       = findViewById(R.id.etDescription)
        etPointsRequired    = findViewById(R.id.etPointsRequired)
        spinnerCategory     = findViewById(R.id.spinnerCategory)
        rewardImagePreview  = findViewById(R.id.rewardImagePreview)
        cameraIconOverlay   = findViewById(R.id.cameraIconOverlay)
        changePhotoText     = findViewById(R.id.changePhotoText)
        etTerms             = findViewById(R.id.etTerms)
        btnUpdateReward     = findViewById(R.id.btnUpdateReward)
        tvBtnLabel          = findViewById(R.id.tvBtnLabel)
        btnBackToList       = findViewById(R.id.btnBackToList)

        // Category spinner
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Changed from #1A1A1A to #5B9E1E to match the green style used in Add Reward
                (view as? TextView)?.setTextColor(android.graphics.Color.parseColor("#5B9E1E"))
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnBack.setOnClickListener { finish() }
        btnBackToList.setOnClickListener { showList() }

        val pickImage = {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
        cameraIconOverlay.setOnClickListener { pickImage() }
        changePhotoText.setOnClickListener { pickImage() }
        rewardImagePreview.setOnClickListener { pickImage() }

        btnUpdateReward.setOnClickListener {
            if (loadedRewardId != -1) validateAndUpdate()
        }

        loadAllRewards()
    }

    private fun loadAllRewards() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getMarketplaceItems(
                    SessionManager.getBearerToken(), null
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val rewards = response.body()?.data?.rewards ?: emptyList()
                    setupRewardList(rewards)
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminUpdateRewardActivity,
                    "Failed to load rewards", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRewardList(rewards: List<RewardResponse>) {
        val adapter = RewardAdapter(
            items         = rewards,
            onItemClick   = { reward -> loadReward(reward.rewardId) },
            onRedeemClick = { reward -> loadReward(reward.rewardId) }
        )
        rvRewards.layoutManager = GridLayoutManager(this, 2)
        rvRewards.adapter = adapter
    }

    private fun showList() {
        formContainer.visibility       = View.GONE
        rewardListContainer.visibility = View.VISIBLE
        loadedRewardId   = -1
        selectedImageUri = null
        existingImageUrl = null
    }

    private fun showForm() {
        rewardListContainer.visibility = View.GONE
        formContainer.visibility       = View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            Glide.with(this).load(selectedImageUri).centerCrop().into(rewardImagePreview)
            changePhotoText.text = "Tap to change image"
        }
    }

    private fun loadReward(id: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getRewardById(
                    SessionManager.getBearerToken(), id
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val reward = response.body()!!.data!!
                    loadedRewardId = id

                    etRewardName.setText(reward.rewardName)
                    etDescription.setText(reward.description ?: "")
                    etPointsRequired.setText(reward.pointsRequired.toString())
                    etTerms.setText(reward.termsAndConditions ?: "")

                    val categoryIndex = categories.indexOfFirst {
                        it.equals(reward.category, ignoreCase = true)
                    }.takeIf { it >= 0 } ?: 0
                    spinnerCategory.setSelection(categoryIndex)

                    existingImageUrl = reward.imageUrl
                    if (!reward.imageUrl.isNullOrEmpty()) {
                        Glide.with(this@AdminUpdateRewardActivity)
                            .load(reward.imageUrl).centerCrop().into(rewardImagePreview)
                        changePhotoText.text = "Tap to change image"
                    }

                    showForm()
                } else {
                    Toast.makeText(this@AdminUpdateRewardActivity,
                        response.body()?.message ?: "Reward not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminUpdateRewardActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateAndUpdate() {
        val name     = etRewardName.text.toString().trim()
        val points   = etPointsRequired.text.toString().trim()
        val category = spinnerCategory.selectedItem.toString()

        if (name.isEmpty()) { etRewardName.error = "Required"; etRewardName.requestFocus(); return }
        if (points.isEmpty()) { etPointsRequired.error = "Required"; etPointsRequired.requestFocus(); return }

        btnUpdateReward.isEnabled = false
        tvBtnLabel.text = "Uploading..."

        lifecycleScope.launch {
            try {
                var photoUrl: String? = existingImageUrl

                if (selectedImageUri != null) {
                    val file = getFileFromUri(selectedImageUri!!)
                    if (file != null) {
                        val requestBody = file.asRequestBody("image/jpeg".toMediaType())
                        val part = MultipartBody.Part.createFormData("photo", file.name, requestBody)
                        val uploadResponse = RetrofitClient.instance.uploadImage(
                            SessionManager.getBearerToken(), "reward_images", part
                        )
                        if (uploadResponse.isSuccessful && uploadResponse.body()?.success == true) {
                            photoUrl = uploadResponse.body()!!.data?.url
                        } else {
                            Toast.makeText(this@AdminUpdateRewardActivity,
                                "Image upload failed", Toast.LENGTH_SHORT).show()
                            resetButton(); return@launch
                        }
                    }
                }

                tvBtnLabel.text = "Saving..."

                val response = RetrofitClient.instance.updateReward(
                    SessionManager.getBearerToken(), loadedRewardId,
                    AdminUpdateRewardRequest(
                        rewardName         = name,
                        description        = etDescription.text.toString().trim().ifEmpty { null },
                        pointsRequired     = points.toInt(),
                        category           = category,
                        imageUrl           = photoUrl,
                        termsAndConditions = etTerms.text.toString().trim().ifEmpty { null }
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@AdminUpdateRewardActivity,
                        "✅ Reward updated successfully!", Toast.LENGTH_LONG).show()
                    showList()
                    loadAllRewards()
                } else {
                    Toast.makeText(this@AdminUpdateRewardActivity,
                        response.body()?.message ?: "Update failed", Toast.LENGTH_LONG).show()
                    resetButton()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminUpdateRewardActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                resetButton()
            }
        }
    }

    private fun resetButton() {
        btnUpdateReward.isEnabled = true
        tvBtnLabel.text = "Save Changes"
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