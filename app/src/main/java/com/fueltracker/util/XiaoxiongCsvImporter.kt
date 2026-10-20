package com.fueltracker.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

object XiaoxiongCsvImporter {

    private const val TAG = "XiaoxiongCsv"
    private const val DEFAULTFUELGRADE = "92"
    private const val FALLBACKUNITPRICE = 8.0
    private const val MAXSANEUNITPRICE = 50.0

    private val DatePatterns = listOf("yyyy/MM/dd", "yyyy-MM-dd", "yyyy/M/d", "yyyy-M-d")

    suspend fun parseFuelCsv(
        context: Context,
        uri: Uri,
        vehicleId: Long
    ): Result<List<FuelRecord>> = withContext(Dispatchers.IO) {
        try {
            val lines = readCsvLines(context, uri)
            var skipCount = 0
            val rows = mutableListOf<XiaoxiongFuelRow>()

            lines.forEachIndexed { index, line ->
                try {
                    val cols = splitCsvLine(line)
                    if (cols.size < 8) throw IllegalArgumentException("列数不足")

                    val odometer = cols[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() }
                        ?: throw IllegalArgumentException("里程非法")
                    val paidAmount = cols[4].trim().toDoubleOrNull()?.takeIf { it.isFinite() }
                        ?: throw IllegalArgumentException("金额非法")

                    // 修复：原价增加有限性和正数校验，防止 Infinity 混入
                    val originalPriceRaw = cols[3].trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

                    val unitPriceRaw = cols[2].trim().toDoubleOrNull()?.takeIf { it.isFinite() }

                    rows.add(
                        XiaoxiongFuelRow(
                            date = cols[0].trim(),
                            odometer = odometer,
                            unitPrice = unitPriceRaw ?: 0.0,
                            originalPrice = originalPriceRaw,
                            paidAmount = paidAmount,
                            fuelGrade = cols[5].trim(),
                            isFull = parseFlag(cols[6], "加满", "没加满"),
                            hasWarningLight = parseFlag(cols[7], "亮", "没亮")
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "跳过第 ${index + 1} 个有效行数据: 格式异常", e)
                    skipCount++
                }
            }

            val records = rows.mapNotNull { row ->
                val timestamp = parseDate(row.date)
                if (timestamp == null) {
                    skipCount++
                    return@mapNotNull null
                }
                if (row.odometer <= 0 || row.paidAmount <= 0) {
                    skipCount++
                    return@mapNotNull null
                }

                var isEstimated = false
                val unitPrice = row.unitPrice.takeIf { it in 0.01..MAXSANEUNITPRICE } ?: run {
                    isEstimated = true
                    FALLBACKUNITPRICE
                }

                // 修复：计算出的油量必须有限且大于 0，防止 Double.MAX_VALUE / 0.01 溢出
                val volume = row.paidAmount / unitPrice
                if (!volume.isFinite() || volume <= 0.0) {
                    skipCount++
                    return@mapNotNull null
                }

                // 修复：合并备注信息，避免“估算”覆盖“油灯亮”
                val noteParts = mutableListOf("小熊导入")
                if (isEstimated) noteParts.add("单价异常(油量估算)")
                if (row.hasWarningLight) noteParts.add("油灯亮")
                val finalNote = noteParts.joinToString(" · ")

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
                    note = finalNote,
                    hasMissedRecord = false
                )
            }

            Log.d(TAG, "加油解析完成: 成功 ${records.size}, 跳过 $skipCount")
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "读取加油 CSV 失败", e)
            Result.failure(e)
        }
    }

    suspend fun parseExpenseCsv(
        context: Context,
        uri: Uri,
        vehicleId: Long
    ): Result<List<ExpenseRecord>> = withContext(Dispatchers.IO) {
        try {
            val lines = readCsvLines(context, uri)
            var skipCount = 0
            val rows = mutableListOf<XiaoxiongExpenseRow>()

            lines.forEachIndexed { index, line ->
                try {
                    val cols = splitCsvLine(line)
                    if (cols.size < 3) throw IllegalArgumentException("列数不足")

                    val amount = cols[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() }
                        ?: throw IllegalArgumentException("金额非法")

                    rows.add(
                        XiaoxiongExpenseRow(
                            date = cols[0].trim(),
                            amount = amount,
                            category = cols[2].trim()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "跳过第 ${index + 1} 行费用数据: 格式异常", e)
                    skipCount++
                }
            }

            val records = rows.mapNotNull { row ->
                val timestamp = parseDate(row.date)
                if (timestamp == null || row.amount <= 0) {
                    skipCount++
                    return@mapNotNull null
                }

                ExpenseRecord(
                    vehicleId = vehicleId,
                    timestamp = timestamp,
                    category = row.category.ifBlank { "其他" },
                    amount = row.amount,
                    note = "小熊导入"
                )
            }

            Log.d(TAG, "费用解析完成: 成功 ${records.size}, 跳过 $skipCount")
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "读取费用 CSV 失败", e)
            Result.failure(e)
        }
    }

    private fun readCsvLines(context: Context, uri: Uri): List<String> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")
        return stream.bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence.map { it.removePrefix("\uFEFF").trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        buffer.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' if !inQuotes -> {
                    result.add(buffer.toString()); buffer.clear()
                }
                else -> buffer.append(c)
            }
            i++
        }
        if (inQuotes) throw IllegalArgumentException("CSV 存在未闭合的双引号")
        result.add(buffer.toString())
        return result
    }

    private fun parseDate(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        for (pattern in DatePatterns) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            val date = format.parse(value, position)
            if (date != null && position.index == value.length) {
                return date.time
            }
        }
        return null
    }

    private fun parseFlag(raw: String, positive: String, negative: String): Boolean {
        val value = raw.trim()
        return when {
            value.contains(negative) -> false
            value.contains(positive) -> true
            else -> throw IllegalArgumentException("无法识别的标记状态：$value")
        }
    }

    private fun normalizeFuelGrade(raw: String): String {
        val value = raw.trim().ifBlank { DEFAULTFUELGRADE }
        return if (value.contains("#")) value else "$value#"
    }
}