package com.example.fuelify.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.auth.network.UpdateProfileRequest
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class EditProfileActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnSave: ImageView
    private lateinit var profileImage: ImageView
    private lateinit var cameraIconOverlay: ImageView
    private lateinit var changePhotoText: TextView
    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var emailDisplay: TextView
    private lateinit var changeEmailBtn: TextView

    private val PICK_IMAGE_REQUEST = 1001
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        btnBack           = findViewById(R.id.btnBack)
        btnSave           = findViewById(R.id.btnSave)
        profileImage      = findViewById(R.id.profileImageEdit)
        cameraIconOverlay = findViewById(R.id.cameraIconOverlay)
        changePhotoText   = findViewById(R.id.changePhotoText)
        firstNameInput    = findViewById(R.id.editFirstName)
        lastNameInput     = findViewById(R.id.editLastName)
        usernameInput     = findViewById(R.id.editUsername)
        emailDisplay      = findViewById(R.id.editEmail)
        changeEmailBtn    = findViewById(R.id.changeEmailBtn)

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveProfile() }

        // Pre-fill from session
        firstNameInput.setText(SessionManager.getFirstName() ?: "")
        lastNameInput.setText(SessionManager.getLastName() ?: "")
        usernameInput.setText(SessionManager.getUsername() ?: "")
        emailDisplay.text = SessionManager.getEmail() ?: ""

        // Load profile picture
        Glide.with(this)
            .load(SessionManager.getProfilePicture())
            .circleCrop()
            .placeholder(R.drawable.placeholder_avatar)
            .error(R.drawable.placeholder_avatar)
            .into(profileImage)

        changeEmailBtn.setOnClickListener {
            startActivity(Intent(this, ChangeEmailActivity::class.java))
        }

        val pickImage = {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
        cameraIconOverlay.setOnClickListener { pickImage() }
        changePhotoText.setOnClickListener { pickImage() }
        profileImage.setOnClickListener { pickImage() }

        // BottomNavHelper.setup() intentionally removed —
        // activity_edit_profile has no bottom nav views
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            Glide.with(this)
                .load(selectedImageUri)
                .circleCrop()
                .placeholder(R.drawable.placeholder_avatar)
                .into(profileImage)
        }
    }

    private fun saveProfile() {
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                var photoUrl: String? = null

                if (selectedImageUri != null) {
                    val file = getFileFromUri(selectedImageUri!!)
                    if (file != null) {
                        val requestBody = file.asRequestBody("image/jpeg".toMediaType())
                        val part = MultipartBody.Part.createFormData("photo", file.name, requestBody)
                        val uploadResponse = RetrofitClient.instance.uploadImage(
                            token  = SessionManager.getBearerToken(),
                            folder = "profile_photos",
                            image  = part
                        )
                        if (uploadResponse.isSuccessful && uploadResponse.body()?.success == true) {
                            photoUrl = uploadResponse.body()!!.data?.url ?: ""
                        } else {
                            Toast.makeText(this@EditProfileActivity,
                                "Image upload failed. Try again.", Toast.LENGTH_SHORT).show()
                            btnSave.isEnabled = true
                            return@launch
                        }
                    }
                }

                val response = RetrofitClient.instance.updateProfile(
                    token   = SessionManager.getBearerToken(),
                    request = UpdateProfileRequest(
                        firstName      = firstNameInput.text.toString().trim(),
                        lastName       = lastNameInput.text.toString().trim(),
                        username       = usernameInput.text.toString().trim(),
                        profilePicture = photoUrl
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()!!.data!!
                    SessionManager.saveUser(user)
                    Toast.makeText(this@EditProfileActivity,
                        "Profile updated!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@EditProfileActivity,
                        response.body()?.message ?: "Update failed",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSave.isEnabled = true
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { inputStream.copyTo(it) }
            file
        } catch (e: Exception) { null }
    }
}