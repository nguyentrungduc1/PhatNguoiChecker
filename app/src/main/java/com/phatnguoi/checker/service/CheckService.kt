package com.phatnguoi.checker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.phatnguoi.checker.data.AppRepository
import com.phatnguoi.checker.data.PhatNguoiApi
import com.phatnguoi.checker.model.ViolationResult
import com.phatnguoi.checker.utils.NotificationHelper
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class CheckService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: AppRepository
    private val api = PhatNguoiApi()

    companion object {
        const val ACTION_CHECK_NOW = "com.phatnguoi.checker.CHECK_NOW"
        const val ACTION_STOP      = "com.phatnguoi.checker.STOP"

        val isRunning   = MutableLiveData(false)
        val isChecking  = MutableLiveData(false)
        val lastResults = MutableLiveData<List<ViolationResult>>()

        fun start(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, CheckService::class.java))

        fun stop(ctx: Context) =
            ctx.startService(Intent(ctx, CheckService::class.java).apply { action = ACTION_STOP })

        fun checkNow(ctx: Context) =
            ctx.startForegroundService(
                Intent(ctx, CheckService::class.java).apply { action = ACTION_CHECK_NOW })

        fun scheduleAlarm(ctx: Context, intervalMinutes: Int) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = alarmPI(ctx)
            val ms = intervalMinutes * 60_000L
            val nextTime = System.currentTimeMillis() + ms
            AppRepository(ctx).setNextCheckTime(nextTime)
            try {
                am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ms, ms, pi)
            } catch (_: SecurityException) {
                am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + ms, ms, pi)
            }
        }

        fun cancelAlarm(ctx: Context) {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmPI(ctx))
            AppRepository(ctx).setNextCheckTime(0)
        }

        private fun alarmPI(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(this)
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_NOW -> {
                startForeground(NotificationHelper.NOTIF_SERVICE_ID,
                    NotificationHelper.buildServiceNotification(this, "Đang kiểm tra..."))
                isRunning.postValue(true)
                repo.setServiceRunning(true)
                performCheck()
            }
            else -> {
                startForeground(NotificationHelper.NOTIF_SERVICE_ID,
                    NotificationHelper.buildServiceNotification(this, "Sẵn sàng kiểm tra"))
                isRunning.postValue(true)
                repo.setServiceRunning(true)
                scheduleAlarm(this, repo.getCheckIntervalMinutes())
                // Also schedule WorkManager as reliable fallback
                CheckWorker.schedule(this, repo.getCheckIntervalMinutes())
                performCheck()
            }
        }
        return START_STICKY
    }

    private fun performCheck() {
        if (isChecking.value == true) return
        isChecking.postValue(true)

        scope.launch {
            val vehicles = repo.getVehicles()
            if (vehicles.isEmpty()) {
                isChecking.postValue(false)
                NotificationHelper.updateServiceNotification(this@CheckService,
                    "Không có xe nào để kiểm tra")
                return@launch
            }

            NotificationHelper.updateServiceNotification(this@CheckService,
                "Đang kiểm tra ${vehicles.size} biển số...")

            val results = mutableListOf<ViolationResult>()

            vehicles.forEachIndexed { i, vehicle ->
                try {
                    NotificationHelper.updateServiceNotification(this@CheckService,
                        "Kiểm tra ${i + 1}/${vehicles.size}: ${vehicle.licensePlate}")
                    val prev   = repo.getResult(vehicle.licensePlate)
                    val result = api.checkViolation(vehicle.licensePlate, vehicle.type)
                    val hasNew = result.unprocessedViolations > 0 &&
                        (prev == null || result.unprocessedViolations > prev.unprocessedViolations)
                    val r = result.copy(hasNewViolation = hasNew)
                    results.add(r)
                    repo.updateResult(r)
                    if (r.unprocessedViolations > 0) {
                        NotificationHelper.sendViolationNotification(this@CheckService, r)
                    }
                    delay(800)
                } catch (e: Exception) {
                    Log.e("CheckService", "Error: ${e.message}")
                }
            }

            val now = System.currentTimeMillis()
            repo.setLastCheckTime(now)
            // Reset next check time after completing
            repo.setNextCheckTime(now + repo.getCheckIntervalMinutes() * 60_000L)

            lastResults.postValue(results)
            isChecking.postValue(false)

            val totalUnprocessed = results.sumOf { it.unprocessedViolations }
            val sdf = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
            NotificationHelper.updateServiceNotification(
                this@CheckService,
                if (totalUnprocessed > 0) "⚠ Phát hiện $totalUnprocessed vi phạm chưa xử lý!"
                else "✓ Không có vi phạm • ${sdf.format(Date())}",
                totalUnprocessed > 0
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        NotificationHelper.stopAlarm()
        isRunning.postValue(false)
        isChecking.postValue(false)   // ← Reset isChecking khi service bị kill
        repo.setServiceRunning(false)
        Log.d("CheckService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
