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

private object FamilyGroupTable : Table("family_group") {
    val groupId   = integer("group_id").autoIncrement()
    val name      = varchar("name", 100)
    val createdBy = integer("created_by")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(groupId)
}

private object FamilyMemberTable : Table("family_member") {
    val memberId = integer("member_id").autoIncrement()
    val groupId  = integer("group_id")
    val userId   = integer("user_id")
    val role     = varchar("role", 20).default("member")
    val joinedAt = datetime("joined_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(memberId)
}

private object FamilyGroceryTable : Table("family_grocery_item") {
    val itemId    = integer("item_id").autoIncrement()
    val groupId   = integer("group_id")
    val itemName  = varchar("item_name", 255)
    val quantity  = varchar("quantity", 100).nullable()
    val addedBy   = integer("added_by")
    val isChecked = bool("is_checked").default(false)
    val checkedBy = integer("checked_by").nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(itemId)
}

private object FamilyUsers : Table("users") {
    val id            = integer("id")
    val name          = varchar("name", 100)
    val goal          = varchar("goal", 100).nullable()
    val gender        = varchar("gender", 20).nullable()
    val age           = integer("age").nullable()
    val weightKg      = integer("weight_kg").nullable()
    val heightCm      = integer("height_cm").nullable()
    val activityLevel = varchar("activity_level", 50).nullable()
    override val primaryKey = PrimaryKey(id)
}

// Harris-Benedict BMR → TDEE calculation
private fun calculateCalorieGoal(
    gender: String?, age: Int?, weightKg: Int?, heightCm: Int?,
    activityLevel: String?, goal: String?
): Int {
    if (age == null || weightKg == null || heightCm == null) return 2000

    val bmr = if (gender?.lowercase() == "female") {
        447.593 + (9.247 * weightKg) + (3.098 * heightCm) - (4.330 * age)
    } else {
        88.362 + (13.397 * weightKg) + (4.799 * heightCm) - (5.677 * age)
    }

    val multiplier = when (activityLevel?.lowercase()) {
        "sedentary"                    -> 1.2
        "lightly active", "light"      -> 1.375
        "moderately active", "moderate"-> 1.55
        "very active"                  -> 1.725
        "extra active"                 -> 1.9
        else                           -> 1.375
    }

    val tdee = bmr * multiplier
    return when {
        goal?.contains("lose", true) == true   -> (tdee - 500).toInt()
        goal?.contains("gain", true) == true ||
        goal?.contains("build", true) == true  -> (tdee + 300).toInt()
        else                                   -> tdee.toInt()
    }.coerceIn(1200, 4000)
}

// Separate table since we cannot ALTER users
private object UserEmail : Table("user_email") {
    val userId = integer("user_id")
    val email  = varchar("email", 255)
    override val primaryKey = PrimaryKey(userId)
}

private object FamilyDailyLogs : Table("daily_logs") {
    val userId      = integer("user_id")
    val logDate     = date("log_date")
    val caloriesEaten = integer("calories_eaten")
}

private object FamilyMealPlans : Table("meal_plans") {
    val planId        = integer("plan_id").autoIncrement()
    val userId        = integer("user_id")
    val scheduledDate = datetime("scheduled_time").nullable()
    override val primaryKey = PrimaryKey(planId)
}



// ─── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class FamilyMemberDto(
    val memberId: Int,
    val userId: Int,
    val name: String,
    val email: String,
    val goal: String,
    val role: String,
    val streakDays: Int,
    val caloriesGoal: Int,
    val caloriesEatenToday: Int
)

@Serializable
data class FamilyGroupDto(
    val groupId: Int,
    val name: String,
    val createdBy: Int,
    val members: List<FamilyMemberDto>,
    val totalMealsPlanned: Int,
    val weekDays: List<WeekDayDto>
)

@Serializable
data class WeekDayDto(
    val date: String,       // "YYYY-MM-DD"
    val dayLabel: String,   // "Mon", "Tue" etc.
    val dayNumber: Int,
    val monthLabel: String,
    val hasPlans: Boolean,
    val isToday: Boolean
)

@Serializable
data class FamilyGroceryItemDto(
    val itemId: Int,
    val itemName: String,
    val quantity: String,
    val addedBy: Int,
    val addedByName: String,
    val isChecked: Boolean,
    val checkedBy: Int?,
    val checkedByName: String?
)

@Serializable
data class CreateFamilyRequest(val name: String)

@Serializable
data class MemberPreviewDto(
    val userId: Int,
    val name: String,
    val goal: String,
    val email: String,
    val alreadyMember: Boolean
)

@Serializable
data class InviteMemberRequest(val email: String)

@Serializable
data class AddFamilyGroceryRequest(
    val itemName: String,
    val quantity: String
)

@Serializable
data class CheckFamilyGroceryRequest(
    val isChecked: Boolean
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

// Get or create a family group for user (as admin)
private suspend fun getOrCreateFamilyGroup(userId: Int): Int = dbQuery {
    // Prefer the group where this user is admin (group they created)
    // Fall back to any group they're a member of
    val existing = FamilyMemberTable
        .select { FamilyMemberTable.userId eq userId }
        .orderBy(FamilyMemberTable.role to SortOrder.ASC) // "admin" < "member" alphabetically
        .firstOrNull()
    if (existing != null) return@dbQuery existing[FamilyMemberTable.groupId]

    // Create new group
    val groupId = FamilyGroupTable.insert {
        it[FamilyGroupTable.name]      = "My Family"
        it[FamilyGroupTable.createdBy] = userId
        // createdAt set by clientDefault
    }[FamilyGroupTable.groupId]

    // Add creator as admin
    FamilyMemberTable.insert {
        it[FamilyMemberTable.groupId]  = groupId
        it[FamilyMemberTable.userId]   = userId
        it[FamilyMemberTable.role]     = "admin"
        // joinedAt set by clientDefault
    }
    groupId
}

private fun computeStreak(userId: Int): Int {
    val today = LocalDate.now()
    var streak = 0
    var checkDate = today
    while (true) {
        val hasLog = FamilyDailyLogs
            .select {
                (FamilyDailyLogs.userId eq userId) and
                (FamilyDailyLogs.logDate eq checkDate) and
                (FamilyDailyLogs.caloriesEaten greater 0)
            }.count() > 0
        if (!hasLog) break
        streak++
        checkDate = checkDate.minusDays(1)
    }
    return streak
}

private fun buildWeekDays(groupMemberIds: List<Int>): List<WeekDayDto> {
    val today = LocalDate.now()
    val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    return (0..6).map { offset ->
        val date = monday.plusDays(offset.toLong())
        val hasPlans = groupMemberIds.any { uid ->
            FamilyMealPlans.select {
                (FamilyMealPlans.userId eq uid) and
                (FamilyMealPlans.scheduledDate greaterEq date.atStartOfDay()) and
                (FamilyMealPlans.scheduledDate lessEq date.atTime(23, 59, 59))
            }.count() > 0
        }
        WeekDayDto(
            date       = date.toString(),
            dayLabel   = date.dayOfWeek.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() },
            dayNumber  = date.dayOfMonth,
            monthLabel = date.month.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() },
            hasPlans   = hasPlans,
            isToday    = date == today
        )
    }
}

// ─── Routes ───────────────────────────────────────────────────────────────────

fun Route.familyRoutes() {

    // ── GET /api/users/{id}/family/lookup?email= ─────────────────────────────
    // Preview a user by email before inviting them
    get("/users/{id}/family/lookup") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user ID", null))
        val email = call.request.queryParameters["email"]?.trim()
        if (email.isNullOrBlank() || !email.contains("@")) {
            return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Please enter a valid email address", null))
        }

        val result = dbQuery {
            val emailRow = UserEmail.select { UserEmail.email eq email }.firstOrNull()
                ?: return@dbQuery null to "No Fuelify account found for '$email'. Ask them to register first."

            val targetUserId = emailRow[UserEmail.userId]

            if (targetUserId == userId) {
                return@dbQuery null to "That's your own email — you can't invite yourself!"
            }

            val user = FamilyUsers.select { FamilyUsers.id eq targetUserId }.firstOrNull()
                ?: return@dbQuery null to "Account found but user profile is incomplete."

            val groupId = FamilyMemberTable
                .select { FamilyMemberTable.userId eq userId }
                .firstOrNull()?.get(FamilyMemberTable.groupId)

            val alreadyMember = if (groupId != null) {
                FamilyMemberTable.select {
                    (FamilyMemberTable.groupId eq groupId) and
                    (FamilyMemberTable.userId eq targetUserId)
                }.count() > 0
            } else false

            MemberPreviewDto(
                userId       = targetUserId,
                name         = user[FamilyUsers.name],
                goal         = user.getOrNull(FamilyUsers.goal)?.replaceFirstChar { it.uppercase() } ?: "No goal set",
                email        = email,
                alreadyMember = alreadyMember
            ) to null
        }

        val (preview, error) = result ?: (null to "Lookup failed, please try again.")
        if (error != null) {
            call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(false, error, null))
        } else {
            call.respond(ApiResponse(success = true, message = "OK", data = preview))
        }
    }

    // ── GET /api/users/{id}/family ────────────────────────────────────────────
    // Get or create family group, return full state
    get("/users/{id}/family") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))

        val groupId = getOrCreateFamilyGroup(userId)

        val data = dbQuery {
            // Group info
            val group = FamilyGroupTable
                .select { FamilyGroupTable.groupId eq groupId }
                .firstOrNull()
                ?: return@dbQuery null

            // Members
            val memberRows = FamilyMemberTable
                .select { FamilyMemberTable.groupId eq groupId }
                .toList()

            val members = memberRows.mapNotNull { memberRow ->
                val uid = memberRow[FamilyMemberTable.userId]
                val user = FamilyUsers.select { FamilyUsers.id eq uid }.firstOrNull()
                    ?: return@mapNotNull null
                val streak = computeStreak(uid)
                val caloriesGoal = calculateCalorieGoal(
                        gender        = user.getOrNull(FamilyUsers.gender),
                        age           = user.getOrNull(FamilyUsers.age),
                        weightKg      = user.getOrNull(FamilyUsers.weightKg),
                        heightCm      = user.getOrNull(FamilyUsers.heightCm),
                        activityLevel = user.getOrNull(FamilyUsers.activityLevel),
                        goal          = user.getOrNull(FamilyUsers.goal)
                    )

                val today = LocalDate.now()
                val caloriesEatenToday = FamilyDailyLogs
                    .slice(FamilyDailyLogs.caloriesEaten)
                    .select {
                        (FamilyDailyLogs.userId eq uid) and
                        (FamilyDailyLogs.logDate eq today)
                    }
                    .sumOf { it[FamilyDailyLogs.caloriesEaten] }

                FamilyMemberDto(
                    memberId           = memberRow[FamilyMemberTable.memberId],
                    userId             = uid,
                    name               = user[FamilyUsers.name],
                    email              = UserEmail.select { UserEmail.userId eq uid }.firstOrNull()?.get(UserEmail.email) ?: "",
                    goal               = user.getOrNull(FamilyUsers.goal)?.replaceFirstChar { it.uppercase() } ?: "Maintain",
                    role               = memberRow[FamilyMemberTable.role],
                    streakDays         = streak,
                    caloriesGoal       = caloriesGoal,
                    caloriesEatenToday = caloriesEatenToday
                )
            }

            val memberIds = members.map { it.userId }

            // Total meals planned this week
            val weekStart = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1)
                .atStartOfDay()
            val weekEnd = weekStart.plusDays(6).plusHours(23).plusMinutes(59)
            val totalMeals = FamilyMealPlans
                .select {
                    (FamilyMealPlans.userId inList memberIds) and
                    (FamilyMealPlans.scheduledDate greaterEq weekStart) and
                    (FamilyMealPlans.scheduledDate lessEq weekEnd)
                }.count().toInt()

            val weekDays = buildWeekDays(memberIds)

            FamilyGroupDto(
                groupId          = groupId,
                name             = group[FamilyGroupTable.name],
                createdBy        = group[FamilyGroupTable.createdBy],
                members          = members,
                totalMealsPlanned = totalMeals,
                weekDays         = weekDays
            )
        }

        if (data == null) {
            call.respond(HttpStatusCode.InternalServerError,
                ApiResponse<Nothing>(false, "Failed to load family", null))
        } else {
            call.respond(ApiResponse(success = true, message = "OK", data = data))
        }
    }

    // ── POST /api/users/{id}/family/invite ────────────────────────────────────
    // Invite member by email
    post("/users/{id}/family/invite") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<InviteMemberRequest>()
        val groupId = getOrCreateFamilyGroup(userId)

        val result = dbQuery {
            // Check role — only admin can invite
            val callerRole = FamilyMemberTable.select {
                (FamilyMemberTable.groupId eq groupId) and
                (FamilyMemberTable.userId eq userId)
            }.firstOrNull()?.get(FamilyMemberTable.role)

            if (callerRole != "admin") return@dbQuery "NOT_ADMIN"

            // Find user by email via user_email table
            val emailRow = UserEmail
                .select { UserEmail.email eq body.email }
                .firstOrNull() ?: return@dbQuery "USER_NOT_FOUND"

            val invitedUserId = emailRow[UserEmail.userId]

            // Check already a member
            val alreadyMember = FamilyMemberTable.select {
                (FamilyMemberTable.groupId eq groupId) and
                (FamilyMemberTable.userId eq invitedUserId)
            }.count() > 0
            if (alreadyMember) return@dbQuery "ALREADY_MEMBER"

            // Add to family
            FamilyMemberTable.insert {
                it[FamilyMemberTable.groupId]  = groupId
                it[FamilyMemberTable.userId]   = invitedUserId
                it[FamilyMemberTable.role]     = "member"
                // joinedAt set by clientDefault
            }
            "SUCCESS"
        }

        when (result) {
            "SUCCESS"        -> call.respond(ApiResponse(success = true, message = "Member added", data = null))
            "USER_NOT_FOUND" -> call.respond(HttpStatusCode.NotFound,
                ApiResponse<Nothing>(false, "No user found with that email", null))
            "ALREADY_MEMBER" -> call.respond(HttpStatusCode.Conflict,
                ApiResponse<Nothing>(false, "User is already a member", null))
            "NOT_ADMIN"      -> call.respond(HttpStatusCode.Forbidden,
                ApiResponse<Nothing>(false, "Only the family admin can invite members", null))
            else             -> call.respond(HttpStatusCode.InternalServerError,
                ApiResponse<Nothing>(false, "Failed to add member", null))
        }
    }

    // ── DELETE /api/users/{id}/family/member/{memberId} ───────────────────────
    delete("/users/{id}/family/member/{memberId}") {
        val userId   = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val memberId = call.parameters["memberId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid member id", null))
        val groupId = getOrCreateFamilyGroup(userId)

        dbQuery {
            // Can remove yourself, or admin can remove anyone
            val callerRole = FamilyMemberTable.select {
                (FamilyMemberTable.groupId eq groupId) and
                (FamilyMemberTable.userId eq userId)
            }.firstOrNull()?.get(FamilyMemberTable.role)

            val targetRow = FamilyMemberTable.select {
                (FamilyMemberTable.memberId eq memberId) and
                (FamilyMemberTable.groupId eq groupId)
            }.firstOrNull()

            val isOwnRow = targetRow?.get(FamilyMemberTable.userId) == userId
            if (callerRole == "admin" || isOwnRow) {
                FamilyMemberTable.deleteWhere { FamilyMemberTable.memberId eq memberId }
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── GET /api/users/{id}/family/grocery ────────────────────────────────────
    get("/users/{id}/family/grocery") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val groupId = getOrCreateFamilyGroup(userId)

        val items = dbQuery {
            FamilyGroceryTable
                .select { FamilyGroceryTable.groupId eq groupId }
                .orderBy(FamilyGroceryTable.isChecked to SortOrder.ASC,
                         FamilyGroceryTable.createdAt to SortOrder.ASC)
                .map { row ->
                    val addedById = row[FamilyGroceryTable.addedBy]
                    val addedByName = FamilyUsers.select { FamilyUsers.id eq addedById }
                        .firstOrNull()?.get(FamilyUsers.name) ?: "Unknown"
                    val checkedById = row.getOrNull(FamilyGroceryTable.checkedBy)
                    val checkedByName = checkedById?.let {
                        FamilyUsers.select { FamilyUsers.id eq it }
                            .firstOrNull()?.get(FamilyUsers.name)
                    }
                    FamilyGroceryItemDto(
                        itemId       = row[FamilyGroceryTable.itemId],
                        itemName     = row[FamilyGroceryTable.itemName],
                        quantity     = row.getOrNull(FamilyGroceryTable.quantity) ?: "",
                        addedBy      = addedById,
                        addedByName  = addedByName,
                        isChecked    = row[FamilyGroceryTable.isChecked],
                        checkedBy    = checkedById,
                        checkedByName = checkedByName
                    )
                }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = items))
    }

    // ── POST /api/users/{id}/family/grocery ───────────────────────────────────
    post("/users/{id}/family/grocery") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<AddFamilyGroceryRequest>()
        val groupId = getOrCreateFamilyGroup(userId)

        dbQuery {
            FamilyGroceryTable.insert {
                it[FamilyGroceryTable.groupId]   = groupId
                it[FamilyGroceryTable.itemName]  = body.itemName
                it[FamilyGroceryTable.quantity]  = body.quantity
                it[FamilyGroceryTable.addedBy]   = userId
                it[FamilyGroceryTable.isChecked] = false
                // createdAt set by clientDefault
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── PATCH /api/users/{id}/family/grocery/{itemId}/check ───────────────────
    patch("/users/{id}/family/grocery/{itemId}/check") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val itemId = call.parameters["itemId"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid item id", null))
        val body = call.receive<CheckFamilyGroceryRequest>()
        val groupId = getOrCreateFamilyGroup(userId)

        dbQuery {
            FamilyGroceryTable.update({
                (FamilyGroceryTable.itemId eq itemId) and
                (FamilyGroceryTable.groupId eq groupId)
            }) {
                it[FamilyGroceryTable.isChecked] = body.isChecked
                it[FamilyGroceryTable.checkedBy] = if (body.isChecked) userId else null
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── DELETE /api/users/{id}/family/grocery/clear-checked ──────────────────
    delete("/users/{id}/family/grocery/clear-checked") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val groupId = getOrCreateFamilyGroup(userId)

        dbQuery {
            FamilyGroceryTable.deleteWhere {
                (FamilyGroceryTable.groupId eq groupId) and
                (FamilyGroceryTable.isChecked eq true)
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── DELETE /api/users/{id}/family/grocery/{itemId} ────────────────────────
    delete("/users/{id}/family/grocery/{itemId}") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val itemId = call.parameters["itemId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid item id", null))
        val groupId = getOrCreateFamilyGroup(userId)

        dbQuery {
            FamilyGroceryTable.deleteWhere {
                (FamilyGroceryTable.itemId eq itemId) and
                (FamilyGroceryTable.groupId eq groupId)
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }

    // ── PATCH /api/users/{id}/family/name ─────────────────────────────────────
    patch("/users/{id}/family/name") {
        val userId = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest,
                ApiResponse<Nothing>(false, "Invalid user id", null))
        val body = call.receive<CreateFamilyRequest>()
        val groupId = getOrCreateFamilyGroup(userId)

        dbQuery {
            FamilyGroupTable.update({ FamilyGroupTable.groupId eq groupId }) {
                it[FamilyGroupTable.name] = body.name
            }
        }
        call.respond(ApiResponse(success = true, message = "OK", data = null))
    }
}
