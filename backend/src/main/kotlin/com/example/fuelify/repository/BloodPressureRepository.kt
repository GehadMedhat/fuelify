package com.example.fuelify.repository

import com.example.fuelify.db.BpReadingsTable
import com.example.fuelify.db.BsReadingsTable
import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.util.Calendar

object BloodPressureRepository {

    // ── Blood Pressure ────────────────────────────────────────────────────────

    suspend fun addBloodPressureReading(req: AddBpReadingRequest): BloodPressureReading {
        val now      = System.currentTimeMillis()
        val category = computeBpCategory(req.systolic, req.diastolic)
        val reading  = BloodPressureReading(
            id            = now,
            systolic      = req.systolic,
            diastolic     = req.diastolic,
            pulse         = req.pulse,
            notes         = req.notes,
            timestamp     = now,
            formattedTime = formatTimestamp(now),
            category      = category,
            categoryLabel = BpCategory.valueOf(category).label
        )
        dbQuery {
            BpReadingsTable.insert {
                it[id]            = reading.id
                it[systolic]      = reading.systolic
                it[diastolic]     = reading.diastolic
                it[pulse]         = reading.pulse
                it[notes]         = reading.notes
                it[timestamp]     = reading.timestamp
                it[formattedTime] = reading.formattedTime
                it[BpReadingsTable.category] = reading.category
                it[categoryLabel] = reading.categoryLabel
            }
        }
        return reading
    }

    suspend fun getAllBpReadings(): List<BloodPressureReading> = dbQuery {
        BpReadingsTable.selectAll()
            .orderBy(BpReadingsTable.timestamp, SortOrder.DESC)
            .map { toBpModel(it) }
    }

    suspend fun getLatestBpReading(): BloodPressureReading? = dbQuery {
        BpReadingsTable.selectAll()
            .orderBy(BpReadingsTable.timestamp, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { toBpModel(it) }
    }

    suspend fun getBpReadingsForMonth(year: Int, month: Int): List<BloodPressureReading> {
        val (start, end) = monthBounds(year, month)
        return dbQuery {
            BpReadingsTable.select(
                (BpReadingsTable.timestamp greaterEq start) and (BpReadingsTable.timestamp less end)
            ).orderBy(BpReadingsTable.timestamp, SortOrder.DESC)
             .map { toBpModel(it) }
        }
    }

    suspend fun getLatestBpReadingForMonth(year: Int, month: Int): BloodPressureReading? =
        getBpReadingsForMonth(year, month).firstOrNull()

    suspend fun getBpWeeklyAverageForMonth(year: Int, month: Int): Pair<Int, Int>? {
        val now = Calendar.getInstance()
        val isCurrentMonth = (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH))
        val readings = if (isCurrentMonth) {
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            dbQuery {
                BpReadingsTable.select(BpReadingsTable.timestamp greaterEq sevenDaysAgo)
                    .map { toBpModel(it) }
            }
        } else getBpReadingsForMonth(year, month)
        if (readings.isEmpty()) return null
        return Pair(
            readings.map { it.systolic }.average().toInt(),
            readings.map { it.diastolic }.average().toInt()
        )
    }

    suspend fun deleteBpReading(id: Long): Boolean = dbQuery {
        BpReadingsTable.deleteWhere { BpReadingsTable.id eq id } > 0
    }

    // ── Blood Sugar ───────────────────────────────────────────────────────────

    suspend fun addBloodSugarReading(req: AddBsReadingRequest): BloodSugarReading {
        val now      = System.currentTimeMillis()
        val category = computeBsCategory(req.glucose, req.mealType)
        val reading  = BloodSugarReading(
            id            = now,
            glucose       = req.glucose,
            mealType      = req.mealType,
            notes         = req.notes,
            timestamp     = now,
            formattedTime = formatTimestamp(now),
            category      = category,
            categoryLabel = BsCategory.valueOf(category).label
        )
        dbQuery {
            BsReadingsTable.insert {
                it[id]            = reading.id
                it[glucose]       = reading.glucose
                it[mealType]      = reading.mealType
                it[notes]         = reading.notes
                it[timestamp]     = reading.timestamp
                it[formattedTime] = reading.formattedTime
                it[BsReadingsTable.category] = reading.category
                it[categoryLabel] = reading.categoryLabel
            }
        }
        return reading
    }

    suspend fun getAllBsReadings(): List<BloodSugarReading> = dbQuery {
        BsReadingsTable.selectAll()
            .orderBy(BsReadingsTable.timestamp, SortOrder.DESC)
            .map { toBsModel(it) }
    }

    suspend fun getLatestBsReading(): BloodSugarReading? = dbQuery {
        BsReadingsTable.selectAll()
            .orderBy(BsReadingsTable.timestamp, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { toBsModel(it) }
    }

    suspend fun getBsReadingsForMonth(year: Int, month: Int): List<BloodSugarReading> {
        val (start, end) = monthBounds(year, month)
        return dbQuery {
            BsReadingsTable.select(
                (BsReadingsTable.timestamp greaterEq start) and (BsReadingsTable.timestamp less end)
            ).orderBy(BsReadingsTable.timestamp, SortOrder.DESC)
             .map { toBsModel(it) }
        }
    }

    suspend fun getLatestBsReadingForMonth(year: Int, month: Int): BloodSugarReading? =
        getBsReadingsForMonth(year, month).firstOrNull()

    suspend fun getBsWeeklyAverageForMonth(year: Int, month: Int): Int? {
        val now = Calendar.getInstance()
        val isCurrentMonth = (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH))
        val readings = if (isCurrentMonth) {
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            dbQuery {
                BsReadingsTable.select(BsReadingsTable.timestamp greaterEq sevenDaysAgo)
                    .map { toBsModel(it) }
            }
        } else getBsReadingsForMonth(year, month)
        if (readings.isEmpty()) return null
        return readings.map { it.glucose }.average().toInt()
    }

    suspend fun deleteBsReading(id: Long): Boolean = dbQuery {
        BsReadingsTable.deleteWhere { BsReadingsTable.id eq id } > 0
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun monthBounds(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }.timeInMillis
        return Pair(start, end)
    }

    private fun toBpModel(row: ResultRow) = BloodPressureReading(
        id            = row[BpReadingsTable.id],
        systolic      = row[BpReadingsTable.systolic],
        diastolic     = row[BpReadingsTable.diastolic],
        pulse         = row[BpReadingsTable.pulse],
        notes         = row[BpReadingsTable.notes],
        timestamp     = row[BpReadingsTable.timestamp],
        formattedTime = row[BpReadingsTable.formattedTime],
        category      = row[BpReadingsTable.category],
        categoryLabel = row[BpReadingsTable.categoryLabel]
    )

    private fun toBsModel(row: ResultRow) = BloodSugarReading(
        id            = row[BsReadingsTable.id],
        glucose       = row[BsReadingsTable.glucose],
        mealType      = row[BsReadingsTable.mealType],
        notes         = row[BsReadingsTable.notes],
        timestamp     = row[BsReadingsTable.timestamp],
        formattedTime = row[BsReadingsTable.formattedTime],
        category      = row[BsReadingsTable.category],
        categoryLabel = row[BsReadingsTable.categoryLabel]
    )
}
