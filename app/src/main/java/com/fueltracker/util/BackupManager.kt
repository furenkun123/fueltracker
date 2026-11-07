package com.fueltracker.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.fueltracker.data.AppDatabase
import com.fueltracker.data.CustomCategoryEntity
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlinx.coroutines.NonCancellable

object BackupManager {

    private const val FORMAT_VERSION = 3
    private val SUPPORTED_VERSIONS = setOf(1, 2, 3)

    private data class LegacyInitialData(
        val odometer: Double?,
        val fuel: Double?
    )

    private data class BackupSnapshot(
        val vehicles: List<Vehicle>,
        val records: List<FuelRecord>,
        val expenses: List<ExpenseRecord>,
        val categories: List<String>
    )

    fun generateFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "随记油耗备份$dateStr.json"
    }

    private fun validateSnapshot(
        vehicles: List<Vehicle>,
        records: List<FuelRecord>,
        expenses: List<ExpenseRecord>
    ) {
        requireUniqueIds(vehicles.map { it.id }, "车辆")
        requireUniqueIds(records.map { it.id }, "加油记录")
        requireUniqueIds(expenses.map { it.id }, "费用记录")

        val vehicleIds = vehicles.map { it.id }.toSet()
        if (records.any { it.vehicleId !in vehicleIds }) {
            throw IllegalStateException("数据库存在引用不存在车辆的加油记录，无法备份")
        }
        if (expenses.any { it.vehicleId !in vehicleIds }) {
            throw IllegalStateException("数据库存在引用不存在车辆的费用记录，无法备份")
        }

        vehicles.forEach { vehicle ->
            require(vehicle.fuelType != "纯电动") { "备份包含当前版本不支持的纯电动车辆" }
            require(vehicle.tankCapacity.isFinite() && vehicle.tankCapacity > 0.0) {
                "数据库存在油箱容量非正数的车辆，无法备份"
            }
        }

        val initialRecordsByVehicle = records.filter { it.isInitialRecord }.groupBy { it.vehicleId }
        require(initialRecordsByVehicle.values.all { it.size == 1 }) {
            "同一辆车不能存在多条初始化记录"
        }
        val vehiclesById = vehicles.associateBy { it.id }
        for ((vehicleId, initialRecords) in initialRecordsByVehicle) {
            val owner = vehiclesById[vehicleId] ?: throw IllegalStateException("初始化记录引用了不存在的车辆")
            val initial = initialRecords.single()
            require(initial.odometer != null) { "数据库中的车辆初始化记录缺少当前里程，无法备份" }
            val initialFuel = initial.initialFuel ?: initial.remainingFuel
            require(initialFuel != null && initialFuel <= owner.tankCapacity + 1e-6) {
                "数据库中的车辆初始化油量缺失或超过油箱容量，无法备份"
            }
        }
        require(vehicles.all { initialRecordsByVehicle.containsKey(it.id) }) {
            "数据库存在缺少初始化记录的车辆，无法备份"
        }

        records.forEach { record ->
            require(record.timestamp > 0L) { "数据库存在无效加油时间，无法备份" }
            requireFiniteNonNegative(record.volume, "加油量")
            requireFiniteNonNegative(record.actualPaidAmount, "加油实付金额")
            requireFiniteNonNegative(record.unitPrice, "加油单价")
            requireNullableFiniteNonNegative(record.odometer, "加油里程")
            requireNullableFiniteNonNegative(record.totalAmount, "加油原价")
            requireNullableFiniteNonNegative(record.discount, "加油优惠")
            requireNullableFiniteNonNegative(record.remainingFuel, "加油后剩余油量")
            requireNullableFiniteNonNegative(record.initialFuel, "初始油量")
            requireNullableFiniteNonNegative(record.missedOdometer, "漏记里程")
            requireNullableFiniteNonNegative(record.missedVolume, "漏记加油量")
            requireNullableFiniteNonNegative(record.consumption, "加油油耗")

            if (record.isInitialRecord) {
                require(
                    record.volume == 0.0 &&
                            record.unitPrice == 0.0 &&
                            record.actualPaidAmount == 0.0
                ) {
                    "数据库存在初始化记录数值不为 0，无法备份"
                }
            } else {
                require(record.volume > 0.0) {
                    "数据库存在普通加油记录加油量为 0，无法备份"
                }
                require(record.unitPrice > 0.0) {
                    "数据库存在普通加油记录单价为 0，无法备份"
                }
            }

            if (record.hasMissedRecord) {
                require(record.missedOdometer != null && record.missedOdometer > 0.0) {
                    "数据库存在启用漏记但漏记里程非正数的记录，无法备份"
                }
                require(record.missedVolume != null && record.missedVolume > 0.0) {
                    "数据库存在启用漏记但漏记油量非正数的记录，无法备份"
                }
                if (record.odometer != null) {
                    require(record.missedOdometer < record.odometer) {
                        "数据库存在漏记里程不小于当前里程的记录，无法备份"
                    }
                }
            } else {
                require(record.missedOdometer == null && record.missedVolume == null) {
                    "数据库存在未启用漏记但漏记字段非空的记录，无法备份"
                }
            }

            val total = record.totalAmount
            val discount = record.discount
            if (discount != null && total == null) {
                throw IllegalStateException("数据库存在有优惠金额但无原价的记录，无法备份")
            }
            if (total != null) {
                if (discount != null) {
                    require(discount <= total) {
                        "数据库存在优惠金额超过原价的记录，无法备份"
                    }
                    require(abs((total - discount) - record.actualPaidAmount) <= 0.05) {
                        "数据库存在原价减优惠不等于实付金额的记录，无法备份"
                    }
                } else {
                    require(abs(total - record.actualPaidAmount) <= 0.05) {
                        "数据库存在原价不等于实付金额的记录，无法备份"
                    }
                }
                if (!record.isInitialRecord) {
                    require(abs((record.volume * record.unitPrice) - total) <= 0.1) {
                        "数据库存在加油量乘单价不等于原价的记录，无法备份"
                    }
                }
            }
        }

        expenses.forEach { expense ->
            require(expense.timestamp > 0L) { "数据库存在无效费用时间，无法备份" }
            require(expense.category.trim().isNotEmpty()) {
                "数据库存在空费用分类，无法备份"
            }
            requireFiniteNonNegative(expense.amount, "费用金额")
        }
    }

    suspend fun createBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)

        val snapshot = db.withTransaction {
            BackupSnapshot(
                vehicles = db.vehicleDao().getAllVehicles(),
                records = db.fuelDao().getAllRecords(),
                expenses = db.expenseDao().getAll(),
                categories = db.customCategoryDao().getAll()
            )
        }

        val vehicles = snapshot.vehicles
        val records = snapshot.records
        val expenses = snapshot.expenses

        validateSnapshot(vehicles, records, expenses)

        val root = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("vehicles", JSONArray().apply {
                vehicles.forEach { v ->
                    put(JSONObject().apply {
                        put("id", v.id)
                        put("brand", v.brand)
                        put("series", v.series)
                        put("year", v.year)
                        put("model", v.model)
                        put("fuelType", v.fuelType)
                        put("fuelGrade", v.fuelGrade)
                        put("tankCapacity", v.tankCapacity)
                        put("deliveryDate", v.deliveryDate ?: JSONObject.NULL)
                        put("isCurrent", v.isCurrent)
                    })
                }
            })
            put("fuelRecords", JSONArray().apply {
                records.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id)
                        put("vehicleId", r.vehicleId)
                        put("timestamp", r.timestamp)
                        put("fuelGrade", r.fuelGrade)
                        put("odometer", r.odometer ?: JSONObject.NULL)
                        put("volume", r.volume)
                        put("totalAmount", r.totalAmount ?: JSONObject.NULL)
                        put("discount", r.discount ?: JSONObject.NULL)
                        put("actualPaidAmount", r.actualPaidAmount)
                        put("unitPrice", r.unitPrice)
                        put("remainingFuel", r.remainingFuel ?: JSONObject.NULL)
                        put("isFull", r.isFull)
                        put("isInitialRecord", r.isInitialRecord)
                        put("initialFuel", r.initialFuel ?: JSONObject.NULL)
                        put("hasMissedRecord", r.hasMissedRecord)
                        put("missedOdometer", r.missedOdometer ?: JSONObject.NULL)
                        put("missedVolume", r.missedVolume ?: JSONObject.NULL)
                        put("note", r.note)
                        put("consumption", r.consumption ?: JSONObject.NULL)
                    })
                }
            })
            put("expenses", JSONArray().apply {
                expenses.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("vehicleId", e.vehicleId)
                        put("timestamp", e.timestamp)
                        put("category", e.category.trim())
                        put("amount", e.amount)
                        put("locationName", e.locationName ?: JSONObject.NULL)
                        put("floor", e.floor ?: JSONObject.NULL)
                        put("area", e.area ?: JSONObject.NULL)
                        put("spotNumber", e.spotNumber ?: JSONObject.NULL)
                        put("isActive", e.isActive)
                        put("notificationIgnored", e.notificationIgnored)
                        put("fromLocation", e.fromLocation ?: JSONObject.NULL)
                        put("toLocation", e.toLocation ?: JSONObject.NULL)
                        put("maintenanceItem", e.maintenanceItem ?: JSONObject.NULL)
                        put("note", e.note)
                    })
                }
            })
            put("customCategories", JSONArray().apply {
                snapshot.categories.forEach { put(it) }
            })
        }
        root.toString(2)
    }

    suspend fun restoreFromJson(context: Context, jsonText: String): List<String>? =
        withContext(Dispatchers.IO) {
            val root = JSONObject(jsonText)
            val version = root.optInt("formatVersion", -1)
            if (version !in SUPPORTED_VERSIONS) {
                throw IllegalArgumentException("不支持的备份版本：$version")
            }

            val vehicleArray = root.readBusinessArray("vehicles", required = true)
            val recordArray = root.readBusinessArray("fuelRecords", required = version >= 2)
            val expenseArray = root.readBusinessArray("expenses", required = version >= 2)

            val vehicles = mutableListOf<Vehicle>()
            val legacyInitialData = mutableMapOf<Long, LegacyInitialData>()
            for (i in 0 until vehicleArray.length()) {
                val json = vehicleArray.getJSONObject(i)
                val id = json.requireLong("id")
                val tankCapacity = json.requireDouble("tankCapacity")
                // v1/v2 备份把初始数据保存在 Vehicle；v3 起统一保存在 FuelRecord。
                val legacyOdometer = json.getNullableDouble("initialOdometer")
                val legacyFuel = json.getNullableDouble("initialFuel")
                requireNullableFiniteNonNegative(legacyOdometer, "旧备份车辆初始里程")
                requireNullableFiniteNonNegative(legacyFuel, "旧备份车辆初始油量")
                if (legacyOdometer != null || legacyFuel != null) {
                    legacyInitialData[id] = LegacyInitialData(legacyOdometer, legacyFuel)
                }
                val fuelType = json.requireString("fuelType")

                require(fuelType != "纯电动") { "备份包含当前版本不支持的纯电动车辆" }
                require(tankCapacity.isFinite() && tankCapacity > 0.0) { "油箱容量必须大于 0" }

                if (id <= 0L) throw IllegalArgumentException("车辆 ID 非法")
                vehicles.add(
                    Vehicle(
                        id = id,
                        brand = json.requireString("brand"),
                        series = json.requireString("series"),
                        year = json.requireInt("year"),
                        model = json.requireString("model"),
                        fuelType = fuelType,
                        fuelGrade = json.requireString("fuelGrade"),
                        tankCapacity = tankCapacity,
                        deliveryDate = json.getNullableLong("deliveryDate"),
                        isCurrent = json.requireBoolean("isCurrent")
                    )
                )
            }
            requireUniqueIds(vehicles.map { it.id }, "车辆")

            val currentVehicles = vehicles.filter { it.isCurrent }
            if (vehicles.isNotEmpty() && currentVehicles.size != 1) {
                throw IllegalArgumentException(
                    "备份文件损坏：存在 ${currentVehicles.size} 辆当前车辆，必须且只能有 1 辆"
                )
            }

            val records = mutableListOf<FuelRecord>()
            for (i in 0 until recordArray.length()) {
                val json = recordArray.getJSONObject(i)
                val id = json.requireLong("id")
                val vehicleId = json.requireLong("vehicleId")
                val ts = json.requireLong("timestamp")
                val volume = json.requireDouble("volume")
                val unitPrice = json.requireDouble("unitPrice")
                val actualPaidAmount = json.requireDouble("actualPaidAmount")
                val isInitial = json.requireBoolean("isInitialRecord")
                val hasMissed = json.requireBoolean("hasMissedRecord")

                if (id <= 0L || vehicleId <= 0L) throw IllegalArgumentException("记录 ID 或车辆 ID 非法")
                if (ts <= 0L) throw IllegalArgumentException("记录时间戳非法")

                requireFiniteNonNegative(volume, "加油量")
                requireFiniteNonNegative(unitPrice, "加油单价")
                requireFiniteNonNegative(actualPaidAmount, "加油实付金额")
                if (isInitial) {
                    require(volume == 0.0) { "初始化记录的加油量必须为 0" }
                    require(unitPrice == 0.0) { "初始化记录的单价必须为 0" }
                    require(actualPaidAmount == 0.0) { "初始化记录的金额必须为 0" }
                } else {
                    require(volume > 0.0) { "普通加油记录的加油量必须大于 0" }
                    require(unitPrice > 0.0) { "普通加油记录的单价必须大于 0" }
                }

                val odometer = json.getNullableDouble("odometer")
                requireNullableFiniteNonNegative(odometer, "加油里程")
                val remainingFuel = json.getNullableDouble("remainingFuel")
                requireNullableFiniteNonNegative(remainingFuel, "加油后剩余油量")
                val initialFuelValue = json.getNullableDouble("initialFuel")
                requireNullableFiniteNonNegative(initialFuelValue, "初始油量")

                val missedOdo = json.getNullableDouble("missedOdometer")
                requireNullableFiniteNonNegative(missedOdo, "漏记里程")
                val missedVol = json.getNullableDouble("missedVolume")
                requireNullableFiniteNonNegative(missedVol, "漏记加油量")
                if (hasMissed) {
                    require(missedOdo != null && missedOdo > 0.0) { "启用漏记时，漏记里程必须大于 0" }
                    require(missedVol != null && missedVol > 0.0) { "启用漏记时，漏记油量必须大于 0" }
                    if (odometer != null) require(missedOdo < odometer) { "漏记里程必须小于当前里程" }
                } else {
                    require(missedOdo == null && missedVol == null) { "未勾选漏记时，漏记字段必须为空" }
                }

                val total = json.getNullableDouble("totalAmount")
                val discount = json.getNullableDouble("discount")
                requireNullableFiniteNonNegative(total, "原价")
                requireNullableFiniteNonNegative(discount, "优惠金额")

                if (discount != null && total == null) throw IllegalArgumentException("存在优惠金额时必须提供原价")
                if (total != null) {
                    if (discount != null) {
                        require(discount <= total) { "优惠金额不能超过原价" }
                        require(abs((total - discount) - actualPaidAmount) <= 0.05) { "原价减去优惠金额应等于实付金额" }
                    } else {
                        require(abs(total - actualPaidAmount) <= 0.05) { "原价应等于实付金额" }
                    }
                    if (!isInitial) {
                        require(abs((volume * unitPrice) - total) <= 0.1) { "加油量乘以单价应近似等于原价" }
                    }
                }

                records.add(
                    FuelRecord(
                        id = id, vehicleId = vehicleId, timestamp = ts,
                        fuelGrade = json.requireString("fuelGrade"), odometer = odometer,
                        volume = volume, totalAmount = total, discount = discount,
                        actualPaidAmount = actualPaidAmount, unitPrice = unitPrice,
                        remainingFuel = remainingFuel, isFull = json.requireBoolean("isFull"),
                        isInitialRecord = isInitial, initialFuel = initialFuelValue,
                        hasMissedRecord = hasMissed, missedOdometer = missedOdo,
                        missedVolume = missedVol, note = json.optString("note", ""),
                        consumption = null
                    )
                )
            }
            requireUniqueIds(records.map { it.id }, "加油记录")

            // 兼容旧备份：将旧 Vehicle 初始字段转换为 FuelRecord；已有初始化记录只补缺失字段。
            var nextRecordId = records.maxOfOrNull { it.id } ?: 0L
            val backupCreatedAt = root.optLong("createdAt", System.currentTimeMillis()).coerceAtLeast(1L)
            for (vehicle in vehicles) {
                val legacy = legacyInitialData[vehicle.id] ?: continue
                val existingIndex = records.indexOfFirst {
                    it.vehicleId == vehicle.id && it.isInitialRecord
                }
                val legacyFuel = legacy.fuel

                if (existingIndex >= 0) {
                    val existing = records[existingIndex]
                    records[existingIndex] = existing.copy(
                        odometer = existing.odometer ?: legacy.odometer,
                        initialFuel = existing.initialFuel ?: existing.remainingFuel ?: legacyFuel
                    )
                } else if (legacy.odometer != null || legacyFuel != null) {
                    check(nextRecordId < Long.MAX_VALUE) { "备份记录 ID 已达到上限" }
                    nextRecordId += 1L
                    val deliveryStart = vehicle.deliveryDate?.takeIf { it > 0L }
                    val firstVisibleFuelTimestamp = records.asSequence()
                        .filter { it.vehicleId == vehicle.id && !it.isInitialRecord }
                        .filter { deliveryStart == null || it.timestamp >= deliveryStart }
                        .minOfOrNull { it.timestamp }
                    val initialTimestamp = firstVisibleFuelTimestamp
                        ?.let { maxOf(deliveryStart ?: 1L, (it - 1L).coerceAtLeast(1L)) }
                        ?: maxOf(deliveryStart ?: 1L, backupCreatedAt)

                    records.add(
                        FuelRecord(
                            id = nextRecordId,
                            vehicleId = vehicle.id,
                            timestamp = initialTimestamp,
                            fuelGrade = vehicle.fuelGrade,
                            odometer = legacy.odometer,
                            volume = 0.0,
                            actualPaidAmount = 0.0,
                            unitPrice = 0.0,
                            isInitialRecord = true,
                            initialFuel = legacyFuel,
                            note = "由旧备份转换的车辆初始化记录"
                        )
                    )
                }
            }
            requireUniqueIds(records.map { it.id }, "加油记录")
            val initialRecordsByVehicle = records.filter { it.isInitialRecord }.groupBy { it.vehicleId }
            require(initialRecordsByVehicle.values.all { it.size == 1 }) {
                "备份文件损坏：同一辆车存在多条初始化记录"
            }
            val vehiclesById = vehicles.associateBy { it.id }
            for ((vehicleId, initialRecords) in initialRecordsByVehicle) {
                val owner = vehiclesById[vehicleId] ?: throw IllegalArgumentException("初始化记录引用了不存在的车辆")
                val initial = initialRecords.single()
                require(initial.odometer != null) { "车辆初始化记录缺少当前里程" }
                val initialFuel = initial.initialFuel ?: initial.remainingFuel
                require(initialFuel != null) { "车辆初始化记录缺少当前剩余油量" }
                require(initialFuel <= owner.tankCapacity + 1e-6) { "车辆初始化油量超过油箱容量" }
            }
            require(vehicles.all { initialRecordsByVehicle.containsKey(it.id) }) {
                "备份文件损坏：有车辆缺少初始化记录"
            }

            val expenses = mutableListOf<ExpenseRecord>()
            for (i in 0 until expenseArray.length()) {
                val json = expenseArray.getJSONObject(i)
                val id = json.requireLong("id")
                val vehicleId = json.requireLong("vehicleId")
                val ts = json.requireLong("timestamp")
                val category = json.requireString("category").trim()
                val amount = json.requireDouble("amount")

                if (id <= 0L || vehicleId <= 0L) throw IllegalArgumentException("费用记录 ID 或车辆 ID 非法")
                if (ts <= 0L) throw IllegalArgumentException("费用记录时间戳非法")
                if (category.isBlank()) throw IllegalArgumentException("费用分类不能为空")
                requireFiniteNonNegative(amount, "费用金额")

                expenses.add(
                    ExpenseRecord(
                        id = id, vehicleId = vehicleId, timestamp = ts,
                        category = category, amount = amount,
                        locationName = json.getNullableString("locationName"),
                        floor = json.getNullableString("floor"),
                        area = json.getNullableString("area"),
                        spotNumber = json.getNullableString("spotNumber"),
                        isActive = json.requireBoolean("isActive"),
                        notificationIgnored = json.requireBoolean("notificationIgnored"),
                        fromLocation = json.getNullableString("fromLocation"),
                        toLocation = json.getNullableString("toLocation"),
                        maintenanceItem = json.getNullableString("maintenanceItem"),
                        note = json.optString("note", "")
                    )
                )
            }
            requireUniqueIds(expenses.map { it.id }, "费用记录")

            val customCategories = if (root.has("customCategories") && !root.isNull("customCategories")) {
                val arr = root.optJSONArray("customCategories") ?: throw IllegalArgumentException("customCategories 字段类型错误")
                (0 until arr.length()).map { idx ->
                    val item = arr.opt(idx)
                    if (item !is String) throw IllegalArgumentException("customCategories[$idx] 不是字符串")
                    val name = item.trim()
                    require(name.isNotEmpty()) { "customCategories[$idx] 为空" }
                    name
                }
            } else null

            val validVehicleIds = vehicles.map { it.id }.toSet()
            if (records.any { it.vehicleId !in validVehicleIds }) throw IllegalArgumentException("备份文件损坏：加油记录引用了不存在的车辆")
            if (expenses.any { it.vehicleId !in validVehicleIds }) throw IllegalArgumentException("备份文件损坏：费用记录引用了不存在的车辆")

            val validExpenses = normalizeActiveParking(expenses)
            val db = AppDatabase.getInstance(context)

            db.withTransaction {
                db.expenseDao().deleteAll()
                db.fuelDao().deleteAll()
                db.vehicleDao().deleteAll()
                vehicles.forEach { db.vehicleDao().insert(it) }
                records.forEach { db.fuelDao().insert(it) }
                validExpenses.forEach { db.expenseDao().insert(it) }

                db.customCategoryDao().deleteAll()
                if (customCategories != null) {
                    db.customCategoryDao().insertAll(customCategories.map { CustomCategoryEntity(it) })
                }
            }

            customCategories
        }

    suspend fun writeBackupToUri(context: Context, uri: Uri) =
        withContext(Dispatchers.IO) {
            val json = createBackupJson(context)
            val bytes = json.toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: throw IllegalStateException("无法创建备份文件")
        }

    suspend fun restoreFromUri(context: Context, uri: Uri) =
        withContext(Dispatchers.IO + NonCancellable) {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            } ?: throw IllegalStateException("无法读取备份文件")
            restoreFromJson(context, json)
        }
}

private fun requireFiniteNonNegative(value: Double, fieldName: String) {
    require(value.isFinite() && value >= 0.0) { "备份文件损坏：${fieldName}必须是有效的非负数" }
}

private fun requireNullableFiniteNonNegative(value: Double?, fieldName: String) {
    if (value != null) requireFiniteNonNegative(value, fieldName)
}

private fun requireUniqueIds(ids: List<Long>, tableName: String) {
    if (ids.any { it <= 0L } || ids.toSet().size != ids.size) {
        throw IllegalArgumentException("备份文件损坏：${tableName}存在无效或重复的 ID")
    }
}

private fun JSONObject.readBusinessArray(key: String, required: Boolean): JSONArray {
    val hasField = has(key) && !isNull(key)
    if (!hasField) {
        if (required) throw IllegalArgumentException("备份文件缺失必需字段: $key")
        return JSONArray()
    }
    return optJSONArray(key) ?: throw IllegalArgumentException("备份文件字段类型错误: $key")
}

private fun normalizeActiveParking(expenses: List<ExpenseRecord>): List<ExpenseRecord> {
    val activeParkingWinnerByVehicle = expenses
        .asSequence()
        .filter { it.category == "停车" && it.isActive }
        .groupBy { it.vehicleId }
        .mapValues { (_, records) -> records.maxWithOrNull(compareBy<ExpenseRecord> { it.timestamp }.thenBy { it.id })?.id }

    return expenses.map { expense ->
        when {
            expense.category != "停车" && expense.isActive -> expense.copy(isActive = false)
            expense.category == "停车" && expense.isActive && activeParkingWinnerByVehicle[expense.vehicleId] != expense.id -> expense.copy(isActive = false)
            else -> expense
        }
    }
}

private fun JSONObject.requireString(key: String): String {
    if (!has(key) || isNull(key)) throw IllegalArgumentException("备份文件缺失必需字段: $key")
    return getString(key)
}

private fun JSONObject.requireInt(key: String): Int {
    if (!has(key) || isNull(key)) throw IllegalArgumentException("备份文件缺失必需字段: $key")
    return getInt(key)
}

private fun JSONObject.requireLong(key: String): Long {
    if (!has(key) || isNull(key)) throw IllegalArgumentException("备份文件缺失必需字段: $key")
    return getLong(key)
}

private fun JSONObject.requireDouble(key: String): Double {
    if (!has(key) || isNull(key)) throw IllegalArgumentException("备份文件缺失必需字段: $key")
    val v = getDouble(key)
    if (!v.isFinite()) throw IllegalArgumentException("备份文件字段非有限值: $key")
    return v
}

private fun JSONObject.requireBoolean(key: String): Boolean {
    if (!has(key) || isNull(key)) throw IllegalArgumentException("备份文件缺失必需字段: $key")
    return getBoolean(key)
}

private fun JSONObject.getNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = getDouble(key)
    if (!value.isFinite()) throw IllegalArgumentException("备份文件字段非有限值: $key")
    return value
}

private fun JSONObject.getNullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return getLong(key)
}

private fun JSONObject.getNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return getString(key).takeIf { it.isNotEmpty() }
}