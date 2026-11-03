package com.fueltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Calendar

// ============================================================
// 内部数据与工具
// ============================================================

private data class DayCell(val year: Int, val month: Int, val day: Int, val inCurrentMonth: Boolean)

private fun buildMonthGrid(year: Int, month: Int): List<DayCell> {
    val cal = Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<DayCell>()

    val prevCal = cal.clone() as Calendar
    prevCal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek)
    repeat(firstDayOfWeek) {
        cells.add(DayCell(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH) + 1, prevCal.get(Calendar.DAY_OF_MONTH), false))
        prevCal.add(Calendar.DAY_OF_MONTH, 1)
    }
    for (d in 1..daysInMonth) cells.add(DayCell(year, month, d, true))
    return cells
}

// ============================================================
// 核心抽离：日历格子组件
// ============================================================

@Composable
private fun RowScope.CalendarDayCell(
    cell: DayCell,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f).height(40.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp).clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(if (isToday && !isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${cell.day}",
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    !cell.inCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// ============================================================
// 日历组件
// ============================================================

@Composable
private fun MiuiCalendar(
    displayYear: Int,
    displayMonth: Int,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Int, Int, Int) -> Unit,
    onTitleClick: () -> Unit
) {
    val cells = remember(displayYear, displayMonth) { buildMonthGrid(displayYear, displayMonth) }
    val today = remember {
        Calendar.getInstance().let { Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 顶部：切换月份按钮与标题
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "${displayYear}年${displayMonth}月", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onTitleClick() }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Box(modifier = Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(w, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 日历格子
        val rowCount = (cells.size + 6) / 7
        for (row in 0 until rowCount) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    if (index >= cells.size) {
                        Box(modifier = Modifier.weight(1f).height(40.dp))
                    } else {
                        val cell = cells[index]
                        CalendarDayCell(
                            cell = cell,
                            isSelected = cell.year == selectedYear && cell.month == selectedMonth && cell.day == selectedDay,
                            isToday = cell.year == today.first && cell.month == today.second && cell.day == today.third,
                            onClick = { onSelectDay(cell.year, cell.month, cell.day) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 年月选择弹窗
// ============================================================

@Composable
private fun MiuiYearMonthPickerDialog(
    initialYear: Int, initialMonth: Int, minYear: Int, maxYear: Int,
    onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList() }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("选择年月", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WheelList(selectedValue = year, items = years, visibleCount = 3, modifier = Modifier.weight(1.4f), style = WheelStyle.Plain, itemLabel = { "${it}年" }, onChange = { year = it })
                    WheelList(selectedValue = month, items = (1..12).toList(), visibleCount = 3, modifier = Modifier.weight(1f), style = WheelStyle.Plain, itemLabel = { "%02d 月".format(it) }, onChange = { month = it })
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { onConfirm(year, month) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("确定", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

// ============================================================
// 主入口
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiDatePickerDialog(
    initialTimestamp: Long,
    title: String = "选择时间",
    minYearOffset: Int = -2,
    maxYearOffset: Int = 10,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialCalendar = remember(initialTimestamp) { Calendar.getInstance().apply { timeInMillis = initialTimestamp } }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val minYear = currentYear + minYearOffset
    val maxYear = currentYear + maxYearOffset

    var selectedYear by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH)) }
    var displayYear by remember { mutableIntStateOf(selectedYear) }
    var displayMonth by remember { mutableIntStateOf(selectedMonth) }
    var hour by remember { mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initialCalendar.get(Calendar.MINUTE)) }
    var showYearMonthDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            // 顶栏
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply { clear(); set(selectedYear, selectedMonth - 1, selectedDay, hour, minute, 0) }
                    onSelected(cal.timeInMillis)
                }) { Text("确定", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            }

            Spacer(Modifier.height(2.dp))

            // 日历
            MiuiCalendar(
                displayYear = displayYear, displayMonth = displayMonth,
                selectedYear = selectedYear, selectedMonth = selectedMonth, selectedDay = selectedDay,
                onPrevMonth = {
                    if (displayMonth == 1) { if (displayYear - 1 >= minYear) { displayYear--; displayMonth = 12 } }
                    else displayMonth--
                },
                onNextMonth = {
                    if (displayMonth == 12) { if (displayYear + 1 <= maxYear) { displayYear++; displayMonth = 1 } }
                    else displayMonth++
                },
                onSelectDay = { y, m, d -> selectedYear = y; selectedMonth = m; selectedDay = d; displayYear = y; displayMonth = m },
                onTitleClick = { showYearMonthDialog = true }
            )

            Spacer(Modifier.height(4.dp))

            // 时间滚轮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(0.6f))
                WheelList(selectedValue = hour, items = (0..23).toList(), visibleCount = 3, modifier = Modifier.weight(1f), style = WheelStyle.Plain, itemLabel = { "%02d 时".format(it) }, onChange = { hour = it })
                WheelList(selectedValue = minute, items = (0..59).toList(), visibleCount = 3, modifier = Modifier.weight(1f), style = WheelStyle.Plain, itemLabel = { "%02d 分".format(it) }, onChange = { minute = it })
                Spacer(Modifier.weight(0.6f))
            }
        }

        if (showYearMonthDialog) {
            MiuiYearMonthPickerDialog(
                initialYear = displayYear, initialMonth = displayMonth, minYear = minYear, maxYear = maxYear,
                onDismiss = { showYearMonthDialog = false },
                onConfirm = { y, m -> displayYear = y; displayMonth = m; showYearMonthDialog = false }
            )
        }
    }
}