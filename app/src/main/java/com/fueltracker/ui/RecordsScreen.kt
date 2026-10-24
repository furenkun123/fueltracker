package com.fueltracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.components.scrollToHideBottomBar
import com.fueltracker.util.ConsumptionResult
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.IntervalConfidence
import com.fueltracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val RECORD_DATE_FORMAT by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

@Composable
fun RecordsScreen(
    viewModel: MainViewModel,
    onEdit: (FuelRecord) -> Unit
) {
    val records by viewModel.records.collectAsState()
    val vehicle by viewModel.currentVehicle.collectAsState()
    val listState = rememberLazyListState()

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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "全部加油记录", fontWeight = FontWeight.Bold, fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).scrollToHideBottomBar(listState, viewModel),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item(key = "filter_bar") {
                RecordFilterBar(filterType, selectedYear, selectedMonth, selectedDay, { filterType = it }, { showDatePicker = true })
            }
            item(key = "statistics") { RecordStatistics(displayRecords, vehicle) }
            item(key = "spacer") { Spacer(modifier = Modifier.height(8.dp)) }

            if (displayRecords.isEmpty()) {
                item(key = "empty_state") { EmptyRecordsState(modifier = Modifier.fillMaxWidth().height(300.dp)) }
            } else {
                items(displayRecords, key = { it.id }) { record ->
                    FuelRecordCard(record, consumptionMap[record.id], { viewModel.deleteRecord(record) }, { onEdit(record) })
                }
            }
        }
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
private fun MiuixCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).then(modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { content() }
}

/** 👇 核心优化 1：抽离统一的统计格子 */
@Composable
private fun StatCell(title: String, value: String, isHighlight: Boolean = false) {
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RecordStatistics(displayRecords: List<FuelRecord>, vehicle: Vehicle?) {
    val totalMoney = remember(displayRecords) { displayRecords.sumOf { it.actualPaidAmount } }
    val totalVolume = remember(displayRecords) { displayRecords.sumOf { it.volume } }
    val averageConsumption = remember(displayRecords, vehicle) {
        if (displayRecords.isEmpty()) null else FuelCalculator.calculateSummary(allRecords = displayRecords, vehicle = vehicle).averageConsumption
    }

    MiuixCard {
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
        FilterCapsule("全部", filterType == "全部") { onFilterChange("全部") }
        FilterCapsule("本月", filterType == "本月") { onFilterChange("本月") }
        FilterCapsule(customText, filterType == "按时间", onMonthClick)
    }
}

@Composable
private fun FilterCapsule(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Text(
            text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompactTextButton(text: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

// =================================================
// 加油记录卡片
// =================================================

@Composable
private fun FuelRecordCard(
    record: FuelRecord, calculationResult: ConsumptionResult?, onDelete: () -> Unit, onEdit: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val timeText = remember(record.timestamp) { RECORD_DATE_FORMAT.format(Date(record.timestamp)) }

    MiuixCard {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 顶栏：时间 + 标签 + 操作
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(timeText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    if (record.fuelGrade.isNotBlank()) TagChip(record.fuelGrade, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                    if (record.isFull) TagChip("加满", Color(0xFF34C759), Color(0xFFE8F8EC))
                    if (record.hasMissedRecord) TagChip("含漏记", Color(0xFFFF9500), Color(0xFFFFF4E5))

                    when {
                        calculationResult?.isOutlier == true -> TagChip("偏离值", Color(0xFFFF3B30), Color(0xFFFFE5E5))
                        calculationResult?.quality?.confidence == IntervalConfidence.LOW || calculationResult?.quality?.confidence == IntervalConfidence.UNRELIABLE ->
                            TagChip("预估值", Color(0xFFFF9500), Color(0xFFFFF4E5))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    CompactTextButton("编辑", MaterialTheme.colorScheme.primary, onEdit)
                    CompactTextButton("删除", Color(0xFFFF3B30).copy(alpha = 0.8f)) { showDeleteDialog = true }
                }
            }

            // 核心数据
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("¥", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text("%.2f".format(record.actualPaidAmount), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("%.2f L · 单价 %.2f元/L".format(record.volume, record.unitPrice), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (calculationResult != null && calculationResult.distance > 0) {
                        Text("%.2f L/100km".format(calculationResult.consumption), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (calculationResult.isOutlier) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary)
                        Text(if (calculationResult.isEstimated) "估算行驶 %.0f km".format(calculationResult.distance) else "行驶 %.0f km".format(calculationResult.distance), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(record.odometer?.takeIf { it > 0 }?.let { "%.0f km".format(it) } ?: "未记录里程", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 补充细节 (有才显示)
            val hasExtraInfo = (record.odometer ?: 0.0) > 0 || record.remainingFuel != null || record.hasMissedRecord || record.note.isNotBlank()
            if (hasExtraInfo) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if ((record.odometer ?: 0.0) > 0) Text("仪表盘里程: ${record.odometer!!.toInt()} km", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    record.remainingFuel?.takeIf { it.isFinite() }?.let { Text("加油后剩余: %.2f L".format(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (record.hasMissedRecord) Text("漏记数据: 约 ${record.missedOdometer ?: 0.0} km / ${record.missedVolume ?: 0.0} L", fontSize = 11.sp, color = Color(0xFFFF9500))
                    if (record.note.isNotBlank()) Text("备注: ${record.note}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }
            }
        }
    }

    if (showDeleteDialog) {
        MiuiConfirmDialog(
            title = "删除记录",
            message = "确定删除该条加油记录吗？删除后相关油耗计算将重新推导。",
            confirmText = "删除",
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = {
                showDeleteDialog = false
            }
        )
    }
}

@Composable
private fun TagChip(text: String, color: Color, bgColor: Color) {
    Surface(color = bgColor, shape = RoundedCornerShape(4.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

// =================================================
// 三滚轮日期选择弹窗
// =================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePicker3WheelSheet(initialYear: Int, initialMonth: Int, initialDay: Int, availableYears: List<Int>, onSelect: (Int, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var tempYear by remember { mutableIntStateOf(initialYear) }
    var tempMonth by remember { mutableIntStateOf(initialMonth) }
    var tempDay by remember { mutableIntStateOf(initialDay) }

    val maxDays = remember(tempYear, tempMonth) {
        Calendar.getInstance().apply { set(Calendar.YEAR, tempYear); set(Calendar.MONTH, tempMonth - 1); set(Calendar.DAY_OF_MONTH, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // 👇 核心优化 2：自动纠偏，省去手动计算天数的冗余逻辑
    LaunchedEffect(maxDays) {
        if (tempDay > maxDays) tempDay = maxDays
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("选择日期", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onSelect(tempYear, tempMonth, tempDay) }) { Text("确定", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                WheelList(selectedValue = tempYear, items = availableYears, modifier = Modifier.weight(1f), style = WheelStyle.DatePicker, itemLabel = { "$it 年" }, onChange = { tempYear = it })
                WheelList(selectedValue = tempMonth, items = (1..12).toList(), modifier = Modifier.weight(1f), style = WheelStyle.DatePicker, itemLabel = { "%02d 月".format(it) }, onChange = { tempMonth = it })
                WheelList(selectedValue = tempDay, items = (0..maxDays).toList(), modifier = Modifier.weight(1f), style = WheelStyle.DatePicker, itemLabel = { if (it == 0) "全月" else "%02d 日".format(it) }, onChange = { tempDay = it })
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// =================================================
// 空状态与确认弹窗
// =================================================

@Composable
private fun EmptyRecordsState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(64.dp).width(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocalGasStation, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(32.dp).width(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("暂无加油记录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("点击首页“记加油”添加新记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiuiConfirmDialog(title: String, message: String, confirmText: String = "确定", dismissText: String = "取消", confirmColor: Color = Color(0xFFFF3B30), onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(0.dp)) { Text(dismissText, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) }
                    Box(Modifier.width(0.6.dp).height(30.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                    TextButton(onClick = onConfirm, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(0.dp)) { Text(confirmText, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = confirmColor) }
                }
            }
        }
    }
}