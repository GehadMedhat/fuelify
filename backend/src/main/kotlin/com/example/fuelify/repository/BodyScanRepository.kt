package com.example.fuelify.repository

import com.example.fuelify.db.BodyScanRecordsTable

import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*   // or wherever you put his models

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.text.SimpleDateFormat
import java.util.*

object BodyScanRepository {

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getAllRecords(): List<BodyScanRecord> = dbQuery {
        BodyScanRecordsTable.selectAll()
            .orderBy(BodyScanRecordsTable.timestamp, SortOrder.DESC)
            .map { toModel(it) }
    }

    suspend fun getLatestRecord(): BodyScanRecord? = dbQuery {
        BodyScanRecordsTable.selectAll()
            .orderBy(BodyScanRecordsTable.timestamp, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { toModel(it) }
    }

    suspend fun getTodayRecords(): List<BodyScanRecord> {
        val sdf      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        return getAllRecords().filter { sdf.format(Date(it.timestamp)) == todayStr }
    }

    suspend fun getTodayLatestRecord(): BodyScanRecord? = getTodayRecords().firstOrNull()

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun saveRecord(req: AddBodyScanRequest): BodyScanRecord {
        require(req.bodyFatPercent    in 0.0..100.0) { "bodyFatPercent must be 0–100" }
        require(req.muscleMassPercent in 0.0..100.0) { "muscleMassPercent must be 0–100" }
        require(req.waterPercent      in 0.0..100.0) { "waterPercent must be 0–100" }
        require(req.bmi               > 0.0)          { "bmi must be positive" }
        val now = System.currentTimeMillis()
        val record = BodyScanRecord(
            timestamp         = now,
            bodyFatPercent    = req.bodyFatPercent,
            muscleMassPercent = req.muscleMassPercent,
            waterPercent      = req.waterPercent,
            bmi               = req.bmi,
            bodyType          = req.bodyType,
            photoUri          = req.photoUri
        )
        dbQuery {
            BodyScanRecordsTable.insert {
                it[timestamp]         = record.timestamp
                it[bodyFatPercent]    = record.bodyFatPercent
                it[muscleMassPercent] = record.muscleMassPercent
                it[waterPercent]      = record.waterPercent
                it[bmi]               = record.bmi
                it[bodyType]          = record.bodyType
                it[photoUri]          = record.photoUri
            }
        }
        return record
    }

    suspend fun deleteRecord(timestamp: Long): Boolean = dbQuery {
        BodyScanRecordsTable.deleteWhere { BodyScanRecordsTable.timestamp eq timestamp } > 0
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    suspend fun getBodyFatChange(): Double {
        val all = getAllRecords()
        return if (all.size >= 2) all[0].bodyFatPercent - all[1].bodyFatPercent else 0.0
    }

    suspend fun getMonthlyBodyFatHistory(): List<MonthlyBodyFatEntry> {
        val all = getAllRecords()
        if (all.isEmpty()) return emptyList()
        val sdf     = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val grouped = all
            .groupBy { sdf.format(Date(it.timestamp)) }
            .map { (label, group) -> label to group.map { it.bodyFatPercent }.average() }
            .sortedBy { (label, _) -> sdf.parse(label)?.time ?: 0L }
        return grouped.mapIndexed { index, (label, avg) ->
            val delta = if (index == 0) 0.0 else avg - grouped[index - 1].second
            MonthlyBodyFatEntry(
                monthLabel     = label,
                bodyFatAvg     = avg,
                changeFromPrev = delta
            )
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun toModel(row: ResultRow) = BodyScanRecord(
        timestamp         = row[BodyScanRecordsTable.timestamp],
        bodyFatPercent    = row[BodyScanRecordsTable.bodyFatPercent],
        muscleMassPercent = row[BodyScanRecordsTable.muscleMassPercent],
        waterPercent      = row[BodyScanRecordsTable.waterPercent],
        bmi               = row[BodyScanRecordsTable.bmi],
        bodyType          = row[BodyScanRecordsTable.bodyType],
        photoUri          = row[BodyScanRecordsTable.photoUri]
    )
}
