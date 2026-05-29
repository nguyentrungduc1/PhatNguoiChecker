package com.phatnguoi.checker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phatnguoi.checker.R
import com.phatnguoi.checker.data.AppRepository
import com.phatnguoi.checker.databinding.ActivityViolationDetailBinding
import com.phatnguoi.checker.model.ViolationDetail

class ViolationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViolationDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViolationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val plate = intent.getStringExtra("license_plate") ?: run { finish(); return }
        val repo = AppRepository(this)
        val result = repo.getResult(plate) ?: run { finish(); return }

        supportActionBar?.title = plate
        binding.tvSummary.text =
            "Tổng: ${result.totalViolations} | Đã xử lý: ${result.processedViolations} | Chưa xử lý: ${result.unprocessedViolations}"

        binding.tvUnprocessedCount.text = "${result.unprocessedViolations}"
        binding.tvUnprocessedCount.setTextColor(
            ContextCompat.getColor(this,
                if (result.unprocessedViolations > 0) R.color.violation_red else R.color.processed_green)
        )

        val adapter = ViolationDetailAdapter()
        binding.rvViolations.layoutManager = LinearLayoutManager(this)
        binding.rvViolations.adapter = adapter

        if (result.violations.isEmpty()) {
            binding.tvNoDetail.visibility = View.VISIBLE
            binding.rvViolations.visibility = View.GONE
        } else {
            binding.tvNoDetail.visibility = View.GONE
            binding.rvViolations.visibility = View.VISIBLE
            adapter.submitList(result.violations)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}

class ViolationDetailAdapter :
    ListAdapter<ViolationDetail, ViolationDetailAdapter.VH>(Diff()) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvStatus: TextView      = view.findViewById(R.id.tv_status)
        val tvVehicleType: TextView = view.findViewById(R.id.tv_vehicle_type)
        val tvPlateColor: TextView  = view.findViewById(R.id.tv_plate_color)
        val tvBehavior: TextView    = view.findViewById(R.id.tv_behavior)
        val tvTime: TextView        = view.findViewById(R.id.tv_violation_time)
        val tvPlace: TextView       = view.findViewById(R.id.tv_violation_place)
        val tvUnit: TextView        = view.findViewById(R.id.tv_detecting_unit)
        val tvResolve: TextView     = view.findViewById(R.id.tv_resolving_unit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_violation_detail, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = getItem(position)
        holder.tvStatus.apply {
            text = v.status.ifEmpty { "N/A" }
            setTextColor(ContextCompat.getColor(context,
                if (v.status.contains("CHƯA", ignoreCase = true))
                    R.color.violation_red else R.color.processed_green))
        }
        holder.tvVehicleType.text = v.vehicleType.ifEmpty { "—" }
        holder.tvPlateColor.text  = v.plateColor.ifEmpty { "—" }
        holder.tvBehavior.text    = v.behavior.ifEmpty { "—" }
        holder.tvTime.text        = v.violationTime.ifEmpty { "—" }
        holder.tvPlace.text       = v.violationPlace.ifEmpty { "—" }
        holder.tvUnit.text        = v.detectingUnit.ifEmpty { "—" }
        holder.tvResolve.text     = v.resolvingUnit.ifEmpty { "—" }
    }

    class Diff : DiffUtil.ItemCallback<ViolationDetail>() {
        override fun areItemsTheSame(a: ViolationDetail, b: ViolationDetail) =
            a.licensePlate == b.licensePlate && a.violationTime == b.violationTime
        override fun areContentsTheSame(a: ViolationDetail, b: ViolationDetail) = a == b
    }
}
