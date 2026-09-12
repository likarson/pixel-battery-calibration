package com.example.data.repository

import com.example.data.db.BatteryDao
import com.example.data.model.BatterySettings
import com.example.data.model.CalibrationRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BatteryRepository(private val batteryDao: BatteryDao) {

    val allCalibrationRecords: Flow<List<CalibrationRecord>> =
        batteryDao.getAllCalibrationRecords()

    val settingsFlow: Flow<BatterySettings> = batteryDao.getSettingsFlow().map {
        it ?: BatterySettings()
    }

    suspend fun getSettings(): BatterySettings {
        val existing = batteryDao.getSettingsDirect()
        if (existing != null) {
            return existing
        }
        val defaultSettings = BatterySettings()
        batteryDao.insertSettings(defaultSettings)
        return defaultSettings
    }

    suspend fun saveSettings(settings: BatterySettings) {
        batteryDao.insertSettings(settings)
    }

    suspend fun recordCalibration(record: CalibrationRecord) {
        batteryDao.insertCalibrationRecord(record)
        // Also update last calibrated cycle in settings
        val current = getSettings()
        batteryDao.insertSettings(
            current.copy(
                lastCalibratedCycle = record.cycleCountAtCalibration
            )
        )
    }

    suspend fun updateCycleBaseline(newCount: Int) {
        val current = getSettings()
        batteryDao.insertSettings(
            current.copy(
                manualCycleOffset = newCount,
                cumulativeDischargePercent = 0f
            )
        )
    }

    suspend fun updateLastCalibratedCycle(cycle: Int) {
        val current = getSettings()
        batteryDao.insertSettings(
            current.copy(lastCalibratedCycle = cycle)
        )
    }

    suspend fun accumulateDischarge(deltaDischarge: Float, currentLevel: Int) {
        if (deltaDischarge <= 0f) return
        val current = getSettings()
        var newCumulative = current.cumulativeDischargePercent + deltaDischarge
        var addedCycles = 0
        while (newCumulative >= 100f) {
            newCumulative -= 100f
            addedCycles += 1
        }
        val newManualOffset = current.manualCycleOffset + addedCycles
        batteryDao.insertSettings(
            current.copy(
                manualCycleOffset = newManualOffset,
                cumulativeDischargePercent = newCumulative,
                lastRecordedLevel = currentLevel
            )
        )
    }

    suspend fun updateLastRecordedLevel(level: Int) {
        val current = getSettings()
        batteryDao.insertSettings(current.copy(lastRecordedLevel = level))
    }

    suspend fun clearHistory() {
        batteryDao.clearAllRecords()
    }
}
