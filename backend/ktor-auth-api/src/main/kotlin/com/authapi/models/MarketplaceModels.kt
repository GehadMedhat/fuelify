package com.authapi.models

import kotlinx.serialization.Serializable

// ─── Request DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class RedeemRewardRequest(
    val rewardId: Int
)

@Serializable
data class EarnPointsRequest(
    val userId: Int,
    val orderId: Int,
    val orderAmountCents: Int
)

// Admin
@Serializable
data class CreateRewardRequest(
    val rewardName: String,
    val pointsRequired: Int,
    val category: String,
    val imageUrl: String? = null,
    val description: String? = null,
    val termsAndConditions: String? = null
)

@Serializable
data class UpdateRewardRequest(
    val rewardName: String? = null,
    val pointsRequired: Int? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val termsAndConditions: String? = null
)

// ─── Response DTOs ────────────────────────────────────────────────────────────

@Serializable
data class RewardResponse(
    val rewardId: Int,
    val rewardName: String,
    val pointsRequired: Int,
    val category: String,
    val imageUrl: String?,
    val description: String?,
    val termsAndConditions: String?
)

@Serializable
data class MarketplaceListResponse(
    val rewards: List<RewardResponse>,
    val total: Int,
    val userPoints: Int
)

@Serializable
data class UserRewardResponse(
    val userRewardId: Int,
    val rewardId: Int,
    val rewardName: String,
    val category: String,
    val earnedDate: String
)

@Serializable
data class UserPointsResponse(
    val userId: Int,
    val balance: Int
)