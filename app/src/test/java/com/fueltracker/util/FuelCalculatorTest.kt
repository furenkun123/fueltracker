package com.fueltracker.util

import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelCalculatorTest {

    @Test
    fun calculateAll_twoFullTankRecords_returnsCorrectConsumption() {
        val vehicle = Vehicle(
            id = 1L,
            brand = "测试品牌",
            series = "测试车系",
            model = "测试车型",
            year = 2024,
            tankCapacity = 50.0,
            fuelType = "汽油",
            fuelGrade = "92#"
        )

        val firstTimestamp = 1_700_000_000_000L

        val firstRecord = FuelRecord(
            id = 1L,
            vehicleId = 1L,
            timestamp = firstTimestamp,
            odometer = 10_000.0,
            volume = 30.0,
            actualPaidAmount = 225.0,
            unitPrice = 7.5,
            isFull = true
        )

        val secondRecord = FuelRecord(
            id = 2L,
            vehicleId = 1L,
            timestamp = firstTimestamp + 86_400_000L,
            odometer = 10_400.0,
            volume = 32.0,
            actualPaidAmount = 240.0,
            unitPrice = 7.5,
            isFull = true
        )

        // 故意倒序传入，验证算法是否正确按时间处理
        val results = FuelCalculator.calculateAll(
            records = listOf(secondRecord, firstRecord),
            vehicle = vehicle,
            sensitivity = OutlierSensitivity.STANDARD
        )

        assertEquals(1, results.size)

        val result = results.single()

        assertEquals(400.0, result.distance, 0.000001)
        assertEquals(32.0, result.fuelUsed, 0.000001)
        assertEquals(8.0, result.consumption, 0.000001)
    }

    @Test
    fun calculateAll_emptyRecords_returnsEmptyList() {
        val results = FuelCalculator.calculateAll(
            records = emptyList(),
            vehicle = null,
            sensitivity = OutlierSensitivity.STANDARD
        )

        assertTrue(results.isEmpty())
    }
    @Test
    fun initialFuelStartsConsumptionUntilFirstFullTank() {
        val vehicle = Vehicle(
            id = 1L,
            brand = "测试品牌",
            series = "测试车系",
            model = "测试车型",
            year = 2024,
            tankCapacity = 50.0,
            fuelType = "汽油",
            fuelGrade = "92#"
        )

        val day = 86_400_000L

        val records = listOf(
            FuelRecord(
                id = 1L,
                vehicleId = 1L,
                timestamp = day,
                odometer = 10_000.0,
                volume = 0.0,
                actualPaidAmount = 0.0,
                unitPrice = 0.0,
                isInitialRecord = true,
                initialFuel = 30.0
            ),
            FuelRecord(
                id = 2L,
                vehicleId = 1L,
                timestamp = day * 2,
                odometer = 10_100.0,
                volume = 10.0,
                actualPaidAmount = 75.0,
                unitPrice = 7.5,
                isFull = false
            ),
            FuelRecord(
                id = 3L,
                vehicleId = 1L,
                timestamp = day * 3,
                odometer = 10_200.0,
                volume = 26.0,
                actualPaidAmount = 195.0,
                unitPrice = 7.5,
                isFull = true
            )
        )

        val results = FuelCalculator.calculateAll(records, vehicle)

        assertEquals(1, results.size)

        val result = results.single()

        assertEquals(200.0, result.distance, 0.000001)
        assertEquals(16.0, result.fuelUsed, 0.000001)
        assertEquals(8.0, result.consumption, 0.000001)
        assertTrue(result.isEstimated)
        assertTrue(
            result.quality?.confidence != IntervalConfidence.HIGH
        )
    }

    @Test
    fun historicalFullTankAndInitialFuelAreCombined() {
        val vehicle = Vehicle(
            id = 1L,
            brand = "测试品牌",
            series = "测试车系",
            model = "测试车型",
            year = 2024,
            tankCapacity = 50.0,
            fuelType = "汽油",
            fuelGrade = "92#"
        )

        val day = 86_400_000L

        val records = listOf(
            // 历史满箱：可靠起点
            FuelRecord(
                id = 1L,
                vehicleId = 1L,
                timestamp = day,
                odometer = 9_000.0,
                volume = 30.0,
                actualPaidAmount = 225.0,
                unitPrice = 7.5,
                isFull = true
            ),
            // 初始记录之前的加油
            FuelRecord(
                id = 2L,
                vehicleId = 1L,
                timestamp = day * 2,
                odometer = 9_050.0,
                volume = 10.0,
                actualPaidAmount = 75.0,
                unitPrice = 7.5,
                isFull = false
            ),
            // 添加车辆时的初始状态
            FuelRecord(
                id = 3L,
                vehicleId = 1L,
                timestamp = day * 3,
                odometer = 9_100.0,
                volume = 0.0,
                actualPaidAmount = 0.0,
                unitPrice = 0.0,
                isInitialRecord = true,
                initialFuel = 30.0
            ),
            // 初始记录之后的加油
            FuelRecord(
                id = 4L,
                vehicleId = 1L,
                timestamp = day * 4,
                odometer = 9_150.0,
                volume = 5.0,
                actualPaidAmount = 37.5,
                unitPrice = 7.5,
                isFull = false
            ),
            // 初始记录之后第一次满箱
            FuelRecord(
                id = 5L,
                vehicleId = 1L,
                timestamp = day * 5,
                odometer = 9_200.0,
                volume = 25.0,
                actualPaidAmount = 187.5,
                unitPrice = 7.5,
                isFull = true
            )
        )

        val results = FuelCalculator.calculateAll(records, vehicle)

        assertEquals(2, results.size)

        // 历史满箱 -> 初始记录：
        // 50 + 10 - 30 = 30 L
        assertEquals(30.0, results[0].fuelUsed, 0.000001)
        assertEquals(30.0, results[0].consumption, 0.000001)
        assertTrue(results[0].isEstimated)

        // 初始记录 -> 后续满箱：
        // 30 + 5 + 25 - 50 = 10 L
        assertEquals(10.0, results[1].fuelUsed, 0.000001)
        assertEquals(10.0, results[1].consumption, 0.000001)
        assertTrue(results[1].isEstimated)
    }
}