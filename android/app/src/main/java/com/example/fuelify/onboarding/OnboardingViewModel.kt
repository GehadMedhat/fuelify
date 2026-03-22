package com.example.fuelify.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.RegisterUserRequest
import com.example.fuelify.data.models.UserOnboardingData
import kotlinx.coroutines.launch

sealed class OnboardingState {
    object Idle : OnboardingState()
    object Loading : OnboardingState()
    data class Success(val userId: Int) : OnboardingState()
    data class Error(val message: String) : OnboardingState()
}

class OnboardingViewModel : ViewModel() {

    // The single model accumulating all user answers
    val userData = UserOnboardingData()

    private val _submitState = MutableLiveData<OnboardingState>(OnboardingState.Idle)
    val submitState: LiveData<OnboardingState> = _submitState

    // ── Setters (called by each fragment before moving next) ──────────────────

    fun setName(v: String)           { userData.name = v }
    fun setGender(v: String)         { userData.gender = v }
    fun setAge(v: Int)               { userData.age = v }
    fun setHeight(v: Int)            { userData.heightCm = v }
    fun setWeight(v: Int)            { userData.weightKg = v }
    fun setGoal(v: String)           { userData.goal = v }
    fun setActivityLevel(v: String)  { userData.activityLevel = v }
    fun setMotivation(v: String)     { userData.motivation = v }
    fun setFitnessLevel(v: String)   { userData.fitnessLevel = v }
    fun setExerciseDays(v: Int)      { userData.exerciseDays = v }
    fun setTrainingPlace(v: String)  { userData.trainingPlace = v }
    fun setMealsPerDay(v: Int)       { userData.mealsPerDay = v }
    fun setLikedFoods(v: List<String>) { userData.likedFoods = v }
    fun setAllergies(v: List<String>){ userData.allergies = v }
    fun setBudget(v: String)         { userData.budget = v }

    // ── Submit to Ktor backend ────────────────────────────────────────────────

    fun submitOnboarding() {
        _submitState.value = OnboardingState.Loading

        viewModelScope.launch {
            try {
                val request = RegisterUserRequest(
                    name          = userData.name,
                    gender        = userData.gender,
                    age           = userData.age,
                    heightCm      = userData.heightCm,
                    weightKg      = userData.weightKg,
                    goal          = userData.goal,
                    activityLevel = userData.activityLevel,
                    motivation    = userData.motivation,
                    fitnessLevel  = userData.fitnessLevel,
                    exerciseDays  = userData.exerciseDays,
                    trainingPlace = userData.trainingPlace,
                    mealsPerDay   = userData.mealsPerDay,
                    likedFoods    = userData.likedFoods,
                    allergies     = userData.allergies,
                    budget        = userData.budget
                )

                val response = RetrofitClient.api.registerUser(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val userId = response.body()!!.data!!.userId
                    _submitState.value = OnboardingState.Success(userId)
                } else {
                    val msg = response.body()?.message ?: "Server error ${response.code()}"
                    _submitState.value = OnboardingState.Error(msg)
                }

            } catch (e: Exception) {
                _submitState.value = OnboardingState.Error(e.message ?: "Network error")
            }
        }
    }
}
