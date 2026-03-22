package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

data class ScannedPantryItem(
    @SerializedName("itemId")          val itemId: Int,
    @SerializedName("productName")     val productName: String,
    @SerializedName("barcode")         val barcode: String,
    @SerializedName("quantity")        val quantity: Double,
    @SerializedName("unit")            val unit: String,
    @SerializedName("expiryDate")      val expiryDate: String,
    @SerializedName("daysUntilExpiry") val daysUntilExpiry: Int,
    @SerializedName("calories")        val calories: Double,
    @SerializedName("protein")         val protein: Double,
    @SerializedName("carbs")           val carbs: Double,
    @SerializedName("fat")             val fat: Double,
    @SerializedName("nutriScore")      val nutriScore: String
)

data class AddScannedPantryRequest(
    @SerializedName("productName") val productName: String,
    @SerializedName("barcode")     val barcode: String,
    @SerializedName("quantity")    val quantity: Double,
    @SerializedName("unit")        val unit: String,
    @SerializedName("expiryDate")  val expiryDate: String,
    @SerializedName("calories")    val calories: Double,
    @SerializedName("protein")     val protein: Double,
    @SerializedName("carbs")       val carbs: Double,
    @SerializedName("fat")         val fat: Double,
    @SerializedName("nutriScore")  val nutriScore: String
)
