package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.battery.BatteryInfo
import com.example.battery.NotificationHelper
import com.example.battery.PixelBatteryReader
import com.example.data.db.BatteryDatabase
import com.example.data.model.BatterySettings
import com.example.data.model.CalibrationRecord
import com.example.data.repository.BatteryRepository
import com.example.service.BatteryCalibrationService
import com.example.service.CalibrationTimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatteryUiState(
    val batteryInfo: BatteryInfo,
    val settings: BatterySettings = BatterySettings(),
    val timerState: CalibrationTimerState = CalibrationTimerState(),
    val calibrationRecords: List<CalibrationRecord> = emptyList(),
    val effectiveCycleCount: Int = 0,
    val cyclesSinceLastCalibration: Int = 0,
    val cyclesUntilNextCalibration: Int = 10,
    val isCalibrationDue: Boolean = false,
    val calibrationProgress: Float = 0f, // 0.0 to 1.0 towards next 10th cycle
    val simulatedCycleIncrement: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BatteryRepository
    private val _batteryInfo = MutableStateFlow(PixelBatteryReader.getBatteryInfo(application))

    private val batteryUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null) {
                updateBatteryInfo(context)
            }
        }
    }

    val uiState: StateFlow<BatteryUiState>

    init {
        val db = BatteryDatabase.getDatabase(application)
        repository = BatteryRepository(db.batteryDao())
        NotificationHelper.createChannels(application)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        application.registerReceiver(batteryUpdateReceiver, filter)

        // Combine repository settings, timerState, and battery info into uiState
        uiState = combine(
            _batteryInfo,
            repository.settingsFlow,
            BatteryCalibrationService.timerState,
            repository.allCalibrationRecords
        ) { info, settings, timer, records ->
            val effectiveCycles = info.hardwareCycleCount ?: settings.manualCycleOffset
            val interval = if (settings.calibrationInterval > 0) settings.calibrationInterval else 10
            val sinceLast = maxOf(0, effectiveCycles - settings.lastCalibratedCycle)
            val isDue = sinceLast >= interval
            val remaining = maxOf(0, interval - (sinceLast % interval))
            val progress = (sinceLast % interval).toFloat() / interval.toFloat()

            BatteryUiState(
                batteryInfo = info,
                settings = settings,
                timerState = timer,
                calibrationRecords = records,
                effectiveCycleCount = effectiveCycles,
                cyclesSinceLastCalibration = sinceLast,
                cyclesUntilNextCalibration = if (isDue) 0 else remaining,
                isCalibrationDue = isDue,
                calibrationProgress = if (isDue) 1.0f else progress.coerceIn(0f, 1f)
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BatteryUiState(
                batteryInfo = _batteryInfo.value
            )
        )

        // Periodic battery refresh
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(3000L)
                updateBatteryInfo(application)
            }
        }
    }

    private fun updateBatteryInfo(context: Context) {
        val info = PixelBatteryReader.getBatteryInfo(context)
        _batteryInfo.value = info
    }

    fun start30MinCalibration(durationSec: Int = 30 * 60) {
        val currentCycle = uiState.value.effectiveCycleCount
        BatteryCalibrationService.start(getApplication(), currentCycle, durationSec)
    }

    fun stopCalibration() {
        BatteryCalibrationService.stop(getApplication())
    }

    fun setBaselineCycleCount(newCount: Int) {
        viewModelScope.launch {
            repository.updateCycleBaseline(newCount)
        }
    }

    fun triggerTestNotification() {
        val currentCycle = uiState.value.effectiveCycleCount
        NotificationHelper.showCalibrationDueAlert(getApplication(), currentCycle)
    }

    fun incrementCycles(amount: Int = 1) {
        viewModelScope.launch {
            val current = repository.getSettings()
            val newOffset = current.manualCycleOffset + amount
            repository.updateCycleBaseline(newOffset)

            // Check if threshold crossed
            val interval = current.calibrationInterval
            val sinceLast = newOffset - current.lastCalibratedCycle
            if (sinceLast >= interval && current.notificationEnabled) {
                NotificationHelper.showCalibrationDueAlert(getApplication(), newOffset)
            }
        }
    }

    fun resetCalibrationCycleToCurrent() {
        viewModelScope.launch {
            val currentCycle = uiState.value.effectiveCycleCount
            repository.updateLastCalibratedCycle(currentCycle)
        }
    }

    fun saveSettings(newSettings: BatterySettings) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unregisterReceiver(batteryUpdateReceiver)
        } catch (_: Throwable) {
        }
        super.onCleared()
    }
}
