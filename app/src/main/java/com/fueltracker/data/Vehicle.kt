package com.fueltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicles"
)
data class Vehicle(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val brand: String,

    val series: String,

    val model: String,

    val year: Int,

    // 油箱容量
    val tankCapacity: Double,

    val fuelType: String,

    val fuelGrade: String,

    // 官方油耗
    val officialConsumption: Double? = null,

    // 提车公里数
    val initialOdometer: Double = 20.0,

    // 初始剩余油量
    val initialFuel: Double = 0.0,

    // 提车日期（毫秒时间戳，可为空）
    val deliveryDate: Long? = null,

    val isCurrent: Boolean = false


)