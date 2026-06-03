package com.example.fuelify.statistics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.fuelify.data.api.RetrofitClient

import java.util.Calendar

object BloodPressureTrackerRepository {

private val api = RetrofitClient.healthApi


    // ── Blood Pressure ────────────────────────────────────────────────────────

    suspend fun getAllBpReadings(): List<BloodPressureReadingData> = withContext(Dispatchers.IO) {
        try { api.getBpReadings().data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getLatestBpReading(): BloodPressureReadingData? = withContext(Dispatchers.IO) {
        try { api.getLatestBpReading().data } catch (e: Exception) { null }
    }

    suspend fun addBpReading(
        systolic: Int, diastolic: Int, pulse: Int, notes: String = ""
    ): BloodPressureReadingData? = withContext(Dispatchers.IO) {
        try { api.addBpReading(AddBpReadingRequest(systolic, diastolic, pulse, notes)).data }
        catch (e: Exception) { null }
    }

    suspend fun deleteBpReading(id: Long) = withContext(Dispatchers.IO) {
        try { api.deleteBpReading(id) } catch (e: Exception) { }
    }

    suspend fun getBpStats(
        year: Int  = Calendar.getInstance().get(Calendar.YEAR),
        month: Int = Calendar.getInstance().get(Calendar.MONTH)
    ): BpStatsData? = withContext(Dispatchers.IO) {
        try { api.getBpStats(year, month).data } catch (e: Exception) { null }
    }

    // ── Blood Sugar ───────────────────────────────────────────────────────────

    suspend fun getAllBsReadings(): List<BloodSugarReadingData> = withContext(Dispatchers.IO) {
        try { api.getBsReadings().data ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    suspend fun getLatestBsReading(): BloodSugarReadingData? = withContext(Dispatchers.IO) {
        try { api.getLatestBsReading().data } catch (e: Exception) { null }
    }

    suspend fun addBsReading(
        glucose: Int, mealType: String, notes: String = ""
    ): BloodSugarReadingData? = withContext(Dispatchers.IO) {
        try { api.addBsReading(AddBsReadingRequest(glucose, mealType, notes)).data }
        catch (e: Exception) { null }
    }

    suspend fun deleteBsReading(id: Long) = withContext(Dispatchers.IO) {
        try { api.deleteBsReading(id) } catch (e: Exception) { }
    }

    suspend fun getBsStats(
        year: Int  = Calendar.getInstance().get(Calendar.YEAR),
        month: Int = Calendar.getInstance().get(Calendar.MONTH)
    ): BsStatsData? = withContext(Dispatchers.IO) {
        try { api.getBsStats(year, month).data } catch (e: Exception) { null }
    }
}
