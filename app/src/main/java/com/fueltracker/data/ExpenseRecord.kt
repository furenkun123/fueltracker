package com.fueltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 预置费用分类。
 * "加油"不在其中 —— 加油由 FuelRecord 独立管理。
 */
val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "停车",
    "高速",
    "保养",
    "保险",
    "维修",
    "罚款",
    "洗车"
)

/** 停车分类名，避免各处硬编码字符串 */
const val CATEGORY_PARKING = "停车"

@Entity(tableName = "expenses")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val timestamp: Long,

    /** 分类名，如"停车"/"高速"/"保养"等 */
    val category: String,

    val amount: Double = 0.0,

    // 停车专用
    val locationName: String? = null,
    val floor: String? = null,
    val area: String? = null,
    val spotNumber: String? = null,
    val isActive: Boolean = false,

    /**
     * 通知是否已被用户忽略。
     * true 表示长期停车场景，卡片保留但不发通知。
     * 取车（isActive=false）时无意义。
     */
    val notificationIgnored: Boolean = false,

    // 高速专用
    val fromLocation: String? = null,
    val toLocation: String? = null,

    // 保养/维修专用
    val maintenanceItem: String? = null,

    val note: String = ""
)