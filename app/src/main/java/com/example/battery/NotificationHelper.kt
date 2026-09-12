package com.example.battery

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_CALIBRATION_ALERT = "pixel_battery_calibration_alert"
    const val CHANNEL_CALIBRATION_TIMER = "pixel_battery_calibration_timer"

    const val NOTIFICATION_ID_DUE_ALERT = 1001
    const val NOTIFICATION_ID_TIMER = 1002
    const val NOTIFICATION_ID_COMPLETE = 1003

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                CHANNEL_CALIBRATION_ALERT,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
                enableLights(true)
            }

            val timerChannel = NotificationChannel(
                CHANNEL_CALIBRATION_TIMER,
                "Calibration Saturation Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active 30-minute battery calibration top-off progress"
                setShowBadge(false)
            }

            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(timerChannel)
        }
    }

    fun showCalibrationDueAlert(context: Context, currentCycle: Int) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_START_CALIBRATION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CALIBRATION_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚡ Battery Calibration Due! (Cycle $currentCycle)")
            .setContentText("Charge to 100% and keep plugged in for 30 min to calibrate the battery sensor accurately.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "You reached cycle $currentCycle (10-cycle interval)! To keep your Pixel battery percentage and sensor reading accurately, charge to 100% and leave it plugged in for 30 minutes to saturate the fuel gauge."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_DUE_ALERT, notification)
    }

    fun showCalibrationCompletedAlert(context: Context, cycle: Int) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CALIBRATION_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎉 Calibration Complete!")
            .setContentText("30-minute saturation finished. Your battery sensor is now accurately calibrated!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Great job! The 30-minute top-off at 100% charge has completed for Cycle $cycle. The fuel gauge coulomb counter has been recalibrated for the next 10 cycles."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    fun buildTimerNotification(
        context: Context,
        secondsRemaining: Int,
        isPaused: Boolean
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutes = secondsRemaining / 60
        val seconds = secondsRemaining % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)

        val title = if (isPaused) {
            "⏸️ Calibration Paused - Plug In Phone"
        } else {
            "🔋 Battery Sensor Calibrating: $timeString"
        }

        val text = if (isPaused) {
            "Disconnected from charger. Plug back in to finish 30-min top-off."
        } else {
            "Holding at 100% to saturate battery fuel gauge ($timeString left)"
        }

        val totalSeconds = 30 * 60
        val progress = ((totalSeconds - secondsRemaining).toFloat() / totalSeconds * 100).toInt()

        return NotificationCompat.Builder(context, CHANNEL_CALIBRATION_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Calibration Top-Off")
            .setProgress(100, progress, false)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
