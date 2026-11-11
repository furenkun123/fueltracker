package com.fueltracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ==========================================
// 金额相关
// ==========================================

/** 格式化为带符号金额：¥12.34 */
fun Double.toMoneyText(): String = "¥%.2f".format(this)

/** 格式化为纯数字金额：12.34 */
fun Double.toPlainMoneyText(): String = "%.2f".format(this)

/** 格式化为整数金额（图表或大数字用）：¥12 */
fun Double.toIntMoneyText(): String = "¥%.0f".format(this)

// ==========================================
// 比例/百分比相关
// ==========================================

/** 格式化为百分比：85%（传入 0.85f） */
fun Float.toPercentText(): String = "%.0f%%".format(this * 100)

// ==========================================
// 日期相关
// ==========================================

private val defaultDateTimeFormat by lazy { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

/** 格式化为短日期时间：10-27 14:30 */
fun Long.toDateTimeText(): String = defaultDateTimeFormat.format(Date(this))
/**
 * 格式化为带年份的日期时间：2026-11-11 14:30
 */
private val dateTimeFormatWithYear by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

fun Long.toDateTimeTextWithYear(): String =
    dateTimeFormatWithYear.format(Date(this))

/** 快速获取某个时间戳的年份 */
fun Long.getYear(): Int = Calendar.getInstance().apply { timeInMillis = this@getYear }.get(Calendar.YEAR)

