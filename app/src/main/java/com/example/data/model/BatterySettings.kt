package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_settings")
data class BatterySettings(
    @PrimaryKey
    val id: Int = 1,
    val manualCycleOffset: Int = 0,
    val lastCalibratedCycle: Int = 0,
    val calibrationInterval: Int = 10,
    val cumulativeDischargePercent: Float = 0f,
    val lastRecordedLevel: Int = -1,
    val notificationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoStartSaturationTimer: Boolean = true
)
