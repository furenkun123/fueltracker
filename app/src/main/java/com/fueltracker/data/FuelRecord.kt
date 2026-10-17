package com.fueltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_records")
data class FuelRecord(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val vehicleId: Long,

    val timestamp: Long,

    // 油品标号 (如 92#、95#、98# 等)
    val fuelGrade: String = "92#",

    // 加油时公里数
    val odometer: Double? = null,

    // 本次加油量 L
    val volume: Double,

    // 原价金额
    val totalAmount: Double? = null,

    // 优惠金额
    val discount: Double? = null,

    // 实际支付金额
    val actualPaidAmount: Double,

    // 单价 元/L
    val unitPrice: Double,

    // 加油后的剩余油量
    val remainingFuel: Double? = null,

    // 是否加满
    val isFull: Boolean = false,

    // 是否首次初始化
    val isInitialRecord: Boolean = false,

    // 首次记录的当前油量
    val initialFuel: Double? = null,

    // 是否存在上次漏记
    val hasMissedRecord: Boolean = false,

    // 漏记时的公里数
    val missedOdometer: Double? = null,

    // 漏记时的加油量
    val missedVolume: Double? = null,

    val note: String = "",

    // 用于存储本次闭合区间的百公里油耗
    val consumption: Double? = null
)