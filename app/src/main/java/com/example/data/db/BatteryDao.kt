package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BatterySettings
import com.example.data.model.CalibrationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    // Calibration records
    @Query("SELECT * FROM calibration_records ORDER BY timestamp DESC")
    fun getAllCalibrationRecords(): Flow<List<CalibrationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibrationRecord(record: CalibrationRecord): Long

    @Query("DELETE FROM calibration_records WHERE id = :id")
    suspend fun deleteCalibrationRecord(id: Int)

    @Query("DELETE FROM calibration_records")
    suspend fun clearAllRecords()

    // Settings
    @Query("SELECT * FROM battery_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<BatterySettings?>

    @Query("SELECT * FROM battery_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): BatterySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: BatterySettings)

    @Update
    suspend fun updateSettings(settings: BatterySettings)
}
