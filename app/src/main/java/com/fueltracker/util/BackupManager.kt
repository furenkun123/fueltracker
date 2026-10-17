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

    fun generateFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "随记油耗备份$dateStr.json"
    }

    // ============================================================
    // 备份
    // ============================================================

    suspend fun createBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val vehicles = db.vehicleDao().getAllVehicles()
        val records = db.fuelDao().getAllRecords()
        val expenses = db.expenseDao().getAll()

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
            vehicles.add(
                Vehicle(
                    id = json.optLong("id"),
                    brand = json.optString("brand"),
                    series = json.optString("series"),
                    year = json.optInt("year"),
                    model = json.optString("model"),
                    fuelType = json.optString("fuelType", "汽油"),
                    fuelGrade = json.optString("fuelGrade", "92#"),
                    tankCapacity = json.optDouble("tankCapacity", 0.0),
                    officialConsumption = json.getNullableDouble("officialConsumption"),
                    initialOdometer = json.optDouble("initialOdometer", 20.0),
                    initialFuel = json.optDouble("initialFuel", 0.0),
                    deliveryDate = json.getNullableLong("deliveryDate"),
                    isCurrent = json.optBoolean("isCurrent")
                )
            )
        }

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
            val ts = json.optLong("timestamp")
            if (ts <= 0L) {
                throw IllegalArgumentException("备份文件损坏：加油记录时间戳非法")
            }
            records.add(
                FuelRecord(
                    id = json.optLong("id"),
                    vehicleId = json.optLong("vehicleId"),
                    timestamp = ts,
                    fuelGrade = json.optString("fuelGrade", "92#"),
                    odometer = json.getNullableDouble("odometer"),
                    volume = json.optDouble("volume"),
                    totalAmount = json.getNullableDouble("totalAmount"),
                    discount = json.getNullableDouble("discount"),
                    actualPaidAmount = json.optDouble("actualPaidAmount"),
                    unitPrice = json.optDouble("unitPrice"),
                    remainingFuel = json.getNullableDouble("remainingFuel"),
                    isFull = json.optBoolean("isFull"),
                    isInitialRecord = json.optBoolean("isInitialRecord"),
                    initialFuel = json.getNullableDouble("initialFuel"),
                    hasMissedRecord = json.optBoolean("hasMissedRecord"),
                    missedOdometer = json.getNullableDouble("missedOdometer"),
                    missedVolume = json.getNullableDouble("missedVolume"),
                    note = json.optString("note", ""),
                    consumption = json.getNullableDouble("consumption")
                )
            )
        }

        // ---------- 费用记录 ----------
        val expenses = mutableListOf<ExpenseRecord>()
        val expenseArray = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until expenseArray.length()) {
            val json = expenseArray.getJSONObject(i)
            val ts = json.optLong("timestamp")
            if (ts <= 0L) {
                throw IllegalArgumentException("备份文件损坏：费用记录时间戳非法")
            }
            expenses.add(
                ExpenseRecord(
                    id = json.optLong("id"),
                    vehicleId = json.optLong("vehicleId"),
                    timestamp = ts,
                    category = json.optString("category"),
                    amount = json.optDouble("amount", 0.0),
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
        val validRecords = records.filter { it.vehicleId in validVehicleIds }
        val validExpenses = expenses.filter { it.vehicleId in validVehicleIds }

        if (validRecords.size != records.size) {
            throw IllegalArgumentException(
                "备份文件损坏：${records.size - validRecords.size} 条加油记录引用了不存在的车辆"
            )
        }
        if (validExpenses.size != expenses.size) {
            throw IllegalArgumentException(
                "备份文件损坏：${expenses.size - validExpenses.size} 条费用记录引用了不存在的车辆"
            )
        }

        db.withTransaction {
            // 清空（外键顺序：先费用/加油，再车辆）
            db.expenseDao().deleteAll()
            db.fuelDao().deleteAll()
            db.vehicleDao().deleteAll()

            // 插入（主键顺序：先车辆，再关联表）
            vehicles.forEach { db.vehicleDao().insert(it) }
            validRecords.forEach { db.fuelDao().insert(it) }
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
// JSON 辅助
// ============================================================

private fun JSONObject.getNullableDouble(key: String): Double? {
    if (isNull(key) || !has(key)) return null
    val v = optDouble(key)
    if (!v.isFinite()) {
        throw IllegalArgumentException("备份文件字段非法：$key")
    }
    return v
}

private fun JSONObject.getNullableLong(key: String): Long? {
    return if (isNull(key) || !has(key)) null else optLong(key)
}

private fun JSONObject.getNullableString(key: String): String? {
    return if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotEmpty() }
}