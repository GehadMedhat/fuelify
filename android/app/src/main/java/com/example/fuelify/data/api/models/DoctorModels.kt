package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

// ── Requests ──────────────────────────────────────────────────────────────────

data class DoctorOnboardRequest(
    @SerializedName("email")         val email:         String,
    @SerializedName("password")      val password:      String,
    @SerializedName("fullName")      val fullName:      String,
    @SerializedName("specialty")     val specialty:     String,
    @SerializedName("qualification") val qualification: String = "",
    @SerializedName("hospital")      val hospital:      String = "",
    @SerializedName("yearsExp")      val yearsExp:      Int    = 0,
    @SerializedName("bio")           val bio:           String = "",
    @SerializedName("documentName")  val documentName:  String = ""
)

data class DoctorLoginRequest(
    @SerializedName("email")    val email:    String,
    @SerializedName("password") val password: String
)

data class DoctorRespondRequest(
    @SerializedName("content") val content: String
)

// ── Responses ─────────────────────────────────────────────────────────────────

data class DoctorProfile(
    @SerializedName("doctorId")      val doctorId:      Int,
    @SerializedName("fullName")      val fullName:      String,
    @SerializedName("specialty")     val specialty:     String,
    @SerializedName("qualification") val qualification: String,
    @SerializedName("hospital")      val hospital:      String,
    @SerializedName("yearsExp")      val yearsExp:      Int,
    @SerializedName("bio")           val bio:           String,
    @SerializedName("isApproved")    val isApproved:    Boolean,
    @SerializedName("pricePerCase")  val pricePerCase:  Int
)

data class DoctorLoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    DoctorProfile?
)

data class DoctorRegisterResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    Map<String, Any>?
)

data class InboxCase(
    @SerializedName("caseId")          val caseId:          Int,
    @SerializedName("patientName")     val patientName:     String,
    @SerializedName("conditionName")   val conditionName:   String,
    @SerializedName("symptoms")        val symptoms:        String,
    @SerializedName("specialtyNeeded") val specialtyNeeded: String,
    @SerializedName("status")          val status:          String,
    @SerializedName("doctorResponded") val doctorResponded: Boolean,
    @SerializedName("createdAt")       val createdAt:       String,
    @SerializedName("messageCount")    val messageCount:    Int
)

data class InboxResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    List<InboxCase>?
)

data class DoctorMessage(
    @SerializedName("messageId")  val messageId:  Int,
    @SerializedName("senderType") val senderType: String,
    @SerializedName("senderName") val senderName: String,
    @SerializedName("content")    val content:    String,
    @SerializedName("fileNames")  val fileNames:  List<String>,
    @SerializedName("sentAt")     val sentAt:     String
)

data class CaseDetail(
    @SerializedName("caseId")        val caseId:        Int,
    @SerializedName("patientName")   val patientName:   String,
    @SerializedName("conditionName") val conditionName: String,
    @SerializedName("affectedArea")  val affectedArea:  String,
    @SerializedName("symptoms")      val symptoms:      String,
    @SerializedName("limitations")   val limitations:   String,
    @SerializedName("status")        val status:        String,
    @SerializedName("messages")      val messages:      List<DoctorMessage>
)

data class CaseDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    CaseDetail?
)

data class WalletTransaction(
    @SerializedName("txId")        val txId:        Int,
    @SerializedName("caseId")      val caseId:      Int?,
    @SerializedName("patientName") val patientName: String,
    @SerializedName("amount")      val amount:      Double,
    @SerializedName("type")        val type:        String,
    @SerializedName("description") val description: String,
    @SerializedName("createdAt")   val createdAt:   String
)

data class DoctorWallet(
    @SerializedName("balance")      val balance:      Double,
    @SerializedName("transactions") val transactions: List<WalletTransaction>
)

data class WalletResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data:    DoctorWallet?
)
