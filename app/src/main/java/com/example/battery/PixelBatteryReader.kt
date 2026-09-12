package com.example.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.io.File

data class BatteryInfo(
    val level: Int, // 0 - 100
    val isPlugged: Boolean,
    val pluggedType: String, // "AC", "USB", "Wireless", "Unplugged"
    val isCharging: Boolean,
    val isFull: Boolean,
    val health: String,
    val voltageMv: Int,
    val temperatureC: Float,
    val currentNowMa: Int?,
    val hardwareCycleCount: Int?, // from Android 14+ API or sysfs
    val isHardwareCycleSupported: Boolean,
    val isPixelDevice: Boolean,
    val deviceModel: String
)

object PixelBatteryReader {

    private const val EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT"
    // Constant value for BatteryManager.BATTERY_PROPERTY_CYCLE_COUNT (API 34+)
    private const val BATTERY_PROPERTY_CYCLE_COUNT = 7

    fun getBatteryInfo(context: Context): BatteryInfo {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, iFilter)

        val level = batteryStatus?.let { intent ->
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) {
                ((rawLevel.toFloat() / scale.toFloat()) * 100f).toInt()
            } else -1
        } ?: -1

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL || level >= 100

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val isPlugged = chargePlug > 0
        val pluggedType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isPlugged) "Connected" else "Unplugged"
        }

        val healthInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempRaw = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureC = if (tempRaw > 0) tempRaw / 10f else 25.0f

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNowMa = batteryManager?.let { bm ->
            val microAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (microAmps != Int.MIN_VALUE && microAmps != 0) {
                microAmps / 1000
            } else null
        }

        // Cycle count detection
        val hardwareCycle = detectHardwareCycleCount(batteryStatus, batteryManager)
        val isPixel = Build.MANUFACTURER.equals("Google", ignoreCase = true)

        return BatteryInfo(
            level = if (level >= 0) level else 50,
            isPlugged = isPlugged,
            pluggedType = pluggedType,
            isCharging = isCharging,
            isFull = isFull,
            health = health,
            voltageMv = voltageMv,
            temperatureC = temperatureC,
            currentNowMa = currentNowMa,
            hardwareCycleCount = hardwareCycle,
            isHardwareCycleSupported = hardwareCycle != null && hardwareCycle >= 0,
            isPixelDevice = isPixel,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    private fun detectHardwareCycleCount(
        batteryStatus: Intent?,
        batteryManager: BatteryManager?
    ): Int? {
        // 1. Android 14+ (API 34+) BatteryManager.EXTRA_CYCLE_COUNT
        if (batteryStatus != null) {
            if (batteryStatus.hasExtra(EXTRA_CYCLE_COUNT)) {
                val cycle = batteryStatus.getIntExtra(EXTRA_CYCLE_COUNT, -1)
                if (cycle >= 0) return cycle
            }
        }

        // 2. Try BatteryManager.BATTERY_PROPERTY_CYCLE_COUNT (API 34 constant value = 7)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && batteryManager != null) {
            try {
                val cycleProp = batteryManager.getIntProperty(BATTERY_PROPERTY_CYCLE_COUNT)
                if (cycleProp > 0 && cycleProp != Int.MIN_VALUE) {
                    return cycleProp
                }
            } catch (_: Throwable) {
            }
        }

        // 3. Try reading sysfs nodes (present on many Pixel / Android kernels)
        val sysfsCandidates = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/battery_cycle",
            "/sys/class/power_supply/maxfg/cycle_count",
            "/sys/class/power_supply/battery/cycle_counter"
        )
        for (path in sysfsCandidates) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val parsed = content.toIntOrNull()
                    if (parsed != null && parsed >= 0) {
                        return parsed
                    }
                }
            } catch (_: Throwable) {
            }
        }

        return null
    }
}
