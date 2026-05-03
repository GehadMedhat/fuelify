package com.example.fuelify.auth

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.fuelify.auth.network.RedeemRewardRequest
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch

class RewardDetailsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var productImage: com.google.android.material.imageview.ShapeableImageView
    private lateinit var productTitle: TextView
    private lateinit var productDescription: TextView
    private lateinit var redeemButton: TextView
    private lateinit var cancelButton: TextView
    private lateinit var termsText: TextView

    private var rewardId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reward_details)

        rewardId = intent.getIntExtra("rewardId", -1)

        btnBack            = findViewById(R.id.btnBack)
        productImage       = findViewById(R.id.productImage)
        productTitle       = findViewById(R.id.productTitle)
        productDescription = findViewById(R.id.productDescription)
        redeemButton       = findViewById(R.id.redeemButton)
        cancelButton       = findViewById(R.id.cancelButton)
        termsText          = findViewById(R.id.termsText)

        btnBack.setOnClickListener { finish() }
        cancelButton.setOnClickListener { finish() }

        redeemButton.setOnClickListener {
            if (rewardId != -1) showRedeemConfirmation()
        }

        if (rewardId != -1) loadRewardDetails()
    }

    private fun loadRewardDetails() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getRewardById(
                    SessionManager.getBearerToken(), rewardId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val reward = response.body()!!.data!!

                    productTitle.text       = reward.rewardName
                    productDescription.text = reward.description ?: "No description available"
                    redeemButton.text       = "Redeem for ${reward.pointsRequired} pts"
                    termsText.text          = reward.termsAndConditions ?: ""

                    if (!reward.imageUrl.isNullOrEmpty()) {
                        Glide.with(this@RewardDetailsActivity)
                            .load(reward.imageUrl)
                            .into(productImage)
                    }
                } else {
                    Toast.makeText(this@RewardDetailsActivity,
                        response.body()?.message ?: "Failed to load reward",
                        Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RewardDetailsActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showRedeemConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Confirm Redemption")
            .setMessage("Are you sure you want to redeem this reward? It will be added to your next order.")
            .setPositiveButton("Redeem") { _, _ -> redeemReward() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun redeemReward() {
        redeemButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.redeemReward(
                    SessionManager.getBearerToken(),
                    RedeemRewardRequest(rewardId)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@RewardDetailsActivity,
                        "🎉 Reward redeemed! It will be added to your next order.",
                        Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@RewardDetailsActivity,
                        response.body()?.message ?: "Redemption failed",
                        Toast.LENGTH_LONG).show()
                    redeemButton.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@RewardDetailsActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                redeemButton.isEnabled = true
            }
        }
    }
}