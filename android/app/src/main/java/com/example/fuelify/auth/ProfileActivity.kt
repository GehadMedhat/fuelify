package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.R
import com.bumptech.glide.Glide
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.auth.network.UpdateProfileRequest
import com.example.fuelify.home.DietActivity
import com.example.fuelify.home.DoctorConsultationActivity
import com.example.fuelify.home.HealthReportActivity
import com.example.fuelify.home.HomeActivity
import com.example.fuelify.home.MedicalAlertsActivity
import com.example.fuelify.home.MedicalInformationActivity
import com.example.fuelify.home.SavedRecipesActivity
import com.example.fuelify.home.SmartPlanActivity
import com.example.fuelify.home.WorkoutHomeActivity
import com.example.fuelify.home.SpinWheelActivity
import com.example.fuelify.home.BingoActivity

import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var profilePicture: ImageView
    private lateinit var userName: TextView
    private lateinit var tvUserName: TextView
    private lateinit var btnEditProfile: android.view.View
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnBack: ImageView
    private lateinit var deleteAccountButton: LinearLayout
    private lateinit var visibilityRadioGroup: RadioGroup
    private lateinit var savedRecipesRow: LinearLayout
    private lateinit var chatbotRow: LinearLayout

    private lateinit var gameRow: LinearLayout
    private lateinit var game2Row: LinearLayout

    private lateinit var marketplaceRow: LinearLayout
    private lateinit var notificationCenterRow: LinearLayout
    private lateinit var notificationSettingsRow: LinearLayout

    // Health & Medical buttons (from Fuelify)
    private lateinit var btnMedical: LinearLayout
    private lateinit var btnAlerts: LinearLayout
    private lateinit var btnPlan: LinearLayout
    private lateinit var btnDoctor: LinearLayout
    private lateinit var btnReport: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        profilePicture          = findViewById(R.id.profileImage)
        userName                = findViewById(R.id.userName)
        tvUserName              = findViewById(R.id.tvUserName)
        btnEditProfile          = findViewById(R.id.btnEditProfile)
        btnLogout               = findViewById(R.id.btnLogout)
        btnBack                 = findViewById(R.id.btnBack)
        deleteAccountButton     = findViewById(R.id.deleteAccountButton)
        visibilityRadioGroup    = findViewById(R.id.visibilityRadioGroup)
        savedRecipesRow         = findViewById(R.id.savedRecipesRow)
        chatbotRow              = findViewById(R.id.chatbotRow)
        gameRow                 = findViewById(R.id.gameRow)
        game2Row                = findViewById(R.id.game2Row)
        marketplaceRow          = findViewById(R.id.marketplaceRow)
        notificationCenterRow   = findViewById(R.id.notificationCenterRow)
        notificationSettingsRow = findViewById(R.id.notificationSettingsRow)
        btnMedical              = findViewById(R.id.btnMedical)
        btnAlerts               = findViewById(R.id.btnAlerts)
        btnPlan                 = findViewById(R.id.btnPlan)
        btnDoctor               = findViewById(R.id.btnDoctor)
        btnReport               = findViewById(R.id.btnReport)

        loadProfileFromSession()
        loadProfileFromApi()
        setupBottomNav()

        btnBack.setOnClickListener { finish() }

        btnEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        savedRecipesRow.setOnClickListener {
            startActivity(Intent(this, SavedRecipesActivity::class.java))
        }

        chatbotRow.setOnClickListener {
            startActivity(Intent(this, QuickQuestionsActivity::class.java))
        }

        gameRow.setOnClickListener {
            startActivity(Intent(this, SpinWheelActivity::class.java))
        }

        game2Row.setOnClickListener {
            startActivity(Intent(this, BingoActivity::class.java))
        }
        marketplaceRow.setOnClickListener {
            startActivity(Intent(this, MarketplaceActivity::class.java))
        }

        notificationCenterRow.setOnClickListener {
            startActivity(Intent(this, NotificationCenterActivity::class.java))
        }

        notificationSettingsRow.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        // Health & Medical buttons
        btnMedical.setOnClickListener {
            startActivity(Intent(this, MedicalInformationActivity::class.java))
        }
        btnAlerts.setOnClickListener {
            startActivity(Intent(this, MedicalAlertsActivity::class.java))
        }
        btnPlan.setOnClickListener {
            startActivity(Intent(this, SmartPlanActivity::class.java))
            finish()
        }
        btnDoctor.setOnClickListener {
            startActivity(Intent(this, DoctorConsultationActivity::class.java))
        }
        btnReport.setOnClickListener {
            startActivity(Intent(this, HealthReportActivity::class.java))
        }

        btnLogout.setOnClickListener { performLogout() }

        deleteAccountButton.setOnClickListener {
            startActivity(Intent(this, DeleteAccountActivity::class.java))
        }

        visibilityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val visibility = when (checkedId) {
                R.id.radioPublic  -> "PUBLIC"
                R.id.radioPrivate -> "PRIVATE"
                R.id.radioFriends -> "FRIENDS"
                else              -> "PRIVATE"
            }
            updateVisibility(visibility)
        }

        BottomNavHelper.setup(this, NavTab.PROFILE)
    }

    private fun loadProfileFromSession() {
        val first    = SessionManager.getFirstName() ?: ""
        val last     = SessionManager.getLastName() ?: ""
        val username = SessionManager.getUsername() ?: ""
        userName.text   = "$first $last".trim().ifEmpty { "User" }
        tvUserName.text = if (username.isNotEmpty()) "@$username" else ""
        Glide.with(this).load(SessionManager.getProfilePicture()).circleCrop()
            .placeholder(R.drawable.placeholder_avatar).error(R.drawable.ic_profile).into(profilePicture)
        when (SessionManager.getVisibility()) {
            "PUBLIC"  -> visibilityRadioGroup.check(R.id.radioPublic)
            "PRIVATE" -> visibilityRadioGroup.check(R.id.radioPrivate)
            "FRIENDS" -> visibilityRadioGroup.check(R.id.radioFriends)
        }
    }

    private fun loadProfileFromApi() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getProfile(SessionManager.getBearerToken())
                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()!!.data!!
                    SessionManager.saveUser(user)
                    userName.text   = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim().ifEmpty { "User" }
                    tvUserName.text = if (!user.username.isNullOrEmpty()) "@${user.username}" else ""
                    Glide.with(this@ProfileActivity).load(user.profilePicture).circleCrop()
                        .placeholder(R.drawable.placeholder_avatar).error(R.drawable.ic_profile).into(profilePicture)
                    when (user.visibility) {
                        "PUBLIC"  -> visibilityRadioGroup.check(R.id.radioPublic)
                        "PRIVATE" -> visibilityRadioGroup.check(R.id.radioPrivate)
                        "FRIENDS" -> visibilityRadioGroup.check(R.id.radioFriends)
                    }
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    private fun updateVisibility(visibility: String) {
        lifecycleScope.launch {
            try {
                RetrofitClient.instance.updateProfile(SessionManager.getBearerToken(), UpdateProfileRequest(visibility = visibility))
            } catch (e: Exception) { /* silent */ }
        }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                RetrofitClient.instance.logout(SessionManager.getBearerToken())
            } catch (e: Exception) { /* ignore */ } finally {
                SessionManager.clear()
                startActivity(Intent(this@ProfileActivity, WelcomeActivity::class.java))
                finishAffinity()
            }
        }
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navDiet).setOnClickListener {
            startActivity(Intent(this, DietActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navWorkouts).setOnClickListener {
            startActivity(Intent(this, WorkoutHomeActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navStats).setOnClickListener {
            Toast.makeText(this, "Statistics coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(
                Intent(this, com.example.fuelify.auth.ProfileActivity::class.java)
            )
        }
    }


    override fun onResume() {
        super.onResume()
        loadProfileFromSession()
        BottomNavHelper.setup(this, NavTab.PROFILE)
    }
}
