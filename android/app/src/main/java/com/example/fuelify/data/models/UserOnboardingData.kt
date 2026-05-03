package com.example.fuelify.data.models

import java.io.Serializable

data class UserOnboardingData(
    var name: String = "",
    var gender: String = "",       // Male / Female
    var age: Int = 18,
    var heightCm: Int = 170,
    var weightKg: Int = 70,
    var goal: String = "",         // lose weight / maintain weight / gain weight / build muscle
    var activityLevel: String = "",// sedentary / lightly active / moderately active / very active / athlete
    var motivation: String = "",   // Health & Wellness / Strength & Endurance / Confidence Boost / Weight Loss
    var fitnessLevel: String = "", // Beginner / Intermediate / Advanced / Athlete (based on push-ups)
    var exerciseDays: Int = 4,
    var trainingPlace: String = "",// GYM / Home / Hybrid
    var mealsPerDay: Int = 3,
    var likedFoods: List<String> = emptyList(),
    var allergies: List<String> = emptyList(),
    var budget: String = ""        // budget friendly / standard / premium
) : Serializable
