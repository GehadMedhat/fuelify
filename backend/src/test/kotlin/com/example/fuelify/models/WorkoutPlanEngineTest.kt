package com.example.fuelify.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for WorkoutPlanEngine.kt
 *
 * Run with:  ./gradlew test --tests "com.example.fuelify.models.WorkoutPlanEngineTest"
 */
class WorkoutPlanEngineTest {

    // =========================================================================
    // 1. Category Selection
    // =========================================================================

    @Nested
    inner class CategorySelectionTests {

        // ── Gym ───────────────────────────────────────────────────────────────

        @Test
        fun `gym + gain muscle → Gym, Upper Body, Personal Training`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "gain muscle", fitnessLevel = "intermediate",
                trainingPlace = "Gym", activityLevel = "moderately active",
                exerciseDays = 4, weightKg = 75, heightCm = 178, age = 25,
                gender = "male", motivation = "strength"
            )
            assertEquals(listOf("Gym", "Upper Body", "Personal Training"), p.categories)
        }

        @Test
        fun `gym + lose weight → Running, Boxing, Gym`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "lose weight", fitnessLevel = "beginner",
                trainingPlace = "Gym", activityLevel = "lightly active",
                exerciseDays = 3, weightKg = 85, heightCm = 170, age = 30,
                gender = "female", motivation = "weight loss"
            )
            assertEquals(listOf("Running", "Boxing", "Gym"), p.categories)
        }

        @Test
        fun `gym + general fitness → Gym, Upper Body, Running`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "get fit", fitnessLevel = "intermediate",
                trainingPlace = "Gym", activityLevel = "moderately active",
                exerciseDays = 4, weightKg = 70, heightCm = 175, age = 25,
                gender = "male", motivation = "health"
            )
            assertEquals(listOf("Gym", "Upper Body", "Running"), p.categories)
        }

        // ── Home ──────────────────────────────────────────────────────────────

        @Test
        fun `home + gain muscle → Upper Body, Stretch`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "gain muscle", fitnessLevel = "intermediate",
                trainingPlace = "Home", activityLevel = "moderately active",
                exerciseDays = 4, weightKg = 75, heightCm = 178, age = 25,
                gender = "male", motivation = "strength"
            )
            assertEquals(listOf("Upper Body", "Stretch"), p.categories)
        }

        @Test
        fun `home + lose weight → Running, Upper Body, Stretch`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "lose weight", fitnessLevel = "beginner",
                trainingPlace = "Home", activityLevel = "sedentary",
                exerciseDays = 3, weightKg = 80, heightCm = 165, age = 28,
                gender = "female", motivation = "weight loss"
            )
            assertEquals(listOf("Running", "Upper Body", "Stretch"), p.categories)
        }

        @Test
        fun `home + general fitness → Upper Body, Running, Stretch, Yoga`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "maintain", fitnessLevel = "beginner",
                trainingPlace = "Home", activityLevel = "lightly active",
                exerciseDays = 3, weightKg = 65, heightCm = 168, age = 22,
                gender = "female", motivation = "wellness"
            )
            assertEquals(listOf("Upper Body", "Running", "Stretch", "Yoga"), p.categories)
        }

        // ── Hybrid ────────────────────────────────────────────────────────────

        @Test
        fun `hybrid + gain muscle → Gym, Upper Body, Personal Training`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "gain muscle", fitnessLevel = "advanced",
                trainingPlace = "Hybrid", activityLevel = "very active",
                exerciseDays = 5, weightKg = 80, heightCm = 180, age = 24,
                gender = "male", motivation = "strength"
            )
            assertEquals(listOf("Gym", "Upper Body", "Personal Training"), p.categories)
        }

        @Test
        fun `category matching is case-insensitive for training place`() {
            val lower = WorkoutPlanEngine.buildPlanParams(
                "gain muscle", "intermediate", "gym", "moderately active",
                4, 75, 178, 25, "male", "strength"
            )
            val upper = WorkoutPlanEngine.buildPlanParams(
                "gain muscle", "intermediate", "GYM", "moderately active",
                4, 75, 178, 25, "male", "strength"
            )
            assertEquals(lower.categories, upper.categories)
        }
    }

    // =========================================================================
    // 2. Difficulty Mapping
    // =========================================================================

    @Nested
    inner class DifficultyTests {

        @Test
        fun `beginner fitness level → Beginner difficulty`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Beginner", "Gym", "moderately active",
                4, 70, 175, 25, "male", "health"
            )
            assertEquals("Beginner", p.difficulty)
        }

        @Test
        fun `irregular fitness level → Beginner difficulty`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Irregular", "Gym", "moderately active",
                4, 70, 175, 25, "male", "health"
            )
            assertEquals("Beginner", p.difficulty)
        }

        @Test
        fun `advanced fitness level → Advanced difficulty`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "gain muscle", "Advanced", "Gym", "very active",
                5, 80, 180, 24, "male", "strength"
            )
            assertEquals("Advanced", p.difficulty)
        }

        @Test
        fun `athlete fitness level → Advanced difficulty`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "gain muscle", "Athlete", "Gym", "athlete",
                6, 85, 182, 22, "male", "performance"
            )
            assertEquals("Advanced", p.difficulty)
        }

        @Test
        fun `intermediate fitness level → Medium difficulty`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Intermediate", "Gym", "moderately active",
                4, 72, 176, 26, "male", "health"
            )
            assertEquals("Medium", p.difficulty)
        }

        @Test
        fun `null fitness level defaults to Medium`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "get fit", null, "Gym", "moderately active",
                4, 70, 175, 25, "male", "health"
            )
            assertEquals("Medium", p.difficulty)
        }
    }

    // =========================================================================
    // 3. Sessions Per Day
    // =========================================================================

    @Nested
    inner class SessionsPerDayTests {

        @Test
        fun `sessions per day is always between 1 and 3`() {
            val goals    = listOf("lose weight", "gain muscle", "get fit", "maintain")
            val places   = listOf("Gym", "Home", "Hybrid")
            val levels   = listOf("Beginner", "Intermediate", "Advanced")
            val activities = listOf("sedentary", "lightly active", "moderately active", "very active")

            for (goal in goals) for (place in places) for (level in levels) for (act in activities) {
                val p = WorkoutPlanEngine.buildPlanParams(
                    goal, level, place, act, 4, 70, 175, 25, "male", "health"
                )
                assertTrue(p.sessionsPerDay in 1..3,
                    "Sessions per day out of range for goal=$goal, place=$place: ${p.sessionsPerDay}")
            }
        }

        @Test
        fun `sedentary user has fewer or equal sessions than very active user`() {
            val sedentary = WorkoutPlanEngine.buildPlanParams(
                "lose weight", "Beginner", "Gym", "sedentary",
                3, 70, 170, 30, "female", "weight loss"
            )
            val active = WorkoutPlanEngine.buildPlanParams(
                "lose weight", "Advanced", "Gym", "very active",
                5, 75, 178, 25, "male", "weight loss"
            )
            assertTrue(sedentary.sessionsPerDay <= active.sessionsPerDay,
                "Sedentary user should not have more sessions than very active user")
        }
    }

    // =========================================================================
    // 4. Session Labels
    // =========================================================================

    @Nested
    inner class SessionLabelsTests {

        @Test
        fun `gain muscle with 1 session per day → Full Body Strength label`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "gain muscle", fitnessLevel = "beginner",
                trainingPlace = "Home", activityLevel = "sedentary",
                exerciseDays = 2, weightKg = 60, heightCm = 165, age = 20,
                gender = "male", motivation = "strength"
            )
            if (p.sessionsPerDay == 1)
                assertEquals(listOf("Full Body Strength"), p.sessionLabels)
        }

        @Test
        fun `lose weight session labels contain cardio-related terms`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "lose weight", fitnessLevel = "intermediate",
                trainingPlace = "Gym", activityLevel = "moderately active",
                exerciseDays = 4, weightKg = 80, heightCm = 170, age = 30,
                gender = "female", motivation = "weight loss"
            )
            val cardioLabels = listOf("Cardio Burn", "Cardio", "HIIT", "Full Body")
            assertTrue(
                p.sessionLabels.all { it in cardioLabels },
                "Lose weight labels should be cardio-related: ${p.sessionLabels}"
            )
        }

        @Test
        fun `session label count matches sessions per day`() {
            val goals  = listOf("lose weight", "gain muscle", "get fit")
            val places = listOf("Gym", "Home")

            for (goal in goals) for (place in places) {
                val p = WorkoutPlanEngine.buildPlanParams(
                    goal, "Intermediate", place, "moderately active",
                    4, 75, 175, 25, "male", "health"
                )
                assertEquals(p.sessionsPerDay, p.sessionLabels.size,
                    "Label count should match sessionsPerDay for goal=$goal, place=$place")
            }
        }

        @Test
        fun `all session labels are non-empty strings`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "gain muscle", "Advanced", "Gym", "very active",
                5, 85, 182, 22, "male", "strength"
            )
            p.sessionLabels.forEach { label ->
                assertTrue(label.isNotBlank(), "Session label should not be blank")
            }
        }
    }

    // =========================================================================
    // 5. Max Duration
    // =========================================================================

    @Nested
    inner class MaxDurationTests {

        @Test
        fun `multiple sessions per day caps duration at 45 minutes`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "lose weight", fitnessLevel = "Advanced",
                trainingPlace = "Gym", activityLevel = "very active",
                exerciseDays = 5, weightKg = 90, heightCm = 185, age = 22,
                gender = "male", motivation = "weight loss"
            )
            if (p.sessionsPerDay >= 2)
                assertEquals(45, p.maxDurationMinutes,
                    "Multi-session days should cap at 45 min")
        }

        @Test
        fun `5+ days per week with 1 session → 50 minutes max`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                goal = "maintain", fitnessLevel = "Beginner",
                trainingPlace = "Home", activityLevel = "sedentary",
                exerciseDays = 5, weightKg = 55, heightCm = 160, age = 35,
                gender = "female", motivation = "health"
            )
            if (p.sessionsPerDay == 1)
                assertEquals(50, p.maxDurationMinutes)
        }

        @Test
        fun `max duration is always positive`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Beginner", "Gym", "sedentary",
                3, 60, 165, 40, "female", "health"
            )
            assertTrue(p.maxDurationMinutes > 0, "Max duration must be positive")
        }
    }

    // =========================================================================
    // 6. Reason String
    // =========================================================================

    @Nested
    inner class ReasonStringTests {

        @Test
        fun `reason contains exercise days per week`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Intermediate", "Gym", "moderately active",
                4, 70, 175, 25, "male", "health"
            )
            assertTrue(p.reason.contains("4"), "Reason should mention exercise days: ${p.reason}")
        }

        @Test
        fun `reason contains training place`() {
            val gymP = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Intermediate", "Gym", "moderately active",
                4, 70, 175, 25, "male", "health"
            )
            val homeP = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Intermediate", "Home", "moderately active",
                4, 70, 175, 25, "male", "health"
            )
            assertTrue(gymP.reason.contains("Gym"),  "Gym reason: ${gymP.reason}")
            assertTrue(homeP.reason.contains("Home"), "Home reason: ${homeP.reason}")
        }

        @Test
        fun `reason is never blank`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                null, null, null, null, null, null, null, null, null, null
            )
            assertTrue(p.reason.isNotBlank(), "Reason should never be blank even with null inputs")
        }
    }

    // =========================================================================
    // 7. Suggested Count
    // =========================================================================

    @Nested
    inner class SuggestedCountTests {

        @ParameterizedTest(name = "{0} exercise days → {1} suggested workouts")
        @CsvSource(
            "1, 2",
            "2, 2",
            "3, 2",
            "4, 3",
            "5, 3",
            "6, 4",
            "7, 4"
        )
        fun `exercise days map to correct suggested count`(exerciseDays: Int, expected: Int) {
            assertEquals(expected, WorkoutPlanEngine.suggestedCount(exerciseDays))
        }
    }

    // =========================================================================
    // 8. Workout Day Offsets
    // =========================================================================

    @Nested
    inner class WorkoutDayOffsetsTests {

        @Test
        fun `1 exercise day → only day 0`() {
            assertEquals(listOf(0), WorkoutPlanEngine.workoutDayOffsets(1))
        }

        @Test
        fun `2 exercise days → days 0 and 3 (rest day in between)`() {
            assertEquals(listOf(0, 3), WorkoutPlanEngine.workoutDayOffsets(2))
        }

        @Test
        fun `3 exercise days → every other day`() {
            assertEquals(listOf(0, 2, 4), WorkoutPlanEngine.workoutDayOffsets(3))
        }

        @Test
        fun `4 exercise days → 0, 1, 3, 4 (avoids back to back full weeks)`() {
            assertEquals(listOf(0, 1, 3, 4), WorkoutPlanEngine.workoutDayOffsets(4))
        }

        @Test
        fun `offset count matches exercise days for 1 through 7`() {
            (1..7).forEach { days ->
                val offsets = WorkoutPlanEngine.workoutDayOffsets(days)
                assertEquals(days, offsets.size, "Offset count should match exercise days: $days")
            }
        }

        @Test
        fun `all offsets are within 0 to 6 (valid week range)`() {
            (1..7).forEach { days ->
                WorkoutPlanEngine.workoutDayOffsets(days).forEach { offset ->
                    assertTrue(offset in 0..6, "Offset $offset out of weekly range for $days days")
                }
            }
        }

        @Test
        fun `offsets are always in ascending order`() {
            (1..7).forEach { days ->
                val offsets = WorkoutPlanEngine.workoutDayOffsets(days)
                assertEquals(offsets.sorted(), offsets, "Offsets should be ascending for $days days")
            }
        }

        @Test
        fun `no duplicate offsets in any configuration`() {
            (1..7).forEach { days ->
                val offsets = WorkoutPlanEngine.workoutDayOffsets(days)
                assertEquals(offsets.size, offsets.toSet().size, "Duplicate offsets for $days days")
            }
        }
    }

    // =========================================================================
    // 9. Null / Default Input Handling
    // =========================================================================

    @Nested
    inner class NullInputTests {

        @Test
        fun `all null inputs returns a valid result without throwing`() {
            assertDoesNotThrow {
                WorkoutPlanEngine.buildPlanParams(
                    null, null, null, null, null, null, null, null, null, null
                )
            }
        }

        @Test
        fun `null goal defaults to general fitness categories`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                null, "Intermediate", "Gym", "moderately active",
                4, 70, 175, 25, "male", null
            )
            assertTrue(p.categories.isNotEmpty(), "Should return categories even with null goal")
        }

        @Test
        fun `null exercise days defaults to 4`() {
            val withNull = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Intermediate", "Gym", "moderately active",
                null, 70, 175, 25, "male", "health"
            )
            val withFour = WorkoutPlanEngine.buildPlanParams(
                "get fit", "Intermediate", "Gym", "moderately active",
                4, 70, 175, 25, "male", "health"
            )
            assertEquals(withFour.reason, withNull.reason,
                "Null exerciseDays should behave same as 4")
        }

        @Test
        fun `result always has non-empty category list`() {
            val p = WorkoutPlanEngine.buildPlanParams(
                null, null, null, null, null, null, null, null, null, null
            )
            assertTrue(p.categories.isNotEmpty())
        }
    }
}
