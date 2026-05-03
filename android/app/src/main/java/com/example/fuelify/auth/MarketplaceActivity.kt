package com.example.fuelify.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.fuelify.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelify.auth.network.RetrofitClient
import com.example.fuelify.auth.network.RewardResponse
import com.example.fuelify.auth.network.SessionManager
import kotlinx.coroutines.launch

class MarketplaceActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var tvUserPoints: TextView
    private lateinit var rvRewards: RecyclerView
    private lateinit var tabAll: TextView
    private lateinit var tabLifestyle: TextView
    private lateinit var tabGym: TextView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: RewardAdapter
    private var allRewards: List<RewardResponse> = emptyList()
    private var activeTab: String = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market_place)

        btnBack      = findViewById(R.id.btnBack)
        tvUserPoints = findViewById(R.id.tvUserPoints)
        rvRewards    = findViewById(R.id.rvRewards)
        tabAll       = findViewById(R.id.tabAll)
        tabLifestyle = findViewById(R.id.tabLifestyle)
        tabGym       = findViewById(R.id.tabGym)
        tvEmpty      = findViewById(R.id.tvEmpty)

        btnBack.setOnClickListener { finish() }

        // Setup RecyclerView with 2-column grid
        adapter = RewardAdapter(
            items         = emptyList(),
            onItemClick   = { reward -> openRewardDetails(reward.rewardId) },
            onRedeemClick = { reward -> confirmRedeem(reward) }
        )
        rvRewards.layoutManager = GridLayoutManager(this, 2)
        rvRewards.adapter = adapter

        // Category tab clicks
        tabAll.setOnClickListener       { setActiveTab("All") }
        tabLifestyle.setOnClickListener { setActiveTab("Lifestyle") }
        tabGym.setOnClickListener       { setActiveTab("Gym") }

        loadUserPoints()
        loadMarketplaceItems()

        // Bottom nav — profile active since marketplace is accessed from profile
        BottomNavHelper.setup(this, NavTab.PROFILE)
    }

    private fun setActiveTab(tab: String) {
        activeTab = tab

        // Reset all tabs
        listOf(tabAll, tabLifestyle, tabGym).forEach { tv ->
            tv.setTextColor(getColor(android.R.color.black))
            tv.setBackgroundResource(android.R.color.transparent)
        }

        // Highlight active tab
        val activeView = when (tab) {
            "Lifestyle" -> tabLifestyle
            "Gym"       -> tabGym
            else        -> tabAll
        }
        activeView.setTextColor(getColor(android.R.color.white))
        activeView.setBackgroundResource(R.drawable.green_rectangle)

        // Filter items
        val filtered = if (tab == "All") allRewards
        else allRewards.filter { it.category.equals(tab, ignoreCase = true) }

        adapter.updateItems(filtered)

        // Empty state
        if (filtered.isEmpty()) {
            tvEmpty.visibility   = View.VISIBLE
            rvRewards.visibility = View.GONE
        } else {
            tvEmpty.visibility   = View.GONE
            rvRewards.visibility = View.VISIBLE
        }
    }

    private fun loadUserPoints() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserPoints(
                    SessionManager.getBearerToken()
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val balance = response.body()!!.data?.balance ?: 0
                    tvUserPoints.text = "Your Points: $balance"
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    private fun loadMarketplaceItems() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getMarketplaceItems(
                    SessionManager.getBearerToken(), null
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    allRewards = response.body()?.data?.rewards ?: emptyList()
                    setActiveTab(activeTab)
                } else {
                    Toast.makeText(this@MarketplaceActivity,
                        "Failed to load rewards", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MarketplaceActivity,
                    "Connection error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRedeem(reward: RewardResponse) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Confirm Redemption")
            .setMessage("Redeem \"${reward.rewardName}\" for ${reward.pointsRequired} points?")
            .setPositiveButton("Redeem") { _, _ -> redeemReward(reward) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun redeemReward(reward: RewardResponse) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.redeemReward(
                    SessionManager.getBearerToken(),
                    com.example.fuelify.auth.network.RedeemRewardRequest(reward.rewardId)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@MarketplaceActivity,
                        "🎉 ${reward.rewardName} redeemed!", Toast.LENGTH_SHORT).show()
                    loadUserPoints()
                } else {
                    Toast.makeText(this@MarketplaceActivity,
                        response.body()?.message ?: "Could not redeem reward",
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MarketplaceActivity,
                    "Connection error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openRewardDetails(rewardId: Int) {
        val intent = Intent(this, RewardDetailsActivity::class.java)
        intent.putExtra("rewardId", rewardId)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadUserPoints()
        BottomNavHelper.setup(this, NavTab.PROFILE)
    }
}