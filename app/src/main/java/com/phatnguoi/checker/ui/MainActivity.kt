package com.phatnguoi.checker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.phatnguoi.checker.R
import com.phatnguoi.checker.data.AppRepository
import com.phatnguoi.checker.databinding.ActivityMainBinding
import com.phatnguoi.checker.model.CHECK_INTERVALS
import com.phatnguoi.checker.utils.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var vehicleAdapter: VehicleAdapter
    private lateinit var resultAdapter: ResultAdapter

    // Countdown timer (ticks every second)
    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            tickHandler.postDelayed(this, 1000)
        }
    }

    // Alarm polling
    private val alarmPollHandler = Handler(Looper.getMainLooper())
    private val alarmPollRunnable = object : Runnable {
        override fun run() {
            val playing = NotificationHelper.isAlarmPlaying()
            binding.btnStopAlarm.visibility = if (playing) View.VISIBLE else View.GONE
            if (playing) alarmPollHandler.postDelayed(this, 500)
        }
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setupRecyclerViews()
        setupIntervalChips()
        setupButtons()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        vehicleAdapter = VehicleAdapter(onDelete = { v ->
            AlertDialog.Builder(this)
                .setTitle("Xóa biển số")
                .setMessage("Xóa biển số ${v.licensePlate}?")
                .setPositiveButton("Xóa") { _, _ -> viewModel.removeVehicle(v.licensePlate) }
                .setNegativeButton("Hủy", null).show()
        })
        binding.rvVehicles.layoutManager = LinearLayoutManager(this)
        binding.rvVehicles.adapter = vehicleAdapter

        resultAdapter = ResultAdapter { result ->
            startActivity(Intent(this, ViolationDetailActivity::class.java)
                .putExtra("license_plate", result.licensePlate))
        }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = resultAdapter
    }

    private fun setupIntervalChips() {
        val currentMinutes = viewModel.repository.getCheckIntervalMinutes()
        CHECK_INTERVALS.forEach { interval ->
            val chip = Chip(this).apply {
                text = interval.label
                isCheckable = true
                isChecked = interval.minutes == currentMinutes
                setChipBackgroundColorResource(R.color.chip_background_selector)
                setTextColor(resources.getColorStateList(R.color.chip_text_selector, theme))
                id = View.generateViewId()
            }
            binding.chipGroupInterval.addView(chip)
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) viewModel.setCheckInterval(interval.minutes)
            }
        }
    }

    private fun setupButtons() {
        binding.btnAddVehicle.setOnClickListener { showAddVehicleDialog() }

        binding.btnToggleService.setOnClickListener {
            if (viewModel.isServiceRunning.value == true) viewModel.stopService()
            else viewModel.startService()
        }

        binding.btnCheckNow.setOnClickListener {
            if (viewModel.vehicles.value.isNullOrEmpty()) {
                Snackbar.make(binding.root, "Thêm biển số trước!", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.checkNowOnce()
        }

        binding.btnClearResults.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Xóa kết quả")
                .setMessage("Xóa toàn bộ kết quả kiểm tra?")
                .setPositiveButton("Xóa") { _, _ -> viewModel.clearResults() }
                .setNegativeButton("Hủy", null).show()
        }

        binding.btnStopAlarm.setOnClickListener {
            NotificationHelper.stopAlarm()
            binding.btnStopAlarm.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.vehicles.observe(this) { list ->
            vehicleAdapter.submitList(list)
            binding.tvVehicleCount.text = "${list.size}/${AppRepository.MAX_VEHICLES}"
            binding.tvEmptyVehicles.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.rvVehicles.visibility      = if (list.isEmpty()) View.GONE  else View.VISIBLE
        }

        viewModel.results.observe(this) { results ->
            resultAdapter.submitList(results)
            val hasResults       = results.isNotEmpty()
            val totalUnprocessed = results.sumOf { it.unprocessedViolations }

            binding.tvEmptyResults.visibility  = if (hasResults) View.GONE    else View.VISIBLE
            binding.rvResults.visibility       = if (hasResults) View.VISIBLE else View.GONE
            binding.btnClearResults.visibility = if (hasResults) View.VISIBLE else View.GONE

            if (totalUnprocessed > 0) {
                binding.layoutAlert.visibility = View.VISIBLE
                binding.tvAlertText.text = "🚨 PHÁT HIỆN $totalUnprocessed VI PHẠM CHƯA XỬ LÝ!"
                alarmPollHandler.removeCallbacks(alarmPollRunnable)
                alarmPollHandler.post(alarmPollRunnable)
            } else {
                binding.layoutAlert.visibility  = View.GONE
                binding.btnStopAlarm.visibility = View.GONE
                alarmPollHandler.removeCallbacks(alarmPollRunnable)
            }

            val lastCheck = viewModel.repository.getLastCheckTime()
            if (lastCheck > 0) {
                binding.tvLastCheck.text = "Kiểm tra lúc: " +
                    SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date(lastCheck))
                binding.tvLastCheck.visibility = View.VISIBLE
            }
        }

        viewModel.isServiceRunning.observe(this) { running ->
            binding.btnToggleService.text =
                if (running) "■  Dừng giám sát" else "▶  Bắt đầu giám sát"
            binding.btnToggleService.setBackgroundColor(
                ContextCompat.getColor(this, if (running) R.color.stop_red else R.color.start_green))
            binding.tvServiceStatus.text =
                if (running) "● Đang giám sát tự động" else "○ Đã dừng"
            binding.tvServiceStatus.setTextColor(
                ContextCompat.getColor(this, if (running) R.color.status_green else R.color.status_gray))

            // Show/hide countdown
            if (running) {
                binding.layoutCountdown.visibility = View.VISIBLE
                tickHandler.removeCallbacks(tickRunnable)
                tickHandler.post(tickRunnable)
            } else {
                binding.layoutCountdown.visibility = View.GONE
                tickHandler.removeCallbacks(tickRunnable)
                binding.tvCountdown.text = "--:--:--"
            }
        }

        viewModel.isManualChecking.observe(this) { checking ->
            binding.progressBar.visibility = if (checking) View.VISIBLE else View.GONE
            binding.btnCheckNow.isEnabled  = !checking
            binding.btnCheckNow.text       = if (checking) "Đang tra..." else "⚡  Tra ngay"
        }

        viewModel.addVehicleError.observe(this) { err ->
            if (err != null) {
                Snackbar.make(binding.root, err, Snackbar.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    /** Update the countdown display every second */
    private fun updateCountdown() {
        val nextCheck = viewModel.repository.getNextCheckTime()
        if (nextCheck <= 0) {
            binding.tvCountdown.text = "--:--:--"
            return
        }
        val remaining = nextCheck - System.currentTimeMillis()
        if (remaining <= 0) {
            binding.tvCountdown.text = "Đang tra..."
            return
        }
        val totalSec = remaining / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        binding.tvCountdown.text = if (h > 0)
            String.format("%02d:%02d:%02d", h, m, s)
        else
            String.format("%02d:%02d", m, s)
    }

    private fun showAddVehicleDialog() {
        val view    = LayoutInflater.from(this).inflate(R.layout.dialog_add_vehicle, null)
        val etPlate = view.findViewById<EditText>(R.id.et_license_plate)
        val rg      = view.findViewById<RadioGroup>(R.id.rg_vehicle_type)
        AlertDialog.Builder(this)
            .setTitle("Thêm biển số xe")
            .setView(view)
            .setPositiveButton("Thêm") { _, _ ->
                val type = when (rg.checkedRadioButtonId) {
                    R.id.rb_moto  -> "Xe máy"
                    R.id.rb_ebike -> "Xe máy điện"
                    else          -> "Xe ô tô"
                }
                viewModel.addVehicle(etPlate.text.toString().trim(), type)
            }
            .setNegativeButton("Hủy", null).show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
        if (viewModel.isServiceRunning.value == true) {
            binding.layoutCountdown.visibility = View.VISIBLE
            tickHandler.removeCallbacks(tickRunnable)
            tickHandler.post(tickRunnable)
        }
        if (NotificationHelper.isAlarmPlaying()) {
            binding.btnStopAlarm.visibility = View.VISIBLE
            alarmPollHandler.post(alarmPollRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickHandler.removeCallbacks(tickRunnable)
        alarmPollHandler.removeCallbacks(alarmPollRunnable)
    }
}
