package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

data class EcoMealScore(
    @SerializedName("mealId")      val mealId: Int,
    @SerializedName("mealName")    val mealName: String,
    @SerializedName("imageUrl")    val imageUrl: String,
    @SerializedName("ecoGrade")    val ecoGrade: String,
    @SerializedName("ecoScore")    val ecoScore: Double,
    @SerializedName("carbonLevel") val carbonLevel: String,
    @SerializedName("originType")  val originType: String,
    @SerializedName("packaging")   val packaging: String
)

data class EcoSustainability(
    @SerializedName("weeklyGrade")   val weeklyGrade: String,
    @SerializedName("weeklyScore")   val weeklyScore: Double,
    @SerializedName("gradeMessage")  val gradeMessage: String,
    @SerializedName("mealScores")    val mealScores: List<EcoMealScore>,
    @SerializedName("suggestions")   val suggestions: List<String>
)

data class EcoResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: EcoSustainability?
)
