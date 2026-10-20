package com.fueltracker.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.fueltracker.data.AppDatabase
import com.fueltracker.data.ExpenseCategoryStore
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

object BackupManager {

    /** 支持的备份格式版本 */
    private const val FORMAT_VERSION = 2

    /** 能读取的历史版本 */
    private val SUPPORTED_VERSIONS = setOf(1, 2)

    /** 检查数据库快照，避免生成无法再次恢复的备份文件。 */
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
            requireFiniteNonNegative(vehicle.tankCapacity, "车辆油箱容量")
            requireNullableFiniteNonNegative(vehicle.officialConsumption, "车辆官方油耗")
            requireFiniteNonNegative(vehicle.initialOdometer, "车辆初始里程")
            requireFiniteNonNegative(vehicle.initialFuel, "车辆初始油量")
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
        }
        expenses.forEach { expense ->
            require(expense.timestamp > 0L) { "数据库存在无效费用时间，无法备份" }
            require(expense.category.isNotBlank()) { "数据库存在空费用分类，无法备份" }
            requireFiniteNonNegative(expense.amount, "费用金额")
        }
    }

    fun generateFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "随记油耗备份$dateStr.json"
    }

    // ============================================================
    // 备份
    // ============================================================

    suspend fun createBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)

        // 三张表在同一 Room 事务中读取，避免备份包含不同时间点的数据。
        val snapshot = db.withTransaction {
            Triple(
                db.vehicleDao().getAllVehicles(),
                db.fuelDao().getAllRecords(),
                db.expenseDao().getAll()
            )
        }
        val vehicles = snapshot.first
        val records = snapshot.second
        val expenses = snapshot.third

        validateSnapshot(vehicles, records, expenses)

        val root = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())

            // 车辆
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
                        put("officialConsumption", v.officialConsumption ?: JSONObject.NULL)
                        put("initialOdometer", v.initialOdometer)
                        put("initialFuel", v.initialFuel)
                        put("deliveryDate", v.deliveryDate ?: JSONObject.NULL)
                        put("isCurrent", v.isCurrent)
                    })
                }
            })

            // 加油记录
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

            // 费用记录（v2 新增）
            put("expenses", JSONArray().apply {
                expenses.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("vehicleId", e.vehicleId)
                        put("timestamp", e.timestamp)
                        put("category", e.category)
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

            // 自定义费用分类（v2 起）
            put("customCategories", JSONArray().apply {
                ExpenseCategoryStore.getCustom().forEach { cat ->
                    put(cat)
                }
            })
        }

        root.toString(2)
    }

    // ============================================================
    // 恢复
    // ============================================================

    suspend fun restoreFromJson(context: Context, jsonText: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonText)

        val version = root.optInt("formatVersion", -1)
        if (version !in SUPPORTED_VERSIONS) {
            throw IllegalArgumentException("不支持的备份版本：$version")
        }

        // ---------- 车辆 ----------
        val vehicles = mutableListOf<Vehicle>()
        val vehicleArray = root.optJSONArray("vehicles") ?: JSONArray()
        for (i in 0 until vehicleArray.length()) {
            val json = vehicleArray.getJSONObject(i)
            val id = json.optLong("id", 0L)
            val tankCapacity = json.optDouble("tankCapacity", 0.0)
            val officialConsumption = json.getNullableDouble("officialConsumption")
            val initialOdometer = json.optDouble("initialOdometer", 20.0)
            val initialFuel = json.optDouble("initialFuel", 0.0)

            if (id <= 0L) throw IllegalArgumentException("备份文件损坏：车辆 ID 非法")
            requireFiniteNonNegative(tankCapacity, "车辆油箱容量")
            requireNullableFiniteNonNegative(officialConsumption, "车辆官方油耗")
            requireFiniteNonNegative(initialOdometer, "车辆初始里程")
            requireFiniteNonNegative(initialFuel, "车辆初始油量")

            vehicles.add(
                Vehicle(
                    id = id,
                    brand = json.optString("brand"),
                    series = json.optString("series"),
                    year = json.optInt("year"),
                    model = json.optString("model"),
                    fuelType = json.optString("fuelType", "汽油"),
                    fuelGrade = json.optString("fuelGrade", "92#"),
                    tankCapacity = tankCapacity,
                    officialConsumption = officialConsumption,
                    initialOdometer = initialOdometer,
                    initialFuel = initialFuel,
                    deliveryDate = json.getNullableLong("deliveryDate"),
                    isCurrent = json.optBoolean("isCurrent")
                )
            )
        }
        requireUniqueIds(vehicles.map { it.id }, "车辆")

        // 兜底：确保有且仅有一辆车是 current
        if (vehicles.isNotEmpty()) {
            val firstCurrentIndex = vehicles.indexOfFirst { it.isCurrent }
            val targetIndex = if (firstCurrentIndex >= 0) firstCurrentIndex else 0
            vehicles.forEachIndexed { i, v ->
                val shouldBeCurrent = (i == targetIndex)
                if (v.isCurrent != shouldBeCurrent) {
                    vehicles[i] = v.copy(isCurrent = shouldBeCurrent)
                }
            }
        }

        // ---------- 加油记录 ----------
        val records = mutableListOf<FuelRecord>()
        val recordArray = root.optJSONArray("fuelRecords") ?: JSONArray()
        for (i in 0 until recordArray.length()) {
            val json = recordArray.getJSONObject(i)
            val id = json.optLong("id", 0L)
            val vehicleId = json.optLong("vehicleId", 0L)
            val ts = json.optLong("timestamp", 0L)
            val volume = json.optDouble("volume", Double.NaN)
            val totalAmount = json.getNullableDouble("totalAmount")
            val discount = json.getNullableDouble("discount")
            val actualPaidAmount = json.optDouble("actualPaidAmount", Double.NaN)
            val unitPrice = json.optDouble("unitPrice", Double.NaN)
            val odometer = json.getNullableDouble("odometer")
            val remainingFuel = json.getNullableDouble("remainingFuel")
            val initialFuel = json.getNullableDouble("initialFuel")
            val missedOdometer = json.getNullableDouble("missedOdometer")
            val missedVolume = json.getNullableDouble("missedVolume")
            val consumption = json.getNullableDouble("consumption")

            if (id <= 0L || vehicleId <= 0L) {
                throw IllegalArgumentException("备份文件损坏：加油记录 ID 或车辆 ID 非法")
            }
            if (ts <= 0L) throw IllegalArgumentException("备份文件损坏：加油记录时间戳非法")
            requireFiniteNonNegative(volume, "加油量")
            requireNullableFiniteNonNegative(odometer, "加油里程")
            requireNullableFiniteNonNegative(totalAmount, "加油原价")
            requireNullableFiniteNonNegative(discount, "加油优惠")
            requireFiniteNonNegative(actualPaidAmount, "加油实付金额")
            requireFiniteNonNegative(unitPrice, "加油单价")
            requireNullableFiniteNonNegative(remainingFuel, "加油后剩余油量")
            requireNullableFiniteNonNegative(initialFuel, "初始油量")
            requireNullableFiniteNonNegative(missedOdometer, "漏记里程")
            requireNullableFiniteNonNegative(missedVolume, "漏记加油量")
            requireNullableFiniteNonNegative(consumption, "加油油耗")

            records.add(
                FuelRecord(
                    id = id,
                    vehicleId = vehicleId,
                    timestamp = ts,
                    fuelGrade = json.optString("fuelGrade", "92#"),
                    odometer = odometer,
                    volume = volume,
                    totalAmount = totalAmount,
                    discount = discount,
                    actualPaidAmount = actualPaidAmount,
                    unitPrice = unitPrice,
                    remainingFuel = remainingFuel,
                    isFull = json.optBoolean("isFull"),
                    isInitialRecord = json.optBoolean("isInitialRecord"),
                    initialFuel = initialFuel,
                    hasMissedRecord = json.optBoolean("hasMissedRecord"),
                    missedOdometer = missedOdometer,
                    missedVolume = missedVolume,
                    note = json.optString("note", ""),
                    consumption = consumption
                )
            )
        }
        requireUniqueIds(records.map { it.id }, "加油记录")

        // ---------- 费用记录 ----------
        val expenses = mutableListOf<ExpenseRecord>()
        val expenseArray = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expenseArray.length()) {
            val json = expenseArray.getJSONObject(i)
            val id = json.optLong("id", 0L)
            val vehicleId = json.optLong("vehicleId", 0L)
            val ts = json.optLong("timestamp", 0L)
            val category = json.optString("category").trim()
            val amount = json.optDouble("amount", Double.NaN)

            if (id <= 0L || vehicleId <= 0L) {
                throw IllegalArgumentException("备份文件损坏：费用记录 ID 或车辆 ID 非法")
            }
            if (ts <= 0L) throw IllegalArgumentException("备份文件损坏：费用记录时间戳非法")
            if (category.isBlank()) throw IllegalArgumentException("备份文件损坏：费用分类为空")
            requireFiniteNonNegative(amount, "费用金额")

            expenses.add(
                ExpenseRecord(
                    id = id,
                    vehicleId = vehicleId,
                    timestamp = ts,
                    category = category,
                    amount = amount,
                    locationName = json.getNullableString("locationName"),
                    floor = json.getNullableString("floor"),
                    area = json.getNullableString("area"),
                    spotNumber = json.getNullableString("spotNumber"),
                    isActive = json.optBoolean("isActive"),
                    notificationIgnored = json.optBoolean("notificationIgnored"),
                    fromLocation = json.getNullableString("fromLocation"),
                    toLocation = json.getNullableString("toLocation"),
                    maintenanceItem = json.getNullableString("maintenanceItem"),
                    note = json.optString("note", "")
                )
            )
        }
        requireUniqueIds(expenses.map { it.id }, "费用记录")

        // ---------- 自定义分类（v2 起）----------
        // null 表示备份里没这个字段（老备份），跟"字段是空列表"区分开
        val customCategories: List<String>? = root.optJSONArray("customCategories")
            ?.let { arr ->
                (0 until arr.length())
                    .mapNotNull { arr.optString(it, "").trim().takeIf { s -> s.isNotEmpty() } }
            }

        // ---------- 一次性写入 ----------
        val db = AppDatabase.getInstance(context)

        val validVehicleIds = vehicles.map { it.id }.toSet()
        val badFuelReferences = records.count { it.vehicleId !in validVehicleIds }
        val badExpenseReferences = expenses.count { it.vehicleId !in validVehicleIds }

        if (badFuelReferences > 0) {
            throw IllegalArgumentException(
                "备份文件损坏：$badFuelReferences 条加油记录引用了不存在的车辆"
            )
        }
        if (badExpenseReferences > 0) {
            throw IllegalArgumentException(
                "备份文件损坏：$badExpenseReferences 条费用记录引用了不存在的车辆"
            )
        }

        // 旧版本数据可能已有多条激活停车记录，恢复时保留最近一条为激活状态。
        val validExpenses = normalizeActiveParking(expenses)

        db.withTransaction {
            // 清空（先子表再父表）
            db.expenseDao().deleteAll()
            db.fuelDao().deleteAll()
            db.vehicleDao().deleteAll()

            // 先恢复车辆，再恢复两种关联记录。任何一条失败都会回滚整个事务。
            vehicles.forEach { db.vehicleDao().insert(it) }
            records.forEach { db.fuelDao().insert(it) }
            validExpenses.forEach { db.expenseDao().insert(it) }
        }

        // 事务成功后，才更新 SharedPreferences（不受 Room 事务管理）
        customCategories?.let { ExpenseCategoryStore.replaceAll(it) }
    }

    // ============================================================
    // 文件读写
    // ============================================================

    suspend fun writeBackupToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val json = createBackupJson(context)
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法创建备份文件")
    }

    suspend fun restoreFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
            it.readText()
        } ?: throw IllegalStateException("无法读取备份文件")

        restoreFromJson(context, json)
    }
}

// ============================================================
// 数据校验辅助
// ============================================================

private fun requireFiniteNonNegative(value: Double, fieldName: String) {
    require(value.isFinite() && value >= 0.0) {
        "备份文件损坏：${fieldName}必须是有效的非负数"
    }
}

private fun requireNullableFiniteNonNegative(value: Double?, fieldName: String) {
    if (value != null) requireFiniteNonNegative(value, fieldName)
}

private fun requireUniqueIds(ids: List<Long>, tableName: String) {
    if (ids.any { it <= 0L } || ids.toSet().size != ids.size) {
        throw IllegalArgumentException("备份文件损坏：${tableName}存在无效或重复的 ID")
    }
}

private fun normalizeActiveParking(expenses: List<ExpenseRecord>): List<ExpenseRecord> {
    val activeParkingWinnerByVehicle = expenses
        .asSequence()
        .filter { it.category == "停车" && it.isActive }
        .groupBy { it.vehicleId }
        .mapValues { (_, records) ->
            records.maxWithOrNull(
                compareBy<ExpenseRecord> { it.timestamp }.thenBy { it.id }
            )?.id
        }

    return expenses.map { expense ->
        if (
            expense.category == "停车" && expense.isActive &&
            activeParkingWinnerByVehicle[expense.vehicleId] != expense.id
        ) {
            expense.copy(isActive = false)
        } else {
            expense
        }
    }
}

// ============================================================
// JSON 辅助
// ============================================================

private fun JSONObject.getNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    if (!value.isFinite()) {
        throw IllegalArgumentException("备份文件字段非法：$key")
    }
    return value
}

private fun JSONObject.getNullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return try {
        getLong(key)
    } catch (e: Exception) {
        throw IllegalArgumentException("备份文件字段非法：$key", e)
    }
}

private fun JSONObject.getNullableString(key: String): String? {
    return if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
