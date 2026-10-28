package com.fueltracker.util

import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import java.util.Calendar

enum class ExpenseRange(val label: String) {
    ALL("全部"), MONTH("本月"), YEAR("今年"), CUSTOM_MONTH("")
}

data class ExpenseSummary(
    val rangeLabel: String,
    val totalAmount: Double,
    val count: Int,
    val dailyAverage: Double,
    val showDaily: Boolean
)

data class MonthTrendPoint(val year: Int, val month: Int, val value: Double)

data class StructureItem(val name: String, val amount: Double, val ratio: Float)

fun inRange(timestamp: Long, start: Long?, end: Long?) =
    (start == null || timestamp >= start) && (end == null || timestamp <= end)

fun computeExpenseRange(range: ExpenseRange, customYear: Int, customMonth: Int): Pair<Long?, Long?> {
    if (range == ExpenseRange.ALL) return null to null
    val cal = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    when (range) {
        ExpenseRange.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        ExpenseRange.YEAR -> { cal.set(Calendar.MONTH, Calendar.JANUARY); cal.set(Calendar.DAY_OF_MONTH, 1) }
        ExpenseRange.CUSTOM_MONTH -> {
            cal.set(Calendar.YEAR, customYear)
            if (customMonth == 0) { cal.set(Calendar.MONTH, Calendar.JANUARY); cal.set(Calendar.DAY_OF_MONTH, 1) }
            else { cal.set(Calendar.MONTH, customMonth - 1); cal.set(Calendar.DAY_OF_MONTH, 1) }
        }
        else -> {}
    }
    val start = cal.timeInMillis
    val endCal = cal.clone() as Calendar
    when (range) {
        ExpenseRange.MONTH -> endCal.add(Calendar.MONTH, 1)
        ExpenseRange.YEAR -> endCal.add(Calendar.YEAR, 1)
        ExpenseRange.CUSTOM_MONTH -> if (customMonth == 0) endCal.add(Calendar.YEAR, 1) else endCal.add(Calendar.MONTH, 1)
        else -> {}
    }
    return start to (endCal.timeInMillis - 1)
}

fun computeSummary(
    expenses: List<ExpenseRecord>,
    fuelRecords: List<FuelRecord>,
    rangeBounds: Pair<Long?, Long?>,
    rangeLabel: String
): ExpenseSummary {
    val (start, end) = rangeBounds
    val filteredExpenses = expenses.filter { inRange(it.timestamp, start, end) }
    val filteredFuel = fuelRecords.filter { !it.isInitialRecord && inRange(it.timestamp, start, end) }
    val total = filteredExpenses.sumOf { it.amount } + filteredFuel.sumOf { it.actualPaidAmount }
    val count = filteredExpenses.size + filteredFuel.size

    val daysInRange = if (start == null || end == null) null else {
        val s = Calendar.getInstance().apply { timeInMillis = start }
        val e = Calendar.getInstance().apply { timeInMillis = end }
        var days = 1
        while (s.before(e)) { s.add(Calendar.DAY_OF_MONTH, 1); days++ }
        days
    }

    return ExpenseSummary(rangeLabel, total, count, if (daysInRange != null) total / daysInRange else 0.0, daysInRange != null)
}

fun buildMonthTrend(
    expenses: List<ExpenseRecord>,
    fuelRecords: List<FuelRecord>,
    year: Int? // null = 全部
): List<MonthTrendPoint> {
    val grouped = mutableMapOf<Pair<Int, Int>, Double>()

    fun accumulate(timestamp: Long, amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) return
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        val y = c.get(Calendar.YEAR)
        if (year != null && y != year) return
        val key = y to (c.get(Calendar.MONTH) + 1)
        grouped[key] = (grouped[key] ?: 0.0) + amount
    }

    fuelRecords.filter { !it.isInitialRecord }.forEach { accumulate(it.timestamp, it.actualPaidAmount) }
    expenses.forEach { accumulate(it.timestamp, it.amount) }

    return grouped.entries
        .filter { it.value > 0.0 }
        .map { (key, value) -> MonthTrendPoint(key.first, key.second, value) }
        .sortedWith(compareBy<MonthTrendPoint> { it.year }.thenBy { it.month })
}

fun buildStructureData(
    expenses: List<ExpenseRecord>,
    fuelRecords: List<FuelRecord>,
    rangeBounds: Pair<Long?, Long?>
): List<StructureItem> {
    val (start, end) = rangeBounds
    val map = mutableMapOf<String, Double>()

    fuelRecords.filter { !it.isInitialRecord && inRange(it.timestamp, start, end) }
        .sumOf { it.actualPaidAmount }.takeIf { it > 0 }?.let { map["加油"] = it }

    expenses.filter { inRange(it.timestamp, start, end) }.groupBy { it.category }.forEach { (cat, list) ->
        map[cat] = (map[cat] ?: 0.0) + list.sumOf { it.amount }
    }

    val total = map.values.sum()
    return map.map { (name, amount) -> StructureItem(name, amount, if (total > 0) (amount / total).toFloat() else 0f) }
        .filter { it.amount > 0 }.sortedByDescending { it.amount }
}