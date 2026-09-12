package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.battery.NotificationHelper
import com.example.battery.PixelBatteryReader
import com.example.data.db.BatteryDatabase
import com.example.data.repository.BatteryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BatteryBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        NotificationHelper.createChannels(context)

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val db = BatteryDatabase.getDatabase(context)
                val repository = BatteryRepository(db.batteryDao())
                val settings = repository.getSettings()

                val info = PixelBatteryReader.getBatteryInfo(context)

                // Check hardware or manual cycle count
                val effectiveCycle = info.hardwareCycleCount ?: settings.manualCycleOffset

                // Accumulate discharge if level dropped
                if (settings.lastRecordedLevel > 0 && info.level < settings.lastRecordedLevel) {
                    val delta = (settings.lastRecordedLevel - info.level).toFloat()
                    repository.accumulateDischarge(delta, info.level)
                } else {
                    repository.updateLastRecordedLevel(info.level)
                }

                // Check if calibration due (e.g. every 10 cycles)
                val interval = if (settings.calibrationInterval > 0) settings.calibrationInterval else 10
                val cyclesSinceLast = effectiveCycle - settings.lastCalibratedCycle

                if (cyclesSinceLast >= interval && settings.notificationEnabled) {
                    NotificationHelper.showCalibrationDueAlert(context, effectiveCycle)
                }
            } catch (_: Throwable) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
