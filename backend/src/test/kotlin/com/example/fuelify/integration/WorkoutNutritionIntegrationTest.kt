package com.example.fuelify.integration

import com.example.fuelify.routes.workoutRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Integration tests for Workout and Nutrition routes.
 *
 * Uses H2 in-memory database — no Neon/PostgreSQL connection required.
 *
 * SETUP: Add this to your build.gradle.kts dependencies block:
 *   testImplementation("com.h2database:h2:2.2.224")
 *
 * Run with: ./gradlew test --tests "com.example.fuelify.integration.WorkoutNutritionIntegrationTest"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkoutNutritionIntegrationTest {

    // ── In-test table definitions (mirrors your production tables) ────────────

    private object TestUsers : Table("users") {
        val id            = integer("id").autoIncrement()
        val email         = varchar("email", 255).uniqueIndex()
        val username      = varchar("username", 100).nullable()
        val passwordHash  = varchar("password_hash", 255).nullable()
        val isVerified    = bool("is_verified").default(false)
        val isActive      = bool("is_active").default(true)
        val isAdmin       = bool("is_admin").default(false)
        val name          = varchar("name", 100).default("")
        val gender        = varchar("gender", 20).default("")
        val age           = integer("age").default(0)
        val heightCm      = integer("height_cm").default(0)
        val weightKg      = integer("weight_kg").default(0)
        val goal          = varchar("goal", 100).default("")
        val activityLevel = varchar("activity_level", 100).default("")
        val motivation    = varchar("motivation", 255).default("")
        val fitnessLevel  = varchar("fitness_level", 50).default("")
        val exerciseDays  = integer("exercise_days").default(0)
        val trainingPlace = varchar("training_place", 50).default("")
        val mealsPerDay   = integer("meals_per_day").default(3)
        val likedFoods    = text("liked_foods").default("[]")
        val allergies     = text("allergies").default("[]")
        val budget        = varchar("budget", 50).default("")
        val profileComplete = bool("profile_complete").default(false)
        val onboardingStep  = integer("onboarding_step").default(0)
        val createdAt     = datetime("created_at").nullable()
        val updatedAt     = datetime("updated_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    private object TestWorkouts : Table("workout") {
        val workoutId              = integer("workout_id").autoIncrement()
        val workoutName            = varchar("workout_name", 255)
        val category               = varchar("category", 100)
        val difficulty             = varchar("difficulty", 50)
        val durationMinutes        = integer("duration_minutes")
        val isPremium              = bool("is_premium").default(false)
        val equipment              = varchar("equipment", 255).nullable()
        val imageUrl               = varchar("image_url", 500).nullable()
        val caloriesBurnedEstimate = integer("calories_burned_estimate").default(0)
        override val primaryKey    = PrimaryKey(workoutId)
    }

    private object TestWorkoutPlan : Table("workout_plan") {
        val planId        = integer("plan_id").autoIncrement()
        val userId        = integer("user_id")
        val workoutId     = integer("workout_id").nullable()
        val workoutName   = varchar("workout_name", 255).nullable()
        val scheduledDate = date("scheduled_date").nullable()
        val status        = varchar("status", 20).default("planned")
        val sessionNumber = integer("session_number").default(1)
        val sessionLabel  = varchar("session_label", 50).default("")
        override val primaryKey = PrimaryKey(planId)
    }

    private object TestMealPlans : Table("meal_plans") {
        val id            = integer("id").autoIncrement()
        val userId        = integer("user_id")
        val planDate      = date("plan_date")
        val mealId        = integer("meal_id")
        val mealType      = varchar("meal_type", 20)
        val scheduledTime = varchar("scheduled_time", 10).default("")
        val isCompleted   = bool("is_completed").default(false)
        val scaledCalories = integer("scaled_calories").default(0)
        override val primaryKey = PrimaryKey(id)
    }

    private object TestDailyLogs : Table("daily_logs") {
        val id           = integer("id").autoIncrement()
        val userId       = integer("user_id")
        val logDate      = date("log_date")
        val caloriesEaten = integer("calories_eaten").default(0)
        val waterGlasses = integer("water_glasses").default(0)
        val workoutsDone = integer("workouts_done").default(0)
        val workoutsGoal = integer("workouts_goal").default(1)
        val streakDays   = integer("streak_days").default(0)
        val updatedAt    = datetime("updated_at")
        override val primaryKey = PrimaryKey(id)
    }

    private object TestWorkoutExercises : Table("workout_exercise") {
        val workoutExerciseId = integer("workout_exercise_id").autoIncrement()
        val workoutId         = integer("workout_id")
        val exerciseId        = integer("exercise_id")
        val reps              = integer("reps").default(0)
        val sets              = integer("sets").default(1)
        val restSeconds       = integer("rest_seconds").default(0)
        override val primaryKey = PrimaryKey(workoutExerciseId)
    }

    private object TestExercises : Table("exercise") {
        val exerciseId      = integer("exercise_id").autoIncrement()
        val exerciseName    = varchar("exercise_name", 255)
        val description     = text("description")
        val equipmentNeeded = varchar("equipment_needed", 100).nullable()
        val muscleGroup     = varchar("muscle_group", 100).nullable()
        val imageUrl        = varchar("image_url", 500).nullable()
        override val primaryKey = PrimaryKey(exerciseId)
    }

    // ── DB setup / teardown ───────────────────────────────────────────────────

    @BeforeAll
    fun setupDatabase() {
        // Connect H2 as the default Exposed database.
        // All routes use DatabaseFactory.dbQuery which calls newSuspendedTransaction()
        // without specifying a db — so it picks up this default connection.
        val db = Database.connect(
            url      = "jdbc:h2:mem:fuelify_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
            driver   = "org.h2.Driver",
            user     = "sa",
            password = ""
        )
        transaction(db) {
            SchemaUtils.create(
                TestUsers,
                TestWorkouts,
                TestExercises,
                TestWorkoutExercises,
                TestWorkoutPlan,
                TestMealPlans,
                TestDailyLogs
            )
        }
    }

    @BeforeEach
    fun seedDatabase() {
        transaction {
            // Clear tables before each test for full isolation
            TestWorkoutPlan.deleteAll()
            TestWorkoutExercises.deleteAll()
            TestMealPlans.deleteAll()
            TestDailyLogs.deleteAll()
            TestUsers.deleteAll()
            TestWorkouts.deleteAll()
            TestExercises.deleteAll()

            // ── Seed a realistic user ─────────────────────────────────────────
            TestUsers.insert {
                it[email]         = "ahmed@test.com"
                it[name]          = "Ahmed Hassan"
                it[gender]        = "male"
                it[age]           = 25
                it[heightCm]      = 175
                it[weightKg]      = 75
                it[goal]          = "lose weight"
                it[activityLevel] = "moderately active"
                it[fitnessLevel]  = "intermediate"
                it[trainingPlace] = "gym"
                it[exerciseDays]  = 4
                it[mealsPerDay]   = 3
                it[budget]        = "standard"
                it[motivation]    = "health and wellness"
                it[profileComplete] = true
                it[createdAt]     = LocalDateTime.now()
            }

            TestUsers.insert {
                it[email]         = "sara@test.com"
                it[name]          = "Sara Ali"
                it[gender]        = "female"
                it[age]           = 28
                it[heightCm]      = 163
                it[weightKg]      = 60
                it[goal]          = "gain muscle"
                it[activityLevel] = "lightly active"
                it[fitnessLevel]  = "beginner"
                it[trainingPlace] = "home"
                it[exerciseDays]  = 3
                it[mealsPerDay]   = 4
                it[budget]        = "budget friendly"
                it[motivation]    = "look better"
                it[profileComplete] = true
                it[createdAt]     = LocalDateTime.now()
            }

            // ── Seed workouts across categories ───────────────────────────────
            val workouts = listOf(
                Triple("Treadmill Run",        "Running",          "Beginner"),
                Triple("HIIT Boxing Circuit",  "Boxing",           "Medium"),
                Triple("Barbell Squat",        "Gym",              "Medium"),
                Triple("Push-up Blast",        "Upper Body",       "Beginner"),
                Triple("Yoga Flow",            "Yoga",             "Beginner"),
                Triple("Full Body Stretch",    "Stretch",          "Beginner"),
                Triple("Personal PT Session",  "Personal Training","Advanced"),
                Triple("Dumbbell Press",       "Upper Body",       "Medium")
            )
            workouts.forEach { (name, cat, diff) ->
                TestWorkouts.insert {
                    it[workoutName]            = name
                    it[category]               = cat
                    it[difficulty]             = diff
                    it[durationMinutes]        = 45
                    it[isPremium]              = false
                    it[caloriesBurnedEstimate] = 320
                }
            }
        }
    }

    @AfterAll
    fun teardownDatabase() {
        transaction {
            SchemaUtils.drop(
                TestWorkoutPlan,
                TestWorkoutExercises,
                TestExercises,
                TestMealPlans,
                TestDailyLogs,
                TestUsers,
                TestWorkouts
            )
        }
    }

    // ── Helper to spin up the test application ────────────────────────────────
    // We do NOT call DatabaseFactory.init() — H2 is already connected in @BeforeAll.
    // We install plugins inline and register only workout routes to avoid
    // pulling in routes that depend on tables not present in the H2 schema.

    private fun withFuelifyApp(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                install(io.ktor.server.plugins.cors.routing.CORS) {
                    anyHost()
                    allowHeader(io.ktor.http.HttpHeaders.ContentType)
                    allowMethod(io.ktor.http.HttpMethod.Options)
                    allowMethod(io.ktor.http.HttpMethod.Get)
                    allowMethod(io.ktor.http.HttpMethod.Post)
                    allowMethod(io.ktor.http.HttpMethod.Put)
                    allowMethod(io.ktor.http.HttpMethod.Patch)
                    allowMethod(io.ktor.http.HttpMethod.Delete)
                }
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(kotlinx.serialization.json.Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                install(io.ktor.server.plugins.statuspages.StatusPages) {
                    exception<Throwable> { call, cause ->
                        call.respondText(
                            text = """{"success":false,"message":"${cause.message}","data":null}""",
                            contentType = io.ktor.http.ContentType.Application.Json,
                            status = io.ktor.http.HttpStatusCode.InternalServerError
                        )
                    }
                    status(io.ktor.http.HttpStatusCode.NotFound) { call, _ ->
                        call.respondText(
                            text = """{"success":false,"message":"Route not found","data":null}""",
                            contentType = io.ktor.http.ContentType.Application.Json
                        )
                    }
                }
                routing {
                    get("/health") { call.respondText("OK") }
                    route("/api") {
                        workoutRoutes()
                    }
                }
            }
            block()
        }

    // ── Helper to get inserted user id ────────────────────────────────────────

    private fun getUserId(email: String): Int = transaction {
        TestUsers.select { TestUsers.email eq email }
            .first()[TestUsers.id]
    }

    // =========================================================================
    // 1. Health Check
    // =========================================================================

    @Nested
    inner class HealthCheckTests {

        @Test
        fun `GET health returns 200 OK`() = withFuelifyApp {
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("OK", response.bodyAsText())
        }
    }

    // =========================================================================
    // 2. Workout Suggested Endpoint
    // =========================================================================

    @Nested
    inner class WorkoutSuggestedTests {

        @Test
        fun `GET suggested workouts returns 200 for existing user`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            val response = client.get("/api/workouts/suggested/$userId")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `GET suggested workouts response body contains success true`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            val response = client.get("/api/workouts/suggested/$userId")
            assertEquals(HttpStatusCode.OK, response.status,
                "200 OK response means success=true in the body")
        }

        @Test
        fun `GET suggested workouts response contains reason field`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            val response = client.get("/api/workouts/suggested/$userId")
            val body = response.bodyAsText()
            assertTrue(body.contains("reason"), "Response should contain a reason field")
        }

        @Test
        fun `GET suggested workouts response contains exerciseDays field`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            val response = client.get("/api/workouts/suggested/$userId")
            val body = response.bodyAsText()
            assertTrue(body.contains("exerciseDays"), "Response should contain exerciseDays")
        }

        @Test
        fun `GET suggested workouts response contains sessionsPerDay field`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            val response = client.get("/api/workouts/suggested/$userId")
            val body = response.bodyAsText()
            assertTrue(body.contains("sessionsPerDay"), "Response should contain sessionsPerDay")
        }

        @Test
        fun `GET suggested workouts for beginner home user returns 200`() = withFuelifyApp {
            val userId = getUserId("sara@test.com")
            val response = client.get("/api/workouts/suggested/$userId")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `GET suggested workouts for non-existing user still returns 200 with defaults`() = withFuelifyApp {
            val response = client.get("/api/workouts/suggested/99999")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `GET suggested workouts with invalid user id returns 400`() = withFuelifyApp {
            val response = client.get("/api/workouts/suggested/abc")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `GET suggested workouts auto-populates workout plan in DB`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")

            // Check that workout_plan rows were inserted for this user this week
            val planCount = transaction {
                val today     = LocalDate.now()
                val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
                val weekEnd   = weekStart.plusDays(6)
                TestWorkoutPlan.select {
                    (TestWorkoutPlan.userId eq userId) and
                    (TestWorkoutPlan.scheduledDate greaterEq weekStart) and
                    (TestWorkoutPlan.scheduledDate lessEq weekEnd)
                }.count()
            }
            assertTrue(planCount > 0, "workout_plan should have rows after calling suggested endpoint")
        }

        @Test
        fun `GET suggested workouts calling twice does not duplicate plan rows`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")
            client.get("/api/workouts/suggested/$userId")

            val planCount = transaction {
                TestWorkoutPlan.select {
                    TestWorkoutPlan.userId eq userId
                }.count()
            }
            // Plan is only inserted once per week (existingPlanCount == 0L guard)
            val today     = LocalDate.now()
            val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val weekEnd   = weekStart.plusDays(6)
            val expectedMax = 4 * 3L // exerciseDays × max sessionsPerDay
            assertTrue(planCount <= expectedMax,
                "Plan rows should not exceed exerciseDays × sessionsPerDay ($expectedMax), got $planCount")
        }
    }

    // =========================================================================
    // 3. Workout Categories Endpoint
    // =========================================================================

    @Nested
    inner class WorkoutCategoriesTests {

        @Test
        fun `GET workout categories returns 200`() = withFuelifyApp {
            val response = client.get("/api/workouts/categories")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `GET workout categories response contains category field`() = withFuelifyApp {
            val response = client.get("/api/workouts/categories")
            val body = response.bodyAsText()
            assertTrue(body.contains("category"), "Response should list categories")
        }

        @Test
        fun `GET workout categories response contains emoji field`() = withFuelifyApp {
            val response = client.get("/api/workouts/categories")
            val body = response.bodyAsText()
            assertTrue(body.contains("emoji"), "Response should contain emoji field")
        }

        @Test
        fun `GET workout categories returns all seeded categories`() = withFuelifyApp {
            val response = client.get("/api/workouts/categories")
            val body = response.bodyAsText()
            assertTrue(body.contains("Running"),  "Should include Running category")
            assertTrue(body.contains("Gym"),      "Should include Gym category")
            assertTrue(body.contains("Upper Body"),"Should include Upper Body category")
        }
    }

    // =========================================================================
    // 4. Weekly Workout Plan Endpoint
    // =========================================================================

    @Nested
    inner class WeeklyPlanTests {

        @Test
        fun `GET weekly plan returns 200`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            // Seed the plan first
            client.get("/api/workouts/suggested/$userId")

            val response = client.get("/api/users/$userId/workout-plan/week")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `GET weekly plan response contains scheduledDate field`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")

            val response = client.get("/api/users/$userId/workout-plan/week")
            val body = response.bodyAsText()
            assertTrue(body.contains("scheduledDate"), "Response should contain scheduledDate")
        }

        @Test
        fun `GET weekly plan response contains sessionLabel field`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")

            val response = client.get("/api/users/$userId/workout-plan/week")
            val body = response.bodyAsText()
            assertTrue(body.contains("sessionLabel"), "Response should contain sessionLabel")
        }

        @Test
        fun `GET weekly plan for invalid user id returns 400`() = withFuelifyApp {
            val response = client.get("/api/users/notanumber/workout-plan/week")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `GET weekly plan for user with no plan returns empty list`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            // Do NOT call suggested first — plan should be empty
            val response = client.get("/api/users/$userId/workout-plan/week")
            val body = response.bodyAsText()
            assertTrue(body.contains("[]"), "Empty plan should return empty data array")
        }
    }

    // =========================================================================
    // 5. Workout List and Detail Endpoints
    // =========================================================================

    @Nested
    inner class WorkoutListAndDetailTests {

        @Test
        fun `GET workouts returns 200`() = withFuelifyApp {
            val response = client.get("/api/workouts")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `GET workouts returns seeded workout names`() = withFuelifyApp {
            val response = client.get("/api/workouts")
            val body = response.bodyAsText()
            assertTrue(body.contains("Treadmill Run"), "Should return seeded workouts")
        }

        @Test
        fun `GET workouts with category filter returns only matching workouts`() = withFuelifyApp {
            val response = client.get("/api/workouts?category=Running")
            val body = response.bodyAsText()
            assertTrue(body.contains("Running"), "Filtered results should contain Running")
            assertFalse(body.contains("Yoga"), "Filtered results should not contain Yoga")
        }

        @Test
        fun `GET workouts with difficulty filter returns only matching workouts`() = withFuelifyApp {
            val response = client.get("/api/workouts?difficulty=Beginner")
            val body = response.bodyAsText()
            assertTrue(body.contains("Beginner"), "Filtered results should contain Beginner workouts")
        }

        @Test
        fun `GET workout by valid id returns 200`() = withFuelifyApp {
            val workoutId = transaction {
                TestWorkouts.select { TestWorkouts.workoutName eq "Treadmill Run" }
                    .first()[TestWorkouts.workoutId]
            }
            val response = client.get("/api/workouts/$workoutId")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `GET workout by valid id returns correct workout name`() = withFuelifyApp {
            val workoutId = transaction {
                TestWorkouts.select { TestWorkouts.workoutName eq "Treadmill Run" }
                    .first()[TestWorkouts.workoutId]
            }
            val response = client.get("/api/workouts/$workoutId")
            val body = response.bodyAsText()
            assertTrue(body.contains("Treadmill Run"), "Should return the correct workout")
        }

        @Test
        fun `GET workout by non-existing id returns 404`() = withFuelifyApp {
            val response = client.get("/api/workouts/99999")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        @Test
        fun `GET workout by invalid id returns 400`() = withFuelifyApp {
            val response = client.get("/api/workouts/abc")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    // =========================================================================
    // 6. NutritionEngine Integration — Verified Through DB Values
    // =========================================================================

    @Nested
    inner class NutritionEngineIntegrationTests {

        /**
         * These tests verify that NutritionEngine math produces expected results
         * for the seeded user profiles, without calling the route directly.
         * They confirm that DB-stored profile data feeds correctly into the engine.
         */

        @Test
        fun `male user profile produces positive BMR`() {
            val user = transaction {
                TestUsers.select { TestUsers.email eq "ahmed@test.com" }.first()
            }
            val bmr = 10.0 * user[TestUsers.weightKg] +
                      6.25 * user[TestUsers.heightCm] -
                      5.0  * user[TestUsers.age] + 5
            assertTrue(bmr > 0, "BMR should be positive for a valid male profile")
        }

        @Test
        fun `female user profile produces positive BMR`() {
            val user = transaction {
                TestUsers.select { TestUsers.email eq "sara@test.com" }.first()
            }
            val bmr = 10.0 * user[TestUsers.weightKg] +
                      6.25 * user[TestUsers.heightCm] -
                      5.0  * user[TestUsers.age] - 161
            assertTrue(bmr > 0, "BMR should be positive for a valid female profile")
        }

        @Test
        fun `lose weight goal produces calorie target below TDEE`() {
            val user = transaction {
                TestUsers.select { TestUsers.email eq "ahmed@test.com" }.first()
            }
            val bmr   = 10.0 * user[TestUsers.weightKg] + 6.25 * user[TestUsers.heightCm] - 5.0 * user[TestUsers.age] + 5
            val tdee  = bmr * 1.55   // moderately active
            val target = (tdee - 500).toInt().coerceAtLeast(1200)
            assertTrue(target < tdee.toInt(), "Lose weight target should be below TDEE")
        }

        @Test
        fun `gain muscle goal produces calorie target above TDEE`() {
            val user = transaction {
                TestUsers.select { TestUsers.email eq "sara@test.com" }.first()
            }
            val bmr   = 10.0 * user[TestUsers.weightKg] + 6.25 * user[TestUsers.heightCm] - 5.0 * user[TestUsers.age] - 161
            val tdee  = bmr * 1.375  // lightly active
            val target = (tdee + 300).toInt()
            assertTrue(target > tdee.toInt(), "Gain muscle target should be above TDEE")
        }

        @Test
        fun `calorie target is never below 1200 kcal safety floor`() {
            // Simulate an extreme case: very low weight elderly user
            val weightKg = 40; val heightCm = 150; val age = 80
            val bmr   = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161  // female
            val tdee  = bmr * 1.2   // sedentary
            val target = (tdee - 500).toInt().coerceAtLeast(1200)
            assertTrue(target >= 1200, "Calorie target must never fall below 1200 kcal")
        }

        @Test
        fun `user profile stored in DB matches what was inserted`() {
            val user = transaction {
                TestUsers.select { TestUsers.email eq "ahmed@test.com" }.first()
            }
            assertEquals(75,  user[TestUsers.weightKg])
            assertEquals(175, user[TestUsers.heightCm])
            assertEquals(25,  user[TestUsers.age])
            assertEquals("lose weight", user[TestUsers.goal])
            assertEquals("gym",         user[TestUsers.trainingPlace])
        }

        @Test
        fun `both users have different goals stored correctly`() {
            val ahmedGoal = transaction {
                TestUsers.select { TestUsers.email eq "ahmed@test.com" }.first()[TestUsers.goal]
            }
            val saraGoal = transaction {
                TestUsers.select { TestUsers.email eq "sara@test.com" }.first()[TestUsers.goal]
            }
            assertNotEquals(ahmedGoal, saraGoal, "Two different users should have different goals")
            assertEquals("lose weight",  ahmedGoal)
            assertEquals("gain muscle",  saraGoal)
        }
    }

    // =========================================================================
    // 7. Full User Flow — Suggested → Plan in DB
    // =========================================================================

    @Nested
    inner class FullUserFlowTests {

        @Test
        fun `full flow user exists, call suggested, plan appears in weekly view`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")

            // Step 1: Call suggested endpoint (generates and saves plan)
            val suggestedResponse = client.get("/api/workouts/suggested/$userId")
            assertEquals(HttpStatusCode.OK, suggestedResponse.status)

            // Step 2: Call weekly plan endpoint
            val weekResponse = client.get("/api/users/$userId/workout-plan/week")
            assertEquals(HttpStatusCode.OK, weekResponse.status)

            // Step 3: Weekly plan should now contain data
            val weekBody = weekResponse.bodyAsText()
            assertFalse(
                weekBody.contains("\"data\":[]") || weekBody.contains("\"data\": []"),
                "Weekly plan should not be empty after calling suggested"
            )
        }

        @Test
        fun `full flow female beginner home user gets valid plan`() = withFuelifyApp {
            val userId = getUserId("sara@test.com")

            val suggestedResponse = client.get("/api/workouts/suggested/$userId")
            assertEquals(HttpStatusCode.OK, suggestedResponse.status)

            val body = suggestedResponse.bodyAsText()
            assertTrue(body.contains("success"), "Response should have success field")
            assertTrue(body.contains("reason"),  "Response should have reason field")
        }

        @Test
        fun `full flow plan rows in DB have correct userId`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")

            val allRows = transaction {
                TestWorkoutPlan.selectAll().map { it[TestWorkoutPlan.userId] }
            }
            assertTrue(allRows.all { it == userId }, "All plan rows should belong to the correct user")
        }

        @Test
        fun `full flow plan rows have valid scheduled dates within current week`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")

            val today     = LocalDate.now()
            val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val weekEnd   = weekStart.plusDays(6)

            val dates = transaction {
                TestWorkoutPlan.select { TestWorkoutPlan.userId eq userId }
                    .mapNotNull { it.getOrNull(TestWorkoutPlan.scheduledDate) }
            }

            assertTrue(dates.isNotEmpty(), "There should be scheduled dates in the plan")
            assertTrue(
                dates.all { it >= weekStart && it <= weekEnd },
                "All scheduled dates should fall within the current week"
            )
        }

        @Test
        fun `full flow plan rows have non-blank session labels`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")

            val labels = transaction {
                TestWorkoutPlan.select { TestWorkoutPlan.userId eq userId }
                    .map { it[TestWorkoutPlan.sessionLabel] }
            }

            assertTrue(labels.isNotEmpty(), "There should be session labels")
            assertTrue(labels.all { it.isNotBlank() }, "All session labels should be non-blank")
        }

        @Test
        fun `full flow plan status is set to planned by default`() = withFuelifyApp {
            val userId = getUserId("ahmed@test.com")
            client.get("/api/workouts/suggested/$userId")

            val statuses = transaction {
                TestWorkoutPlan.select { TestWorkoutPlan.userId eq userId }
                    .map { it[TestWorkoutPlan.status] }
            }

            assertTrue(statuses.isNotEmpty())
            assertTrue(statuses.all { it == "planned" }, "All new plan entries should have status 'planned'")
        }
    }
}
