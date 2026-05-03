package com.example.fuelify.auth

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelify.R
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.RewardResponse
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch

class AdminDeleteRewardActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var rvRewards: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_delete_reward)

        btnBack   = findViewById(R.id.btnBack)
        rvRewards = findViewById(R.id.rvRewards)

        btnBack.setOnClickListener { finish() }

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
                } else {
                    Toast.makeText(this@AdminDeleteRewardActivity,
                        "Failed to load rewards", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminDeleteRewardActivity,
                    "Connection error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRewardList(rewards: List<RewardResponse>) {
        val adapter = RewardAdapter(
            items         = rewards,
            onItemClick   = { reward -> showDeleteConfirmation(reward) },
            onRedeemClick = { reward -> showDeleteConfirmation(reward) }
        )
        rvRewards.layoutManager = GridLayoutManager(this, 2)
        rvRewards.adapter = adapter
    }

    private fun showDeleteConfirmation(reward: RewardResponse) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Reward")
            .setMessage("Are you sure you want to permanently delete \"${reward.rewardName}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteReward(reward.rewardId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteReward(rewardId: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.deleteReward(
                    SessionManager.getBearerToken(), rewardId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@AdminDeleteRewardActivity,
                        "🗑 Reward deleted successfully.", Toast.LENGTH_LONG).show()
                    loadAllRewards() // Refresh the list
                } else {
                    Toast.makeText(this@AdminDeleteRewardActivity,
                        response.body()?.message ?: "Delete failed", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdminDeleteRewardActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}