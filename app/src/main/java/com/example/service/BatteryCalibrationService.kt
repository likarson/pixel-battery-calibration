package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.battery.NotificationHelper
import com.example.battery.PixelBatteryReader
import com.example.data.db.BatteryDatabase
import com.example.data.model.CalibrationRecord
import com.example.data.repository.BatteryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CalibrationTimerState(
    val isRunning: Boolean = false,
    val secondsRemaining: Int = 30 * 60, // 30 minutes
    val totalSeconds: Int = 30 * 60,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val startVoltageMv: Int = 0,
    val currentVoltageMv: Int = 0,
    val currentTempC: Float = 0f,
    val currentLevel: Int = 100
)

class BatteryCalibrationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private lateinit var repository: BatteryRepository

    private var initialCycleCount: Int = 0
    private var startVoltageMv: Int = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            val action = intent.action
            if (action == Intent.ACTION_BATTERY_CHANGED ||
                action == Intent.ACTION_POWER_CONNECTED ||
                action == Intent.ACTION_POWER_DISCONNECTED
            ) {
                val info = PixelBatteryReader.getBatteryInfo(context)
                val isPlugged = info.isPlugged
                _timerState.value = _timerState.value.copy(
                    isPaused = !isPlugged && _timerState.value.isRunning,
                    currentVoltageMv = info.voltageMv,
                    currentTempC = info.temperatureC,
                    currentLevel = info.level
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val db = BatteryDatabase.getDatabase(applicationContext)
        repository = BatteryRepository(db.batteryDao())
        NotificationHelper.createChannels(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                val cycle = intent?.getIntExtra(EXTRA_CYCLE, 0) ?: 0
                val durationSec = intent?.getIntExtra(EXTRA_DURATION_SEC, 30 * 60) ?: (30 * 60)
                startCalibration(cycle, durationSec)
            }
            ACTION_STOP -> {
                stopCalibration(false)
            }
            ACTION_PAUSE -> {
                pauseCalibration()
            }
            ACTION_RESUME -> {
                resumeCalibration()
            }
        }
        return START_STICKY
    }

    private fun startCalibration(cycle: Int, durationSec: Int) {
        initialCycleCount = cycle
        val batteryInfo = PixelBatteryReader.getBatteryInfo(this)
        startVoltageMv = batteryInfo.voltageMv

        _timerState.value = CalibrationTimerState(
            isRunning = true,
            secondsRemaining = durationSec,
            totalSeconds = durationSec,
            isPaused = !batteryInfo.isPlugged,
            isFinished = false,
            startVoltageMv = startVoltageMv,
            currentVoltageMv = batteryInfo.voltageMv,
            currentTempC = batteryInfo.temperatureC,
            currentLevel = batteryInfo.level
        )

        val notification = NotificationHelper.buildTimerNotification(
            this,
            durationSec,
            !batteryInfo.isPlugged
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_TIMER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_TIMER, notification)
        }

        startTimerLoop()
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_timerState.value.secondsRemaining > 0 && _timerState.value.isRunning) {
                delay(1000L)
                if (!_timerState.value.isPaused) {
                    val remaining = _timerState.value.secondsRemaining - 1
                    _timerState.value = _timerState.value.copy(
                        secondsRemaining = remaining
                    )

                    // Update notification periodically or every 10 seconds to avoid spam
                    if (remaining % 10 == 0 || remaining <= 10) {
                        val notif = NotificationHelper.buildTimerNotification(
                            this@BatteryCalibrationService,
                            remaining,
                            _timerState.value.isPaused
                        )
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        manager.notify(NotificationHelper.NOTIFICATION_ID_TIMER, notif)
                    }
                }
            }

            if (_timerState.value.secondsRemaining <= 0 && _timerState.value.isRunning) {
                completeCalibration()
            }
        }
    }

    private fun pauseCalibration() {
        _timerState.value = _timerState.value.copy(isPaused = true)
    }

    private fun resumeCalibration() {
        _timerState.value = _timerState.value.copy(isPaused = false)
    }

    private fun completeCalibration() {
        val finalInfo = PixelBatteryReader.getBatteryInfo(this)
        _timerState.value = _timerState.value.copy(
            isRunning = false,
            secondsRemaining = 0,
            isFinished = true
        )

        triggerCompletionFeedback()

        serviceScope.launch {
            // Log to Room database
            val record = CalibrationRecord(
                cycleCountAtCalibration = initialCycleCount,
                durationSeconds = _timerState.value.totalSeconds,
                startVoltageMv = startVoltageMv,
                endVoltageMv = finalInfo.voltageMv,
                temperatureC = finalInfo.temperatureC,
                completedSuccessfully = true,
                notes = "Completed 30-min top-off saturation at 100%."
            )
            repository.recordCalibration(record)
            NotificationHelper.showCalibrationCompletedAlert(
                this@BatteryCalibrationService,
                initialCycleCount
            )
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun triggerCompletionFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 400, 200, 400, 200, 600),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 400, 200, 400, 200, 600),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(500)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun stopCalibration(cancelledByUser: Boolean) {
        timerJob?.cancel()
        _timerState.value = CalibrationTimerState(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Throwable) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"

        const val EXTRA_CYCLE = "EXTRA_CYCLE"
        const val EXTRA_DURATION_SEC = "EXTRA_DURATION_SEC"

        private val _timerState = MutableStateFlow(CalibrationTimerState())
        val timerState: StateFlow<CalibrationTimerState> = _timerState.asStateFlow()

        fun start(context: Context, cycle: Int, durationSec: Int = 30 * 60) {
            val intent = Intent(context, BatteryCalibrationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CYCLE, cycle)
                putExtra(EXTRA_DURATION_SEC, durationSec)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BatteryCalibrationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
