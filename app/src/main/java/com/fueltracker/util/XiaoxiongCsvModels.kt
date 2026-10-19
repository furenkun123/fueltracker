package com.fueltracker.util

/** 小熊简化版 - 加油记录行 */
data class XiaoxiongFuelRow(
    val date: String,        // 列A，如 "2026/10/19"
    val odometer: Double,    // 列B，公里数
    val unitPrice: Double,   // 列C，单价
    val originalPrice: Double, // 列D，原价
    val paidAmount: Double,  // 列E，实付价
    val fuelGrade: String,   // 列F，燃油标号
    val isFull: Boolean,     // 列G，是否加满
    val hasWarningLight: Boolean // 列H，有无亮灯
)

/** 小熊简化版 - 费用记录行 */
data class XiaoxiongExpenseRow(
    val date: String,        // 列A，如 "2026/10/19"
    val amount: Double,      // 列B，价格
    val category: String     // 列C，费用类别
)