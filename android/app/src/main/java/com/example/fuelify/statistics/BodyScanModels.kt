package com.example.fuelify.statistics

// ── Core body-scan record ─────────────────────────────────────────────────────
// هذا الملف يحتوي على كل الـ data classes المشتركة بين:
// BodyScanRepository, BodyRecognitionActivity, BodyStatisticsActivity

data class BodyScanRecord(
    val timestamp         : Long   = System.currentTimeMillis(),
    val bodyFatPercent    : Double = 0.0,
    val muscleMassPercent : Double = 0.0,
    val waterPercent      : Double = 0.0,
    val bmi               : Double = 0.0,
    val bodyType          : String = "",
    val photoUri          : String = ""
)

data class MonthlyBodyFatEntry(
    val monthLabel     : String,
    val bodyFatAvg     : Double,
    val changeFromPrev : Double
)
