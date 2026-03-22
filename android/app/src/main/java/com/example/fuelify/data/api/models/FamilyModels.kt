package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

// ── Family Member ─────────────────────────────────────────────────────────────

data class FamilyMember(
    @SerializedName("memberId")           val memberId: Int,
    @SerializedName("userId")             val userId: Int,
    @SerializedName("name")               val name: String,
    @SerializedName("email")              val email: String,
    @SerializedName("goal")               val goal: String,
    @SerializedName("role")               val role: String,
    @SerializedName("streakDays")         val streakDays: Int,
    @SerializedName("caloriesGoal")       val caloriesGoal: Int,
    @SerializedName("caloriesEatenToday") val caloriesEatenToday: Int
)

// ── Week Day ──────────────────────────────────────────────────────────────────

data class WeekDay(
    @SerializedName("date")       val date: String,
    @SerializedName("dayLabel")   val dayLabel: String,
    @SerializedName("dayNumber")  val dayNumber: Int,
    @SerializedName("monthLabel") val monthLabel: String,
    @SerializedName("hasPlans")   val hasPlans: Boolean,
    @SerializedName("isToday")    val isToday: Boolean
)

// ── Family Group ──────────────────────────────────────────────────────────────

data class FamilyGroup(
    @SerializedName("groupId")           val groupId: Int,
    @SerializedName("name")              val name: String,
    @SerializedName("createdBy")         val createdBy: Int,
    @SerializedName("members")           val members: List<FamilyMember>,
    @SerializedName("totalMealsPlanned") val totalMealsPlanned: Int,
    @SerializedName("weekDays")          val weekDays: List<WeekDay>
)

data class FamilyGroupResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: FamilyGroup?
)

// ── Family Grocery ────────────────────────────────────────────────────────────

data class FamilyGroceryItem(
    @SerializedName("itemId")        val itemId: Int,
    @SerializedName("itemName")      val itemName: String,
    @SerializedName("quantity")      val quantity: String,
    @SerializedName("addedBy")       val addedBy: Int,
    @SerializedName("addedByName")   val addedByName: String,
    @SerializedName("isChecked")     val isChecked: Boolean,
    @SerializedName("checkedBy")     val checkedBy: Int?,
    @SerializedName("checkedByName") val checkedByName: String?
)

data class FamilyGroceryResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<FamilyGroceryItem>?
)

// ── Requests ──────────────────────────────────────────────────────────────────

data class InviteMemberRequest(
    @SerializedName("email") val email: String
)

data class AddFamilyGroceryRequest(
    @SerializedName("itemName")  val itemName: String,
    @SerializedName("quantity")  val quantity: String
)

data class CheckFamilyGroceryRequest(
    @SerializedName("isChecked") val isChecked: Boolean
)

data class RenameFamilyRequest(
    @SerializedName("name") val name: String
)

data class MemberPreview(
    @SerializedName("userId")       val userId: Int,
    @SerializedName("name")         val name: String,
    @SerializedName("goal")         val goal: String,
    @SerializedName("email")        val email: String,
    @SerializedName("alreadyMember") val alreadyMember: Boolean
)

data class MemberPreviewResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data")    val data: MemberPreview?
)
