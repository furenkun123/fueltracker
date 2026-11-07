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

    // 提车日期（毫秒时间戳，可为空）
    val deliveryDate: Long? = null,

    val isCurrent: Boolean = false


)