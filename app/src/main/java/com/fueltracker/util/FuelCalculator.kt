package com.fueltracker.util

import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================
// 数据结构
// ============================================================

data class ConsumptionResult(
    val recordId: Long,
    val startOdometer: Double,
    val endOdometer: Double,
    val distance: Double,
    val fuelUsed: Double,
    val consumption: Double,
    val isEstimated: Boolean,
    val algorithm: String = "FULL_TO_FULL",
    val quality: IntervalQuality? = null,
    val isOutlier: Boolean = false
)

/**
 * 区间置信等级（level 不依赖 enum 声明顺序）
 */
enum class IntervalConfidence(val level: Int) {
    UNRELIABLE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    fun atLeast(other: IntervalConfidence): Boolean = level >= other.level
}

data class IntervalQuality(
    val startOdometerEstimated: Boolean = false,
    val missingOdometerCount: Int = 0,
    val missedFuelUnrecordedCount: Int = 0,
    val missedFuelRecordedVolume: Double = 0.0,
    val middlePartialFuelingCount: Int = 0,
    val intervalRecordCount: Int = 0,
    val invalidFuelRecordCount: Int = 0,
    val odometerAnomalyCount: Int = 0,
    val inconsistentRemainingFuelCount: Int = 0,
    val oversizedSingleFuelCount: Int = 0,
    val suspiciousOdometerJumpCount: Int = 0,
    val confidenceScore: Double = 1.0,
    val confidence: IntervalConfidence = IntervalConfidence.HIGH,
    val exclusionReason: String? = null
)

data class StatisticsSummary(
    val totalCount: Int,
    val totalVolume: Double,
    val totalMissedVolume: Double,
    val totalCost: Double,
    val averagePrice: Double?,

    /** 全部闭合区间的总里程，包含离群区间 */
    val totalDistance: Double,

    /** 全部闭合区间的总燃油，包含离群区间 */
    val totalFuel: Double,

    /**
     * 实际平均油耗:
     * Σ有效区间燃油 / Σ有效区间里程 × 100
     * 只使用：非离群 & 置信度 >= LOW
     */
    val averageConsumption: Double?,

    val odometerSpanText: String,

    /**
     * 满箱记录中未形成结果的次数。
     * 第一条合法满箱锚点不计入未闭合。
     */
    val unclosedCount: Int,

    val rangeResults: List<ConsumptionResult>,

    /**
     * 范围内未形成油耗结果的记录（按时间倒序）。
     * 包括：无里程、未满箱闭合、被硬边界拒绝等。
     */
    val pendingRecords: List<FuelRecord> = emptyList(),

    /**
     * odometer 为空的记录数（"完全没填里程"的次数）。
     */
    val missingOdometerCount: Int = 0,

    /** 鲁棒加权中位数 (权重：confidenceScore × sqrt(distance)) */
    val robustAverageConsumption: Double? = null,

    /** 置信度 × 里程加权算术平均 (权重：confidenceScore × distance) */
    val weightedAverageConsumption: Double? = null,

    val reliableResultCount: Int = 0,
    val outlierCount: Int = 0
)

// ============================================================
// 核心计算器 V3.8.2
// ============================================================

object FuelCalculator {

    // 硬边界（阻止明显不合理的数学结果）
    private const val MIN_HARD_CONSUMPTION = 0.1
    private const val MAX_HARD_CONSUMPTION = 200.0

    // MAD 离群检测参数
    private const val LOCAL_MAD_WINDOW = 11
    private const val MIN_MAD_SAMPLE_COUNT = 5
    private const val MAD_THRESHOLD_FLOOR = 0.35
    private const val MAD_MULTIPLIER = 3.0

    // 里程跳变与基准
    private const val ODOMETER_JUMP_SUSPICIOUS_SPEED = 250.0
    private const val ODOMETER_JUMP_ABSOLUTE_LIMIT = 500.0

    // remainingFuel 一致性检查
    private const val REMAINING_FUEL_CONSISTENCY_LOW = 0.90
    private const val REMAINING_FUEL_CONSISTENCY_HIGH = 1.00
    private const val REMAINING_FUEL_SEVERE_LOW = 0.70

    // 单次加油量边界
    private const val SINGLE_FUEL_OVER_TANK_RATIO = 1.15
    private const val SINGLE_FUEL_SEVERE_RATIO = 1.50

    // SoftRange 与扣分限制
    private const val SOFT_RANGE_PENALTY_CAP = 0.30
    private const val MAX_TOTAL_PENALTY = 0.80
    private const val MIN_SCORE = 1.0 - MAX_TOTAL_PENALTY

    // 质量评分细项扣分系数
    private const val PENALTY_ESTIMATED_START = 0.30
    private const val PENALTY_MISSING_ODOMETER_PER = 0.06
    private const val PENALTY_MISSING_ODOMETER_CAP = 0.25
    private const val PENALTY_MISSED_FUEL_UNRECORDED_PER = 0.20
    private const val PENALTY_MISSED_FUEL_UNRECORDED_CAP = 0.50
    private const val RELIEF_MISSED_ODOMETER_PER = 0.05
    private const val PENALTY_MISSED_FUEL_RECORDED_CAP = 0.15
    private const val PENALTY_MIDDLE_PARTIAL_PER = 0.05
    private const val PENALTY_MIDDLE_PARTIAL_CAP = 0.20
    private const val PENALTY_INVALID_FUEL_PER = 0.12
    private const val PENALTY_INVALID_FUEL_CAP = 0.30
    private const val PENALTY_ODOMETER_ANOMALY_PER = 0.15
    private const val PENALTY_ODOMETER_ANOMALY_CAP = 0.45
    private const val PENALTY_SUSPICIOUS_JUMP_PER = 0.10
    private const val PENALTY_SUSPICIOUS_JUMP_CAP = 0.20
    private const val PENALTY_INCONSISTENT_REMAINING_PER = 0.05
    private const val PENALTY_INCONSISTENT_REMAINING_CAP = 0.15
    private const val PENALTY_OVERSIZED_PER = 0.25
    private const val PENALTY_OVERSIZED_CAP = 0.50
    private const val PENALTY_SHORT_DISTANCE_BASE = 0.15

    private const val CONFIDENCE_HIGH_THRESHOLD = 0.80
    private const val CONFIDENCE_MEDIUM_THRESHOLD = 0.55
    private const val CONFIDENCE_LOW_THRESHOLD = 0.30

    private data class SoftRange(val min: Double, val max: Double)

    private fun softRangeFor(fuelType: String?): SoftRange {
        val type = fuelType?.trim()?.uppercase().orEmpty()
        return when {
            type.contains("柴油") || type.contains("DIESEL") -> SoftRange(3.0, 15.0)
            type.contains("插电") || type.contains("插混") || type.contains("PHEV") ||
                    type.contains("增程") || type.contains("EREV") -> SoftRange(1.0, 12.0)
            type.contains("混动") || type.contains("油电") || type.contains("HEV") ||
                    type.contains("HYBRID") -> SoftRange(2.0, 10.0)
            else -> SoftRange(3.0, 30.0) // 汽油 / 未识别
        }
    }

    // ========================================================
    // 对外接口
    // ========================================================

    @JvmOverloads
    fun latestReliableConsumption(
        records: List<FuelRecord>,
        vehicle: Vehicle? = null
    ): Double? {
        return calculateAll(records, vehicle)
            .lastOrNull { isReliable(it) }
            ?.consumption
    }

    @JvmOverloads
    fun calculateMap(
        records: List<FuelRecord>,
        vehicle: Vehicle? = null
    ): Map<Long, ConsumptionResult> {
        return calculateAll(records, vehicle).associateBy { it.recordId }
    }

    // ========================================================
    // 主算法
    // ========================================================

    @JvmOverloads
    fun calculateAll(
        records: List<FuelRecord>,
        vehicle: Vehicle? = null
    ): List<ConsumptionResult> {
        if (records.isEmpty()) return emptyList()

        val deliveryDate = vehicle?.deliveryDate?.takeIf { it > 0L }
        val fuelRecords = records.asSequence()
            .filter { isFuelVisible(it, deliveryDate) }
            .sortedWith(RECORD_COMPARATOR)
            .toList()

        if (fuelRecords.isEmpty()) return emptyList()

        val tankCapacity = vehicle?.tankCapacity?.takeIf { it.isFinite() && it > 0.0 }
        val configuredInitialOdometer = vehicle?.initialOdometer?.takeIf { it.isFinite() && it > 0.0 }

        // 1. 找第一个真正可用的锚点
        val anchor = findFirstAnchor(
            records = fuelRecords,
            tankCapacity = tankCapacity,
            configuredInitialOdometer = configuredInitialOdometer
        ) ?: return emptyList()

        var segmentStartOdometer = anchor.odometer
        var segmentStartEstimated = anchor.isEstimated

        val state = SegmentState()
        state.resetForStartRecord()
        state.recordAnchorQualityFlags(
            record = fuelRecords[anchor.index],
            tankCapacity = tankCapacity
        )

        val results = ArrayList<ConsumptionResult>((fuelRecords.size - anchor.index).coerceAtLeast(0))
        var lastReliableOdometer = segmentStartOdometer
        var lastReliableTimestamp = fuelRecords[anchor.index].timestamp

        // 2. 从锚点之后开始扫描
        for (index in anchor.index + 1 until fuelRecords.size) {
            val record = fuelRecords[index]
            val currentOdometer = validOdometer(record)

            // A. 识别里程可疑跳变
            // 注意：segmentStartEstimated = true 时跳过跳变判定。
            // 估算的起点里程（来自 vehicle.initialOdometer）本身可能
            // 与实际里程偏差极大，用它触发"跳变"会导致后续所有记录
            // 被拒，整个区间永远无法闭合。等第一个区间闭合、
            // segmentStartEstimated 恢复 false 后，跳变判定正常工作。
            var isSuspiciousJump = false
            if (!segmentStartEstimated &&
                currentOdometer != null &&
                lastReliableOdometer > 0.0 &&
                currentOdometer > lastReliableOdometer
            ) {
                val jump = currentOdometer - lastReliableOdometer
                val timeHours = (record.timestamp - lastReliableTimestamp).toDouble() / 3_600_000.0
                if (isSuspiciousOdometerJump(jump = jump, timeHours = timeHours)) {
                    state.markSuspiciousOdometerJump()
                    isSuspiciousJump = true
                }
            }

            // B. 无条件记录质量问题
            state.addRecordQualityFlags(record = record, tankCapacity = tankCapacity)

            // C. 分别判断当前加油与漏记加油是否严重异常
            val currentFuelSevere = isSeverelyOversizedVolume(volume = record.volume, tankCapacity = tankCapacity)
            val missedFuelSevere = record.missedVolume?.let {
                isSeverelyOversizedVolume(volume = it, tankCapacity = tankCapacity)
            } ?: false

            // D. 当前加油独立累计
            if (!currentFuelSevere) state.addCurrentFuel(record)

            // E. missedVolume 独立累计
            if (!missedFuelSevere) state.addMissedFuel(record)

            // F. 更新可靠里程基准
            val odometerRollback = currentOdometer != null && lastReliableOdometer > 0.0 && currentOdometer < lastReliableOdometer
            if (odometerRollback) {
                state.markOdometerAnomaly()
            } else if (currentOdometer != null && !isSuspiciousJump) {
                lastReliableOdometer = currentOdometer
                lastReliableTimestamp = record.timestamp
            }

            // 以下均属于“闭合判断”，不影响已累计的加油量
            if (currentFuelSevere) continue
            if (isRemainingFuelSeverelyInconsistent(record = record, tankCapacity = tankCapacity)) {
                state.markAsMiddlePartialFueling()
                continue
            }
            if (!record.isFull) {
                state.markAsMiddlePartialFueling()
                continue
            }
            if (!hasValidFuelVolume(record)) continue
            if (isSuspiciousJump) continue
            if (currentOdometer == null) continue
            if (odometerRollback) continue

            // M. 计算区间里程
            val distance = currentOdometer - segmentStartOdometer
            if (!distance.isFinite() || distance <= 0.0) {
                state.markOdometerAnomaly()
                continue
            }

            // N. 获取区间总有效燃油
            val totalFuel = state.pendingFuel
            if (!totalFuel.isFinite() || totalFuel <= 0.0) {
                if (isSuitableAsAnchor(record = record, tankCapacity = tankCapacity)) {
                    segmentStartOdometer = currentOdometer
                    segmentStartEstimated = false
                    lastReliableOdometer = currentOdometer
                    lastReliableTimestamp = record.timestamp
                    state.resetForStartRecord()
                    state.recordAnchorQualityFlags(
                        record = record,
                        tankCapacity = tankCapacity
                    )
                }
                continue
            }

            // O. 计算油耗
            val consumption = calculateConsumption(fuel = totalFuel, distance = distance)

            // P. 硬边界检查
            if (!consumption.isFinite() || consumption < MIN_HARD_CONSUMPTION || consumption > MAX_HARD_CONSUMPTION) {
                if (isSuitableAsAnchor(record = record, tankCapacity = tankCapacity)) {
                    segmentStartOdometer = currentOdometer
                    segmentStartEstimated = false
                    lastReliableOdometer = currentOdometer
                    lastReliableTimestamp = record.timestamp
                    state.resetForStartRecord()
                    state.recordAnchorQualityFlags(
                        record = record,
                        tankCapacity = tankCapacity
                    )
                }
                continue
            }

            // Q. 构建质量评分与生成结果
            val quality = buildQuality(
                state = state,
                startOdometerEstimated = segmentStartEstimated,
                distance = distance,
                tankCapacity = tankCapacity,
                fuelType = vehicle?.fuelType,
                consumption = consumption
            )
            val isEstimated = quality.confidence != IntervalConfidence.HIGH

            results.add(
                ConsumptionResult(
                    recordId = record.id,
                    startOdometer = segmentStartOdometer,
                    endOdometer = currentOdometer,
                    distance = distance,
                    fuelUsed = totalFuel,
                    consumption = consumption,
                    isEstimated = isEstimated,
                    algorithm = "FULL_TO_FULL",
                    quality = quality
                )
            )

            // S. 当前满箱成为下一段新的锚点
            segmentStartOdometer = currentOdometer
            segmentStartEstimated = false
            lastReliableOdometer = currentOdometer
            lastReliableTimestamp = record.timestamp
            state.resetForStartRecord()
        }

        // 3. 离群检测
        return markOutliers(results)
    }

    // ========================================================
    // 统计汇总
    // ========================================================

    @JvmOverloads
    fun calculateSummary(
        allRecords: List<FuelRecord>,
        vehicle: Vehicle? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): StatisticsSummary {
        val resultsById = calculateMap(allRecords, vehicle)
        val deliveryDate = vehicle?.deliveryDate?.takeIf { it > 0L }

        val visibleRecords = allRecords.asSequence()
            .filter { isFuelVisible(it, deliveryDate) }
            .sortedWith(RECORD_COMPARATOR)
            .toList()

        val periodRecords = visibleRecords.asSequence()
            .filter {
                if (startTime != null && it.timestamp < startTime) return@filter false
                if (endTime != null && it.timestamp >= endTime) return@filter false
                true
            }
            .toList()

        val rangeResults = periodRecords.mapNotNull { resultsById[it.id] }

        // 未形成油耗结果的记录（无里程 / 未满箱闭合 / 被硬边界拒绝等）
        val pendingRecords = periodRecords
            .filter { resultsById[it.id] == null }
            .sortedByDescending { it.timestamp }

        // 完全没填里程的记录数
        val missingOdometerCount = periodRecords.count { it.odometer == null }

        val totalCount = periodRecords.size
        val totalVolume = periodRecords.sumOf { safeFuelVolume(it) }
        val totalMissedVolume = periodRecords.sumOf { safeMissedVolume(it) }
        val totalCost = periodRecords.sumOf { safeMoney(it.actualPaidAmount) }
        val averagePrice = if (totalVolume > 0.0) totalCost / totalVolume else null

        val totalDistance = rangeResults.sumOf { it.distance }
        val totalFuel = rangeResults.sumOf { it.fuelUsed }

        val reliableResults = rangeResults.filter(::isReliable)
        val reliableDistance = reliableResults.sumOf { it.distance }
        val reliableFuel = reliableResults.sumOf { it.fuelUsed }

        val averageConsumption = if (reliableDistance > 0.0) {
            reliableFuel / reliableDistance * 100.0
        } else null

        val robust = robustWeightedConsumption(reliableResults)
        val weightedMean = weightedArithmeticMean(reliableResults)

        val odometerSpanText = if (rangeResults.isNotEmpty()) {
            val minOdo = rangeResults.minOf { it.startOdometer }
            val maxOdo = rangeResults.maxOf { it.endOdometer }
            "%.0f - %.0f km".format(Locale.US, minOdo, maxOdo)
        } else {
            "--"
        }

        val configuredInitialOdometer = vehicle?.initialOdometer?.takeIf { it.isFinite() && it > 0.0 }
        val tankCapacity = vehicle?.tankCapacity?.takeIf { it.isFinite() && it > 0.0 }

        val firstAnchorIndex = findFirstAnchor(
            records = visibleRecords,
            tankCapacity = tankCapacity,
            configuredInitialOdometer = configuredInitialOdometer
        )?.index

        val firstAnchorId = firstAnchorIndex?.let { visibleRecords.getOrNull(it)?.id }

        val unclosedCount = periodRecords.count { record ->
            record.isFull && record.id != firstAnchorId && resultsById[record.id] == null
        }

        return StatisticsSummary(
            totalCount = totalCount,
            totalVolume = totalVolume,
            totalMissedVolume = totalMissedVolume,
            totalCost = totalCost,
            averagePrice = averagePrice,
            totalDistance = totalDistance,
            totalFuel = totalFuel,
            averageConsumption = averageConsumption,
            odometerSpanText = odometerSpanText,
            unclosedCount = unclosedCount,
            rangeResults = rangeResults,
            pendingRecords = pendingRecords,
            missingOdometerCount = missingOdometerCount,
            robustAverageConsumption = robust,
            weightedAverageConsumption = weightedMean,
            reliableResultCount = reliableResults.size,
            outlierCount = rangeResults.count { it.isOutlier }
        )
    }

    private fun isReliable(result: ConsumptionResult): Boolean {
        return !result.isOutlier && (result.quality?.confidence ?: IntervalConfidence.HIGH).atLeast(IntervalConfidence.LOW)
    }

    // ========================================================
    // 锚点查找与辅助
    // ========================================================

    private data class AnchorInfo(
        val index: Int,
        val odometer: Double,
        val isEstimated: Boolean
    )

    private fun findFirstAnchor(
        records: List<FuelRecord>,
        tankCapacity: Double?,
        configuredInitialOdometer: Double?
    ): AnchorInfo? {
        for (index in records.indices) {
            val record = records[index]
            if (!isSuitableAsAnchor(record = record, tankCapacity = tankCapacity)) continue

            val odometer = validOdometer(record)
            if (odometer != null) {
                return AnchorInfo(index = index, odometer = odometer, isEstimated = false)
            }
            if (configuredInitialOdometer != null) {
                return AnchorInfo(index = index, odometer = configuredInitialOdometer, isEstimated = true)
            }
        }
        return null
    }

    private fun isSuitableAsAnchor(record: FuelRecord, tankCapacity: Double?): Boolean {
        if (!record.isFull || !hasValidFuelVolume(record)) return false
        if (isSeverelyOversizedVolume(volume = record.volume, tankCapacity = tankCapacity)) return false
        if (isRemainingFuelSeverelyInconsistent(record = record, tankCapacity = tankCapacity)) return false
        return true
    }

    // ========================================================
    // 区间状态累计
    // ========================================================

    private class SegmentState {
        var pendingFuel: Double = 0.0; private set
        var processedRecordCount: Int = 0; private set
        var missingOdometerCount: Int = 0; private set
        var missedFuelUnrecordedCount: Int = 0; private set
        var missedFuelRecordedVolume: Double = 0.0; private set
        var middlePartialFuelingCount: Int = 0; private set
        var invalidFuelRecordCount: Int = 0; private set
        var odometerAnomalyCount: Int = 0; private set
        var inconsistentRemainingFuelCount: Int = 0; private set
        var missedOdometerProvidedCount: Int = 0; private set
        var oversizedSingleFuelCount: Int = 0; private set
        var suspiciousOdometerJumpCount: Int = 0; private set

        fun resetForStartRecord() {
            pendingFuel = 0.0
            processedRecordCount = 1
            missingOdometerCount = 0
            missedFuelUnrecordedCount = 0
            missedFuelRecordedVolume = 0.0
            middlePartialFuelingCount = 0
            invalidFuelRecordCount = 0
            odometerAnomalyCount = 0
            inconsistentRemainingFuelCount = 0
            missedOdometerProvidedCount = 0
            oversizedSingleFuelCount = 0
            suspiciousOdometerJumpCount = 0
        }

        fun recordAnchorQualityFlags(record: FuelRecord, tankCapacity: Double?) {
            if (tankCapacity == null || tankCapacity <= 0.0) return

            val volume = record.volume
            if (volume.isFinite() && volume > 0.0 && volume > tankCapacity * SINGLE_FUEL_OVER_TANK_RATIO) {
                oversizedSingleFuelCount++
            }

            val rf = record.remainingFuel
            if (record.isFull && rf != null && rf.isFinite() && rf > 0.0 &&
                rf < tankCapacity * REMAINING_FUEL_CONSISTENCY_LOW &&
                rf >= tankCapacity * REMAINING_FUEL_SEVERE_LOW
            ) {
                inconsistentRemainingFuelCount++
            }
        }

        fun addCurrentFuel(record: FuelRecord) {
            pendingFuel = addFuelSafely(pendingFuel, safeFuelVolume(record))
        }

        fun addMissedFuel(record: FuelRecord) {
            pendingFuel = addFuelSafely(pendingFuel, safeMissedVolume(record))
        }

        fun addRecordQualityFlags(record: FuelRecord, tankCapacity: Double?) {
            processedRecordCount++

            if (validOdometer(record) == null) missingOdometerCount++
            if (isMissedFuelUnrecorded(record)) missedFuelUnrecordedCount++

            val missedVolumeSafe = safeMissedVolume(record)
            val missedSevere = isSeverelyOversizedVolume(volume = missedVolumeSafe, tankCapacity = tankCapacity)
            if (!missedSevere) missedFuelRecordedVolume += missedVolumeSafe

            if (!hasValidFuelVolume(record)) invalidFuelRecordCount++

            if (record.hasMissedRecord) {
                val missedOdometer = record.missedOdometer
                if (missedOdometer != null && missedOdometer.isFinite() && missedOdometer > 0.0) {
                    missedOdometerProvidedCount++
                }
            }

            if (tankCapacity != null && tankCapacity > 0.0) {
                val remainingFuel = record.remainingFuel
                if (remainingFuel != null && remainingFuel.isFinite()) {
                    if (remainingFuel < 0.0 || remainingFuel > tankCapacity * REMAINING_FUEL_CONSISTENCY_HIGH) {
                        inconsistentRemainingFuelCount++
                    } else if (record.isFull && remainingFuel > 0.0 && remainingFuel < tankCapacity * REMAINING_FUEL_CONSISTENCY_LOW) {
                        inconsistentRemainingFuelCount++
                    }
                }

                val volume = record.volume
                if (volume.isFinite() && volume > 0.0 && volume > tankCapacity * SINGLE_FUEL_OVER_TANK_RATIO) {
                    oversizedSingleFuelCount++
                }

                val missedVolume = safeMissedVolume(record)
                if (missedVolume > 0.0 && missedVolume > tankCapacity * SINGLE_FUEL_OVER_TANK_RATIO) {
                    oversizedSingleFuelCount++
                }
            }
        }

        fun markAsMiddlePartialFueling() { middlePartialFuelingCount++ }
        fun markOdometerAnomaly() { odometerAnomalyCount++ }
        fun markSuspiciousOdometerJump() { suspiciousOdometerJumpCount++ }
    }

    // ========================================================
    // 质量评分
    // ========================================================

    private fun buildQuality(
        state: SegmentState,
        startOdometerEstimated: Boolean,
        distance: Double,
        tankCapacity: Double?,
        fuelType: String?,
        consumption: Double
    ): IntervalQuality {
        var totalPenalty = 0.0

        if (startOdometerEstimated) {
            totalPenalty += PENALTY_ESTIMATED_START
        }

        totalPenalty += min(PENALTY_MISSING_ODOMETER_CAP, PENALTY_MISSING_ODOMETER_PER * state.missingOdometerCount)

        val rawMissedPenalty = PENALTY_MISSED_FUEL_UNRECORDED_PER * state.missedFuelUnrecordedCount
        val relief = RELIEF_MISSED_ODOMETER_PER * state.missedOdometerProvidedCount
        totalPenalty += min(PENALTY_MISSED_FUEL_UNRECORDED_CAP, (rawMissedPenalty - relief).coerceAtLeast(0.0))

        if (state.missedFuelRecordedVolume > 0.0) {
            val penalty = if (tankCapacity != null && tankCapacity > 0.0) {
                min(PENALTY_MISSED_FUEL_RECORDED_CAP, state.missedFuelRecordedVolume / tankCapacity * PENALTY_MISSED_FUEL_RECORDED_CAP)
            } else {
                min(0.10, state.missedFuelRecordedVolume * 0.01)
            }
            totalPenalty += penalty
        }

        totalPenalty += min(PENALTY_MIDDLE_PARTIAL_CAP, PENALTY_MIDDLE_PARTIAL_PER * state.middlePartialFuelingCount)
        totalPenalty += min(PENALTY_INVALID_FUEL_CAP, PENALTY_INVALID_FUEL_PER * state.invalidFuelRecordCount)
        totalPenalty += min(PENALTY_ODOMETER_ANOMALY_CAP, PENALTY_ODOMETER_ANOMALY_PER * state.odometerAnomalyCount)
        totalPenalty += min(PENALTY_SUSPICIOUS_JUMP_CAP, PENALTY_SUSPICIOUS_JUMP_PER * state.suspiciousOdometerJumpCount)
        totalPenalty += min(PENALTY_INCONSISTENT_REMAINING_CAP, PENALTY_INCONSISTENT_REMAINING_PER * state.inconsistentRemainingFuelCount)
        totalPenalty += min(PENALTY_OVERSIZED_CAP, PENALTY_OVERSIZED_PER * state.oversizedSingleFuelCount)

        totalPenalty += calculateSoftRangePenalty(consumption = consumption, soft = softRangeFor(fuelType))

        if (distance < 100.0) {
            val ratio = (100.0 - distance.coerceAtLeast(0.0)) / 100.0
            totalPenalty += PENALTY_SHORT_DISTANCE_BASE * ratio
        }

        val clampedScore = (1.0 - min(totalPenalty, MAX_TOTAL_PENALTY)).coerceIn(MIN_SCORE, 1.0)

        val confidence = when {
            clampedScore >= CONFIDENCE_HIGH_THRESHOLD -> IntervalConfidence.HIGH
            clampedScore >= CONFIDENCE_MEDIUM_THRESHOLD -> IntervalConfidence.MEDIUM
            clampedScore >= CONFIDENCE_LOW_THRESHOLD -> IntervalConfidence.LOW
            else -> IntervalConfidence.UNRELIABLE
        }

        return IntervalQuality(
            startOdometerEstimated = startOdometerEstimated,
            missingOdometerCount = state.missingOdometerCount,
            missedFuelUnrecordedCount = state.missedFuelUnrecordedCount,
            missedFuelRecordedVolume = state.missedFuelRecordedVolume,
            middlePartialFuelingCount = state.middlePartialFuelingCount,
            intervalRecordCount = state.processedRecordCount,
            invalidFuelRecordCount = state.invalidFuelRecordCount,
            odometerAnomalyCount = state.odometerAnomalyCount,
            inconsistentRemainingFuelCount = state.inconsistentRemainingFuelCount,
            oversizedSingleFuelCount = state.oversizedSingleFuelCount,
            suspiciousOdometerJumpCount = state.suspiciousOdometerJumpCount,
            confidenceScore = clampedScore,
            confidence = confidence,
            exclusionReason = null
        )
    }

    private fun calculateSoftRangePenalty(consumption: Double, soft: SoftRange): Double {
        if (!consumption.isFinite()) return SOFT_RANGE_PENALTY_CAP

        return when {
            consumption > soft.max -> {
                val ratio = (consumption - soft.max) / soft.max.coerceAtLeast(0.1)
                min(SOFT_RANGE_PENALTY_CAP, ratio * SOFT_RANGE_PENALTY_CAP)
            }
            consumption < soft.min -> {
                val ratio = (soft.min - consumption) / soft.min.coerceAtLeast(0.1)
                min(SOFT_RANGE_PENALTY_CAP, ratio * SOFT_RANGE_PENALTY_CAP)
            }
            else -> 0.0
        }
    }

    // ========================================================
    // MAD 离群检测
    // ========================================================

    private fun markOutliers(results: List<ConsumptionResult>): List<ConsumptionResult> {
        if (results.size <= MIN_MAD_SAMPLE_COUNT) return results

        val halfWindow = LOCAL_MAD_WINDOW / 2

        return results.mapIndexed { index, result ->
            val start = max(0, index - halfWindow)
            val end = min(results.size, index + halfWindow + 1)

            val context = ArrayList<Double>((end - start - 1).coerceAtLeast(0))
            for (i in start until end) {
                if (i == index) continue
                val value = results[i].consumption
                if (value.isFinite()) context.add(value)
            }

            val bounds = robustBounds(context)

            if (bounds != null && (result.consumption < bounds.lower || result.consumption > bounds.upper)) {
                val oldQuality = result.quality ?: IntervalQuality(
                    confidenceScore = if (result.isEstimated) 0.50 else 1.0,
                    confidence = if (result.isEstimated) IntervalConfidence.MEDIUM else IntervalConfidence.HIGH
                )

                result.copy(
                    isOutlier = true,
                    quality = oldQuality.copy(
                        confidenceScore = 0.05,
                        confidence = IntervalConfidence.UNRELIABLE,
                        exclusionReason = "OUTLIER_LOCAL_MAD"
                    )
                )
            } else {
                result
            }
        }
    }

    // ========================================================
    // 统计加权算法
    // ========================================================

    private fun robustWeightedConsumption(results: List<ConsumptionResult>): Double? {
        val kept = results.filter {
            isReliable(it) && it.distance.isFinite() && it.distance > 0.0 && it.consumption.isFinite()
        }
        if (kept.isEmpty()) return null

        val weighted = kept.asSequence()
            .map { result ->
                val confidence = (result.quality?.confidenceScore ?: if (result.isEstimated) 0.50 else 1.0).coerceIn(0.05, 1.0)
                val distanceWeight = sqrt(result.distance.coerceAtLeast(1.0))
                WeightedValue(value = result.consumption, weight = confidence * distanceWeight)
            }
            .filter { it.weight.isFinite() && it.weight > 0.0 }
            .sortedBy { it.value }
            .toList()

        if (weighted.isEmpty()) return null

        val totalWeight = weighted.sumOf { it.weight }
        if (!totalWeight.isFinite() || totalWeight <= 0.0) return null

        val halfWeight = totalWeight / 2.0
        var accumulated = 0.0

        for ((value, weight) in weighted) {
            accumulated += weight
            if (accumulated >= halfWeight) return value
        }

        return weighted.last().value
    }

    private fun weightedArithmeticMean(
        results: List<ConsumptionResult>
    ): Double? {
        val kept = results.filter {
            isReliable(it) &&
                    it.distance.isFinite() &&
                    it.distance > 0.0 &&
                    it.consumption.isFinite()
        }
        if (kept.isEmpty()) {
            return null
        }
        var totalWeight = 0.0
        var weightedSum = 0.0
        for ((_, _, _, distance, _, consumption, isEstimated, _, quality) in kept) {
            val confidence = (
                    quality?.confidenceScore
                        ?: if (isEstimated) 0.50 else 1.0
                    ).coerceIn(0.05, 1.0)
            val distanceWeight = distance.coerceAtLeast(1.0)
            val weight = confidence * distanceWeight
            if (!weight.isFinite() || weight <= 0.0) {
                continue
            }
            totalWeight += weight
            weightedSum += consumption * weight
        }
        if (!totalWeight.isFinite() || totalWeight <= 0.0) {
            return null
        }
        return weightedSum / totalWeight
    }

    private data class WeightedValue(val value: Double, val weight: Double)

    // ========================================================
    // MAD 数学辅助
    // ========================================================

    private data class RobustBounds(
        val median: Double,
        val mad: Double,
        val lower: Double,
        val upper: Double
    )

    private fun robustBounds(values: List<Double>): RobustBounds? {
        val finite = values.filter { it.isFinite() }.sorted()
        if (finite.size < MIN_MAD_SAMPLE_COUNT) return null

        val medianValue = median(finite) ?: return null
        val deviations = finite.map { abs(it - medianValue) }.sorted()
        val madValue = median(deviations) ?: return null

        val threshold = max(MAD_MULTIPLIER * madValue, MAD_THRESHOLD_FLOOR)

        return RobustBounds(
            median = medianValue,
            mad = madValue,
            lower = (medianValue - threshold).coerceAtLeast(MIN_HARD_CONSUMPTION),
            upper = (medianValue + threshold).coerceAtMost(MAX_HARD_CONSUMPTION)
        )
    }

    private fun median(sorted: List<Double>): Double? {
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    // ========================================================
    // 校验与判断辅助
    // ========================================================

    private fun isSeverelyOversizedVolume(volume: Double, tankCapacity: Double?): Boolean {
        if (tankCapacity == null || tankCapacity <= 0.0) return false
        if (!volume.isFinite() || volume <= 0.0) return false
        return volume > tankCapacity * SINGLE_FUEL_SEVERE_RATIO
    }

    private fun isRemainingFuelSeverelyInconsistent(record: FuelRecord, tankCapacity: Double?): Boolean {
        if (tankCapacity == null || tankCapacity <= 0.0 || !record.isFull) return false
        val remainingFuel = record.remainingFuel ?: return false
        if (!remainingFuel.isFinite()) return false

        if (remainingFuel < 0.0) return true
        if (remainingFuel > tankCapacity * REMAINING_FUEL_CONSISTENCY_HIGH) return true

        // 0 特殊处理：兼容"未填写=0"的旧数据
        if (remainingFuel == 0.0) return false

        return remainingFuel < tankCapacity * REMAINING_FUEL_SEVERE_LOW
    }

    private fun isSuspiciousOdometerJump(jump: Double, timeHours: Double): Boolean {
        if (!jump.isFinite() || jump <= 0.0) return false
        if (!timeHours.isFinite() || timeHours <= 0.0) {
            return jump > ODOMETER_JUMP_ABSOLUTE_LIMIT
        }
        val averageSpeed = jump / timeHours
        return averageSpeed > ODOMETER_JUMP_SUSPICIOUS_SPEED
    }

    private fun isFuelVisible(record: FuelRecord, deliveryDate: Long?): Boolean {
        if (record.isInitialRecord) return false
        if (deliveryDate != null && record.timestamp < deliveryDate) return false
        return true
    }

    private fun validOdometer(record: FuelRecord): Double? {
        val odometer = record.odometer ?: return null
        return if (odometer.isFinite() && odometer > 0.0) odometer else null
    }

    private fun hasValidFuelVolume(record: FuelRecord): Boolean {
        val volume = record.volume
        return volume.isFinite() && volume > 0.0
    }

    private fun safeFuelVolume(record: FuelRecord): Double {
        val volume = record.volume
        return if (volume.isFinite() && volume > 0.0) volume else 0.0
    }

    private fun safeMissedVolume(record: FuelRecord): Double {
        val missed = record.missedVolume ?: return 0.0
        return if (missed.isFinite() && missed > 0.0) missed else 0.0
    }

    private fun isMissedFuelUnrecorded(record: FuelRecord): Boolean {
        if (!record.hasMissedRecord) return false
        val missed = record.missedVolume ?: 0.0
        return !missed.isFinite() || missed <= 0.0
    }

    private fun addFuelSafely(current: Double, addition: Double): Double {
        if (!current.isFinite() || !addition.isFinite() || addition <= 0.0) return current
        val result = current + addition
        return if (result.isFinite()) result else Double.NaN
    }

    private fun calculateConsumption(fuel: Double, distance: Double): Double {
        if (!fuel.isFinite() || !distance.isFinite() || fuel <= 0.0 || distance <= 0.0) {
            return Double.NaN
        }
        return (fuel / distance) * 100.0
    }

    private fun safeMoney(value: Double): Double {
        return if (value.isFinite() && value >= 0.0) value else 0.0
    }

    /** 比较器：加入 odometer 与 id 确保同一时间戳下的排序绝对稳定 */
    private val RECORD_COMPARATOR = compareBy<FuelRecord> { it.timestamp }
        .thenBy { it.odometer ?: 0.0 }
        .thenBy { it.id }
}