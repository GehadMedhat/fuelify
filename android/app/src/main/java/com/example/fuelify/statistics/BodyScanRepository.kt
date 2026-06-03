package com.example.fuelify.statistics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.fuelify.data.api.RetrofitClient


object BodyScanRepository {

private val api = RetrofitClient.healthApi


    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getAllRecords(): List<BodyScanRecord> = withContext(Dispatchers.IO) {
        try {
            api.getBodyScanRecords().data?.map { it.toBodyScanRecord() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getLatestRecord(): BodyScanRecord? = withContext(Dispatchers.IO) {
        try { api.getLatestBodyScanRecord().data?.toBodyScanRecord() } catch (e: Exception) { null }
    }

    suspend fun getTodayRecords(): List<BodyScanRecord> = withContext(Dispatchers.IO) {
        try {
            api.getTodayBodyScanRecords().data?.records?.map { it.toBodyScanRecord() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getTodayLatestRecord(): BodyScanRecord? = withContext(Dispatchers.IO) {
        try { api.getTodayBodyScanRecords().data?.latestRecord?.toBodyScanRecord() }
        catch (e: Exception) { null }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun saveRecord(record: BodyScanRecord): BodyScanRecord? = withContext(Dispatchers.IO) {
        try {
            api.saveBodyScanRecord(
                AddBodyScanRequest(
                    bodyFatPercent    = record.bodyFatPercent,
                    muscleMassPercent = record.muscleMassPercent,
                    waterPercent      = record.waterPercent,
                    bmi               = record.bmi,
                    bodyType          = record.bodyType,
                    photoUri          = record.photoUri
                )
            ).data?.toBodyScanRecord()
        } catch (e: Exception) { null }
    }

    suspend fun deleteRecord(timestamp: Long) = withContext(Dispatchers.IO) {
        try { api.deleteBodyScanRecord(timestamp) } catch (e: Exception) { }
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    suspend fun getBodyFatChange(): Double = withContext(Dispatchers.IO) {
        try { api.getBodyScanStats().data?.bodyFatChange ?: 0.0 } catch (e: Exception) { 0.0 }
    }

    suspend fun getMonthlyBodyFatHistory(): List<MonthlyBodyFatEntry> = withContext(Dispatchers.IO) {
        try {
            api.getBodyScanStats().data?.monthlyHistory?.map {
                MonthlyBodyFatEntry(it.monthLabel, it.bodyFatAvg, it.changeFromPrev)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun BodyScanRecordData.toBodyScanRecord() =
        BodyScanRecord(timestamp, bodyFatPercent, muscleMassPercent, waterPercent, bmi, bodyType, photoUri)
}
