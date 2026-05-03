package com.example.fuelify.auth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.fuelify.R
import com.google.android.material.imageview.ShapeableImageView
import com.example.fuelify.auth.network.RewardResponse

class RewardAdapter(
    private var items: List<RewardResponse>,
    private val onItemClick: (RewardResponse) -> Unit,
    private val onRedeemClick: (RewardResponse) -> Unit
) : RecyclerView.Adapter<RewardAdapter.RewardViewHolder>() {

    inner class RewardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rewardImage: ShapeableImageView = itemView.findViewById(R.id.rewardImage)
        val rewardName: TextView            = itemView.findViewById(R.id.rewardName)
        val rewardPoints: TextView          = itemView.findViewById(R.id.rewardPoints)
        val rewardCategory: TextView        = itemView.findViewById(R.id.rewardCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reward, parent, false)
        return RewardViewHolder(view)
    }

    override fun onBindViewHolder(holder: RewardViewHolder, position: Int) {
        val reward = items[position]

        holder.rewardName.text     = reward.rewardName
        holder.rewardPoints.text   = "Redeem · ${reward.pointsRequired} pts"
        holder.rewardCategory.text = reward.category

        if (!reward.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(reward.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .into(holder.rewardImage)
        } else {
            holder.rewardImage.setImageResource(R.drawable.ic_launcher_background)
        }

        holder.itemView.setOnClickListener { onItemClick(reward) }
        holder.rewardPoints.setOnClickListener { onRedeemClick(reward) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<RewardResponse>) {
        items = newItems
        notifyDataSetChanged()
    }
}