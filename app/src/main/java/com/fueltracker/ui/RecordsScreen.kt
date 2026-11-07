package com.fueltracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalGasStation
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.LazyColumnScrollbar
import com.fueltracker.ui.miuix.dialogs.MiuixBottomSheetWrapper
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.components.scrollToHideFab
import com.fueltracker.ui.miuix.MiuixCard
import com.fueltracker.ui.miuix.MiuixChip
import com.fueltracker.ui.miuix.MiuixFilterChip
import com.fueltracker.ui.miuix.MiuixTextButton
import com.fueltracker.util.ConsumptionResult
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.IntervalConfidence
import com.fueltracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs


private val RECORD_DATE_FORMAT by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

@Composable
fun RecordsScreen(
    viewModel: MainViewModel,
    onEdit: (FuelRecord) -> Unit
) {
    val records by viewModel.records.collectAsState()
    val vehicle by viewModel.currentVehicle.collectAsState()
    val listState = rememberLazyListState()
    val isFloating = viewModel.isFloatingBottomBar

    val consumptionMap = remember(records, vehicle) { FuelCalculator.calculateMap(records, vehicle) }

    val now = remember { Calendar.getInstance() }
    val currentYear = remember(now) { now.get(Calendar.YEAR) }

    var filterType by remember { mutableStateOf("全部") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableIntStateOf(0) }

    val availableYears = remember(records, currentYear, selectedYear) {
        val recordYears = records.map { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR) }
        (recordYears + currentYear + selectedYear).distinct().sortedDescending()
    }

    val displayRecords = remember(records, filterType, selectedYear, selectedMonth, selectedDay) {
        val sorted = records.sortedByDescending { it.timestamp }
        when (filterType) {
            "本月" -> {
                val cNow = Calendar.getInstance()
                sorted.filter { record ->
                    val c = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                    c.get(Calendar.YEAR) == cNow.get(Calendar.YEAR) && c.get(Calendar.MONTH) == cNow.get(Calendar.MONTH)
                }
            }
            "按时间" -> {
                sorted.filter { record ->
                    val c = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                    c.get(Calendar.YEAR) == selectedYear &&
                            (c.get(Calendar.MONTH) + 1) == selectedMonth &&
                            (selectedDay == 0 || c.get(Calendar.DAY_OF_MONTH) == selectedDay)
                }
            }
            else -> sorted
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollToHideFab(listState, viewModel),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = if (isFloating) 120.dp else 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "records_title") {
                Text(
                    text = "加油记录",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-15).dp)
                        .padding(horizontal = 15.dp, vertical = 0.dp)
                )
            }

            item(key = "filter_bar") {
                RecordFilterBar(filterType, selectedYear, selectedMonth, selectedDay, { filterType = it }, { showDatePicker = true })
            }
            item(key = "statistics") {
                RecordStatistics(displayRecords, vehicle)
            }
            item(key = "spacer") { Spacer(modifier = Modifier.height(8.dp)) }

            if (displayRecords.isEmpty()) {
                item(key = "empty_state") { EmptyRecordsState(modifier = Modifier.fillMaxWidth().height(300.dp)) }
            } else {
                items(displayRecords, key = { it.id }) { record ->
                    FuelRecordCard(
                            record = record,
                            calculationResult = consumptionMap[record.id],
                            vehicle = vehicle,
                            onEdit = { onEdit(record) }
                        )
                    }
                }
            }
        }
        LazyColumnScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }

    if (showDatePicker) {
        DatePicker3WheelSheet(
            initialYear = selectedYear, initialMonth = selectedMonth, initialDay = selectedDay, availableYears = availableYears,
            onSelect = { y, m, d -> selectedYear = y; selectedMonth = m; selectedDay = d; filterType = "按时间"; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

// =================================================
// 基础卡片 & 统计区域
// =================================================

@Composable
private fun StatCell(title: String, value: String, isHighlight: Boolean = false) {
    Column {
        Text(title, color = MiuixTheme.colorScheme.onSurfaceSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isHighlight) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RecordStatistics(displayRecords: List<FuelRecord>, vehicle: Vehicle?) {
    val totalMoney = remember(displayRecords) { displayRecords.sumOf { it.actualPaidAmount } }
    val totalVolume = remember(displayRecords) { displayRecords.sumOf { it.volume } }
    val averageConsumption = remember(displayRecords, vehicle) {
        if (displayRecords.isEmpty()) null else FuelCalculator.calculateSummary(allRecords = displayRecords, vehicle = vehicle).averageConsumption
    }

    MiuixCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell("次数", "${displayRecords.size}")
            StatCell("金额", "¥%.2f".format(totalMoney))
            StatCell("油量", "%.1f L".format(totalVolume))
            StatCell("油耗", averageConsumption?.let { "%.1f".format(it) } ?: "--", isHighlight = true)
        }
    }
}

// =================================================
// 筛选组件
// =================================================

@Composable
private fun RecordFilterBar(filterType: String, year: Int, month: Int, day: Int, onFilterChange: (String) -> Unit, onMonthClick: () -> Unit) {
    val customText = remember(year, month, day) { if (day == 0) "${year}.${month}月 ▼" else "${year}.${month}.${day} ▼" }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiuixFilterChip("全部", filterType == "全部") { onFilterChange("全部") }
        MiuixFilterChip("本月", filterType == "本月") { onFilterChange("本月") }
        MiuixFilterChip(customText, filterType == "按时间", onMonthClick)
    }
}

// =================================================
// 加油记录卡片
// =================================================

@Composable
private fun FuelRecordCard(
    record: FuelRecord,
    calculationResult: ConsumptionResult?,
    vehicle: Vehicle?,
    onEdit: () -> Unit
) {
    val timeText = remember(record.timestamp) { RECORD_DATE_FORMAT.format(Date(record.timestamp)) }

    val isRemainingFuelAnomaly = vehicle != null &&
            vehicle.tankCapacity > 0.0 &&
            record.isFull &&
            record.remainingFuel != null &&
            abs(record.remainingFuel - vehicle.tankCapacity) > 0.1

    MiuixCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth() // ✅ 确保内层撑满卡片
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. 顶部：时间、标签、编辑
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f), // ✅ 限制左侧标签区宽度，防止挤压编辑按钮
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        timeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (record.fuelGrade.isNotBlank()) TagChip(record.fuelGrade, MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                    if (record.isFull) TagChip("加满", Color(0xFF34C759), Color(0xFF34C759).copy(alpha = 0.15f))
                    if (record.hasMissedRecord) TagChip("含漏记", Color(0xFFFF9500), Color(0xFFFF9500).copy(alpha = 0.15f))
                    if (isRemainingFuelAnomaly) TagChip("油量异常", Color(0xFFFF3B30), Color(0xFFFF3B30).copy(alpha = 0.15f))
                    when {
                        calculationResult?.isOutlier == true -> TagChip("偏离值", Color(0xFFFF3B30), Color(0xFFFF3B30).copy(alpha = 0.15f))
                        calculationResult?.quality?.confidence == IntervalConfidence.LOW || calculationResult?.quality?.confidence == IntervalConfidence.UNRELIABLE ->
                            TagChip("预估值", Color(0xFFFF9500), Color(0xFFFF9500).copy(alpha = 0.15f))
                    }
                }

                MiuixTextButton(
                    text = "编辑",
                    color = MiuixTheme.colorScheme.primary,
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 13.sp
                )
            }

            // 2. 金额与油耗
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✅ 左侧加上 weight(1f) 和 maxLines，彻底杜绝长文本把布局撑破
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("¥", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text("%.2f".format(record.actualPaidAmount), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "%.2f L · 单价 %.2f元/L".format(record.volume, record.unitPrice),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp)) // 左右增加一点间距

                Column(horizontalAlignment = Alignment.End) {
                    if (calculationResult != null && calculationResult.distance > 0) {
                        Text(
                            text = "%.2f L/100km".format(calculationResult.consumption),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (calculationResult.isOutlier) Color(0xFFFF3B30) else MiuixTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = if (calculationResult.isEstimated) "估算行驶 %.0f km".format(calculationResult.distance) else "行驶 %.0f km".format(calculationResult.distance),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = record.odometer?.takeIf { it > 0 }?.let { "%.0f km".format(it) } ?: "未记录里程",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            // 3. 底部额外信息
            val hasExtraInfo = (record.odometer ?: 0.0) > 0 || record.remainingFuel != null || record.hasMissedRecord || record.note.isNotBlank()
            if (hasExtraInfo) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if ((record.odometer ?: 0.0) > 0) {
                        Text("仪表盘里程: ${record.odometer!!.toInt()} km", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                    record.remainingFuel?.takeIf { it.isFinite() }?.let {
                        Text(
                            text = "加油后剩余: %.2f L".format(it),
                            fontSize = 11.sp,
                            color = if (isRemainingFuelAnomaly) Color(0xFFFF3B30) else MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                    if (record.hasMissedRecord) {
                        Text("漏记数据: 约 ${record.missedOdometer ?: 0.0} km / ${record.missedVolume ?: 0.0} L", fontSize = 11.sp, color = Color(0xFFFF9500))
                    }
                    if (record.note.isNotBlank()) {
                        Text("备注: ${record.note}", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color, bgColor: Color) {
    MiuixChip(
        text = text,
        backgroundColor = bgColor,
        contentColor = color,
        cornerRadius = 6.dp,
        paddingH = 6.dp,
        paddingV = 2.dp,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
    )
}

// =================================================
// 三滚轮日期选择弹窗
// =================================================

@Composable
private fun DatePicker3WheelSheet(initialYear: Int, initialMonth: Int, initialDay: Int, availableYears: List<Int>, onSelect: (Int, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var tempYear by remember { mutableIntStateOf(initialYear) }
    var tempMonth by remember { mutableIntStateOf(initialMonth) }
    var tempDay by remember { mutableIntStateOf(initialDay) }

    val maxDays = remember(tempYear, tempMonth) {
        Calendar.getInstance().apply { set(Calendar.YEAR, tempYear); set(Calendar.MONTH, tempMonth - 1); set(Calendar.DAY_OF_MONTH, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    LaunchedEffect(maxDays) {
        if (tempDay > maxDays) tempDay = maxDays
    }

    MiuixBottomSheetWrapper(
        title = "选择日期",
        onDismiss = onDismiss,
        onConfirm = { onSelect(tempYear, tempMonth, tempDay) }
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            WheelList(selectedValue = tempYear, items = availableYears, modifier = Modifier.weight(1f), style = WheelStyle.DatePicker, itemLabel = { "$it 年" }, onChange = { tempYear = it })
            WheelList(selectedValue = tempMonth, items = (1..12).toList(), modifier = Modifier.weight(1f), style = WheelStyle.DatePicker, itemLabel = { "%02d 月".format(it) }, onChange = { tempMonth = it })
            WheelList(selectedValue = tempDay, items = (0..maxDays).toList(), modifier = Modifier.weight(1f), style = WheelStyle.DatePicker, itemLabel = { if (it == 0) "全月" else "%02d 日".format(it) }, onChange = { tempDay = it })
        }
        Spacer(Modifier.height(20.dp))
    }
}

// =================================================
// 空状态
// =================================================

@Composable
private fun EmptyRecordsState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(64.dp).width(64.dp).background(MiuixTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocalGasStation, null, tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.height(32.dp).width(32.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("暂无加油记录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("点击首页“记加油”添加新记录", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
    }
}
