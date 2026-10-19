package com.fueltracker.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 小熊油耗 CSV 导入器。
 *
 * 支持格式（无表头、逗号分隔、可能带 UTF-8 BOM）：
 *  - 加油：日期, 里程, 单价, 原价, 实付, 燃油标号, 是否加满, 是否亮灯（8 列）
 *  - 费用：日期, 金额, 类别（3 列）
 *
 * 解析失败的行会被跳过并记录日志，不会中断整个导入。
 * 全部行都失败时仍返回 [Result.success]（空列表），由调用方决定如何提示。
 */
object XiaoxiongCsvImporter {

    private const val TAG = "XiaoxiongCsv"

    private const val DEFAULT_FUEL_GRADE = "92"
    private const val FALLBACK_UNIT_PRICE = 8.0
    private const val MAX_SANE_UNIT_PRICE = 50.0
    private const val IMPORT_NOTE = "小熊导入"
    private const val IMPORT_NOTE_WITH_WARNING = "小熊导入 · 油灯亮"

    /** 兼容 "2026/10/19"、"2026-10-19" 以及单数字月份/日期 */
    private val DatePatterns = listOf(
        "yyyy/MM/dd",
        "yyyy-MM-dd",
        "yyyy/M/d",
        "yyyy-M-d"
    )

    // ============================================================
    // 加油 CSV
    // ============================================================

    suspend fun parseFuelCsv(
        context: Context,
        uri: Uri,
        vehicleId: Long
    ): Result<List<FuelRecord>> = withContext(Dispatchers.IO) {
        try {
            val lines = readCsvLines(context, uri)
            var skipCount = 0

            // 第一阶段：文本 → XiaoxiongFuelRow
            val rows = mutableListOf<XiaoxiongFuelRow>()
            lines.forEach { line ->
                val cols = splitCsvLine(line)
                if (cols.size < 8) {
                    skipCount++
                    Log.w(TAG, "加油行列数不足，跳过: $line")
                    return@forEach
                }

                try {
                    rows.add(
                        XiaoxiongFuelRow(
                            date = cols[0].trim(),
                            odometer = cols[1].trim().toDoubleOrNull() ?: 0.0,
                            unitPrice = cols[2].trim().toDoubleOrNull() ?: 0.0,
                            originalPrice = cols[3].trim().toDoubleOrNull() ?: 0.0,
                            paidAmount = cols[4].trim().toDoubleOrNull() ?: 0.0,
                            fuelGrade = cols[5].trim(),
                            isFull = parseFlag(cols[6], positive = "加满", negative = "没加满"),
                            hasWarningLight = parseFlag(cols[7], positive = "亮", negative = "没亮")
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "解析加油行失败: $line", e)
                    skipCount++
                }
            }

            // 第二阶段：XiaoxiongFuelRow → FuelRecord（业务校验 + 字段映射）
            val records = rows.mapNotNull { row ->
                val timestamp = parseDate(row.date)
                if (timestamp == null) {
                    Log.w(TAG, "日期解析失败，跳过: ${row.date}")
                    skipCount++
                    return@mapNotNull null
                }

                if (row.odometer <= 0 || row.paidAmount <= 0) {
                    Log.w(TAG, "里程或实付金额无效，跳过: $row")
                    skipCount++
                    return@mapNotNull null
                }

                // 单价异常（缺失 / 离谱）时按 8 元/L 兜底，避免算出天文数字的升数
                val unitPrice = row.unitPrice.takeIf { it in 0.01..MAX_SANE_UNIT_PRICE }
                    ?: FALLBACK_UNIT_PRICE
                val volume = row.paidAmount / unitPrice

                FuelRecord(
                    vehicleId = vehicleId,
                    timestamp = timestamp,
                    odometer = row.odometer,
                    volume = volume,
                    unitPrice = unitPrice,
                    actualPaidAmount = row.paidAmount,
                    totalAmount = row.originalPrice.takeIf { it > 0 },
                    fuelGrade = normalizeFuelGrade(row.fuelGrade),
                    isFull = row.isFull,
                    note = if (row.hasWarningLight) IMPORT_NOTE_WITH_WARNING else IMPORT_NOTE,
                    hasMissedRecord = false
                )
            }

            Log.d(TAG, "加油记录解析完成: 成功 ${records.size}, 跳过 $skipCount")
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "读取加油 CSV 失败", e)
            Result.failure(e)
        }
    }

    // ============================================================
    // 费用 CSV
    // ============================================================

    suspend fun parseExpenseCsv(
        context: Context,
        uri: Uri,
        vehicleId: Long
    ): Result<List<ExpenseRecord>> = withContext(Dispatchers.IO) {
        try {
            val lines = readCsvLines(context, uri)
            var skipCount = 0

            // 第一阶段：文本 → XiaoxiongExpenseRow
            val rows = mutableListOf<XiaoxiongExpenseRow>()
            lines.forEach { line ->
                val cols = splitCsvLine(line)
                if (cols.size < 3) {
                    skipCount++
                    Log.w(TAG, "费用行列数不足，跳过: $line")
                    return@forEach
                }

                try {
                    rows.add(
                        XiaoxiongExpenseRow(
                            date = cols[0].trim(),
                            amount = cols[1].trim().toDoubleOrNull() ?: 0.0,
                            category = cols[2].trim()
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "解析费用行失败: $line", e)
                    skipCount++
                }
            }

            // 第二阶段：XiaoxiongExpenseRow → ExpenseRecord
            val records = rows.mapNotNull { row ->
                val timestamp = parseDate(row.date)
                if (timestamp == null) {
                    Log.w(TAG, "日期解析失败，跳过: ${row.date}")
                    skipCount++
                    return@mapNotNull null
                }

                if (row.amount <= 0) {
                    Log.w(TAG, "费用金额无效，跳过: $row")
                    skipCount++
                    return@mapNotNull null
                }

                ExpenseRecord(
                    vehicleId = vehicleId,
                    timestamp = timestamp,
                    category = row.category.ifBlank { "其他" },
                    amount = row.amount,
                    note = IMPORT_NOTE
                )
            }

            Log.d(TAG, "费用记录解析完成: 成功 ${records.size}, 跳过 $skipCount")
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "读取费用 CSV 失败", e)
            Result.failure(e)
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 读取 CSV 文本行：
     * - 去掉 UTF-8 BOM（[\\uFEFF]）
     * - 逐行 trim
     * - 过滤空行
     */
    private fun readCsvLines(context: Context, uri: Uri): List<String> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")

        return stream.bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence
                .map { it.removePrefix("\uFEFF").trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }

    /**
     * 支持引号的 CSV 行切分。
     * 小熊导出的类别名一般不含逗号，但加上这层保护成本很低，
     * 以后遇到 "保养,洗车" 这类字段也不会错位。
     */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // 转义的 ""
                        buffer.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' if !inQuotes -> {
                    result.add(buffer.toString())
                    buffer.clear()
                }
                else -> buffer.append(c)
            }
            i++
        }
        result.add(buffer.toString())
        return result
    }

    /**
     * 依次尝试多种日期格式，全部失败返回 null。
     * 使用 isLenient = false，避免 "2026/13/45" 这种越界日期被错误解析。
     */
    private fun parseDate(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        for (pattern in DatePatterns) {
            val format = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                isLenient = false
            }
            try {
                val date = format.parse(value)
                if (date != null) return date.time
            } catch (_: Exception) {
                // 试下一种格式
            }
        }
        return null
    }

    /**
     * 解析中文布尔字段，例如 "加满" / "没加满"、"亮" / "没亮"。
     * 先判否定词再判肯定词，因为 "没加满" 包含 "加满"。
     */
    private fun parseFlag(raw: String, positive: String, negative: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty()) return false
        if (value.contains(negative)) return false
        return value.contains(positive)
    }

    /**
     * 统一燃油标号格式，缺省补 92，未带 # 的补上 #。
     */
    private fun normalizeFuelGrade(raw: String): String {
        val value = raw.trim().ifBlank { DEFAULT_FUEL_GRADE }
        return if (value.contains("#")) value else "$value#"
    }
}