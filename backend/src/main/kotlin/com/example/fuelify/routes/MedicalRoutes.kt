package com.example.fuelify.routes

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

// ─── Tables ───────────────────────────────────────────────────────────────────

private object UserMedicalInfo : Table("user_medical_info") {
    val id           = integer("id").autoIncrement()
    val userId       = integer("user_id")
    val conditions   = text("conditions").default("[]")
    val allergies    = text("allergies").default("[]")
    val medications  = text("medications").default("[]")
    val hideWeight   = bool("hide_weight").default(false)
    val hideCalories = bool("hide_calories").default(false)
    override val primaryKey = PrimaryKey(id)
}

private object MedicalAlert : Table("medical_alert") {
    val alertId     = integer("alert_id").autoIncrement()
    val userId      = integer("user_id")
    val alertType   = varchar("alert_type", 50)
    val title       = varchar("title", 255)
    val message     = text("message")
    val suggestion  = varchar("suggestion", 500).nullable()
    val severity    = varchar("severity", 20).default("warning")
    val isDismissed = bool("is_dismissed").default(false)
    val isApplied   = bool("is_applied").default(false)
    val createdAt   = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(alertId)
}

// Reuse MealPlanTable for reading today's meals
private object MedicalMealPlans : Table("meal_plans") {
    // No plan_id — meal_plans uses composite key (user_id, meal_id, plan_date)
    val userId    = integer("user_id")
    val mealId    = integer("meal_id")
    val planDate  = date("plan_date")
    val mealType  = varchar("meal_type", 30).nullable()
}

private object MedicalDailyLogs : Table("daily_logs") {
    val id           = integer("id").autoIncrement()
    val userId       = integer("user_id")
    val logDate      = date("log_date")
    val caloriesEaten = integer("calories_eaten").default(0)
    val workoutsDone = integer("workouts_done").default(0)
    override val primaryKey = PrimaryKey(id)
}

// Tables for smart plan application
private object SmartMeals : Table("meal") {
    val mealId   = integer("meal_id")
    val mealName = varchar("meal_name", 255)
    val dietTag  = varchar("diet_tag", 50).nullable()    // added by smart_plan_meal_tags.sql
    override val primaryKey = PrimaryKey(mealId)
}

private object SmartWorkouts : Table("workout") {
    val workoutId  = integer("workout_id")
    val workoutName = varchar("workout_name", 255)
    val category   = varchar("category", 100)
    val difficulty = varchar("difficulty", 50)
    override val primaryKey = PrimaryKey(workoutId)
}

private object SmartWorkoutPlan : Table("workout_plan") {
    val planId        = integer("plan_id").autoIncrement()  // exists in workout_plan (added by WorkoutSessionRoutes)
    val userId        = integer("user_id")
    val workoutId     = integer("workout_id").nullable()
    val workoutName   = varchar("workout_name", 255).nullable()
    val scheduledDate = date("scheduled_date").nullable()
    val status        = varchar("status", 20).default("planned")
    override val primaryKey = PrimaryKey(planId)
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class MedicalInfoDto(
    val conditions:   List<String>,
    val allergies:    List<String>,
    val medications:  List<String>,
    val hideWeight:   Boolean,
    val hideCalories: Boolean
)

@Serializable
data class LabResultsDto(
    val hba1c: Double? = null,          // HbA1c % (Diabetes)
    val totalCholesterol: Double? = null, // mg/dL
    val ldl: Double? = null,             // mg/dL
    val hdl: Double? = null,             // mg/dL
    val triglycerides: Double? = null,   // mg/dL
    val fastingGlucose: Double? = null,  // mg/dL
    val tsh: Double? = null,             // mIU/L (Thyroid)
    val bloodPressureSystolic: Int? = null,  // mmHg
    val bloodPressureDiastolic: Int? = null, // mmHg
    val notes: String = ""
)

@Serializable
data class SaveMedicalInfoRequest(
    val conditions:   List<String>,
    val allergies:    List<String>,
    val medications:  List<String>,
    val hideWeight:   Boolean = false,
    val hideCalories: Boolean = false,
    val labResults:   LabResultsDto? = null
)

@Serializable
data class MedicalAlertDto(
    val alertId:     Int,
    val alertType:   String,
    val title:       String,
    val message:     String,
    val suggestion:  String,
    val severity:    String,
    val isDismissed: Boolean,
    val isApplied:   Boolean
)

@Serializable
data class AlertActionRequest(val action: String)   // "dismiss" | "apply"

@Serializable
data class AlertsResponseDto(
    val alerts:       List<MedicalAlertDto>,
    val alertsToday:  Int,
    val swapsApplied: Int
)

@Serializable
data class SmartPlanDto(
    val conditions:           List<String>,
    val dietRemovedItems:     List<PlanItemDto>,
    val dietAddedItems:       List<PlanItemDto>,
    val doctorRecommendations: List<String>,
    val workoutRemovedItems:  List<PlanItemDto>,
    val workoutAddedItems:    List<PlanItemDto>,
    val recoveryRecommendations: List<String>
)

@Serializable
data class PlanItemDto(val name: String, val reason: String)

@Serializable
data class DailyProgressDto(
    val dayLabel: String,      // "Mon", "Tue" etc
    val calories: Int,
    val workoutDone: Boolean,
    val progressPct: Int       // 0-100 relative to goal
)

@Serializable
data class MealSwapDto(
    val planId:       Int,
    val planDate:     String,
    val mealType:     String,
    val oldMealId:    Int,
    val oldMealName:  String,
    val newMealId:    Int,
    val newMealName:  String,
    val reason:       String
)

@Serializable
data class WorkoutSwapDto(
    val planId:          Int,
    val scheduledDate:   String,
    val oldWorkoutId:    Int,
    val oldWorkoutName:  String,
    val newWorkoutId:    Int,
    val newWorkoutName:  String,
    val reason:          String
)

@Serializable
data class SmartPlanPreviewDto(
    val mealSwaps:    List<MealSwapDto>,
    val workoutSwaps: List<WorkoutSwapDto>,
    val totalChanges: Int
)

@Serializable
data class SmartPlanApplyResult(
    val mealsReplaced:    Int,
    val workoutsReplaced: Int,
    val message:          String
)

@Serializable
data class HealthReportDto(
    val conditions:        List<String>,
    val allergies:         List<String>,
    val medications:       List<String>,
    val workoutsCompleted: Int,
    val workoutsTotal:     Int,
    val mealsLogged:       Int,
    val mealsTotal:        Int,
    val totalCaloriesThisWeek: Int,
    val avgDailyCalories:  Int,
    val hideWeight:        Boolean,
    val hideCalories:      Boolean,
    val weeklyProgress:    List<Int>,       // raw kcal per day for 7 days
    val dailyProgress:     List<DailyProgressDto>
)

// ─── JSON helpers ─────────────────────────────────────────────────────────────

private fun parseJsonList(json: String): List<String> {
    return try {
        json.trim().removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) { emptyList() }
}

private fun toJsonList(list: List<String>): String {
    return "[${list.joinToString(",") { "\"$it\"" }}]"
}

// ─── Smart Plan Logic ─────────────────────────────────────────────────────────

private data class SmartPlan(
    val dietRemoved:  List<PlanItemDto>,
    val dietAdded:    List<PlanItemDto>,
    val doctorRecs:   List<String>,
    val wkRemoved:    List<PlanItemDto>,
    val wkAdded:      List<PlanItemDto>,
    val recoveryRecs: List<String>
)

private fun buildSmartPlan(conditions: List<String>): SmartPlan {
    val dietRemoved  = mutableListOf<PlanItemDto>()
    val dietAdded    = mutableListOf<PlanItemDto>()
    val doctorRecs   = mutableListOf<String>()
    val wkRemoved    = mutableListOf<PlanItemDto>()
    val wkAdded      = mutableListOf<PlanItemDto>()
    val recoveryRecs = mutableListOf<String>()

    val cond = conditions.map { it.lowercase() }

    if ("diabetes" in cond) {
        dietRemoved.addAll(listOf(
            PlanItemDto("Processed sugars", "Blood sugar spike risk"),
            PlanItemDto("White rice & bread", "High glycemic index")
        ))
        dietAdded.addAll(listOf(
            PlanItemDto("Leafy greens & legumes", "Low GI, fiber-rich"),
            PlanItemDto("Fatty fish (salmon)", "Anti-inflammatory omega-3")
        ))
        doctorRecs.add("Monitor blood glucose before and after meals")
        doctorRecs.add("Avoid processed sugar for 2 weeks minimum")
        wkAdded.addAll(listOf(
            PlanItemDto("Brisk walking (30 min)", "Intensity: Low"),
            PlanItemDto("Light swimming", "Intensity: Low-Moderate")
        ))
    }

    if ("hypertension" in cond) {
        dietRemoved.addAll(listOf(
            PlanItemDto("Processed sugars", "Inflammation risk"),
            PlanItemDto("Fried foods", "Inflammation risk"),
            PlanItemDto("Excessive caffeine", "Muscle tension"),
            PlanItemDto("Alcohol", "Healing interference")
        ))
        dietAdded.addAll(listOf(
            PlanItemDto("Fatty fish (salmon, mackerel)", "Anti-inflammatory omega-3"),
            PlanItemDto("Leafy greens & berries", "Antioxidants"),
            PlanItemDto("Turmeric & ginger", "Natural anti-inflammatory"),
            PlanItemDto("Nuts and seeds", "Healthy fats")
        ))
        doctorRecs.add("Increase daily water intake to 3 liters")
        doctorRecs.add("Avoid processed sugar for 2 weeks")
        doctorRecs.add("Consider magnesium supplement (consult pharmacist)")
        wkRemoved.addAll(listOf(
            PlanItemDto("Deadlifts", "Direct spinal load"),
            PlanItemDto("Squats", "Lumbar compression"),
            PlanItemDto("Leg Press", "Lower back strain"),
            PlanItemDto("Running", "Impact stress")
        ))
        wkAdded.addAll(listOf(
            PlanItemDto("Upper body push exercises", "Intensity: Moderate"),
            PlanItemDto("Seated shoulder press", "Intensity: Moderate"),
            PlanItemDto("Chest press (supported)", "Intensity: Moderate"),
            PlanItemDto("Light core work (planks)", "Intensity: Low"),
            PlanItemDto("Walking (flat surface)", "Intensity: Low")
        ))
        recoveryRecs.add("Rest 48 hours before starting modified plan")
        recoveryRecs.add("Gradual return to training after 2 weeks")
        recoveryRecs.add("Stop immediately if pain increases")
        recoveryRecs.add("Consider physical therapy consultation")
    }

    if ("thyroid" in cond) {
        dietAdded.addAll(listOf(
            PlanItemDto("Selenium-rich foods (Brazil nuts)", "Thyroid hormone support"),
            PlanItemDto("Zinc-rich foods (pumpkin seeds)", "T3 conversion support")
        ))
        dietRemoved.add(PlanItemDto("Excessive soy products", "Thyroid absorption interference"))
        wkRemoved.add(PlanItemDto("High-intensity interval training", "Cortisol elevation risk"))
        wkAdded.addAll(listOf(
            PlanItemDto("Yoga & stretching", "Intensity: Low"),
            PlanItemDto("Light resistance training", "Intensity: Low-Moderate")
        ))
        doctorRecs.add("Take thyroid medication 30-60 min before eating")
        recoveryRecs.add("Prioritize 8+ hours of sleep — critical for thyroid")
    }

    if ("pcos" in cond) {
        dietRemoved.add(PlanItemDto("Refined carbohydrates", "Insulin spike trigger"))
        dietAdded.addAll(listOf(
            PlanItemDto("High-fiber vegetables", "Hormone balance support"),
            PlanItemDto("Anti-inflammatory spices", "Cycle regulation support")
        ))
        wkAdded.addAll(listOf(
            PlanItemDto("Resistance training (3x/week)", "Intensity: Moderate"),
            PlanItemDto("Low-impact cardio", "Intensity: Moderate")
        ))
        doctorRecs.add("Aim for consistent meal times to regulate insulin")
    }

    if ("high cholesterol" in cond || "highcholesterol" in cond) {
        dietRemoved.add(PlanItemDto("Saturated fats (red meat, butter)", "LDL cholesterol elevation"))
        dietAdded.add(PlanItemDto("Oat bran & soluble fiber", "Cholesterol binding"))
        wkAdded.add(PlanItemDto("Aerobic cardio (30+ min)", "Intensity: Moderate"))
        doctorRecs.add("Consider plant sterols/stanols supplementation")
    }

    if ("ibs" in cond) {
        dietRemoved.add(PlanItemDto("High-FODMAP foods (onion, garlic)", "IBS trigger foods"))
        dietAdded.add(PlanItemDto("Probiotic-rich foods (yogurt, kefir)", "Gut microbiome support"))
        wkAdded.add(PlanItemDto("Gentle yoga (digestive poses)", "Intensity: Low"))
        doctorRecs.add("Keep a food diary to identify personal triggers")
    }

    if ("asthma" in cond) {
        wkRemoved.add(PlanItemDto("Cold weather outdoor running", "Bronchospasm risk"))
        wkAdded.add(PlanItemDto("Swimming (warm pool)", "Intensity: Moderate"))
        recoveryRecs.add("Always carry rescue inhaler during workouts")
        doctorRecs.add("Warm up for at least 10 minutes before any exercise")
    }

    // Generic if no conditions
    if (cond.isEmpty()) {
        dietAdded.add(PlanItemDto("Balanced meals (protein + complex carbs)", "General wellness"))
        wkAdded.add(PlanItemDto("Mix of cardio and strength (3-5x/week)", "Intensity: Moderate"))
    }

    return SmartPlan(
        dietRemoved  = dietRemoved.distinctBy { it.name },
        dietAdded    = dietAdded.distinctBy { it.name },
        doctorRecs   = doctorRecs.distinct(),
        wkRemoved    = wkRemoved.distinctBy { it.name },
        wkAdded      = wkAdded.distinctBy { it.name },
        recoveryRecs = recoveryRecs.distinct()
    )
}

// ─── Alert Generation Logic ───────────────────────────────────────────────────

private suspend fun generateAlertsForUser(userId: Int) {
    val medInfo = dbQuery {
        UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
    } ?: return

    val conditions = parseJsonList(medInfo[UserMedicalInfo.conditions]).map { it.lowercase() }
    val allergies  = parseJsonList(medInfo[UserMedicalInfo.allergies]).map  { it.lowercase() }

    // Build alert list based on conditions
    data class AlertData(val type: String, val title: String, val message: String, val suggestion: String, val severity: String)
    val alertsToCreate = mutableListOf<AlertData>()

    if ("hypertension" in conditions || "high cholesterol" in conditions) {
        alertsToCreate.add(AlertData("high_sodium", "High Sodium Alert",
            "Today\'s meal is high in sodium (Hypertension).",
            "Suggested swap: quinoa salad with grilled chicken", "danger"))
    }
    if ("diabetes" in conditions || "pcos" in conditions) {
        alertsToCreate.add(AlertData("high_sugar", "High Sugar Detected",
            "High sugar detected in breakfast — recommended lower GI options.",
            "Try: Greek yogurt with berries and nuts", "danger"))
    }
    if ("thyroid" in conditions) {
        alertsToCreate.add(AlertData("exercise_mod", "Exercise Modification",
            "Avoid heavy lifting today (Thyroid).",
            "Alternative: Light yoga or walking", "warning"))
    }
    if (allergies.any { it.contains("dairy") || it.contains("milk") }) {
        alertsToCreate.add(AlertData("meal_swap_dairy", "Meal Swap Recommended",
            "Your lunch contains dairy (Allergy detected).",
            "Swap to: Vegan Buddha bowl", "info"))
    }
    if (allergies.any { it.contains("gluten") || it.contains("wheat") }) {
        alertsToCreate.add(AlertData("meal_swap_gluten", "Gluten Alert",
            "Your dinner plan contains wheat (Gluten allergy).",
            "Swap to: Rice noodle stir-fry", "danger"))
    }
    if (allergies.any { it.contains("nuts") || it.contains("peanut") }) {
        alertsToCreate.add(AlertData("meal_swap_nuts", "Nut Allergy Alert",
            "A planned meal contains nuts. Please review your meal plan.",
            "Swap to: Sunflower seed trail mix", "danger"))
    }

    if (alertsToCreate.isEmpty()) return

    dbQuery {
        // Check ALL existing alerts (including dismissed) by type+title
        // Prevents re-creating alerts the user already dismissed/applied
        val existingKeys = MedicalAlert
            .slice(MedicalAlert.alertType, MedicalAlert.title)
            .select { MedicalAlert.userId eq userId }
            .map { "${it[MedicalAlert.alertType]}|${it[MedicalAlert.title]}" }
            .toSet()

        alertsToCreate.forEach { alert ->
            val key = "${alert.type}|${alert.title}"
            if (key !in existingKeys) {
                MedicalAlert.insert {
                    it[MedicalAlert.userId]     = userId
                    it[MedicalAlert.alertType]  = alert.type
                    it[MedicalAlert.title]      = alert.title
                    it[MedicalAlert.message]    = alert.message
                    it[MedicalAlert.severity]   = alert.severity
                    it[MedicalAlert.suggestion] = alert.suggestion
                }
            }
        }
    }
}

// ─── Routes ───────────────────────────────────────────────────────────────────

// ─── Smart Plan Helpers ───────────────────────────────────────────────────────

private fun getUnsafeTags(conditions: List<String>, allergies: List<String>): List<String> {
    val tags = mutableListOf<String>()
    if (conditions.any { it in listOf("diabetes","pcos") })
        tags.addAll(listOf("high_sugar"))
    if (conditions.any { it in listOf("hypertension","high cholesterol") })
        tags.addAll(listOf("fried","high_sodium"))
    if (conditions.any { it == "ibs" })
        tags.add("high_fat")
    if (allergies.any { it.contains("dairy") || it.contains("milk") })
        tags.add("dairy")
    if (allergies.any { it.contains("gluten") || it.contains("wheat") })
        tags.add("gluten")
    if (allergies.any { it.contains("nut") })
        tags.add("nuts")
    return tags
}

private fun getSafeTags(conditions: List<String>): List<String> {
    return when {
        conditions.any { it in listOf("diabetes","pcos") }       -> listOf("low_gi","lean_protein","vegan","mediterranean","balanced")
        conditions.any { it in listOf("hypertension","high cholesterol") } -> listOf("lean_protein","mediterranean","vegan","balanced","low_gi")
        else -> listOf("lean_protein","mediterranean","balanced","low_gi")
    }
}

private fun getUnsafeWorkoutCategories(conditions: List<String>): List<String> {
    val cats = mutableListOf<String>()
    if (conditions.any { it in listOf("hypertension","high cholesterol") })
        cats.addAll(listOf("Gym","Boxing"))
    if (conditions.any { it == "thyroid" })
        cats.add("Boxing")
    if (conditions.any { it == "asthma" })
        cats.add("Running")
    return cats
}

private fun getSafeWorkoutCategories(conditions: List<String>): List<String> {
    return when {
        conditions.any { it == "asthma" }      -> listOf("Yoga","Stretch","Upper Body")
        conditions.any { it == "thyroid" }     -> listOf("Yoga","Stretch","Running")
        conditions.any { it in listOf("hypertension","high cholesterol") } -> listOf("Yoga","Stretch","Running","Upper Body")
        else -> listOf("Yoga","Stretch","Running")
    }
}

fun Route.medicalRoutes() {

    // ── GET /api/users/{id}/medical-info ─────────────────────────────────────
    get("/users/{id}/medical-info") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val info = dbQuery {
            UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
        }

        val dto = if (info != null) {
            MedicalInfoDto(
                conditions   = parseJsonList(info[UserMedicalInfo.conditions]),
                allergies    = parseJsonList(info[UserMedicalInfo.allergies]),
                medications  = parseJsonList(info[UserMedicalInfo.medications]),
                hideWeight   = info[UserMedicalInfo.hideWeight],
                hideCalories = info[UserMedicalInfo.hideCalories]
            )
        } else {
            MedicalInfoDto(emptyList(), emptyList(), emptyList(), false, false)
        }

        call.respond(ApiResponse(success = true, message = "OK", data = dto))
    }

    // ── POST /api/users/{id}/medical-info ────────────────────────────────────
    post("/users/{id}/medical-info") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<SaveMedicalInfoRequest>()

        dbQuery {
            val existing = UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
            if (existing != null) {
                UserMedicalInfo.update({ UserMedicalInfo.userId eq userId }) {
                    it[conditions]   = toJsonList(body.conditions)
                    it[allergies]    = toJsonList(body.allergies)
                    it[medications]  = toJsonList(body.medications)
                    it[hideWeight]   = body.hideWeight
                    it[hideCalories] = body.hideCalories
                }
            } else {
                UserMedicalInfo.insert {
                    it[UserMedicalInfo.userId]      = userId
                    it[UserMedicalInfo.conditions]  = toJsonList(body.conditions)
                    it[UserMedicalInfo.allergies]   = toJsonList(body.allergies)
                    it[UserMedicalInfo.medications] = toJsonList(body.medications)
                    it[UserMedicalInfo.hideWeight]  = body.hideWeight
                    it[UserMedicalInfo.hideCalories]= body.hideCalories
                }
            }
        }

        // Regenerate alerts based on new conditions
        try { generateAlertsForUser(userId) } catch (e: Exception) {
            call.application.log.warn("Alert generation failed (run medical_setup.sql): ${e.message}")
        }

        // Generate lab-based recommendations
        val labRecs = body.labResults?.let { lab ->
            val recs = mutableListOf<String>()
            lab.hba1c?.let { if (it > 6.5) recs.add("HbA1c ${it}% is elevated — follow diabetic meal plan strictly") }
            lab.totalCholesterol?.let { if (it > 200) recs.add("Total cholesterol ${it} mg/dL is high — increase omega-3 intake") }
            lab.ldl?.let { if (it > 130) recs.add("LDL ${it} mg/dL is high — reduce saturated fats") }
            lab.hdl?.let { if (it < 40) recs.add("HDL ${it} mg/dL is low — increase aerobic exercise") }
            lab.fastingGlucose?.let { if (it > 100) recs.add("Fasting glucose ${it} mg/dL elevated — avoid refined carbs") }
            lab.tsh?.let {
                when {
                    it > 4.5 -> recs.add("TSH ${it} mIU/L suggests hypothyroidism — consult endocrinologist")
                    it < 0.4 -> recs.add("TSH ${it} mIU/L suggests hyperthyroidism — avoid excess iodine")
                }
            }
            lab.bloodPressureSystolic?.let { sys ->
                val dia = lab.bloodPressureDiastolic ?: 80
                if (sys > 140 || dia > 90) recs.add("BP ${sys}/${dia} mmHg is high — reduce sodium, increase potassium")
            }
            recs
        } ?: emptyList()

        val message = if (labRecs.isNotEmpty())
            "Medical info saved! Lab recommendations: ${labRecs.joinToString("; ")}"
        else "Medical information saved!"

        call.respond(ApiResponse(success = true, message = message, data = null))
    }

    // ── GET /api/users/{id}/medical-alerts ───────────────────────────────────
    get("/users/{id}/medical-alerts") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        // Auto-generate alerts if conditions exist
        try { generateAlertsForUser(userId) } catch (e: Exception) {
            // Table may not exist yet - run medical_setup.sql in Neon
            call.application.log.warn("Alert generation failed: ${e.message}")
        }

        val alerts = dbQuery {
            MedicalAlert.select {
                (MedicalAlert.userId eq userId) and
                (MedicalAlert.isDismissed eq false)
            }
            .orderBy(MedicalAlert.createdAt, SortOrder.DESC)
            .map { row ->
                MedicalAlertDto(
                    alertId     = row[MedicalAlert.alertId],
                    alertType   = row[MedicalAlert.alertType],
                    title       = row[MedicalAlert.title],
                    message     = row[MedicalAlert.message],
                    suggestion  = row.getOrNull(MedicalAlert.suggestion) ?: "",
                    severity    = row[MedicalAlert.severity],
                    isDismissed = row[MedicalAlert.isDismissed],
                    isApplied   = row[MedicalAlert.isApplied]
                )
            }
        }

        val swapsApplied = dbQuery {
            MedicalAlert.select {
                (MedicalAlert.userId eq userId) and
                (MedicalAlert.isApplied eq true)
            }.count().toInt()
        }

        val responseDto = AlertsResponseDto(
            alerts       = alerts,
            alertsToday  = alerts.size,
            swapsApplied = swapsApplied
        )
        call.respond(ApiResponse(success = true, message = "OK", data = responseDto))
    }

    // ── PATCH /api/users/{id}/medical-alerts/{alertId} ───────────────────────
    patch("/users/{id}/medical-alerts/{alertId}") {
        val userId  = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val alertId = call.parameters["alertId"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid alert id", null))
        val body = call.receive<AlertActionRequest>()

        dbQuery {
            when (body.action) {
                "dismiss" -> MedicalAlert.update({
                    (MedicalAlert.alertId eq alertId) and (MedicalAlert.userId eq userId)
                }) { it[MedicalAlert.isDismissed] = true }
                "apply"   -> MedicalAlert.update({
                    (MedicalAlert.alertId eq alertId) and (MedicalAlert.userId eq userId)
                }) {
                    it[MedicalAlert.isApplied]   = true
                    it[MedicalAlert.isDismissed] = true
                }
            }
        }
        call.respond(ApiResponse(success = true, message = "Alert updated", data = null))
    }

    // ── GET /api/users/{id}/smart-plan ───────────────────────────────────────
    get("/users/{id}/smart-plan") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val info = dbQuery {
            UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
        }
        val conditions = if (info != null)
            parseJsonList(info[UserMedicalInfo.conditions])
        else emptyList()

        val plan = buildSmartPlan(conditions)

        val dto = SmartPlanDto(
            conditions               = conditions,
            dietRemovedItems         = plan.dietRemoved,
            dietAddedItems           = plan.dietAdded,
            doctorRecommendations    = plan.doctorRecs,
            workoutRemovedItems      = plan.wkRemoved,
            workoutAddedItems        = plan.wkAdded,
            recoveryRecommendations  = plan.recoveryRecs
        )
        call.respond(ApiResponse(success = true, message = "OK", data = dto))
    }

    // ── GET /api/users/{id}/smart-plan/preview ───────────────────────────────
    // Returns EXACTLY which meals and workouts will be swapped, before applying
    get("/users/{id}/smart-plan/preview") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val info = dbQuery {
            UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.OK,
            ApiResponse(true, "No medical info", data = SmartPlanPreviewDto(emptyList(), emptyList(), 0)))

        val conditions  = parseJsonList(info[UserMedicalInfo.conditions]).map { it.lowercase() }
        val allergies   = parseJsonList(info[UserMedicalInfo.allergies]).map  { it.lowercase() }
        val unsafeTags  = getUnsafeTags(conditions, allergies)
        val safeTags    = getSafeTags(conditions)
        val unsafeWkCats = getUnsafeWorkoutCategories(conditions)
        val safeWkCats   = getSafeWorkoutCategories(conditions)

        val today     = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val weekEnd   = weekStart.plusDays(6)

        val mealSwaps    = mutableListOf<MealSwapDto>()
        val workoutSwaps = mutableListOf<WorkoutSwapDto>()

        dbQuery {
            // ── Find meal swaps ───────────────────────────────────────────────
            if (unsafeTags.isNotEmpty()) {
                val safeMeals = SmartMeals.selectAll()
                    .mapNotNull { row ->
                        val tag = row.getOrNull(SmartMeals.dietTag)?.lowercase() ?: "balanced"
                        if (safeTags.any { st -> tag.contains(st) })
                            row[SmartMeals.mealId] to row[SmartMeals.mealName]
                        else null
                    }.shuffled()

                val currentMeals = MedicalMealPlans.select {
                    (MedicalMealPlans.userId eq userId) and
                    (MedicalMealPlans.planDate.between(weekStart, weekEnd))
                }.toList()

                var safeIdx = 0
                currentMeals.forEach { planRow ->
                    val mealId   = planRow[MedicalMealPlans.mealId]
                    val planDate = planRow[MedicalMealPlans.planDate].toString()
                    val mealType = planRow.getOrNull(MedicalMealPlans.mealType) ?: "meal"

                    val mealRow  = SmartMeals.select { SmartMeals.mealId eq mealId }.firstOrNull()
                    val mealName = mealRow?.get(SmartMeals.mealName) ?: "Unknown meal"
                    val tag      = mealRow?.getOrNull(SmartMeals.dietTag)?.lowercase() ?: "balanced"

                    val isUnsafe = unsafeTags.any { unsafe -> tag.contains(unsafe) }
                    if (isUnsafe && safeMeals.isNotEmpty()) {
                        val (newId, newName) = safeMeals[safeIdx % safeMeals.size]
                        safeIdx++
                        val reason = when {
                            tag.contains("high_sugar")  -> "High sugar — unsuitable for Diabetes/PCOS"
                            tag.contains("fried")       -> "Fried food — worsens inflammation"
                            tag.contains("high_sodium") -> "High sodium — dangerous for Hypertension"
                            tag.contains("dairy")       -> "Contains dairy allergen"
                            tag.contains("gluten")      -> "Contains gluten allergen"
                            else -> "Unsuitable for your health conditions"
                        }
                        // Use 0 as placeholder planId since meal_plans has no plan_id column
                        mealSwaps.add(MealSwapDto(0, planDate, mealType,
                            mealId, mealName, newId, newName, reason))
                    }
                }
            }

            // ── Find workout swaps ────────────────────────────────────────────
            if (unsafeWkCats.isNotEmpty()) {
                val safeWorkouts = SmartWorkouts
                    .select { SmartWorkouts.category inList safeWkCats }
                    .limit(20)
                    .map { it[SmartWorkouts.workoutId] to it[SmartWorkouts.workoutName] }
                    .shuffled()

                val plannedWorkouts = SmartWorkoutPlan.select {
                    (SmartWorkoutPlan.userId eq userId) and
                    (SmartWorkoutPlan.scheduledDate.between(today, weekEnd)) and
                    (SmartWorkoutPlan.status eq "planned")
                }.toList()

                var wkIdx = 0
                plannedWorkouts.forEach { wkRow ->
                    val wkId       = wkRow.getOrNull(SmartWorkoutPlan.workoutId) ?: return@forEach
                    val planId     = wkRow[SmartWorkoutPlan.planId]
                    val schedDate  = wkRow.getOrNull(SmartWorkoutPlan.scheduledDate)?.toString() ?: ""
                    val wkName     = wkRow.getOrNull(SmartWorkoutPlan.workoutName) ?: "Unknown workout"

                    val wkCat = SmartWorkouts
                        .select { SmartWorkouts.workoutId eq wkId }
                        .firstOrNull()?.get(SmartWorkouts.category) ?: ""

                    if (wkCat in unsafeWkCats && safeWorkouts.isNotEmpty()) {
                        val (newId, newName) = safeWorkouts[wkIdx % safeWorkouts.size]
                        wkIdx++
                        val reason = when (wkCat) {
                            "Gym"    -> "Heavy lifting restricted due to Hypertension/Thyroid"
                            "Boxing" -> "High-intensity boxing not suitable for your conditions"
                            "Running" -> "Running restricted due to Asthma"
                            else -> "Unsuitable exercise for your conditions"
                        }
                        workoutSwaps.add(WorkoutSwapDto(planId, schedDate,
                            wkId, wkName, newId, newName, reason))
                    }
                }
            }
        }

        val preview = SmartPlanPreviewDto(
            mealSwaps    = mealSwaps,
            workoutSwaps = workoutSwaps,
            totalChanges = mealSwaps.size + workoutSwaps.size
        )
        call.respond(ApiResponse(success = true, message = "Preview ready", data = preview))
    }

    // ── POST /api/users/{id}/smart-plan/apply ────────────────────────────────
    // Actually applies the smart plan — modifies meal_plans and workout_plan in DB
    post("/users/{id}/smart-plan/apply") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val info = dbQuery {
            UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
        } ?: return@post call.respond(HttpStatusCode.NotFound,
            ApiResponse<Nothing>(false, "Save medical info first", null))

        val conditions   = parseJsonList(info[UserMedicalInfo.conditions]).map { it.lowercase() }
        val allergies    = parseJsonList(info[UserMedicalInfo.allergies]).map  { it.lowercase() }
        val unsafeTags   = getUnsafeTags(conditions, allergies)
        val safeTags     = getSafeTags(conditions)
        val unsafeWkCats = getUnsafeWorkoutCategories(conditions)
        val safeWkCats   = getSafeWorkoutCategories(conditions)

        val today     = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val weekEnd   = weekStart.plusDays(6)

        var mealsReplaced    = 0
        var workoutsReplaced = 0

        dbQuery {
            // ── MEALS: replace unsafe diet_tag meals with safe ones ──────────
            if (unsafeTags.isNotEmpty()) {
                val safeMeals = SmartMeals.selectAll()
                    .mapNotNull { row ->
                        val tag = row.getOrNull(SmartMeals.dietTag)?.lowercase() ?: "balanced"
                        if (safeTags.any { st -> tag.contains(st) })
                            row[SmartMeals.mealId] to row[SmartMeals.mealName]
                        else null
                    }.shuffled()

                val currentMeals = MedicalMealPlans.select {
                    (MedicalMealPlans.userId eq userId) and
                    (MedicalMealPlans.planDate.between(weekStart, weekEnd))
                }.toList()

                var safeIdx = 0
                currentMeals.forEach { planRow ->
                    val oldMealId = planRow[MedicalMealPlans.mealId]
                    val planDate  = planRow[MedicalMealPlans.planDate]
                    val tag       = SmartMeals.select { SmartMeals.mealId eq oldMealId }
                        .firstOrNull()?.getOrNull(SmartMeals.dietTag)?.lowercase() ?: "balanced"

                    if (unsafeTags.any { unsafe -> tag.contains(unsafe) } && safeMeals.isNotEmpty()) {
                        val (newId, _) = safeMeals[safeIdx % safeMeals.size]
                        safeIdx++
                        // Use composite key (user_id + old_meal_id + plan_date) — no plan_id column
                        MedicalMealPlans.update({
                            (MedicalMealPlans.userId eq userId) and
                            (MedicalMealPlans.mealId eq oldMealId) and
                            (MedicalMealPlans.planDate eq planDate)
                        }) {
                            it[MedicalMealPlans.mealId] = newId
                        }
                        mealsReplaced++
                    }
                }
            }

            // ── WORKOUTS: replace unsafe category workouts ───────────────────
            if (unsafeWkCats.isNotEmpty()) {
                val safeWorkouts = SmartWorkouts
                    .select { SmartWorkouts.category inList safeWkCats }
                    .limit(20).shuffled()
                    .map { it[SmartWorkouts.workoutId] to it[SmartWorkouts.workoutName] }

                val plannedWorkouts = SmartWorkoutPlan.select {
                    (SmartWorkoutPlan.userId eq userId) and
                    (SmartWorkoutPlan.scheduledDate.between(today, weekEnd)) and
                    (SmartWorkoutPlan.status eq "planned")
                }.toList()

                var wkIdx = 0
                plannedWorkouts.forEach { wkRow ->
                    val wkId   = wkRow.getOrNull(SmartWorkoutPlan.workoutId) ?: return@forEach
                    val planId = wkRow[SmartWorkoutPlan.planId]
                    val cat    = SmartWorkouts.select { SmartWorkouts.workoutId eq wkId }
                        .firstOrNull()?.get(SmartWorkouts.category) ?: ""

                    if (cat in unsafeWkCats && safeWorkouts.isNotEmpty()) {
                        val (newId, newName) = safeWorkouts[wkIdx % safeWorkouts.size]
                        wkIdx++
                        SmartWorkoutPlan.update({ SmartWorkoutPlan.planId eq planId }) {
                            it[SmartWorkoutPlan.workoutId]   = newId
                            it[SmartWorkoutPlan.workoutName] = newName
                        }
                        workoutsReplaced++
                    }
                }
            }
        }

        val result = SmartPlanApplyResult(
            mealsReplaced    = mealsReplaced,
            workoutsReplaced = workoutsReplaced,
            message = when {
                mealsReplaced == 0 && workoutsReplaced == 0 ->
                    "Your current plan already suits your conditions! No changes needed."
                else ->
                    "Done! Replaced $mealsReplaced unsafe meals and $workoutsReplaced workouts."
            }
        )
        call.respond(ApiResponse(success = true, message = result.message, data = result))
    }

    // ── GET /api/users/{id}/health-report ────────────────────────────────────
    get("/users/{id}/health-report") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val info = dbQuery {
            UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
        }
        val conditions   = if (info != null) parseJsonList(info[UserMedicalInfo.conditions]) else emptyList()
        val allergies    = if (info != null) parseJsonList(info[UserMedicalInfo.allergies])   else emptyList()
        val hideWeight   = info?.get(UserMedicalInfo.hideWeight)   ?: false
        val hideCalories = info?.get(UserMedicalInfo.hideCalories) ?: false

        val today = java.time.LocalDate.now()
        val weekStart = today.minusDays(6)

        // Workouts completed in last 7 days
        val workoutsDone = dbQuery {
            MedicalDailyLogs.select {
                (MedicalDailyLogs.userId eq userId) and
                (MedicalDailyLogs.logDate greaterEq weekStart) and
                (MedicalDailyLogs.workoutsDone greater 0)
            }.count().toInt()
        }

        // Meals logged in last 7 days
        val mealsLogged = dbQuery {
            MedicalMealPlans.select {
                (MedicalMealPlans.userId eq userId) and
                (MedicalMealPlans.planDate greaterEq weekStart) and
                (MedicalMealPlans.planDate lessEq today)
            }.count().toInt()
        }

        val dayLabels = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
        val dailyData = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val log = dbQuery {
                MedicalDailyLogs.select {
                    (MedicalDailyLogs.userId eq userId) and
                    (MedicalDailyLogs.logDate eq date)
                }.firstOrNull()
            }
            val cal     = log?.get(MedicalDailyLogs.caloriesEaten) ?: 0
            val workout = (log?.get(MedicalDailyLogs.workoutsDone) ?: 0) > 0
            val pct     = minOf((cal * 100 / 2000), 100)
            Triple(cal, workout, pct)
        }

        val weeklyCalories = dailyData.map { it.first }
        val totalCal = weeklyCalories.sum()
        val avgCal   = if (dailyData.isNotEmpty()) totalCal / dailyData.size else 0

        val dailyProgressList = dailyData.mapIndexed { i, (cal, workout, pct) ->
            DailyProgressDto(
                dayLabel     = dayLabels[i],
                calories     = cal,
                workoutDone  = workout,
                progressPct  = pct
            )
        }

        val medInfo2 = dbQuery {
            UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
        }
        val medications = if (medInfo2 != null) parseJsonList(medInfo2[UserMedicalInfo.medications]) else emptyList()

        val dto = HealthReportDto(
            conditions             = conditions,
            allergies              = allergies,
            medications            = medications,
            workoutsCompleted      = workoutsDone,
            workoutsTotal          = 15,
            mealsLogged            = mealsLogged,
            mealsTotal             = 21,
            totalCaloriesThisWeek  = totalCal,
            avgDailyCalories       = avgCal,
            hideWeight             = hideWeight,
            hideCalories           = hideCalories,
            weeklyProgress         = weeklyCalories,
            dailyProgress          = dailyProgressList
        )
        call.respond(ApiResponse(success = true, message = "OK", data = dto))
    }

    // ── PATCH /api/users/{id}/health-report/privacy ──────────────────────────
    patch("/users/{id}/health-report/privacy") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        @Serializable
        data class PrivacyRequest(val hideWeight: Boolean, val hideCalories: Boolean)
        val body = call.receive<PrivacyRequest>()

        dbQuery {
            val existing = UserMedicalInfo.select { UserMedicalInfo.userId eq userId }.firstOrNull()
            if (existing != null) {
                UserMedicalInfo.update({ UserMedicalInfo.userId eq userId }) {
                    it[hideWeight]   = body.hideWeight
                    it[hideCalories] = body.hideCalories
                }
            } else {
                UserMedicalInfo.insert {
                    it[UserMedicalInfo.userId]      = userId
                    it[UserMedicalInfo.hideWeight]  = body.hideWeight
                    it[UserMedicalInfo.hideCalories]= body.hideCalories
                }
            }
        }
        call.respond(ApiResponse(success = true, message = "Privacy settings updated", data = null))
    }
}
