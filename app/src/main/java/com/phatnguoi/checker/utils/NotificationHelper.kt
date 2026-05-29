package com.phatnguoi.checker.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.phatnguoi.checker.R
import com.phatnguoi.checker.model.ViolationResult
import com.phatnguoi.checker.ui.MainActivity
import com.phatnguoi.checker.ui.ViolationDetailActivity

object NotificationHelper {

    const val CHANNEL_SERVICE   = "service_channel"
    const val CHANNEL_VIOLATION = "violation_channel"
    const val NOTIF_SERVICE_ID  = 1001

    private var activeRingtone: Ringtone? = null
    private val stopHandler  = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopAlarm() }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel(CHANNEL_SERVICE, "Dịch vụ kiểm tra",
            NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            manager.createNotificationChannel(this)
        }

        // Use NOTIFICATION (message) sound for the channel
        val msgUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        NotificationChannel(CHANNEL_VIOLATION, "🚨 Cảnh báo vi phạm",
            NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 600)
            enableLights(true)
            lightColor = android.graphics.Color.RED
            setSound(msgUri, audioAttr)
            manager.createNotificationChannel(this)
        }
    }

    fun buildServiceNotification(
        context: Context,
        status: String = "Đang chạy nền...",
        hasViolation: Boolean = false
    ): Notification {
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (hasViolation) "🚨 Phát hiện có vi phạm!" else "Kiểm tra phạt nguội"
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(title)
            .setContentText(status)
            .setSmallIcon(if (hasViolation) R.drawable.ic_warning else R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setColor(if (hasViolation) android.graphics.Color.RED else android.graphics.Color.BLUE)
            .build()
    }

    fun sendViolationNotification(context: Context, result: ViolationResult) {
        if (result.unprocessedViolations == 0) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, ViolationDetailActivity::class.java).apply {
            putExtra("license_plate", result.licensePlate)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, result.licensePlate.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_VIOLATION)
            .setContentTitle("🚨 PHÁT HIỆN VI PHẠM CHƯA XỬ LÝ!")
            .setContentText("${result.licensePlate}: ${result.unprocessedViolations} vi phạm chưa xử lý")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(android.graphics.Color.RED)
            .setColorized(true)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "⚠️ ${result.licensePlate}: ${result.unprocessedViolations} vi phạm CHƯA xử lý / tổng ${result.totalViolations}.\nNhấn để xem chi tiết."
            ))
            .build()
        manager.notify(result.licensePlate.hashCode(), notif)
    }

    fun updateServiceNotification(context: Context, status: String, hasViolation: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_SERVICE_ID, buildServiceNotification(context, status, hasViolation))
    }

    /** Fallback only: play a single notification sound without looping. */
    fun playAlarmSound(context: Context, autoStopSeconds: Int = 10) {
        stopAlarm()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = false
            }
            ringtone.play()
            activeRingtone = ringtone
            stopHandler.removeCallbacks(stopRunnable)
            stopHandler.postDelayed(stopRunnable, autoStopSeconds * 1000L)
        } catch (_: Exception) {}
    }

    fun stopAlarm() {
        stopHandler.removeCallbacks(stopRunnable)
        try { activeRingtone?.stop() } catch (_: Exception) {}
        activeRingtone = null
    }

    fun isAlarmPlaying(): Boolean = activeRingtone?.isPlaying == true
}
