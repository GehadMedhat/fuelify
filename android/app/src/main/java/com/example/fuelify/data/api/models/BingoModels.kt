package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

data class BingoCell(
    @SerializedName("id")        val id:        Int,
    @SerializedName("task")      val task:      String,
    @SerializedName("emoji")     val emoji:     String,
    @SerializedName("category")  val category:  String,
    @SerializedName("target")    val target:    Int,
    @SerializedName("current")   val current:   Int,
    @SerializedName("completed") val completed: Boolean
)

data class BingoCard(
    @SerializedName("week_start")    val weekStart:    String,
    @SerializedName("week_end")      val weekEnd:      String,
    @SerializedName("cells")         val cells:        List<BingoCell>,
    @SerializedName("rows_complete") val rowsComplete: Int,
    @SerializedName("cols_complete") val colsComplete: Int,
    @SerializedName("has_bingo")     val hasBingo:     Boolean,
    @SerializedName("total_done")    val totalDone:    Int,
    @SerializedName("reward_msg")    val rewardMsg:    String
)

data class BingoResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    BingoCard?
)
