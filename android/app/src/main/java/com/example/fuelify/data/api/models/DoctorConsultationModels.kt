package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

data class ConsultationMessage(
    @SerializedName("messageId")  val messageId:  Int,
    @SerializedName("senderType") val senderType: String,
    @SerializedName("senderName") val senderName: String,
    @SerializedName("content")    val content:    String,
    @SerializedName("fileNames")  val fileNames:  List<String>,
    @SerializedName("sentAt")     val sentAt:     String
)

data class ConsultationCase(
    @SerializedName("caseId")              val caseId:             Int,
    @SerializedName("conditionName")       val conditionName:      String,
    @SerializedName("affectedArea")        val affectedArea:       String,
    @SerializedName("symptoms")            val symptoms:           String,
    @SerializedName("limitations")         val limitations:        String,
    @SerializedName("status")              val status:             String,
    @SerializedName("adjustmentsApplied")  val adjustmentsApplied: Boolean,
    @SerializedName("createdAt")           val createdAt:          String,
    @SerializedName("messages")            val messages:           List<ConsultationMessage>
)

// Lightweight summary for the cases list screen
data class ConsultationCaseSummary(
    @SerializedName("caseId")        val caseId:        Int,
    @SerializedName("conditionName") val conditionName: String,
    @SerializedName("affectedArea")  val affectedArea:  String,
    @SerializedName("status")        val status:        String,
    @SerializedName("createdAt")     val createdAt:     String,
    @SerializedName("messageCount")  val messageCount:  Int
)

data class CreateConsultationRequest(
    @SerializedName("conditionName") val conditionName: String,
    @SerializedName("affectedArea")  val affectedArea:  String,
    @SerializedName("symptoms")      val symptoms:      String,
    @SerializedName("limitations")   val limitations:   String = "",
    @SerializedName("fileNames")     val fileNames:     List<String> = emptyList()
)

data class SendMessageRequest(
    @SerializedName("content")   val content:   String,
    @SerializedName("fileNames") val fileNames: List<String> = emptyList()
)

data class UpdateCaseRequest(
    @SerializedName("status") val status: String
)

data class ConsultationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    ConsultationCase?
)

// Response for the cases list
data class ConsultationListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    List<ConsultationCaseSummary>?
)

data class CreateConsultationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    Int?
)
