package com.phatnguoi.checker.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phatnguoi.checker.model.Vehicle
import com.phatnguoi.checker.model.ViolationResult

class AppRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "phatnguoi_prefs"
        private const val KEY_VEHICLES = "vehicles"
        private const val KEY_RESULTS  = "results"
        private const val KEY_INTERVAL_MINUTES = "check_interval_minutes"
        private const val KEY_SERVICE_RUNNING  = "service_running"
        private const val KEY_LAST_CHECK       = "last_check_time"
        private const val KEY_NEXT_CHECK       = "next_check_time"
        const val MAX_VEHICLES = 5
    }

    fun getVehicles(): List<Vehicle> {
        val json = prefs.getString(KEY_VEHICLES, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<List<Vehicle>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveVehicles(vehicles: List<Vehicle>) {
        prefs.edit().putString(KEY_VEHICLES, gson.toJson(vehicles)).apply()
    }

    fun addVehicle(vehicle: Vehicle): Boolean {
        val list = getVehicles().toMutableList()
        if (list.size >= MAX_VEHICLES) return false
        if (list.any { it.licensePlate.equals(vehicle.licensePlate, ignoreCase = true) }) return false
        list.add(vehicle)
        saveVehicles(list)
        return true
    }

    fun removeVehicle(licensePlate: String) {
        saveVehicles(getVehicles().filter { !it.licensePlate.equals(licensePlate, ignoreCase = true) })
        saveResults(getResults().filter { !it.licensePlate.equals(licensePlate, ignoreCase = true) })
    }

    fun getResults(): List<ViolationResult> {
        val json = prefs.getString(KEY_RESULTS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<List<ViolationResult>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveResults(results: List<ViolationResult>) {
        prefs.edit().putString(KEY_RESULTS, gson.toJson(results)).apply()
    }

    fun updateResult(result: ViolationResult) {
        val results = getResults().toMutableList()
        val idx = results.indexOfFirst { it.licensePlate.equals(result.licensePlate, ignoreCase = true) }
        if (idx >= 0) results[idx] = result else results.add(result)
        saveResults(results)
    }

    fun getResult(licensePlate: String) =
        getResults().find { it.licensePlate.equals(licensePlate, ignoreCase = true) }

    fun clearAllResults() { prefs.edit().remove(KEY_RESULTS).apply() }

    // Interval stored in minutes
    fun getCheckIntervalMinutes(): Int = prefs.getInt(KEY_INTERVAL_MINUTES, 60)
    fun setCheckIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL_MINUTES, minutes).apply()
    }

    // Legacy hours getter for backward compat
    fun getCheckIntervalHours(): Int = getCheckIntervalMinutes() / 60

    fun isServiceRunning(): Boolean = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
    fun setServiceRunning(running: Boolean) { prefs.edit().putBoolean(KEY_SERVICE_RUNNING, running).apply() }

    fun getLastCheckTime(): Long = prefs.getLong(KEY_LAST_CHECK, 0)
    fun setLastCheckTime(time: Long) { prefs.edit().putLong(KEY_LAST_CHECK, time).apply() }

    fun getNextCheckTime(): Long = prefs.getLong(KEY_NEXT_CHECK, 0)
    fun setNextCheckTime(time: Long) { prefs.edit().putLong(KEY_NEXT_CHECK, time).apply() }
}
