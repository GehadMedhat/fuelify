package com.example.fuelify.data.api.models

import com.google.gson.annotations.SerializedName

data class MedicalInfo(
    @SerializedName("conditions")   val conditions:   List<String>,
    @SerializedName("allergies")    val allergies:    List<String>,
    @SerializedName("medications")  val medications:  List<String>,
    @SerializedName("hideWeight")   val hideWeight:   Boolean,
    @SerializedName("hideCalories") val hideCalories: Boolean
)

data class SaveMedicalInfoRequest(
    @SerializedName("conditions")   val conditions:   List<String>,
    @SerializedName("allergies")    val allergies:    List<String>,
    @SerializedName("medications")  val medications:  List<String>,
    @SerializedName("hideWeight")   val hideWeight:   Boolean = false,
    @SerializedName("hideCalories") val hideCalories: Boolean = false,
    @SerializedName("labResults")   val labResults:   LabResults? = null
)

data class MedicalAlert(
    @SerializedName("alertId")     val alertId:     Int,
    @SerializedName("alertType")   val alertType:   String,
    @SerializedName("title")       val title:       String,
    @SerializedName("message")     val message:     String,
    @SerializedName("suggestion")  val suggestion:  String,
    @SerializedName("severity")    val severity:    String,
    @SerializedName("isDismissed") val isDismissed: Boolean,
    @SerializedName("isApplied")   val isApplied:   Boolean
)

data class AlertActionRequest(
    @SerializedName("action") val action: String   // "dismiss" | "apply"
)

data class PlanItem(
    @SerializedName("name")   val name:   String,
    @SerializedName("reason") val reason: String
)

data class SmartPlan(
    @SerializedName("conditions")               val conditions:               List<String>,
    @SerializedName("dietRemovedItems")         val dietRemovedItems:         List<PlanItem>,
    @SerializedName("dietAddedItems")           val dietAddedItems:           List<PlanItem>,
    @SerializedName("doctorRecommendations")    val doctorRecommendations:    List<String>,
    @SerializedName("workoutRemovedItems")      val workoutRemovedItems:      List<PlanItem>,
    @SerializedName("workoutAddedItems")        val workoutAddedItems:        List<PlanItem>,
    @SerializedName("recoveryRecommendations")  val recoveryRecommendations:  List<String>
)

data class DailyProgress(
    @SerializedName("dayLabel")     val dayLabel:     String,
    @SerializedName("calories")     val calories:     Int,
    @SerializedName("workoutDone")  val workoutDone:  Boolean,
    @SerializedName("progressPct")  val progressPct:  Int
)

data class LabResults(
    @SerializedName("hba1c")                   val hba1c: Double? = null,
    @SerializedName("totalCholesterol")        val totalCholesterol: Double? = null,
    @SerializedName("ldl")                     val ldl: Double? = null,
    @SerializedName("hdl")                     val hdl: Double? = null,
    @SerializedName("triglycerides")           val triglycerides: Double? = null,
    @SerializedName("fastingGlucose")          val fastingGlucose: Double? = null,
    @SerializedName("tsh")                     val tsh: Double? = null,
    @SerializedName("bloodPressureSystolic")   val bloodPressureSystolic: Int? = null,
    @SerializedName("bloodPressureDiastolic")  val bloodPressureDiastolic: Int? = null,
    @SerializedName("notes")                   val notes: String = ""
)

data class HealthReport(
    @SerializedName("conditions")             val conditions:             List<String>,
    @SerializedName("allergies")              val allergies:              List<String>,
    @SerializedName("medications")            val medications:            List<String>,
    @SerializedName("workoutsCompleted")      val workoutsCompleted:      Int,
    @SerializedName("workoutsTotal")          val workoutsTotal:          Int,
    @SerializedName("mealsLogged")            val mealsLogged:            Int,
    @SerializedName("mealsTotal")             val mealsTotal:             Int,
    @SerializedName("totalCaloriesThisWeek")  val totalCaloriesThisWeek:  Int,
    @SerializedName("avgDailyCalories")       val avgDailyCalories:       Int,
    @SerializedName("hideWeight")             val hideWeight:             Boolean,
    @SerializedName("hideCalories")           val hideCalories:           Boolean,
    @SerializedName("weeklyProgress")         val weeklyProgress:         List<Int>,
    @SerializedName("dailyProgress")          val dailyProgress:          List<DailyProgress>
)

data class PrivacyRequest(
    @SerializedName("hideWeight")   val hideWeight:   Boolean,
    @SerializedName("hideCalories") val hideCalories: Boolean
)

data class MealSwap(
    @SerializedName("planId")       val planId:       Int,
    @SerializedName("planDate")     val planDate:     String,
    @SerializedName("mealType")     val mealType:     String,
    @SerializedName("oldMealId")    val oldMealId:    Int,
    @SerializedName("oldMealName")  val oldMealName:  String,
    @SerializedName("newMealId")    val newMealId:    Int,
    @SerializedName("newMealName")  val newMealName:  String,
    @SerializedName("reason")       val reason:       String
)

data class WorkoutSwap(
    @SerializedName("planId")         val planId:         Int,
    @SerializedName("scheduledDate")  val scheduledDate:  String,
    @SerializedName("oldWorkoutId")   val oldWorkoutId:   Int,
    @SerializedName("oldWorkoutName") val oldWorkoutName: String,
    @SerializedName("newWorkoutId")   val newWorkoutId:   Int,
    @SerializedName("newWorkoutName") val newWorkoutName: String,
    @SerializedName("reason")         val reason:         String
)

data class SmartPlanPreview(
    @SerializedName("mealSwaps")    val mealSwaps:    List<MealSwap>,
    @SerializedName("workoutSwaps") val workoutSwaps: List<WorkoutSwap>,
    @SerializedName("totalChanges") val totalChanges: Int
)

data class SmartPlanApplyResult(
    @SerializedName("mealsReplaced")    val mealsReplaced:    Int,
    @SerializedName("workoutsReplaced") val workoutsReplaced: Int,
    @SerializedName("message")          val message:          String
)

// ── Alerts response — matches backend AlertsResponseDto exactly ───────────────

data class AlertsResponse(
    @SerializedName("alerts")       val alerts:       List<MedicalAlert> = emptyList(),
    @SerializedName("alertsToday")  val alertsToday:  Int = 0,
    @SerializedName("swapsApplied") val swapsApplied: Int = 0
)

// Response wrappers
data class MedicalInfoResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: MedicalInfo?
)

data class SmartPlanResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: SmartPlan?
)

data class SmartPlanPreviewResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: SmartPlanPreview?
)

data class AlertsApiResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String = "",
    @SerializedName("data")    val data: AlertsResponse? = null
)

data class ApplyResultResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data")    val data: SmartPlanApplyResult?
)

data class HealthReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: HealthReport?
)
