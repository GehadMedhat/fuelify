package com.example.fuelify.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.abs

/**
 * Unit tests for NutritionEngine.kt
 *
 * Run with:  ./gradlew test --tests "com.example.fuelify.models.NutritionEngineTest"
 *
 * Dependencies to add in build.gradle (backend module):
 *
 *   testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
 *   testRuntimeOnly("org.junit.platform:junit-platform-launcher")
 *
 * And in the test block:
 *   useJUnitPlatform()
 */
class NutritionEngineTest {

    // ─── Helper ───────────────────────────────────────────────────────────────

    /** Asserts two doubles are equal within 0.01 tolerance. */
    private fun assertNearlyEqual(expected: Double, actual: Double, msg: String = "") {
        assertTrue(
            abs(expected - actual) < 0.01,
            "$msg → expected ≈ $expected but was $actual"
        )
    }

    // =========================================================================
    // 1. BMR  (Mifflin-St Jeor)
    // =========================================================================

    @Nested
    inner class BmrTests {

        /**
         * Male formula:  10W + 6.25H - 5A + 5
         * Example:  weight=70kg, height=175cm, age=25
         *   = 700 + 1093.75 - 125 + 5 = 1673.75
         */
        @Test
        fun `male BMR uses correct formula`() {
            val result = NutritionEngine.bmr(weightKg = 70, heightCm = 175, age = 25, gender = "male")
            assertNearlyEqual(1673.75, result, "Male BMR")
        }

        /**
         * Female formula:  10W + 6.25H - 5A - 161
         * Example:  weight=60kg, height=165cm, age=30
         *   = 600 + 1031.25 - 150 - 161 = 1320.25
         */
        @Test
        fun `female BMR uses correct formula`() {
            val result = NutritionEngine.bmr(weightKg = 60, heightCm = 165, age = 30, gender = "female")
            assertNearlyEqual(1320.25, result, "Female BMR")
        }

        @Test
        fun `gender is case-insensitive`() {
            val upper = NutritionEngine.bmr(70, 175, 25, "FEMALE")
            val lower = NutritionEngine.bmr(70, 175, 25, "female")
            assertNearlyEqual(upper, lower, "Case insensitive gender")
        }

        @Test
        fun `male and female BMR differ by 166`() {
            // The only difference between the two formulas is +5 vs -161 → delta = 166
            val male   = NutritionEngine.bmr(70, 175, 25, "male")
            val female = NutritionEngine.bmr(70, 175, 25, "female")
            assertNearlyEqual(166.0, male - female, "BMR gender delta")
        }

        @Test
        fun `BMR increases with higher weight`() {
            val light  = NutritionEngine.bmr(60, 175, 25, "male")
            val heavy  = NutritionEngine.bmr(90, 175, 25, "male")
            assertTrue(heavy > light, "Heavier person should have higher BMR")
        }

        @Test
        fun `BMR decreases with older age`() {
            val young = NutritionEngine.bmr(70, 175, 20, "male")
            val old   = NutritionEngine.bmr(70, 175, 60, "male")
            assertTrue(young > old, "Younger person should have higher BMR")
        }
    }

    // =========================================================================
    // 2. TDEE
    // =========================================================================

    @Nested
    inner class TdeeTests {

        private val baseBmr = 1500.0

        @ParameterizedTest(name = "{0} → multiplier {1}")
        @CsvSource(
            "sedentary,         1.2",
            "lightly active,    1.375",
            "light,             1.375",
            "moderately active, 1.55",
            "moderate,          1.55",
            "very active,       1.725",
            "extra active,      1.9",
            "athlete,           1.9"
        )
        fun `activity level maps to correct multiplier`(level: String, multiplier: Double) {
            val expected = baseBmr * multiplier
            val actual   = NutritionEngine.tdee(baseBmr, level)
            assertNearlyEqual(expected, actual, "TDEE for '$level'")
        }

        @Test
        fun `unknown activity level defaults to moderately active (1_55)`() {
            val result = NutritionEngine.tdee(baseBmr, "couch warrior")
            assertNearlyEqual(baseBmr * 1.55, result, "Unknown activity default")
        }

        @Test
        fun `activity level matching is case and whitespace insensitive`() {
            val normal = NutritionEngine.tdee(baseBmr, "sedentary")
            val messy  = NutritionEngine.tdee(baseBmr, "  SEDENTARY  ")
            assertNearlyEqual(normal, messy, "TDEE case-insensitive")
        }

        @Test
        fun `higher activity level produces higher TDEE`() {
            val sed    = NutritionEngine.tdee(baseBmr, "sedentary")
            val active = NutritionEngine.tdee(baseBmr, "very active")
            assertTrue(active > sed, "Very active should have higher TDEE than sedentary")
        }
    }

    // =========================================================================
    // 3. Daily Calories (goal adjustment + floor)
    // =========================================================================

    @Nested
    inner class DailyCaloriesTests {

        private val baseTdee = 2000.0

        @Test
        fun `lose weight subtracts 500 from TDEE`() {
            val result = NutritionEngine.dailyCalories(baseTdee, "lose weight")
            assertEquals(1500, result)
        }

        @Test
        fun `gain muscle adds 300 to TDEE`() {
            val result = NutritionEngine.dailyCalories(baseTdee, "gain muscle")
            assertEquals(2300, result)
        }

        @Test
        fun `gain weight adds 300 to TDEE`() {
            val result = NutritionEngine.dailyCalories(baseTdee, "gain weight")
            assertEquals(2300, result)
        }

        @Test
        fun `maintain returns TDEE unchanged`() {
            assertEquals(2000, NutritionEngine.dailyCalories(baseTdee, "maintain"))
            assertEquals(2000, NutritionEngine.dailyCalories(baseTdee, "maintain weight"))
            assertEquals(2000, NutritionEngine.dailyCalories(baseTdee, "get fit"))
        }

        @Test
        fun `daily calories never go below 1200 floor`() {
            // Very low TDEE: 1100 - 500 = 600, should be clamped to 1200
            val result = NutritionEngine.dailyCalories(1100.0, "lose weight")
            assertEquals(1200, result, "Should be clamped to 1200 minimum")
        }

        @Test
        fun `1200 floor applies even with unknown goal`() {
            val result = NutritionEngine.dailyCalories(500.0, "some unknown goal")
            assertEquals(1200, result, "Floor should apply for any goal path")
        }

        @Test
        fun `goal matching is case-insensitive`() {
            val lower = NutritionEngine.dailyCalories(baseTdee, "lose weight")
            val upper = NutritionEngine.dailyCalories(baseTdee, "LOSE WEIGHT")
            assertEquals(lower, upper)
        }
    }

    // =========================================================================
    // 4. BMI
    // =========================================================================

    @Nested
    inner class BmiTests {

        /**
         * BMI = weight / (height_m)²
         * 70kg / (1.75m)² = 70 / 3.0625 ≈ 22.86
         */
        @Test
        fun `BMI calculated correctly`() {
            val result = NutritionEngine.bmi(weightKg = 70, heightCm = 175)
            assertNearlyEqual(22.86, result, "BMI 70kg 175cm")
        }

        @Test
        fun `BMI increases with weight at constant height`() {
            val normal    = NutritionEngine.bmi(70, 175)
            val overweight = NutritionEngine.bmi(90, 175)
            assertTrue(overweight > normal)
        }

        @Test
        fun `BMI decreases with height at constant weight`() {
            val shorter = NutritionEngine.bmi(70, 160)
            val taller  = NutritionEngine.bmi(70, 190)
            assertTrue(shorter > taller, "Shorter person has higher BMI at same weight")
        }

        @Test
        fun `BMI of 18_5 boundary (underweight threshold) is correct`() {
            // 50kg / (1.646m)² ≈ 18.5
            val result = NutritionEngine.bmi(weightKg = 50, heightCm = 164)
            assertTrue(result in 18.0..19.0, "Should be near underweight threshold: $result")
        }
    }

    // =========================================================================
    // 5. Macros
    // =========================================================================

    @Nested
    inner class MacrosTests {

        @Test
        fun `lose weight macro split is 35-40-25`() {
            val m = NutritionEngine.macros(2000, "lose weight")
            // protein: 2000 * 0.35 / 4 = 175g
            assertEquals(175, m.proteinG, "Protein for lose weight")
            // carbs:   2000 * 0.40 / 4 = 200g
            assertEquals(200, m.carbsG, "Carbs for lose weight")
            // fat:     2000 * 0.25 / 9 = 55g
            assertEquals(55, m.fatG, "Fat for lose weight")
        }

        @Test
        fun `gain muscle macro split is 30-45-25`() {
            val m = NutritionEngine.macros(2000, "gain muscle")
            assertEquals(150, m.proteinG)   // 2000 * 0.30 / 4
            assertEquals(225, m.carbsG)     // 2000 * 0.45 / 4
            assertEquals(55,  m.fatG)       // 2000 * 0.25 / 9
        }

        @Test
        fun `default macro split is 25-50-25`() {
            val m = NutritionEngine.macros(2000, "maintain")
            assertEquals(125, m.proteinG)   // 2000 * 0.25 / 4
            assertEquals(250, m.carbsG)     // 2000 * 0.50 / 4
            assertEquals(55,  m.fatG)       // 2000 * 0.25 / 9
        }

        @Test
        fun `all macro values are non-negative`() {
            listOf("lose weight", "gain muscle", "maintain", "get fit").forEach { goal ->
                val m = NutritionEngine.macros(1500, goal)
                assertTrue(m.proteinG >= 0, "Protein negative for $goal")
                assertTrue(m.carbsG   >= 0, "Carbs negative for $goal")
                assertTrue(m.fatG     >= 0, "Fat negative for $goal")
            }
        }
    }

    // =========================================================================
    // 6. Meal Slots
    // =========================================================================

    @Nested
    inner class MealSlotsTests {

        @Test
        fun `3 meals per day returns 3 slots`() {
            assertEquals(3, NutritionEngine.mealSlots(3).size)
        }

        @Test
        fun `5 meals per day returns 5 slots`() {
            assertEquals(5, NutritionEngine.mealSlots(5).size)
        }

        @ParameterizedTest(name = "{0} meals per day → calorie percentages sum to 1.0")
        @CsvSource("1", "2", "3", "4", "5", "6")
        fun `calorie percentages sum to 1 for all meal counts`(count: Int) {
            val total = NutritionEngine.mealSlots(count).sumOf { it.caloriePct }
            assertNearlyEqual(1.0, total, "$count meals/day percentage sum")
        }

        @Test
        fun `unknown meal count falls back to 3 slots`() {
            assertEquals(3, NutritionEngine.mealSlots(99).size, "Unknown meal count should default to 3")
        }

        @Test
        fun `all slots have non-empty meal time and scheduled time`() {
            NutritionEngine.mealSlots(3).forEach { slot ->
                assertTrue(slot.mealTime.isNotBlank(),     "mealTime should not be blank")
                assertTrue(slot.scheduledTime.isNotBlank(), "scheduledTime should not be blank")
            }
        }

        @Test
        fun `all calorie percentages are positive`() {
            (1..6).forEach { count ->
                NutritionEngine.mealSlots(count).forEach { slot ->
                    assertTrue(slot.caloriePct > 0, "Slot ${slot.mealTime} has non-positive caloriePct")
                }
            }
        }
    }

    // =========================================================================
    // 7. Preferred Diet Types
    // =========================================================================

    @Nested
    inner class PreferredDietTypesTests {

        @Test
        fun `lose weight goal ranks Low Carb first`() {
            val result = NutritionEngine.preferredDietTypes(
                goal = "lose weight", bmi = 27.0,
                activityLevel = "sedentary", motivation = "weight loss", fitnessLevel = "beginner"
            )
            assertEquals("Low Carb", result.first(), "Lose weight should prefer Low Carb")
        }

        @Test
        fun `gain muscle goal ranks High Protein first`() {
            val result = NutritionEngine.preferredDietTypes(
                goal = "gain muscle", bmi = 22.0,
                activityLevel = "moderately active", motivation = "strength", fitnessLevel = "intermediate"
            )
            assertEquals("High Protein", result.first())
        }

        @Test
        fun `athlete activity level ranks High Protein first`() {
            val result = NutritionEngine.preferredDietTypes(
                goal = "get fit", bmi = 21.0,
                activityLevel = "athlete", motivation = "performance", fitnessLevel = "advanced"
            )
            assertEquals("High Protein", result.first())
        }

        @Test
        fun `health motivation ranks Balanced first`() {
            val result = NutritionEngine.preferredDietTypes(
                goal = "get fit", bmi = 22.0,
                activityLevel = "moderately active", motivation = "health & wellness", fitnessLevel = "beginner"
            )
            assertEquals("Balanced", result.first())
        }

        @Test
        fun `result always contains exactly 4 diet types`() {
            val goals = listOf("lose weight", "gain muscle", "get fit", "maintain")
            goals.forEach { goal ->
                val result = NutritionEngine.preferredDietTypes(
                    goal, 22.0, "moderately active", "health", "beginner"
                )
                assertEquals(4, result.size, "Should always return 4 diet types for goal: $goal")
            }
        }

        @Test
        fun `result contains all four diet type options`() {
            val result = NutritionEngine.preferredDietTypes(
                goal = "lose weight", bmi = 25.0,
                activityLevel = "sedentary", motivation = "weight", fitnessLevel = "beginner"
            )
            assertTrue(result.containsAll(listOf("Low Carb", "Balanced", "High Protein", "Vegan")))
        }
    }

    // =========================================================================
    // 8. Max Price (Budget)
    // =========================================================================

    @Nested
    inner class MaxPriceTests {

        @Test
        fun `budget friendly returns 5`() {
            assertEquals(5.0, NutritionEngine.maxPrice("budget friendly"))
        }

        @Test
        fun `standard returns 10`() {
            assertEquals(10.0, NutritionEngine.maxPrice("standard"))
        }

        @Test
        fun `premium returns 999`() {
            assertEquals(999.0, NutritionEngine.maxPrice("premium"))
        }

        @Test
        fun `unknown budget defaults to standard (10)`() {
            assertEquals(10.0, NutritionEngine.maxPrice("mystery tier"))
        }

        @Test
        fun `budget matching is case-insensitive`() {
            assertEquals(
                NutritionEngine.maxPrice("budget friendly"),
                NutritionEngine.maxPrice("BUDGET FRIENDLY")
            )
        }
    }

    // =========================================================================
    // 9. Full pipeline integration (BMR → TDEE → dailyCalories)
    // =========================================================================

    @Nested
    inner class PipelineTests {

        @Test
        fun `full pipeline for typical male lose-weight user`() {
            // 25yo male, 80kg, 178cm, moderately active, lose weight
            val bmr     = NutritionEngine.bmr(80, 178, 25, "male")
            val tdee    = NutritionEngine.tdee(bmr, "moderately active")
            val cal     = NutritionEngine.dailyCalories(tdee, "lose weight")

            // BMR = 10×80 + 6.25×178 - 5×25 + 5 = 1792.5
            // TDEE = 1792.5 × 1.55 = 2778.375
            // cal  = 2778 - 500 = 2278
            assertTrue(bmr  in 1780.0..1810.0, "BMR out of expected range: $bmr")
            assertTrue(tdee in 2760.0..2800.0, "TDEE out of expected range: $tdee")
            assertTrue(cal  in 2250..2300,     "Daily cal out of expected range: $cal")
        }

        @Test
        fun `full pipeline for typical female gain-muscle user`() {
            // 22yo female, 55kg, 162cm, lightly active, gain muscle
            val bmr  = NutritionEngine.bmr(55, 162, 22, "female")
            val tdee = NutritionEngine.tdee(bmr, "lightly active")
            val cal  = NutritionEngine.dailyCalories(tdee, "gain muscle")

            // BMR ≈ 1296, TDEE ≈ 1782, after +300 ≈ 2082
            assertTrue(bmr  in 1270.0..1330.0, "BMR out of range: $bmr")
            assertTrue(tdee in 1740.0..1820.0, "TDEE out of range: $tdee")
            assertTrue(cal  in 2040..2120,     "Daily cal out of range: $cal")
        }

        @Test
        fun `sedentary person wanting to lose weight hits 1200 floor when BMR is very low`() {
            // Extreme case: very small person, very low TDEE
            val bmr  = NutritionEngine.bmr(40, 145, 70, "female")
            val tdee = NutritionEngine.tdee(bmr, "sedentary")
            val cal  = NutritionEngine.dailyCalories(tdee, "lose weight")

            // Even if math gives < 1200, must be clamped
            assertTrue(cal >= 1200, "Should never be below 1200 floor, was: $cal")
        }
    }
}
