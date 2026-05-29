package com.phatnguoi.checker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phatnguoi.checker.R
import com.phatnguoi.checker.model.Vehicle
import com.phatnguoi.checker.model.ViolationResult
import java.text.SimpleDateFormat
import java.util.*

class VehicleAdapter(
    private val onDelete: (Vehicle) -> Unit
) : ListAdapter<Vehicle, VehicleAdapter.VH>(VehicleDiff()) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView       = view.findViewById(R.id.tv_vehicle_icon)
        val tvPlate: TextView      = view.findViewById(R.id.tv_plate)
        val tvType: TextView       = view.findViewById(R.id.tv_type)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_vehicle, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = getItem(position)
        holder.tvIcon.text = when (v.type) {
            "Xe máy"      -> "🛵"
            "Xe máy điện" -> "⚡"
            else          -> "🚗"
        }
        holder.tvPlate.text = v.licensePlate
        holder.tvType.text  = v.type
        holder.btnDelete.setOnClickListener { onDelete(v) }
    }

    class VehicleDiff : DiffUtil.ItemCallback<Vehicle>() {
        override fun areItemsTheSame(a: Vehicle, b: Vehicle) = a.id == b.id
        override fun areContentsTheSame(a: Vehicle, b: Vehicle) = a == b
    }
}

class ResultAdapter(
    private val onClick: (ViolationResult) -> Unit
) : ListAdapter<ViolationResult, ResultAdapter.VH>(ResultDiff()) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvPlate: TextView         = view.findViewById(R.id.tv_result_plate)
        val tvTotal: TextView         = view.findViewById(R.id.tv_total)
        val tvProcessed: TextView     = view.findViewById(R.id.tv_processed)
        val tvUnprocessed: TextView   = view.findViewById(R.id.tv_unprocessed)
        val tvTime: TextView          = view.findViewById(R.id.tv_check_time)
        val tvBadge: TextView         = view.findViewById(R.id.tv_new_badge)
        val tvNoViolation: TextView   = view.findViewById(R.id.tv_no_violation)
        val layoutStats: LinearLayout = view.findViewById(R.id.layout_stats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r   = getItem(position)
        val ctx = holder.itemView.context
        val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())

        holder.tvPlate.text = r.licensePlate
        holder.tvTime.text  = sdf.format(Date(r.checkedAt))
        holder.tvBadge.visibility     = if (r.hasNewViolation) View.VISIBLE else View.GONE
        holder.tvNoViolation.visibility = if (r.totalViolations == 0) View.VISIBLE else View.GONE

        // RED panel: vi phạm > 0 VÀ chưa xử lý > 0
        val showRed = r.totalViolations > 0 && r.unprocessedViolations > 0

        if (showRed) {
            holder.layoutStats.background =
                ContextCompat.getDrawable(ctx, R.drawable.bg_stats_violation)
            val white = ContextCompat.getColor(ctx, R.color.white)
            holder.tvTotal.apply {
                text = "Vi phạm: ${r.totalViolations}"
                setTextColor(white)
            }
            holder.tvProcessed.apply {
                // Đã xử lý = ĐÃ XỬ PHẠT
                text = "Đã xử lý: ${r.processedViolations}"
                setTextColor(white)
            }
            holder.tvUnprocessed.apply {
                // Chưa xử lý = CHƯA XỬ PHẠT
                text = "⚠ Chưa xử lý: ${r.unprocessedViolations}"
                setTextColor(white)
            }
            holder.itemView.setOnClickListener { onClick(r) }

        } else {
            holder.layoutStats.background =
                ContextCompat.getDrawable(ctx, R.drawable.bg_stats_normal)
            holder.tvTotal.apply {
                text = "Vi phạm: ${r.totalViolations}"
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
            holder.tvProcessed.apply {
                text = "Đã xử lý: ${r.processedViolations}"
                setTextColor(ContextCompat.getColor(ctx, R.color.processed_green))
            }
            holder.tvUnprocessed.apply {
                text = "Chưa xử lý: ${r.unprocessedViolations}"
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
            holder.itemView.setOnClickListener(
                if (r.totalViolations > 0) ({ onClick(r) }) else null
            )
        }
    }

    class ResultDiff : DiffUtil.ItemCallback<ViolationResult>() {
        override fun areItemsTheSame(a: ViolationResult, b: ViolationResult) =
            a.licensePlate == b.licensePlate
        override fun areContentsTheSame(a: ViolationResult, b: ViolationResult) = a == b
    }
}
