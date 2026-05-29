package com.phatnguoi.checker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.phatnguoi.checker.data.AppRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("BootReceiver", "Received: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val repo = AppRepository(context)
                if (repo.isServiceRunning() && repo.getVehicles().isNotEmpty()) {
                    Log.d("BootReceiver", "Auto-starting after boot")
                    // Start both foreground service AND WorkManager
                    CheckService.start(context)
                }
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm fired")
        val repo = AppRepository(context)
        if (!repo.isServiceRunning()) return

        // Try to start foreground service first
        try {
            CheckService.checkNow(context)
        } catch (e: Exception) {
            // If service can't start (background restriction), use WorkManager
            Log.w("AlarmReceiver", "Service failed, using WorkManager: ${e.message}")
            CheckWorker.scheduleOneTime(context)
        }
    }
}
