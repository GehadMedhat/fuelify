package com.example.fuelify.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuelify.data.api.RetrofitClient
import com.example.fuelify.data.api.models.DashboardData
import com.example.fuelify.data.api.models.LogMealRequest
import com.example.fuelify.data.api.models.LogWaterRequest
import kotlinx.coroutines.launch

sealed class DashboardState {
    object Loading : DashboardState()
    data class Success(val data: DashboardData) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

class HomeViewModel : ViewModel() {

    private val _state = MutableLiveData<DashboardState>()
    val state: LiveData<DashboardState> = _state

    fun loadDashboard(userId: Int) {
        _state.value = DashboardState.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.getDashboard(userId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _state.value = DashboardState.Success(response.body()!!.data!!)
                } else {
                    _state.value = DashboardState.Error(
                        response.body()?.message ?: "Failed to load dashboard"
                    )
                }
            } catch (e: Exception) {
                _state.value = DashboardState.Error(e.message ?: "Network error")
            }
        }
    }

    fun logWater(userId: Int, glasses: Int) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.logWater(userId, LogWaterRequest(glasses))
                loadDashboard(userId) // refresh
            } catch (e: Exception) { /* silent fail */ }
        }
    }

    fun logMeal(userId: Int, mealId: Int) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.logMeal(userId, LogMealRequest(mealId))
                loadDashboard(userId) // refresh
            } catch (e: Exception) { /* silent fail */ }
        }
    }
}
