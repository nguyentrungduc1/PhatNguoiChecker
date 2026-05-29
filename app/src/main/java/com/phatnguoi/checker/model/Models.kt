package com.phatnguoi.checker.model

data class Vehicle(
    val id: String = java.util.UUID.randomUUID().toString(),
    val licensePlate: String,
    val type: String = "Xe ô tô",
    val addedAt: Long = System.currentTimeMillis()
)

data class ViolationResult(
    val licensePlate: String,
    val totalViolations: Int,
    val processedViolations: Int,
    val unprocessedViolations: Int,
    val violations: List<ViolationDetail>,
    val checkedAt: Long = System.currentTimeMillis(),
    val hasNewViolation: Boolean = false
)

data class ViolationDetail(
    val licensePlate: String,
    val status: String,
    val vehicleType: String,
    val plateColor: String,
    val behavior: String,
    val violationTime: String,
    val violationPlace: String,
    val detectingUnit: String,
    val resolvingUnit: String,
    val resolvingAddress: String,
    val resolvingPhone: String
)

data class CheckInterval(
    val label: String,
    val minutes: Int   // store as minutes for precision (15min, 30min, etc.)
) {
    val hours: Double get() = minutes / 60.0
    val milliseconds: Long get() = minutes * 60_000L
}

val CHECK_INTERVALS = listOf(
    CheckInterval("15 phút",  15),
    CheckInterval("30 phút",  30),
    CheckInterval("1 giờ",    60),
    CheckInterval("3 giờ",    180),
    CheckInterval("6 giờ",    360),
    CheckInterval("12 giờ",   720),
    CheckInterval("24 giờ",   1440),
    CheckInterval("48 giờ",   2880)
)
