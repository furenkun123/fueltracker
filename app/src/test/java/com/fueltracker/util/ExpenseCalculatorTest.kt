package com.fueltracker.util

import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCalculatorTest {

    @Test
    fun initialRefuelingIsIncludedInExpenseStatistics() {
        val timestamp = 1_735_689_600_000L

        val records = listOf(
            // 纯初始状态，不是一笔交易
            FuelRecord(
                id = 1L,
                vehicleId = 1L,
                timestamp = timestamp,
                volume = 0.0,
                actualPaidAmount = 0.0,
                unitPrice = 0.0,
                isInitialRecord = true,
                initialFuel = 30.0
            ),

            // 初始记录中包含的真实加油交易
            FuelRecord(
                id = 2L,
                vehicleId = 1L,
                timestamp = timestamp,
                volume = 8.0,
                actualPaidAmount = 60.0,
                unitPrice = 7.5,
                isInitialRecord = true,
                initialFuel = 30.0
            ),

            // 普通加油交易
            FuelRecord(
                id = 3L,
                vehicleId = 1L,
                timestamp = timestamp,
                volume = 10.0,
                actualPaidAmount = 75.0,
                unitPrice = 7.5
            )
        )

        val expenses = listOf(
            ExpenseRecord(
                id = 1L,
                vehicleId = 1L,
                timestamp = timestamp,
                category = "停车",
                amount = 20.0
            )
        )

        // 1. 验证费用汇总
        val summary = computeSummary(
            expenses = expenses,
            fuelRecords = records,
            rangeBounds = null to null,
            rangeLabel = "全部"
        )

        // 60 + 75 + 20
        assertEquals(155.0, summary.totalAmount, 0.000001)

        // 两笔加油交易 + 一笔停车费用
        assertEquals(3, summary.count)

        // 2. 验证费用结构
        val structure = buildStructureData(
            expenses = expenses,
            fuelRecords = records,
            rangeBounds = null to null
        )

        val fuelItem = structure.single { it.name == "加油" }

        assertEquals(135.0, fuelItem.amount, 0.000001)

        val parkingItem = structure.single { it.name == "停车" }

        assertEquals(20.0, parkingItem.amount, 0.000001)

        assertTrue(structure.none { it.amount <= 0.0 })

        // 3. 验证月度趋势
        val trend = buildMonthTrend(
            expenses = expenses,
            fuelRecords = records,
            year = null
        )

        assertEquals(1, trend.size)

        // 月度总费用：135 + 20
        assertEquals(155.0, trend.single().value, 0.000001)
    }
}