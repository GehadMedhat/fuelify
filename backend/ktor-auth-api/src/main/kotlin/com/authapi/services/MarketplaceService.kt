package com.authapi.services

import com.authapi.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class MarketplaceService {

    private val logger = LoggerFactory.getLogger(MarketplaceService::class.java)
    private val pointsPerCent = 0.1

    // ─────────────────────────────────────────────────────────────────────────
    // MARKETPLACE — Browse rewards
    // ─────────────────────────────────────────────────────────────────────────

    fun getRewards(
        userId: Int,
        category: String? = null
    ): ApiResponse<MarketplaceListResponse> {
        val (rewards, userPoints) = transaction {
            val query = Reward.selectAll()

            if (!category.isNullOrBlank() && category.lowercase() != "all") {
                query.andWhere { Reward.category eq category }
            }

            val rows = query
                .orderBy(Reward.pointsRequired, SortOrder.ASC)
                .map { rowToRewardResponse(it) }

            val points = getUserPointsBalance(userId)
            rows to points
        }

        return ApiResponse(
            true, "Rewards retrieved",
            MarketplaceListResponse(
                rewards    = rewards,
                total      = rewards.size,
                userPoints = userPoints
            )
        )
    }

    fun getRewardById(rewardId: Int): ApiResponse<RewardResponse> {
        val reward = transaction {
            Reward.selectAll()
                .where { Reward.rewardId eq rewardId }
                .singleOrNull()
                ?.let { rowToRewardResponse(it) }
        } ?: throw NotFoundException("Reward not found")

        return ApiResponse(true, "Reward retrieved", reward)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POINTS
    // ─────────────────────────────────────────────────────────────────────────

    fun getUserPoints(userId: Int): ApiResponse<UserPointsResponse> {
        val balance = transaction { getUserPointsBalance(userId) }
        return ApiResponse(true, "Points retrieved", UserPointsResponse(userId, balance))
    }

    fun earnPointsFromOrder(req: EarnPointsRequest): ApiResponse<UserPointsResponse> {
        if (req.orderAmountCents <= 0)
            throw ValidationException(listOf("Order amount must be greater than zero"))

        val pointsEarned = (req.orderAmountCents * pointsPerCent).toInt()
        if (pointsEarned <= 0)
            throw ValidationException(listOf("Order amount too small to earn points"))

        val newBalance = transaction {
            val user = Users.selectAll()
                .where { Users.id eq req.userId }
                .singleOrNull() ?: throw NotFoundException("User not found")

            val currentPoints = user[Users.points] ?: 0

            Users.update({ Users.id eq req.userId }) {
                it[Users.points]    = currentPoints + pointsEarned
                it[Users.updatedAt] = LocalDateTime.now()
            }

            getUserPointsBalance(req.userId)
        }

        logger.info("User ${req.userId} earned $pointsEarned points from order ${req.orderId}")
        return ApiResponse(true, "Points earned", UserPointsResponse(req.userId, newBalance))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REDEMPTIONS
    // ─────────────────────────────────────────────────────────────────────────

    fun redeemReward(userId: Int, req: RedeemRewardRequest): ApiResponse<UserRewardResponse> {
        val result = transaction {
            val reward = Reward.selectAll()
                .where { Reward.rewardId eq req.rewardId }
                .singleOrNull() ?: throw NotFoundException("Reward not found")

            val pointsRequired = reward[Reward.pointsRequired]

            val currentBalance = getUserPointsBalance(userId)
            if (currentBalance < pointsRequired)
                throw ValidationException(
                    listOf("Not enough points. You have $currentBalance pts, need $pointsRequired pts")
                )

            Users.update({ Users.id eq userId }) {
                it[Users.points]    = currentBalance - pointsRequired
                it[Users.updatedAt] = LocalDateTime.now()
            }

            val today = LocalDate.now()
            val userRewardId = UserReward.insert {
                it[UserReward.userId]     = userId
                it[UserReward.rewardId]   = req.rewardId
                it[UserReward.earnedDate] = today
            } get UserReward.userRewardId

            UserRewardResponse(
                userRewardId = userRewardId,
                rewardId     = req.rewardId,
                rewardName   = reward[Reward.rewardName],
                category     = reward[Reward.category],
                earnedDate   = today.toString()
            )
        }

        logger.info("User $userId redeemed reward ${req.rewardId}")
        return ApiResponse(true, "Reward redeemed! Collect it with your next order.", result)
    }

    fun getUserRewards(userId: Int): ApiResponse<List<UserRewardResponse>> {
        val list = transaction {
            (UserReward innerJoin Reward)
                .selectAll()
                .where { UserReward.userId eq userId }
                .orderBy(UserReward.earnedDate, SortOrder.DESC)
                .map { row ->
                    UserRewardResponse(
                        userRewardId = row[UserReward.userRewardId],
                        rewardId     = row[UserReward.rewardId],
                        rewardName   = row[Reward.rewardName],
                        category     = row[Reward.category],
                        earnedDate   = row[UserReward.earnedDate].toString()
                    )
                }
        }
        return ApiResponse(true, "User rewards retrieved", list)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN
    // ─────────────────────────────────────────────────────────────────────────

    fun adminCreateReward(req: CreateRewardRequest): ApiResponse<RewardResponse> {
        validateRewardRequest(req.rewardName, req.category, req.pointsRequired)

        val reward = transaction {
            val id = Reward.insert {
                it[rewardName]           = req.rewardName.trim()
                it[pointsRequired]       = req.pointsRequired
                it[category]             = req.category
                it[imageUrl]             = req.imageUrl
                it[description]          = req.description
                it[termsAndConditions]   = req.termsAndConditions
            } get Reward.rewardId

            Reward.selectAll()
                .where { Reward.rewardId eq id }
                .single()
                .let { rowToRewardResponse(it) }
        }

        return ApiResponse(true, "Reward created", reward)
    }

    fun adminUpdateReward(rewardId: Int, req: UpdateRewardRequest): ApiResponse<RewardResponse> {
        val reward = transaction {
            Reward.selectAll()
                .where { Reward.rewardId eq rewardId }
                .singleOrNull() ?: throw NotFoundException("Reward not found")

            Reward.update({ Reward.rewardId eq rewardId }) {
                if (req.rewardName != null)          it[rewardName]          = req.rewardName.trim()
                if (req.pointsRequired != null)      it[pointsRequired]      = req.pointsRequired
                if (req.category != null)            it[category]            = req.category
                if (req.imageUrl != null)            it[imageUrl]            = req.imageUrl
                if (req.description != null)         it[description]         = req.description
                if (req.termsAndConditions != null)  it[termsAndConditions]  = req.termsAndConditions
            }

            Reward.selectAll()
                .where { Reward.rewardId eq rewardId }
                .single()
                .let { rowToRewardResponse(it) }
        }

        return ApiResponse(true, "Reward updated", reward)
    }

    fun adminDeleteReward(rewardId: Int): ApiResponse<Unit> {
        transaction {
            val count = Reward.selectAll()
                .where { Reward.rewardId eq rewardId }
                .count()
            if (count == 0L) throw NotFoundException("Reward not found")
            Reward.deleteWhere { Reward.rewardId eq rewardId }
        }
        return ApiResponse(success = true, message = "Reward deleted", data = Unit)
    }

    fun adminGetAllRewards(): ApiResponse<List<RewardResponse>> {
        val rewards = transaction {
            Reward.selectAll()
                .orderBy(Reward.rewardId, SortOrder.ASC)
                .map { rowToRewardResponse(it) }
        }
        return ApiResponse(true, "All rewards retrieved", rewards)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun getUserPointsBalance(userId: Int): Int {
        return Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
            ?.get(Users.points) ?: 0
    }

    private fun validateRewardRequest(name: String, category: String, pointsRequired: Int) {
        if (name.isBlank())
            throw ValidationException(listOf("Reward name is required"))
        if (pointsRequired <= 0)
            throw ValidationException(listOf("Points required must be greater than zero"))
        val validCategories = listOf("Lifestyle", "Gym", "All")
        if (category !in validCategories)
            throw ValidationException(listOf("Invalid category. Valid values: $validCategories"))
    }

    private fun rowToRewardResponse(row: ResultRow) = RewardResponse(
        rewardId           = row[Reward.rewardId],
        rewardName         = row[Reward.rewardName],
        pointsRequired     = row[Reward.pointsRequired],
        category           = row[Reward.category],
        imageUrl           = row[Reward.imageUrl],
        description        = row[Reward.description],
        termsAndConditions = row[Reward.termsAndConditions]
    )
}