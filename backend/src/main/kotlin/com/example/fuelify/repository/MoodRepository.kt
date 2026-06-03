package com.example.fuelify.repository

import com.example.fuelify.db.MoodEntriesTable
import com.example.fuelify.db.DatabaseFactory.dbQuery
import com.example.fuelify.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.util.*

object MoodRepository {

    private val log = LoggerFactory.getLogger(MoodRepository::class.java)

    private val DATE_FMT  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val MONTH_FMT = SimpleDateFormat("yyyy-MM",    Locale.US)
    private val LABEL_FMT = SimpleDateFormat("MMMM yyyy",  Locale.US)

    private fun todayKey(): String = DATE_FMT.format(Date())

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun addEntry(moodType: MoodType): MoodEntry {
        val today = todayKey()
        val now   = System.currentTimeMillis()
        log.debug("addEntry() called | moodType={} | dateKey={}", moodType, today)

        val entry = MoodEntry(
            id        = UUID.randomUUID().toString(),
            mood      = moodType.name,
            dateKey   = today,
            timestamp = now
        )

        try {
            dbQuery {
                val deleted = MoodEntriesTable.deleteWhere { MoodEntriesTable.dateKey eq today }
                log.debug("addEntry() delete existing | dateKey={} | rowsDeleted={}", today, deleted)

                MoodEntriesTable.insert {
                    it[id]        = entry.id
                    it[mood]      = entry.mood
                    it[dateKey]   = entry.dateKey
                    it[timestamp] = entry.timestamp
                }
                log.debug("addEntry() insert OK | id={} | mood={} | dateKey={}", entry.id, entry.mood, entry.dateKey)
            }
        } catch (e: Exception) {
            log.error("addEntry() DB error | moodType={} | dateKey={} | error={}", moodType, today, e.message, e)
            throw e
        }

        log.debug("addEntry() returning entry | id={}", entry.id)
        return entry
    }

    suspend fun deleteToday(): Boolean {
        val today = todayKey()
        log.debug("deleteToday() called | dateKey={}", today)
        return try {
            val result = dbQuery {
                MoodEntriesTable.deleteWhere { MoodEntriesTable.dateKey eq today } > 0
            }
            log.debug("deleteToday() result={} | dateKey={}", result, today)
            result
        } catch (e: Exception) {
            log.error("deleteToday() DB error | dateKey={} | error={}", today, e.message, e)
            throw e
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getAllEntries(): List<MoodEntry> {
        log.debug("getAllEntries() called")
        return try {
            val entries = dbQuery {
                MoodEntriesTable.selectAll()
                    .orderBy(MoodEntriesTable.timestamp, SortOrder.DESC)
                    .map { toModel(it) }
            }
            log.debug("getAllEntries() returning {} entries", entries.size)
            entries
        } catch (e: Exception) {
            log.error("getAllEntries() DB error | error={}", e.message, e)
            throw e
        }
    }

    suspend fun getTotalLogs(): Int {
        log.debug("getTotalLogs() called")
        return try {
            val count = dbQuery {
                MoodEntriesTable.selectAll().count().toInt()
            }
            log.debug("getTotalLogs() count={}", count)
            count
        } catch (e: Exception) {
            log.error("getTotalLogs() DB error | error={}", e.message, e)
            throw e
        }
    }

    suspend fun getTodayMood(): MoodType? {
        val today = todayKey()
        log.debug("getTodayMood() called | dateKey={}", today)
        return try {
            val mood = dbQuery {
                MoodEntriesTable.select(MoodEntriesTable.dateKey eq today)
                    .firstOrNull()
                    ?.let { MoodType.valueOf(it[MoodEntriesTable.mood]) }
            }
            log.debug("getTodayMood() result={} | dateKey={}", mood, today)
            mood
        } catch (e: Exception) {
            log.error("getTodayMood() DB error | dateKey={} | error={}", today, e.message, e)
            throw e
        }
    }

    suspend fun getMostCommonMood(): MoodType? {
        log.debug("getMostCommonMood() called")
        val entries = getAllEntries()
        if (entries.isEmpty()) {
            log.debug("getMostCommonMood() no entries, returning null")
            return null
        }
        val result = entries
            .groupingBy { MoodType.valueOf(it.mood) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        log.debug("getMostCommonMood() result={}", result)
        return result
    }

    suspend fun getDayStreak(): Int {
        log.debug("getDayStreak() called")
        val entries = getAllEntries()
        if (entries.isEmpty()) {
            log.debug("getDayStreak() no entries, streak=0")
            return 0
        }
        val keys = entries.map { it.dateKey }.toSortedSet().toList().sortedDescending()
        log.debug("getDayStreak() uniqueDateKeys={} | keys={}", keys.size, keys.take(5))
        var streak   = 0
        val cal      = Calendar.getInstance()
        var expected = DATE_FMT.format(cal.time)
        for (key in keys) {
            if (key == expected) {
                streak++
                cal.add(Calendar.DATE, -1)
                expected = DATE_FMT.format(cal.time)
            } else {
                log.debug("getDayStreak() streak broken | expected={} | got={}", expected, key)
                break
            }
        }
        log.debug("getDayStreak() streak={}", streak)
        return streak
    }

    suspend fun getEntriesForMonth(monthKey: String): Map<String, String> {
        log.debug("getEntriesForMonth() called | monthKey={}", monthKey)
        return try {
            val result = dbQuery {
                MoodEntriesTable.select(MoodEntriesTable.dateKey like "$monthKey%")
                    .associate { it[MoodEntriesTable.dateKey].trim() to it[MoodEntriesTable.mood] }
            }
            log.debug("getEntriesForMonth() returning {} entries | monthKey={}", result.size, monthKey)
            result
        } catch (e: Exception) {
            log.error("getEntriesForMonth() DB error | monthKey={} | error={}", monthKey, e.message, e)
            throw e
        }
    }

    suspend fun getMoodCounts(): Map<String, Int> {
        log.debug("getMoodCounts() called")
        val entries = getAllEntries()
        val counts = MoodType.values().associate { mood ->
            mood.name to entries.count { it.mood == mood.name }
        }
        log.debug("getMoodCounts() result={}", counts)
        return counts
    }

    fun currentMonthKey():   String = MONTH_FMT.format(Date())
    fun currentMonthLabel(): String = LABEL_FMT.format(Date())

    private fun toModel(row: ResultRow) = MoodEntry(
        id        = row[MoodEntriesTable.id],
        mood      = row[MoodEntriesTable.mood],
        dateKey   = row[MoodEntriesTable.dateKey].trim(),
        timestamp = row[MoodEntriesTable.timestamp]
    )
}
