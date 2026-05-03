package com.authapi.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date

// ─── Reward (Marketplace Items) ───────────────────────────────────────────────
// Maps to existing "reward" table

object Reward : Table("reward") {
    val rewardId            = integer("reward_id").autoIncrement()
    val rewardName          = varchar("reward_name", 255)
    val pointsRequired      = integer("points_required")
    val category            = varchar("category", 100)
    val imageUrl            = text("image_url").nullable()
    val description         = text("description").nullable()
    val termsAndConditions  = varchar("terms_and_conditions", 500).nullable()

    override val primaryKey = PrimaryKey(rewardId)
}

// ─── User Reward (Redemptions) ────────────────────────────────────────────────
// Maps to existing "user_reward" table

object UserReward : Table("user_reward") {
    val userRewardId = integer("user_reward_id").autoIncrement()
    val userId       = integer("user_id").references(Users.id)
    val rewardId     = integer("reward_id").references(Reward.rewardId)
    val earnedDate   = date("earned_date")

    override val primaryKey = PrimaryKey(userRewardId)
}