package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_records")
data class CalibrationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val cycleCountAtCalibration: Int,
    val durationSeconds: Int,
    val startVoltageMv: Int,
    val endVoltageMv: Int,
    val temperatureC: Float,
    val completedSuccessfully: Boolean,
    val notes: String = ""
)
