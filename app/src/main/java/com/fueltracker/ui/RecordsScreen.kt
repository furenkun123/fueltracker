package com.fueltracker.ui

// 👇 引入公共组件
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.MiuiBottomSheetWrapper
import com.fueltracker.ui.components.MiuiConfirmDialog
import com.fueltracker.ui.components.MiuiFilterChip
import com.fueltracker.ui.components.MiuiTextButton
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.components.scrollToHideFab
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiTopBar
import com.fueltracker.util.ConsumptionResult
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.IntervalConfidence
import com.fueltracker.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


private val RECORD_DATE_FORMAT by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

@Composable
fun RecordsScreen(
    viewModel: MainViewModel,
    isGlassEnabled: Boolean = false,
    hazeState: HazeState? = null,
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

        // 👇 1. 底层滚动列表（挂载 hazeSource）
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 👈 优化：只有在开启了效果且 hazeState 不为空时才挂载，防止崩溃
                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
                .scrollToHideFab(listState, viewModel),
            contentPadding = PaddingValues(top = 96.dp, bottom = if (isFloating) 120.dp else 80.dp) // 👈 顶部留白给悬浮顶栏
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

        // 👇 2. 悬浮顶栏（放在 Box 内部，用 align 悬浮）
        MiuiTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = "全部加油记录",
            isGlassEnabled = isGlassEnabled
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

    // 👈 直接复用公共 MiuiCard
    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
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
        // 👈 复用 MiuiFilterChip（原本是胶囊形 FilterCapsule，为了全局统一，这里使用标准 Chip）
        MiuiFilterChip("全部", filterType == "全部") { onFilterChange("全部") }
        MiuiFilterChip("本月", filterType == "本月") { onFilterChange("本月") }
        MiuiFilterChip(customText, filterType == "按时间", onMonthClick)
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

    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                    // 👈 替换为公共 MiuiTextButton
                    MiuiTextButton("编辑", MaterialTheme.colorScheme.primary, onEdit)
                    MiuiTextButton("删除", Color(0xFFFF3B30).copy(alpha = 0.8f)) { showDeleteDialog = true }
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

            // 补充细节
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
        // 👈 直接使用公共的底部弹窗版确认框
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

    // 👈 直接使用公共的底部弹窗外壳
    MiuiBottomSheetWrapper(
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
        Box(Modifier.height(64.dp).width(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocalGasStation, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(32.dp).width(32.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("暂无加油记录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("点击首页“记加油”添加新记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}