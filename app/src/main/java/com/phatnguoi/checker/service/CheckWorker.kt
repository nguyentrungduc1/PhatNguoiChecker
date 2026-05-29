package com.phatnguoi.checker.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.phatnguoi.checker.data.AppRepository
import com.phatnguoi.checker.data.PhatNguoiApi
import com.phatnguoi.checker.model.ViolationResult
import com.phatnguoi.checker.utils.NotificationHelper
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker — runs even when app is killed or screen is locked.
 * This is the reliable fallback when the foreground service is killed by Android.
 */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val repo = AppRepository(context)
        val api  = PhatNguoiApi()

        NotificationHelper.createChannels(context)
        Log.d("CheckWorker", "Starting background check")

        val vehicles = repo.getVehicles()
        if (vehicles.isEmpty()) return Result.success()

        try {
            vehicles.forEach { vehicle ->
                try {
                    val prev   = repo.getResult(vehicle.licensePlate)
                    val result = api.checkViolation(vehicle.licensePlate, vehicle.type)
                    val hasNew = result.unprocessedViolations > 0 &&
                        (prev == null || result.unprocessedViolations > prev.unprocessedViolations)
                    val r = result.copy(hasNewViolation = hasNew)
                    repo.updateResult(r)
                    if (r.unprocessedViolations > 0) {
                        NotificationHelper.sendViolationNotification(context, r)
                        Log.d("CheckWorker", "Violation found: ${vehicle.licensePlate}")
                    }
                    delay(800)
                } catch (e: Exception) {
                    Log.e("CheckWorker", "Error checking ${vehicle.licensePlate}: ${e.message}")
                }
            }

            val now = System.currentTimeMillis()
            repo.setLastCheckTime(now)
            repo.setNextCheckTime(now + repo.getCheckIntervalMinutes() * 60_000L)

            val totalUnprocessed = repo.getResults().sumOf { it.unprocessedViolations }
            val sdf = java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.getDefault())
            NotificationHelper.updateServiceNotification(
                context,
                if (totalUnprocessed > 0) "⚠ Phát hiện $totalUnprocessed vi phạm chưa xử lý!"
                else "✓ Không có vi phạm • ${sdf.format(java.util.Date())}",
                totalUnprocessed > 0
            )
        } catch (e: Exception) {
            Log.e("CheckWorker", "Worker failed: ${e.message}")
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "periodic_violation_check"

        fun schedule(context: Context, intervalMinutes: Int) {
            val minutes = intervalMinutes.toLong().coerceAtLeast(15)

            val request = PeriodicWorkRequestBuilder<CheckWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d("CheckWorker", "Scheduled every $minutes minutes")
        }

        fun scheduleOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<CheckWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("one_time_check",
                    ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("CheckWorker", "Cancelled")
        }
    }
}
