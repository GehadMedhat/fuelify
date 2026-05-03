package com.example.fuelify.models

/**
 * Pure calculation functions only.
 * No DB access here — just math based on user profile.
 */
object NutritionEngine {

    // ── BMR (Mifflin-St Jeor) ─────────────────────────────────────────────────
    fun bmr(weightKg: Int, heightCm: Int, age: Int, gender: String): Double =
        if (gender.lowercase() == "female")
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161
        else
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5

    // ── TDEE ─────────────────────────────────────────────────────────────────
    fun tdee(bmr: Double, activityLevel: String): Double =
        bmr * when (activityLevel.lowercase().trim()) {
            "sedentary"                           -> 1.2
            "lightly active", "light"             -> 1.375
            "moderately active", "moderate"       -> 1.55
            "very active"                         -> 1.725
            "extra active", "athlete"             -> 1.9
            else                                  -> 1.55
        }

    // ── Daily calorie target after goal adjustment ────────────────────────────
    fun dailyCalories(tdee: Double, goal: String): Int =
        when (goal.lowercase().trim()) {
            "lose weight"                             -> tdee - 500
            "gain muscle", "gain weight"              -> tdee + 300
            "get fit", "maintain", "maintain weight"  -> tdee
            else                                      -> tdee
        }.toInt().coerceAtLeast(1200)

    // ── BMI ───────────────────────────────────────────────────────────────────
    fun bmi(weightKg: Int, heightCm: Int): Double {
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }

    // ── Macro targets (grams) ──────────────────────────────────────────────────
    data class Macros(val proteinG: Int, val carbsG: Int, val fatG: Int)

    fun macros(dailyCal: Int, goal: String): Macros {
        val pPct: Double; val cPct: Double; val fPct: Double
        when (goal.lowercase().trim()) {
            "lose weight"  -> { pPct = 0.35; cPct = 0.40; fPct = 0.25 }
            "gain muscle"  -> { pPct = 0.30; cPct = 0.45; fPct = 0.25 }
            else           -> { pPct = 0.25; cPct = 0.50; fPct = 0.25 }
        }
        return Macros(
            proteinG = (dailyCal * pPct / 4.0).toInt(),
            carbsG   = (dailyCal * cPct / 4.0).toInt(),
            fatG     = (dailyCal * fPct / 9.0).toInt()
        )
    }

    // ── Preferred diet types based on user profile ────────────────────────────
    /**
     * Returns a ranked list of diet types to prefer, based on:
     * - goal (lose weight / gain muscle / get fit)
     * - BMI
     * - activity level
     * - motivation
     */
    fun preferredDietTypes(
        goal: String,
        bmi: Double,
        activityLevel: String,
        motivation: String,
        fitnessLevel: String
    ): List<String> {
        val g = goal.lowercase().trim()
        val m = motivation.lowercase()

        return when {
            // Lose weight: prefer Low Carb, then Balanced
            g == "lose weight" -> listOf("Low Carb", "Balanced", "High Protein", "Vegan")

            // Gain muscle / bodybuilding: High Protein first
            g == "gain muscle" || g == "gain weight" ->
                listOf("High Protein", "Balanced", "Low Carb", "Vegan")

            // Very active / athlete: High Protein for recovery
            activityLevel.lowercase().contains("very active") ||
            activityLevel.lowercase().contains("athlete") ->
                listOf("High Protein", "Balanced", "Low Carb", "Vegan")

            // Health & wellness motivation: Balanced/Vegan
            m.contains("health") || m.contains("wellness") ->
                listOf("Balanced", "Vegan", "High Protein", "Low Carb")

            // Default: Balanced
            else -> listOf("Balanced", "High Protein", "Low Carb", "Vegan")
        }
    }

    // ── Price range based on budget ───────────────────────────────────────────
    fun maxPrice(budget: String): Double = when (budget.lowercase().trim()) {
        "budget friendly" -> 5.0
        "standard"        -> 10.0
        "premium"         -> 999.0
        else              -> 10.0
    }

    // ── Meal time slots based on meals per day ────────────────────────────────
    data class MealSlot(val mealTime: String, val scheduledTime: String, val caloriePct: Double)

    fun mealSlots(mealsPerDay: Int): List<MealSlot> = when (mealsPerDay) {
        1 -> listOf(
            MealSlot("Lunch",  "1:00 PM", 1.0)
        )
        2 -> listOf(
            MealSlot("Breakfast", "8:00 AM",  0.45),
            MealSlot("Dinner",    "7:00 PM",  0.55)
        )
        3 -> listOf(
            MealSlot("Breakfast", "8:00 AM",  0.25),
            MealSlot("Lunch",     "12:30 PM", 0.40),
            MealSlot("Dinner",    "7:00 PM",  0.35)
        )
        4 -> listOf(
            MealSlot("Breakfast", "8:00 AM",  0.25),
            MealSlot("Lunch",     "12:30 PM", 0.35),
            MealSlot("Dinner",    "7:00 PM",  0.30),
            MealSlot("Snack",     "3:30 PM",  0.10)
        )
        5 -> listOf(
            MealSlot("Breakfast", "8:00 AM",  0.20),
            MealSlot("Snack",     "10:30 AM", 0.10),
            MealSlot("Lunch",     "1:00 PM",  0.30),
            MealSlot("Dinner",    "7:00 PM",  0.30),
            MealSlot("Snack",     "4:00 PM",  0.10)
        )
        6 -> listOf(
            MealSlot("Breakfast", "8:00 AM",  0.20),
            MealSlot("Snack",     "10:30 AM", 0.08),
            MealSlot("Lunch",     "1:00 PM",  0.27),
            MealSlot("Snack",     "3:30 PM",  0.08),
            MealSlot("Dinner",    "7:00 PM",  0.27),
            MealSlot("Snack",     "9:00 PM",  0.10)
        )
        else -> listOf(
            MealSlot("Breakfast", "8:00 AM",  0.25),
            MealSlot("Lunch",     "12:30 PM", 0.40),
            MealSlot("Dinner",    "7:00 PM",  0.35)
        )
    }

    // ── Allergy keyword map ───────────────────────────────────────────────────
    // Maps allergy type names → keywords to check in meal names/ingredients
    val allergyKeywords = mapOf(
        "dairy"     to listOf("yogurt", "cheese", "butter", "milk", "cream", "whey"),
        "gluten"    to listOf("pasta", "bread", "toast", "wrap", "pancake", "oat", "wheat", "meatball"),
        "nuts"      to listOf("almond", "walnut", "cashew", "pistachio", "nut"),
        "soy"       to listOf("tofu", "soy", "edamame"),
        "eggs"      to listOf("egg", "omelette", "frittata"),
        "shellfish" to listOf("shrimp", "prawn", "crab", "lobster", "scallop"),
        "peanuts"   to listOf("peanut"),
        "fish"      to listOf("salmon", "tuna", "cod", "tilapia", "fish", "anchovy", "sardine")
    )
}
