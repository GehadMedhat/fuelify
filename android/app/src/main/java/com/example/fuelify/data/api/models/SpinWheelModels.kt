package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

data class SpinWheelMeal(
    @SerializedName("meal_id")       val mealId:       Int,
    @SerializedName("meal_name")     val mealName:     String,
    @SerializedName("image_url")     val imageUrl:     String,
    @SerializedName("calories")      val calories:     Int,
    @SerializedName("protein")       val protein:      Int,
    @SerializedName("carbs")         val carbs:        Int,
    @SerializedName("fat")           val fat:          Int,
    @SerializedName("meal_time")     val mealTime:     String,
    @SerializedName("diet_type")     val dietType:     String,
    @SerializedName("difficulty")    val difficulty:   String,
    @SerializedName("eco_score")     val ecoScore:     Double,
    @SerializedName("base_price")    val basePrice:    Double,
    @SerializedName("discount_pct")  val discountPct:  Int,
    @SerializedName("discount_egp")  val discountEgp:  Double,
    @SerializedName("final_price")   val finalPrice:   Double,
    @SerializedName("discount_code") val discountCode: String,
    @SerializedName("expires_at")    val expiresAt:    String,
    @SerializedName("spin_reason")   val spinReason:   String
)

data class SpinWheelResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    SpinWheelMeal?
)
