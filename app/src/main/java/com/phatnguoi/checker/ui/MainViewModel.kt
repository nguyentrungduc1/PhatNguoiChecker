package com.phatnguoi.checker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.phatnguoi.checker.data.AppRepository
import com.phatnguoi.checker.data.PhatNguoiApi
import com.phatnguoi.checker.model.Vehicle
import com.phatnguoi.checker.model.ViolationResult
import com.phatnguoi.checker.service.CheckService
import com.phatnguoi.checker.service.CheckWorker
import com.phatnguoi.checker.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AppRepository(application)
    private val api = PhatNguoiApi()

    private val _vehicles = MutableLiveData<List<Vehicle>>()
    val vehicles: LiveData<List<Vehicle>> = _vehicles

    private val _results = MutableLiveData<List<ViolationResult>>()
    val results: LiveData<List<ViolationResult>> = _results

    private val _addVehicleError = MutableLiveData<String?>()
    val addVehicleError: LiveData<String?> = _addVehicleError

    private val _isManualChecking = MutableLiveData(false)
    val isManualChecking: LiveData<Boolean> = _isManualChecking

    // Mirror service state — also check SharedPrefs so state survives app restart
    val isServiceRunning: LiveData<Boolean> = CheckService.isRunning
    val isChecking: LiveData<Boolean>       = CheckService.isChecking

    init {
        loadData()
        // Sync LiveData with saved state on init (app was killed and reopened)
        if (repository.isServiceRunning()) {
            CheckService.isRunning.postValue(true)
            // If service was running but isChecking stuck — reset it
            CheckService.isChecking.postValue(false)
        }
        CheckService.lastResults.observeForever { if (it != null) _results.postValue(it) }
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _vehicles.postValue(repository.getVehicles())
            _results.postValue(repository.getResults())
        }
    }

    fun addVehicle(licensePlate: String, type: String) {
        val clean = licensePlate.trim().uppercase()
        if (clean.length < 5) { _addVehicleError.value = "Biển số không hợp lệ"; return }
        if (repository.getVehicles().size >= AppRepository.MAX_VEHICLES) {
            _addVehicleError.value = "Tối đa ${AppRepository.MAX_VEHICLES} biển số"; return
        }
        if (!repository.addVehicle(Vehicle(licensePlate = clean, type = type))) {
            _addVehicleError.value = "Biển số đã tồn tại"; return
        }
        _addVehicleError.value = null
        loadData()
    }

    fun removeVehicle(licensePlate: String) { repository.removeVehicle(licensePlate); loadData() }

    fun clearResults() { repository.clearAllResults(); _results.postValue(emptyList()) }

    fun setCheckInterval(minutes: Int) {
        repository.setCheckIntervalMinutes(minutes)
        if (isServiceRunning.value == true) {
            CheckService.scheduleAlarm(getApplication(), minutes)
            CheckWorker.schedule(getApplication(), minutes)
        }
    }

    fun startService() {
        CheckService.start(getApplication())
    }

    fun stopService() {
        CheckService.stop(getApplication())
        CheckService.cancelAlarm(getApplication())
        CheckWorker.cancel(getApplication())
        repository.setNextCheckTime(0)
    }

    fun checkNowOnce() {
        val vehicles = repository.getVehicles()
        if (vehicles.isEmpty() || _isManualChecking.value == true) return
        _isManualChecking.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val results = mutableListOf<ViolationResult>()
            vehicles.forEach { vehicle ->
                try {
                    val prev   = repository.getResult(vehicle.licensePlate)
                    val result = api.checkViolation(vehicle.licensePlate)
                    val hasNew = result.unprocessedViolations > 0 &&
                        (prev == null || result.unprocessedViolations > prev.unprocessedViolations)
                    val r = result.copy(hasNewViolation = hasNew)
                    results.add(r)
                    repository.updateResult(r)
                    if (r.unprocessedViolations > 0) {
                        // Notification channel plays sound once — no need to call playAlarmSound()
                        NotificationHelper.sendViolationNotification(ctx, r)
                    }
                    delay(800)
                } catch (_: Exception) {}
            }
            val now = System.currentTimeMillis()
            repository.setLastCheckTime(now)
            // Reset countdown after manual check if service is running
            if (repository.isServiceRunning()) {
                repository.setNextCheckTime(now + repository.getCheckIntervalMinutes() * 60_000L)
            }
            _results.postValue(results)
            _isManualChecking.postValue(false)
        }
    }

    fun clearError() { _addVehicleError.value = null }
}
