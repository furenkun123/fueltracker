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
    private const val FALLBACKUNITPRICE = 8.0
    private const val MAXSANEUNITPRICE = 50.0

    private val DatePatterns = listOf("yyyy/MM/dd", "yyyy-MM-dd", "yyyy/M/d", "yyyy-M-d")

    suspend fun parseFuelCsv(
        context: Context,
        uri: Uri,
        vehicleId: Long
    ): Result<List<FuelRecord>> = withContext(Dispatchers.IO) {
        try {
            var skipCount = 0
            val records = mutableListOf<FuelRecord>()

            forEachCsvLine(context, uri) { index, line ->
                try {
                    val cols = splitCsvLine(line)
                    if (cols.size < 8) {
                        throw IllegalArgumentException("列数不足")
                    }

                    val date = cols[0].trim()

                    val odometer = cols[1].trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() }
                        ?: throw IllegalArgumentException("里程非法")

                    val unitPriceRaw = cols[2].trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() }

                    val originalPrice = cols[3].trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

                    val paidAmount = cols[4].trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() }
                        ?: throw IllegalArgumentException("金额非法")

                    val fuelGrade = cols[5].trim()
                    val isFull = parseFlag(cols[6], "加满", "没加满")
                    val hasWarningLight = parseFlag(cols[7], "亮", "没亮")

                    val timestamp = parseDate(date)

                    if (timestamp == null || odometer <= 0 || paidAmount <= 0) {
                        skipCount++
                        return@forEachCsvLine
                    }

                    var isEstimated = false

                    val unitPrice = unitPriceRaw
                        ?.takeIf { it in 0.01..MAXSANEUNITPRICE }
                        ?: run {
                            isEstimated = true
                            FALLBACKUNITPRICE
                        }

                    val validOriginalPrice = originalPrice.takeIf {
                        it >= paidAmount - 0.01 && it > 0.0
                    }

                    val volume = if (validOriginalPrice != null) {
                        validOriginalPrice / unitPrice
                    } else {
                        paidAmount / unitPrice
                    }

                    if (!volume.isFinite() || volume <= 0.0) {
                        skipCount++
                        return@forEachCsvLine
                    }

                    val noteParts = mutableListOf("小熊导入")

                    if (isEstimated) {
                        noteParts.add("单价异常(油量估算)")
                    }

                    if (hasWarningLight) {
                        noteParts.add("油灯亮")
                    }

                    val finalNote = noteParts.joinToString(" · ")

                    val finalDiscount = validOriginalPrice?.let { orig ->
                        val diff = orig - paidAmount
                        if (diff > 0.01) diff else null
                    }

                    records.add(
                        FuelRecord(
                            vehicleId = vehicleId,
                            timestamp = timestamp,
                            odometer = odometer,
                            volume = volume,
                            unitPrice = unitPrice,
                            actualPaidAmount = paidAmount,
                            totalAmount = validOriginalPrice,
                            discount = finalDiscount,
                            fuelGrade = normalizeFuelGrade(fuelGrade),
                            isFull = isFull,
                            note = finalNote,
                            hasMissedRecord = false
                        )
                    )

                } catch (e: Exception) {
                    Log.w(TAG, "跳过第 ${index + 1} 个有效行数据: 格式异常", e)
                    skipCount++
                }
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
            var skipCount = 0
            val records = mutableListOf<ExpenseRecord>()

            forEachCsvLine(context, uri) { index, line ->
                try {
                    val cols = splitCsvLine(line)

                    if (cols.size < 3) {
                        throw IllegalArgumentException("列数不足")
                    }

                    val timestamp = parseDate(cols[0].trim())

                    val amount = cols[1].trim().toDoubleOrNull()
                        ?.takeIf { it.isFinite() }
                        ?: throw IllegalArgumentException("金额非法")

                    val category = cols[2].trim()

                    if (timestamp == null || amount <= 0) {
                        skipCount++
                        return@forEachCsvLine
                    }

                    records.add(
                        ExpenseRecord(
                            vehicleId = vehicleId,
                            timestamp = timestamp,
                            category = category.ifBlank { "其他" },
                            amount = amount,
                            note = "小熊导入"
                        )
                    )

                } catch (e: Exception) {
                    Log.w(TAG, "跳过第 ${index + 1} 行费用数据: 格式异常", e)
                    skipCount++
                }
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
    /**
     * 逐行读取 CSV。
     *
     * 不再将整个文件转换为 List<String>，
     * 每读取一行就交给调用方处理。
     */
    private fun forEachCsvLine(
        context: Context,
        uri: Uri,
        action: (Int, String) -> Unit
    ) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")

        stream.bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence
                .map { it.removePrefix("\uFEFF").trim() }
                .filter { it.isNotEmpty() }
                .forEachIndexed { index, line ->
                    action(index, line)
                }
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
        val value = raw.trim()
            .replace("＃", "#")
            .replace(Regex("\\s+"), "")

        if (value.isBlank()) return "92号"

        // 汽油标号
        val gasolineRegex = Regex(
            "^(89|90|92|93|95|97|98)[号#]?(?:汽油)?$"
        )

        val gasolineMatch = gasolineRegex.matchEntire(value)
        if (gasolineMatch != null) {
            return "${gasolineMatch.groupValues[1]}号"
        }

        // 柴油标号
        val dieselRegex = Regex(
            "^(-?\\d{1,2})[号#]?柴油$"
        )

        val dieselMatch = dieselRegex.matchEntire(value)
        if (dieselMatch != null) {
            return "${dieselMatch.groupValues[1]}号柴油"
        }

        // 空白或无法识别的内容统一归为 92 号
        Log.w(TAG, "无法识别小熊油耗燃油标号，按 92 号处理：$raw")
        return "92号"
    }
}