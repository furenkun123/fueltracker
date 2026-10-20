package com.fueltracker.util

import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ============================================================
// 公共数据结构：保持与现有调用方兼容
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
    val isOutlier: Boolean = false,
    val startTimestamp: Long = 0L,
    val endTimestamp: Long = 0L
)

enum class IntervalConfidence(val level: Int) {
    UNRELIABLE(0), LOW(1), MEDIUM(2), HIGH(3);
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
    val oversizedMissedFuelCount: Int = 0,
    val suspiciousOdometerJumpCount: Int = 0,
    val hardBoundaryEventCount: Int = 0,
    /** 存在无法可靠估算的燃油缺口或燃油累计溢出。 */
    val hasUnresolvedFuelGap: Boolean = false,
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
    /** 所有闭合区间总里程（含低可信度/离群）。 */
    val totalDistance: Double,
    /** 所有闭合区间总燃油（含低可信度/离群）。 */
    val totalFuel: Double,
    /** 仅使用物理可用区间计算的平均油耗。 */
    val averageConsumption: Double?,
    val odometerSpanText: String,
    val unclosedCount: Int,
    val rejectedCount: Int = 0,
    val rangeResults: List<ConsumptionResult>,
    /** 未作为区间终点的记录，包括起点、半箱、未闭合及被拒绝记录。 */
    val pendingRecords: List<FuelRecord> = emptyList(),
    val missingOdometerCount: Int = 0,
    val robustAverageConsumption: Double? = null,
    val weightedAverageConsumption: Double? = null,
    val reliableResultCount: Int = 0,
    val outlierCount: Int = 0,
    val hasValidAnchor: Boolean = true,
    /** 原始输入包含非法数值或统计发生数值异常时为 false。 */
    val isCalculationValid: Boolean = true,  /** 统计运算是否发生数值异常，例如溢出或非有限结果。 */
    val hasInvalidRecords: Boolean = false /** 统计范围内是否存在关键字段无效的记录。 */
)

// ============================================================
// FuelCalculator V4.9
//
// 设计原则：
// 1. 仅由有效满箱记录闭合区间；不使用可撤销但未保存完整快照的暂存闭合。
// 2. 每条记录的油量字段独立检查，严重异常油量不进入累计，并标记燃油缺口。
// 3. 拒绝区间闭合时，不把该次满箱的油量继续混进下一个区间；有效满箱可作为新锚点。
// 4. 里程基准只由被接受的里程更新；小幅回落不会降低普通区间中的可靠基准。
// 5. 统计对非法输入和非有限运算显式失效，不把非法数值静默当作有效零值。
// ============================================================

object FuelCalculator {

    private const val MIN_HARD_CONSUMPTION = 0.1
    private const val MAX_HARD_CONSUMPTION = 200.0

    private const val ODOMETER_JUMP_ABSOLUTE_LIMIT = 500.0
    private const val ODOMETER_JUMP_SUSPICIOUS_SPEED = 250.0
    private const val ODOMETER_MATCH_TOLERANCE = 20.0
    private const val REANCHOR_MIN_DROP = 20.0
    private const val SHORT_DISTANCE_MERGE_KM = 30.0

    private const val LOCAL_MAD_WINDOW = 11
    private const val MIN_MAD_SAMPLE_COUNT = 5
    private const val MAD_THRESHOLD_FLOOR = 0.35
    private const val MAD_MULTIPLIER = 3.0
    private const val MAD_CONSISTENCY_FACTOR = 1.4826
    private const val GLOBAL_MAD_SAMPLE_THRESHOLD = 15
    private const val RELATIVE_MAD_MIN_WIDTH = 0.35
    private const val PLUG_IN_MAD_MIN_WIDTH = 0.75

    private const val REMAINING_FUEL_CONSISTENCY_LOW = 0.90
    private const val REMAINING_FUEL_CONSISTENCY_HIGH = 1.05
    private const val REMAINING_FUEL_SEVERE_LOW = 0.70

    private const val SINGLE_FUEL_OVER_TANK_RATIO = 1.15
    private const val SINGLE_FUEL_SEVERE_RATIO = 1.50

    private const val SOFT_RANGE_PENALTY_CAP = 0.30
    private const val MAX_TOTAL_PENALTY = 0.80

    private const val PENALTY_ESTIMATED_START = 0.30
    private const val PENALTY_MISSING_ODOMETER_PER = 0.06
    private const val PENALTY_MISSING_ODOMETER_CAP = 0.25
    private const val PENALTY_MISSED_FUEL_UNRECORDED_PER = 0.20
    private const val PENALTY_MISSED_FUEL_UNRECORDED_CAP = 0.50
    private const val RELIEF_MISSED_ODOMETER_PER = 0.05
    private const val PENALTY_MISSED_FUEL_RECORDED_CAP = 0.15
    private const val PENALTY_MIDDLE_PARTIAL_PER = 0.02
    private const val PENALTY_MIDDLE_PARTIAL_CAP = 0.10
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
    private const val PENALTY_HARD_BOUNDARY = 0.40

    private const val CONFIDENCE_HIGH_THRESHOLD = 0.80
    private const val CONFIDENCE_MEDIUM_THRESHOLD = 0.55
    private const val CONFIDENCE_LOW_THRESHOLD = 0.30

    const val REASON_NO_ANCHOR = "NO_ANCHOR"
    const val REASON_VOLUME_SEVERE = "VOLUME_SEVERE"
    const val REASON_REMAINING_SEVERE = "REMAINING_SEVERE"
    const val REASON_VOLUME_INVALID = "VOLUME_INVALID"
    const val REASON_NO_ODOMETER = "NO_ODOMETER"
    const val REASON_JUMP = "JUMP"
    const val REASON_SPIKE = "SPIKE"
    const val REASON_ROLLBACK = "ROLLBACK"
    const val REASON_BASELINE_DIP = "BASELINE_DIP"
    const val REASON_ODOMETER_ANOMALY = "ODOMETER_ANOMALY"
    const val REASON_SHORT_DISTANCE = "SHORT_DISTANCE"
    const val REASON_HARD_BOUNDARY = "HARD_BOUNDARY"
    const val REASON_ESTIMATED_DISTANCE = "ESTIMATED_DISTANCE"
    const val REASON_OUTLIER = "OUTLIER_MAD"
    const val REASON_FUEL_OVERFLOW = "FUEL_OVERFLOW"

    private enum class OdoClass {
        MISSING, ACCEPT, SMALL_DIP, HIGH_SPIKE, SUSPICIOUS_JUMP,
        LOW_CLUSTER, LOW_SPIKE, UNCONFIRMED_DIP, BELOW_START, BELOW_START_REANCHOR
    }

    private data class SoftRange(val min: Double, val max: Double)

    private data class Analysis(
        val visible: List<FuelRecord>,
        val results: List<ConsumptionResult>,
        val rejected: Map<Int, String>,
        val openAnchorIndex: Int?,
        val hasValidAnchor: Boolean
    )

    private data class AnchorInfo(val index: Int, val odometer: Double, val isEstimated: Boolean)
    private data class WeightedValue(val value: Double, val weight: Double)
    private data class RobustBounds(val median: Double, val mad: Double, val lower: Double, val upper: Double)

    // --------------------------------------------------------
    // 对外接口
    // --------------------------------------------------------

    @JvmOverloads
    fun latestReliableConsumption(records: List<FuelRecord>, vehicle: Vehicle? = null): Double? =
        calculateAll(records, vehicle).lastOrNull(::isReliable)?.consumption

    @JvmOverloads
    fun calculateMap(records: List<FuelRecord>, vehicle: Vehicle? = null): Map<Long, ConsumptionResult> {
        val map = LinkedHashMap<Long, ConsumptionResult>()
        for (result in calculateAll(records, vehicle)) {
            if (result.recordId > 0L) map[result.recordId] = result
        }
        return map
    }

    @JvmOverloads
    fun calculateAll(records: List<FuelRecord>, vehicle: Vehicle? = null): List<ConsumptionResult> =
        analyze(records, vehicle).results

    @JvmOverloads
    fun calculateSummary(
        allRecords: List<FuelRecord>,
        vehicle: Vehicle? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): StatisticsSummary {
        val analysis = analyze(allRecords, vehicle)
        val visible = analysis.visible
        val tankCapacity = vehicle?.tankCapacity?.takeIf { it.isFinite() && it > 0.0 }

        fun inPeriod(timestamp: Long): Boolean =
            (startTime == null || timestamp >= startTime) && (endTime == null || timestamp < endTime)

        val periodRecords = visible.filter { inPeriod(it.timestamp) }
        val rangeResults = analysis.results.filter {
            inPeriod(it.startTimestamp) && inPeriod(it.endTimestamp)
        }
        val resultRecordIds = analysis.results.map { it.recordId }.toSet()
        val pendingRecords = periodRecords
            .filter { it.id !in resultRecordIds || it.id == 0L }
            .sortedByDescending { it.timestamp }

        val missingOdometerCount = periodRecords.count { validOdometer(it) == null }
        val totalCount = periodRecords.size
        var isCalculationValid = true
        var hasInvalidRecords = false

        fun safeAdd(a: Double, b: Double): Double {
            if (!isCalculationValid) return 0.0

            if (!a.isFinite() || !b.isFinite()) {
                isCalculationValid = false
                return 0.0
            }
            val sum = a + b
            if (!sum.isFinite()) {
                isCalculationValid = false
                return 0.0
            }
            return sum
        }

        var totalVolume = 0.0
        var totalMissedVolume = 0.0
        var totalCost = 0.0
        var pricedVolume = 0.0
        var pricedCost = 0.0

        for (record in periodRecords) {
            val rawVolume = record.volume
            val volumeValid = isValidVolume(rawVolume, tankCapacity)

            val rawMissedVolume = safeMissedVolume(record)
            val missedVolumeValid = !record.hasMissedRecord ||
                    isValidVolume(rawMissedVolume, tankCapacity)

            val costValid = isValidMoney(record.actualPaidAmount)

            // 新增：检查其他关键字段
            val odometerValid = record.odometer == null || (record.odometer.isFinite() && record.odometer > 0.0)
            val remainingValid = record.remainingFuel == null || (record.remainingFuel.isFinite() && record.remainingFuel >= 0.0)
            val missedOdoValid = !record.hasMissedRecord || (record.missedOdometer != null && record.missedOdometer.isFinite() && record.missedOdometer > 0.0)

            if (!volumeValid || !missedVolumeValid || !costValid || !odometerValid || !remainingValid || !missedOdoValid) {
                hasInvalidRecords = true
            }

            val volume = if (volumeValid) rawVolume else 0.0
            val missedVolume = if (missedVolumeValid) rawMissedVolume else 0.0
            val cost = if (costValid) record.actualPaidAmount else 0.0

            totalVolume = safeAdd(totalVolume, volume)
            totalMissedVolume = safeAdd(totalMissedVolume, missedVolume)
            totalCost = safeAdd(totalCost, cost)

            // 只有同时合法的金额和油量才能参与均价计算
            if (volumeValid && costValid) {
                pricedVolume = safeAdd(pricedVolume, volume)
                pricedCost = safeAdd(pricedCost, cost)
            }
        }

        var averagePrice: Double? = null
        if (pricedVolume > 0.0) {
            val price = pricedCost / pricedVolume
            if (price.isFinite()) averagePrice = price else isCalculationValid = false
        }

        var totalDistance = 0.0
        var totalFuel = 0.0
        var physicalDistance = 0.0
        var physicalFuel = 0.0
        for (result in rangeResults) {
            totalDistance = safeAdd(totalDistance, result.distance)
            totalFuel = safeAdd(totalFuel, result.fuelUsed)
            if (isPhysicallyUsable(result)) {
                physicalDistance = safeAdd(physicalDistance, result.distance)
                physicalFuel = safeAdd(physicalFuel, result.fuelUsed)
            }
        }

        var averageConsumption: Double? = null
        if (physicalDistance > 0.0) {
            val value = physicalFuel / physicalDistance * 100.0
            if (value.isFinite()) averageConsumption = value else isCalculationValid = false
        }

        val reliableResults = rangeResults.filter(::isReliable)
        val robust = robustWeightedConsumption(reliableResults) { isCalculationValid = false }
        val weightedMean = weightedArithmeticMean(reliableResults) { isCalculationValid = false }
        val odometerSpanText = if (rangeResults.isNotEmpty()) {
            "%.0f - %.0f km".format(
                Locale.US,
                rangeResults.minOf { it.startOdometer },
                rangeResults.maxOf { it.endOdometer }
            )
        } else "--"

        val rejectedCount = analysis.rejected.keys.count { index ->
            index in visible.indices && inPeriod(visible[index].timestamp)
        }
        val anchorIndex = analysis.openAnchorIndex
        val unclosedCount = if (
            analysis.hasValidAnchor && anchorIndex != null &&
            anchorIndex < visible.lastIndex && inPeriod(visible[anchorIndex].timestamp)
        ) 1 else 0

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
            rejectedCount = rejectedCount,
            rangeResults = rangeResults,
            pendingRecords = pendingRecords,
            missingOdometerCount = missingOdometerCount,
            robustAverageConsumption = robust,
            weightedAverageConsumption = weightedMean,
            reliableResultCount = reliableResults.size,
            outlierCount = rangeResults.count { it.isOutlier },
            hasValidAnchor = analysis.hasValidAnchor,
            isCalculationValid = isCalculationValid,
            hasInvalidRecords = hasInvalidRecords
        )
    }

    // --------------------------------------------------------
    // 主算法
    // --------------------------------------------------------

    private fun analyze(records: List<FuelRecord>, vehicle: Vehicle?): Analysis {
        if (records.isEmpty()) return Analysis(emptyList(), emptyList(), emptyMap(), null, false)

        val deliveryDate = vehicle?.deliveryDate?.takeIf { it > 0L }
        val visibleRecords = records.asSequence()
            .filter { isFuelVisible(it, deliveryDate) }
            .sortedWith(RECORD_COMPARATOR)
            .toList()
        if (visibleRecords.isEmpty()) {
            return Analysis(emptyList(), emptyList(), emptyMap(), null, false)
        }

        val nextValidIndex = IntArray(visibleRecords.size) { -1 }
        val priorValidOdometer = BooleanArray(visibleRecords.size)
        var nextIndex = -1
        var hasPriorOdometer = false
        for (i in visibleRecords.indices) {
            priorValidOdometer[i] = hasPriorOdometer
            if (validOdometer(visibleRecords[i]) != null) hasPriorOdometer = true
        }
        for (i in visibleRecords.indices.reversed()) {
            nextValidIndex[i] = nextIndex
            if (validOdometer(visibleRecords[i]) != null) nextIndex = i
        }

        val tankCapacity = vehicle?.tankCapacity?.takeIf { it.isFinite() && it > 0.0 }
        val initialOdometer = vehicle?.initialOdometer?.takeIf { it.isFinite() && it > 0.0 }
        val firstAnchor = findFirstAnchor(
            visibleRecords, nextValidIndex, priorValidOdometer, tankCapacity, initialOdometer
        )

        if (firstAnchor == null) {
            val rejected = HashMap<Int, String>()
            visibleRecords.forEachIndexed { index, record ->
                if (record.isFull) rejected[index] = REASON_NO_ANCHOR
            }
            return Analysis(visibleRecords, emptyList(), rejected, null, false)
        }

        val results = ArrayList<ConsumptionResult>()
        val rejected = HashMap<Int, String>()
        val state = SegmentState()

        var segmentStartOdometer = firstAnchor.odometer
        var segmentStartEstimated = firstAnchor.isEstimated
        var segmentStartTimestamp = visibleRecords[firstAnchor.index].timestamp
        var segmentStartIndex = firstAnchor.index
        var lastReliableOdometer = segmentStartOdometer
        var lastReliableTimestamp = segmentStartTimestamp

        state.resetForStartRecord()
        state.recordAnchorBaselineFlags(visibleRecords[firstAnchor.index], tankCapacity)

        // 统一的重锚操作：结束旧区间，把当前有效满箱作为新区间起点。
        fun reanchor(index: Int, record: FuelRecord, odometer: Double) {
            segmentStartOdometer = odometer
            segmentStartEstimated = false
            segmentStartTimestamp = record.timestamp
            segmentStartIndex = index
            lastReliableOdometer = odometer
            lastReliableTimestamp = record.timestamp
            state.resetForStartRecord()
            state.recordAnchorBaselineFlags(record, tankCapacity)
        }

        for (index in (firstAnchor.index + 1) until visibleRecords.size) {
            val record = visibleRecords[index]
            val currentOdometer = validOdometer(record)
            val odoClass = classifyOdometer(
                records = visibleRecords,
                index = index,
                nextValidIndexArray = nextValidIndex,
                current = currentOdometer,
                segmentStart = segmentStartOdometer,
                lastReliable = lastReliableOdometer,
                lastTimestamp = lastReliableTimestamp,
                record = record,
                tankCapacity = tankCapacity
            )

            // 明确的里程表回退/换表：只允许合格满箱记录重建锚点。
            if (odoClass == OdoClass.BELOW_START_REANCHOR && currentOdometer != null &&
                isSuitableAsAnchor(record, tankCapacity)
            ) {
                reanchor(index, record, currentOdometer)
                continue
            }

            // 仅采纳正常单调前进的里程。小幅回落可以参与当前闭合判断，但不降低后续分类基准。
            if (currentOdometer != null && odoClass == OdoClass.ACCEPT) {
                lastReliableOdometer = currentOdometer
                lastReliableTimestamp = record.timestamp
            }

            val currentFuelSevere = isSeverelyOversizedVolume(record.volume, tankCapacity)
            val missedFuelSevere = record.hasMissedRecord &&
                    record.missedVolume?.let { isSeverelyOversizedVolume(it, tankCapacity) } == true

            // 先登记质量信息，再按字段可采信性分别累计油量。
            state.addRecordQualityFlags(record, tankCapacity)

            when (odoClass) {
                OdoClass.SUSPICIOUS_JUMP -> state.markSuspiciousOdometerJump()
                OdoClass.HIGH_SPIKE, OdoClass.BELOW_START, OdoClass.LOW_CLUSTER,
                OdoClass.LOW_SPIKE, OdoClass.UNCONFIRMED_DIP -> state.markOdometerAnomaly()
                OdoClass.SMALL_DIP -> Unit
                else -> Unit
            }

            if (currentFuelSevere) {
                // 严重超量的当前油量被排除，但未知的真实加油量构成缺口。
                state.markUnresolvedFuelGap()
            } else if (hasValidFuelVolume(record)) {
                val currentVolume = safeFuelVolume(record)
                state.addFuel(currentVolume)
            } else {
                // 记录存在但油量字段无效：不能把缺失油量当成 0 后继续给出高可信结果。
                state.markUnresolvedFuelGap()
            }

            if (missedFuelSevere) {
                state.markUnresolvedFuelGap()
            } else {
                val missedVolume = safeMissedVolume(record)
                if (missedVolume > 0.0) {
                    state.addFuel(missedVolume)
                }
            }

            if (currentFuelSevere) {
                rejected[index] = REASON_VOLUME_SEVERE
                continue
            }
            if (isRemainingFuelSeverelyInconsistent(record, tankCapacity)) {
                state.markAsMiddlePartialFueling()
                rejected[index] = REASON_REMAINING_SEVERE
                continue
            }

            // 对任何满箱闭合尝试，先保证当前记录油量合法；不能让无效满箱静默进入闭合流程。
            if (!hasValidFuelVolume(record)) {
                rejected[index] = REASON_VOLUME_INVALID
                continue
            }

            if (!record.isFull) {
                state.markAsMiddlePartialFueling()
                continue
            }

            when (odoClass) {
                OdoClass.SUSPICIOUS_JUMP -> {
                    rejected[index] = REASON_JUMP
                    continue
                }
                OdoClass.HIGH_SPIKE -> {
                    rejected[index] = REASON_SPIKE
                    continue
                }
                OdoClass.BELOW_START -> {
                    rejected[index] = REASON_ROLLBACK
                    continue
                }
                OdoClass.LOW_CLUSTER, OdoClass.LOW_SPIKE, OdoClass.UNCONFIRMED_DIP -> {
                    rejected[index] = REASON_BASELINE_DIP
                    continue
                }
                OdoClass.MISSING -> {
                    rejected[index] = REASON_NO_ODOMETER
                    continue
                }
                OdoClass.ACCEPT, OdoClass.SMALL_DIP -> Unit
                OdoClass.BELOW_START_REANCHOR -> {
                    // 该分支通常已在前面处理；不合格记录不能作为新锚点。
                    rejected[index] = REASON_ROLLBACK
                    continue
                }
            }

            if (currentOdometer == null) {
                rejected[index] = REASON_NO_ODOMETER
                continue
            }

            val distance = currentOdometer - segmentStartOdometer
            if (!distance.isFinite() || distance <= 0.0) {
                state.markOdometerAnomaly()
                rejected[index] = REASON_ODOMETER_ANOMALY
                // 已被里程分类接受的满箱可以作为新的起点，避免重复里程污染后续累计。
                reanchor(index, record, currentOdometer)
                continue
            }

            if (state.hasFuelOverflow) {
                state.markUnresolvedFuelGap()
                rejected[index] = REASON_FUEL_OVERFLOW
                reanchor(index, record, currentOdometer)
                continue
            }

            val fuelUsed = state.pendingFuel
            val consumption = calculateConsumption(fuelUsed, distance)
            if (!consumption.isFinite() || consumption < MIN_HARD_CONSUMPTION ||
                consumption > MAX_HARD_CONSUMPTION
            ) {
                state.markHardBoundaryEvent()
                state.markUnresolvedFuelGap()
                rejected[index] = if (
                    consumption.isFinite() && consumption > MAX_HARD_CONSUMPTION &&
                    distance < SHORT_DISTANCE_MERGE_KM
                ) REASON_SHORT_DISTANCE else REASON_HARD_BOUNDARY
                // 关键修正：不把失败闭合的累计油量继续带进下一个区间。
                reanchor(index, record, currentOdometer)
                continue
            }

            val quality = buildQuality(
                state = state,
                startOdometerEstimated = segmentStartEstimated,
                distance = distance,
                tankCapacity = tankCapacity,
                fuelType = vehicle?.fuelType,
                consumption = consumption
            )
            results.add(
                ConsumptionResult(
                    recordId = record.id,
                    startOdometer = segmentStartOdometer,
                    endOdometer = currentOdometer,
                    distance = distance,
                    fuelUsed = fuelUsed,
                    consumption = consumption,
                    isEstimated = quality.confidence != IntervalConfidence.HIGH,
                    quality = quality,
                    startTimestamp = segmentStartTimestamp,
                    endTimestamp = record.timestamp
                )
            )

            // 成功闭合：当前满箱是下一段起点，当前记录油量不可重复计入下一段。
            reanchor(index, record, currentOdometer)
        }

        return Analysis(
            visible = visibleRecords,
            results = markOutliers(results, vehicle?.fuelType),
            rejected = rejected,
            openAnchorIndex = segmentStartIndex,
            hasValidAnchor = true
        )
    }

    // --------------------------------------------------------
    // 区间质量状态
    // --------------------------------------------------------

    private class SegmentState {
        var pendingFuel: Double = 0.0
            private set
        var hasFuelOverflow: Boolean = false
            private set
        private var fuelCompensation: Double = 0.0

        var missingOdometerCount: Int = 0
            private set
        var missedFuelUnrecordedCount: Int = 0
            private set
        var missedFuelRecordedVolume: Double = 0.0
            private set
        var middlePartialFuelingCount: Int = 0
            private set
        var processedRecordCount: Int = 0
            private set
        var invalidFuelRecordCount: Int = 0
            private set
        var odometerAnomalyCount: Int = 0
            private set
        var inconsistentRemainingFuelCount: Int = 0
            private set
        var missedOdometerProvidedCount: Int = 0
            private set
        var oversizedSingleFuelCount: Int = 0
            private set
        var oversizedMissedFuelCount: Int = 0
            private set
        var suspiciousOdometerJumpCount: Int = 0
            private set
        var hardBoundaryEventCount: Int = 0
            private set
        var hasUnresolvedFuelGap: Boolean = false
            private set

        fun resetForStartRecord() {
            pendingFuel = 0.0
            hasFuelOverflow = false
            fuelCompensation = 0.0
            missingOdometerCount = 0
            missedFuelUnrecordedCount = 0
            missedFuelRecordedVolume = 0.0
            middlePartialFuelingCount = 0
            processedRecordCount = 1
            invalidFuelRecordCount = 0
            odometerAnomalyCount = 0
            inconsistentRemainingFuelCount = 0
            missedOdometerProvidedCount = 0
            oversizedSingleFuelCount = 0
            oversizedMissedFuelCount = 0
            suspiciousOdometerJumpCount = 0
            hardBoundaryEventCount = 0
            hasUnresolvedFuelGap = false
        }

        fun recordAnchorBaselineFlags(record: FuelRecord, tankCapacity: Double?) {
            if (tankCapacity == null || tankCapacity <= 0.0) return
            val remaining = record.remainingFuel ?: return
            if (!record.isFull || !remaining.isFinite() || remaining <= 0.0) return
            if (remaining < tankCapacity * REMAINING_FUEL_CONSISTENCY_LOW &&
                remaining >= tankCapacity * REMAINING_FUEL_SEVERE_LOW
            ) {
                inconsistentRemainingFuelCount++
            }
        }

        fun addFuel(addition: Double) {
            if (!addition.isFinite() || addition <= 0.0) return
            val y = addition - fuelCompensation
            val sum = pendingFuel + y
            if (!sum.isFinite()) {
                hasFuelOverflow = true
                hasUnresolvedFuelGap = true
                return
            }
            fuelCompensation = (sum - pendingFuel) - y
            pendingFuel = sum
        }

        fun addRecordQualityFlags(record: FuelRecord, tankCapacity: Double?) {
            processedRecordCount++
            if (validOdometer(record) == null) missingOdometerCount++
            if (isMissedFuelUnrecorded(record)) missedFuelUnrecordedCount++

            val missedVolume = safeMissedVolume(record)
            if (missedVolume > 0.0) {
                if (!isSeverelyOversizedVolume(missedVolume, tankCapacity)) {
                    val next = missedFuelRecordedVolume + missedVolume
                    if (next.isFinite()) missedFuelRecordedVolume = next
                    else markUnresolvedFuelGap()
                } else if (record.hasMissedRecord) {
                    oversizedMissedFuelCount++
                }
            } else if (record.hasMissedRecord && record.missedVolume != null &&
                isSeverelyOversizedVolume(record.missedVolume, tankCapacity)
            ) {
                oversizedMissedFuelCount++
            }

            if (!hasValidFuelVolume(record)) invalidFuelRecordCount++

            val missedOdometer = record.missedOdometer
            if (isMissedFuelUnrecorded(record) && missedOdometer != null &&
                missedOdometer.isFinite() && missedOdometer > 0.0
            ) {
                missedOdometerProvidedCount++
            }

            if (tankCapacity == null || tankCapacity <= 0.0) return
            val remaining = record.remainingFuel
            if (remaining != null) {
                if (!remaining.isFinite() || remaining < 0.0 ||
                    remaining > tankCapacity * REMAINING_FUEL_CONSISTENCY_HIGH
                ) {
                    inconsistentRemainingFuelCount++
                } else if (record.isFull && remaining > 0.0 &&
                    remaining < tankCapacity * REMAINING_FUEL_CONSISTENCY_LOW
                ) {
                    inconsistentRemainingFuelCount++
                }
            }
            if (record.volume.isFinite() && record.volume > tankCapacity * SINGLE_FUEL_OVER_TANK_RATIO) {
                oversizedSingleFuelCount++
            }
        }

        fun markAsMiddlePartialFueling() { middlePartialFuelingCount++ }
        fun markOdometerAnomaly() { odometerAnomalyCount++ }
        fun markSuspiciousOdometerJump() { suspiciousOdometerJumpCount++ }
        fun markHardBoundaryEvent() { hardBoundaryEventCount++ }
        fun markUnresolvedFuelGap() { hasUnresolvedFuelGap = true }
    }

    private fun buildQuality(
        state: SegmentState,
        startOdometerEstimated: Boolean,
        distance: Double,
        tankCapacity: Double?,
        fuelType: String?,
        consumption: Double
    ): IntervalQuality {
        var penalty = 0.0
        if (startOdometerEstimated) penalty += PENALTY_ESTIMATED_START
        penalty += min(PENALTY_MISSING_ODOMETER_CAP, PENALTY_MISSING_ODOMETER_PER * state.missingOdometerCount)

        val missingFuelPenalty = PENALTY_MISSED_FUEL_UNRECORDED_PER * state.missedFuelUnrecordedCount
        val missingOdoRelief = RELIEF_MISSED_ODOMETER_PER * state.missedOdometerProvidedCount
        penalty += min(
            PENALTY_MISSED_FUEL_UNRECORDED_CAP,
            (missingFuelPenalty - missingOdoRelief).coerceAtLeast(0.0)
        )

        if (state.missedFuelRecordedVolume > 0.0) {
            val recordedPenalty = if (tankCapacity != null && tankCapacity > 0.0) {
                min(
                    PENALTY_MISSED_FUEL_RECORDED_CAP,
                    state.missedFuelRecordedVolume / tankCapacity * PENALTY_MISSED_FUEL_RECORDED_CAP
                )
            } else {
                min(0.10, state.missedFuelRecordedVolume * 0.01)
            }
            penalty += recordedPenalty
        }

        penalty += min(PENALTY_MIDDLE_PARTIAL_CAP, PENALTY_MIDDLE_PARTIAL_PER * state.middlePartialFuelingCount)
        penalty += min(PENALTY_INVALID_FUEL_CAP, PENALTY_INVALID_FUEL_PER * state.invalidFuelRecordCount)
        penalty += min(PENALTY_ODOMETER_ANOMALY_CAP, PENALTY_ODOMETER_ANOMALY_PER * state.odometerAnomalyCount)
        penalty += min(PENALTY_SUSPICIOUS_JUMP_CAP, PENALTY_SUSPICIOUS_JUMP_PER * state.suspiciousOdometerJumpCount)
        penalty += min(
            PENALTY_INCONSISTENT_REMAINING_CAP,
            PENALTY_INCONSISTENT_REMAINING_PER * state.inconsistentRemainingFuelCount
        )
        penalty += min(
            PENALTY_OVERSIZED_CAP,
            PENALTY_OVERSIZED_PER * (state.oversizedSingleFuelCount + state.oversizedMissedFuelCount)
        )
        if (state.hasUnresolvedFuelGap) penalty += PENALTY_HARD_BOUNDARY
        penalty += calculateSoftRangePenalty(consumption, softRangeFor(fuelType))
        if (distance < 100.0) {
            penalty += PENALTY_SHORT_DISTANCE_BASE * ((100.0 - distance.coerceAtLeast(0.0)) / 100.0)
        }

        val penaltyMillis = (penalty * 1000.0).roundToInt()
            .coerceIn(0, (MAX_TOTAL_PENALTY * 1000.0).roundToInt())
        var score = 1.0 - penaltyMillis / 1000.0
        var confidence = when {
            score >= CONFIDENCE_HIGH_THRESHOLD -> IntervalConfidence.HIGH
            score >= CONFIDENCE_MEDIUM_THRESHOLD -> IntervalConfidence.MEDIUM
            score >= CONFIDENCE_LOW_THRESHOLD -> IntervalConfidence.LOW
            else -> IntervalConfidence.UNRELIABLE
        }
        var exclusionReason: String? = null
        if (state.hasUnresolvedFuelGap) {
            confidence = IntervalConfidence.UNRELIABLE
            score = min(score, 0.20)
            exclusionReason = REASON_HARD_BOUNDARY
        }
        val soft = softRangeFor(fuelType)
        if (startOdometerEstimated && consumption < soft.min * 0.5) {
            confidence = IntervalConfidence.UNRELIABLE
            score = min(score, 0.20)
            if (exclusionReason == null) exclusionReason = REASON_ESTIMATED_DISTANCE
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
            oversizedMissedFuelCount = state.oversizedMissedFuelCount,
            suspiciousOdometerJumpCount = state.suspiciousOdometerJumpCount,
            hardBoundaryEventCount = state.hardBoundaryEventCount,
            hasUnresolvedFuelGap = state.hasUnresolvedFuelGap,
            confidenceScore = score,
            confidence = confidence,
            exclusionReason = exclusionReason
        )
    }

    // --------------------------------------------------------
    // 里程分类与锚点
    // --------------------------------------------------------

    private fun classifyOdometer(
        records: List<FuelRecord>,
        index: Int,
        nextValidIndexArray: IntArray,
        current: Double?,
        segmentStart: Double,
        lastReliable: Double,
        lastTimestamp: Long,
        record: FuelRecord,
        tankCapacity: Double?
    ): OdoClass {
        if (current == null) return OdoClass.MISSING

        val nextIndex = nextValidIndexArray[index]
        val next = if (nextIndex >= 0) validOdometer(records[nextIndex]) else null
        val next2Index = if (nextIndex >= 0) nextValidIndexArray[nextIndex] else -1
        val next2 = if (next2Index >= 0) validOdometer(records[next2Index]) else null

        if (current < segmentStart) {
            if (current < segmentStart - REANCHOR_MIN_DROP &&
                next != null && next < segmentStart - REANCHOR_MIN_DROP &&
                closer(next, current, segmentStart) && isSuitableAsAnchor(record, tankCapacity)
            ) {
                return OdoClass.BELOW_START_REANCHOR
            }
            return OdoClass.BELOW_START
        }

        if (current < lastReliable) {
            val drop = lastReliable - current
            if (drop <= ODOMETER_MATCH_TOLERANCE) return OdoClass.SMALL_DIP
            if (next == null) return OdoClass.UNCONFIRMED_DIP
            if (next >= lastReliable - ODOMETER_MATCH_TOLERANCE) return OdoClass.LOW_SPIKE
            if (closer(next, current, lastReliable)) return OdoClass.LOW_CLUSTER
            if (next2 != null && next2 >= lastReliable - ODOMETER_MATCH_TOLERANCE) return OdoClass.LOW_SPIKE
            return OdoClass.UNCONFIRMED_DIP
        }

        val jump = current - lastReliable
        val elapsedHours = (record.timestamp - lastTimestamp).toDouble() / 3_600_000.0
        if (jump > 0.0 && isSuspiciousOdometerJump(jump, elapsedHours)) {
            return OdoClass.SUSPICIOUS_JUMP
        }

        // 用后续两个有效里程辅助识别孤立高点，减少错误满箱闭合。
        val nextConfirmsLower = next != null && next < current - ODOMETER_MATCH_TOLERANCE &&
                closer(next, lastReliable, current)
        val next2ConfirmsLower = next2 != null && next2 < current - ODOMETER_MATCH_TOLERANCE &&
                closer(next2, lastReliable, current)
        if (nextConfirmsLower || next2ConfirmsLower) return OdoClass.HIGH_SPIKE

        return OdoClass.ACCEPT
    }

    private fun closer(value: Double, a: Double, b: Double): Boolean =
        abs(value - a) + ODOMETER_MATCH_TOLERANCE < abs(value - b)

    private fun findFirstAnchor(
        records: List<FuelRecord>,
        nextValidIndexArray: IntArray,
        hasPriorValidOdometer: BooleanArray,
        tankCapacity: Double?,
        configuredInitialOdometer: Double?
    ): AnchorInfo? {
        for (index in records.indices) {
            val record = records[index]
            if (!isSuitableAsAnchor(record, tankCapacity)) continue
            val odometer = validOdometer(record)
            if (odometer != null) {
                if (isIsolatedHighValue(records, nextValidIndexArray, index, odometer)) continue
                return AnchorInfo(index, odometer, false)
            }

            if (configuredInitialOdometer == null || hasPriorValidOdometer[index]) continue
            val nextIndex = nextValidIndexArray[index]
            val next = if (nextIndex >= 0) validOdometer(records[nextIndex]) else null
            if (next != null && configuredInitialOdometer >= next) continue
            if (isIsolatedHighValue(records, nextValidIndexArray, index, configuredInitialOdometer)) continue
            return AnchorInfo(index, configuredInitialOdometer, true)
        }
        return null
    }

    private fun isIsolatedHighValue(
        records: List<FuelRecord>,
        nextValidIndexArray: IntArray,
        index: Int,
        candidate: Double
    ): Boolean {
        val nextIndex = nextValidIndexArray[index]
        if (nextIndex < 0) return false
        val next = validOdometer(records[nextIndex]) ?: return false
        if (candidate - next <= REANCHOR_MIN_DROP) return false
        val next2Index = nextValidIndexArray[nextIndex]
        val next2 = if (next2Index >= 0) validOdometer(records[next2Index]) else null
        return !(next2 == null || next2 > candidate - ODOMETER_MATCH_TOLERANCE) && closer(next2, next, candidate)
    }

    private fun isSuitableAsAnchor(record: FuelRecord, tankCapacity: Double?): Boolean {
        if (!record.isFull || !hasValidFuelVolume(record)) return false
        if (isSeverelyOversizedVolume(record.volume, tankCapacity)) return false
        if (isRemainingFuelSeverelyInconsistent(record, tankCapacity)) return false
        return true
    }

    // --------------------------------------------------------
    // 离群检测与统计辅助
    // --------------------------------------------------------

    private fun markOutliers(results: List<ConsumptionResult>, fuelType: String?): List<ConsumptionResult> {
        val reliableIndices = results.indices.filter {
            isReliable(results[it]) && results[it].consumption.isFinite()
        }
        if (reliableIndices.size <= MIN_MAD_SAMPLE_COUNT) return results

        val reliableSamples = reliableIndices.map { results[it] }
        val useGlobalMad = reliableSamples.size < GLOBAL_MAD_SAMPLE_THRESHOLD
        val halfWindow = if (useGlobalMad) reliableSamples.size else LOCAL_MAD_WINDOW / 2
        val plugIn = isPlugIn(fuelType)
        val soft = softRangeFor(fuelType)
        val updated = results.toMutableList()

        for ((sampleIndex, result) in reliableSamples.withIndex()) {
            if (plugIn && result.consumption in soft.min..soft.max) continue
            val start = if (useGlobalMad) 0 else max(0, sampleIndex - halfWindow)
            val end = if (useGlobalMad) reliableSamples.size else min(reliableSamples.size, sampleIndex + halfWindow + 1)
            val context = ArrayList<Double>(end - start)
            for (cursor in start until end) {
                if (cursor != sampleIndex) context.add(reliableSamples[cursor].consumption)
            }
            val bounds = robustBounds(
                context,
                if (plugIn) PLUG_IN_MAD_MIN_WIDTH else RELATIVE_MAD_MIN_WIDTH
            ) ?: continue
            if (result.consumption < bounds.lower || result.consumption > bounds.upper) {
                val oldQuality = result.quality ?: IntervalQuality()
                updated[reliableIndices[sampleIndex]] = result.copy(
                    isOutlier = true,
                    quality = oldQuality.copy(exclusionReason = oldQuality.exclusionReason ?: REASON_OUTLIER)
                )
            }
        }
        return updated
    }

    private fun robustBounds(values: List<Double>, relativeMinWidth: Double): RobustBounds? {
        val finite = values.filter { it.isFinite() }.sorted()
        if (finite.size < MIN_MAD_SAMPLE_COUNT) return null
        val medianValue = median(finite) ?: return null
        val deviations = finite.map { abs(it - medianValue) }.sorted()
        val madValue = median(deviations) ?: return null
        val madWidth = MAD_MULTIPLIER * madValue * MAD_CONSISTENCY_FACTOR
        val minimumWidth = max(MAD_THRESHOLD_FLOOR, abs(medianValue) * relativeMinWidth)
        val threshold = max(madWidth, minimumWidth)
        return RobustBounds(
            median = medianValue,
            mad = madValue,
            lower = (medianValue - threshold).coerceAtLeast(MIN_HARD_CONSUMPTION),
            upper = (medianValue + threshold).coerceAtMost(MAX_HARD_CONSUMPTION)
        )
    }

    private fun robustWeightedConsumption(
        results: List<ConsumptionResult>,
        onOverflow: () -> Unit
    ): Double? {
        val weighted = results.asSequence()
            .filter { isReliable(it) && it.distance > 0.0 && it.consumption.isFinite() }
            .map { result ->
                val confidence = (result.quality?.confidenceScore ?: 1.0).coerceIn(0.05, 1.0)
                WeightedValue(result.consumption, confidence * sqrt(result.distance.coerceAtLeast(1.0)))
            }
            .filter { it.weight.isFinite() && it.weight > 0.0 }
            .sortedBy { it.value }
            .toList()
        if (weighted.isEmpty()) return null

        var totalWeight = 0.0
        for ((_, weight) in weighted) {
            totalWeight += weight
            if (!totalWeight.isFinite()) {
                onOverflow()
                return null
            }
        }
        if (totalWeight <= 0.0) return null

        val halfWeight = totalWeight / 2.0
        var accumulated = 0.0
        for ((value, weight) in weighted) {
            accumulated += weight
            if (!accumulated.isFinite()) {
                onOverflow()
                return null
            }
            if (accumulated >= halfWeight) return value
        }
        return weighted.last().value
    }

    private fun weightedArithmeticMean(
        results: List<ConsumptionResult>,
        onOverflow: () -> Unit
    ): Double? {
        var totalWeight = 0.0
        var weightedSum = 0.0
        for (result in results) {
            if (!isReliable(result) || result.distance <= 0.0 || !result.consumption.isFinite()) continue
            val confidence = (result.quality?.confidenceScore ?: 1.0).coerceIn(0.05, 1.0)
            val weight = confidence * result.distance.coerceAtLeast(1.0)
            if (!weight.isFinite() || weight <= 0.0) continue
            val product = result.consumption * weight
            if (!product.isFinite()) {
                onOverflow()
                return null
            }
            totalWeight += weight
            weightedSum += product
            if (!totalWeight.isFinite() || !weightedSum.isFinite()) {
                onOverflow()
                return null
            }
        }
        if (totalWeight <= 0.0) return null
        val mean = weightedSum / totalWeight
        if (!mean.isFinite()) {
            onOverflow()
            return null
        }
        return mean
    }

    private fun median(sorted: List<Double>): Double? {
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        if (sorted.size % 2 == 1) return sorted[middle]
        val left = sorted[middle - 1]
        val right = sorted[middle]
        val sum = left + right
        return if (sum.isFinite()) sum / 2.0 else left / 2.0 + right / 2.0
    }

    // --------------------------------------------------------
    // 油耗规则与输入校验
    // --------------------------------------------------------

    private fun normalizedFuelType(fuelType: String?): String = fuelType?.trim()?.uppercase(Locale.US).orEmpty()

    private fun isPlugIn(fuelType: String?): Boolean {
        val type = normalizedFuelType(fuelType)
        return type.contains("插电") || type.contains("插混") || type.contains("PHEV") ||
                type.contains("增程") || type.contains("EREV")
    }

    private fun softRangeFor(fuelType: String?): SoftRange {
        val type = normalizedFuelType(fuelType)
        return when {
            type.contains("柴油") || type.contains("DIESEL") -> SoftRange(3.0, 15.0)
            isPlugIn(fuelType) -> SoftRange(1.0, 12.0)
            type.contains("混动") || type.contains("油电") || type.contains("HEV") ||
                    type.contains("HYBRID") -> SoftRange(2.0, 10.0)
            else -> SoftRange(3.0, 30.0)
        }
    }

    private fun calculateSoftRangePenalty(consumption: Double, soft: SoftRange): Double {
        if (!consumption.isFinite()) return SOFT_RANGE_PENALTY_CAP
        return when {
            consumption > soft.max -> min(
                SOFT_RANGE_PENALTY_CAP,
                (consumption - soft.max) / soft.max.coerceAtLeast(0.1) * SOFT_RANGE_PENALTY_CAP
            )
            consumption < soft.min -> min(
                SOFT_RANGE_PENALTY_CAP,
                (soft.min - consumption) / soft.min.coerceAtLeast(0.1) * SOFT_RANGE_PENALTY_CAP
            )
            else -> 0.0
        }
    }

    private fun isSeverelyOversizedVolume(volume: Double, tankCapacity: Double?): Boolean {
        if (tankCapacity == null || !tankCapacity.isFinite() || tankCapacity <= 0.0) return false
        if (!volume.isFinite() || volume <= 0.0) return false
        return volume > tankCapacity * SINGLE_FUEL_SEVERE_RATIO
    }

    private fun isRemainingFuelSeverelyInconsistent(record: FuelRecord, tankCapacity: Double?): Boolean {
        if (tankCapacity == null || !tankCapacity.isFinite() || tankCapacity <= 0.0 || !record.isFull) return false
        val remaining = record.remainingFuel ?: return false
        if (!remaining.isFinite() || remaining == 0.0) return false
        if (remaining < 0.0 || remaining > tankCapacity * REMAINING_FUEL_CONSISTENCY_HIGH) return true
        return remaining < tankCapacity * REMAINING_FUEL_SEVERE_LOW
    }

    private fun isSuspiciousOdometerJump(jump: Double, elapsedHours: Double): Boolean {
        if (!jump.isFinite() || jump <= 0.0) return false
        if (!elapsedHours.isFinite() || elapsedHours <= 0.0) return jump > ODOMETER_JUMP_ABSOLUTE_LIMIT
        return jump / elapsedHours > ODOMETER_JUMP_SUSPICIOUS_SPEED
    }

    private fun isFuelVisible(record: FuelRecord, deliveryDate: Long?): Boolean {
        if (record.isInitialRecord) return false
        if (deliveryDate != null && record.timestamp < deliveryDate) return false
        return true
    }

    private fun validOdometer(record: FuelRecord): Double? {
        val value = record.odometer ?: return null
        return if (value.isFinite() && value > 0.0) value else null
    }

    private fun hasValidFuelVolume(record: FuelRecord): Boolean = record.volume.isFinite() && record.volume > 0.0
    private fun safeFuelVolume(record: FuelRecord): Double =
        if (record.volume.isFinite() && record.volume > 0.0) record.volume else 0.0

    private fun safeMissedVolume(record: FuelRecord): Double {
        if (!record.hasMissedRecord) return 0.0
        val volume = record.missedVolume ?: return 0.0
        return if (volume.isFinite() && volume > 0.0) volume else 0.0
    }

    private fun isMissedFuelUnrecorded(record: FuelRecord): Boolean =
        record.hasMissedRecord && safeMissedVolume(record) <= 0.0

    private fun calculateConsumption(fuel: Double, distance: Double): Double {
        if (!fuel.isFinite() || !distance.isFinite() || fuel <= 0.0 || distance <= 0.0) return Double.NaN
        val value = fuel / distance * 100.0
        return if (value.isFinite()) value else Double.NaN
    }

    private fun isPhysicallyUsable(result: ConsumptionResult): Boolean {
        if (!result.distance.isFinite() || result.distance <= 0.0) return false
        if (!result.fuelUsed.isFinite() || result.fuelUsed <= 0.0) return false
        if (!result.consumption.isFinite()) return false
        val quality = result.quality ?: return true
        if (quality.hasUnresolvedFuelGap) return false
        if (quality.exclusionReason == REASON_HARD_BOUNDARY) return false
        if (quality.exclusionReason == REASON_ESTIMATED_DISTANCE) return false
        return quality.confidence.atLeast(IntervalConfidence.LOW)
    }

    private fun isReliable(result: ConsumptionResult): Boolean =
        !result.isOutlier && isPhysicallyUsable(result)

    private val RECORD_COMPARATOR = compareBy<FuelRecord> { it.timestamp }
        .thenBy { it.odometer ?: 0.0 }
        .thenBy { it.id }

    private fun isValidMoney(value: Double): Boolean {
        return value.isFinite() && value >= 0.0
    }

    private fun isValidVolume(volume: Double, tankCapacity: Double?): Boolean {
        if (!volume.isFinite() || volume <= 0.0) return false
        if (tankCapacity != null && tankCapacity > 0.0) {
            if (volume > tankCapacity * SINGLE_FUEL_SEVERE_RATIO) return false
        }
        return true
    }
}

