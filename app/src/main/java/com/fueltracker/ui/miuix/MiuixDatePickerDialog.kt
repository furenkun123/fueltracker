package com.fueltracker.ui.miuix

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
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
                .background(if (isSelected) MiuixTheme.colorScheme.primary else Color.Transparent)
                .then(if (isToday && !isSelected) Modifier.border(1.dp, MiuixTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${cell.day}",
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MiuixTheme.colorScheme.onPrimary
                    !cell.inCurrentMonth -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    isToday -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// ============================================================
// 日历组件
// ============================================================

@Composable
private fun MiuixCalendar(
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月", tint = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
            Text(
                text = "${displayYear}年${displayMonth}月", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onTitleClick() }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月", tint = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        }

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Box(modifier = Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(w, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceSecondary)
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
private fun MiuixYearMonthPickerDialog(
    initialYear: Int, initialMonth: Int, minYear: Int, maxYear: Int,
    onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MiuixTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("选择年月", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WheelList(selectedValue = year, items = years, visibleCount = 3, modifier = Modifier.weight(1.4f), style = WheelStyle.Plain, itemLabel = { "${it}年" }, onChange = { year = it })
                    WheelList(selectedValue = month, items = (1..12).toList(), visibleCount = 3, modifier = Modifier.weight(1f), style = WheelStyle.Plain, itemLabel = { "%02d 月".format(it) }, onChange = { month = it })
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        cornerRadius = 22.dp,
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.surfaceVariant,
                            textColor = MiuixTheme.colorScheme.onSurface
                        )
                    )
                    TextButton(
                        text = "确定",
                        onClick = { onConfirm(year, month) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        cornerRadius = 22.dp,
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.primary,
                            textColor = MiuixTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

// ============================================================
// 主入口
// ============================================================

@Composable
fun MiuixDatePickerDialog(
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

    WindowBottomSheet(
        show = true,
        title = title,
        startAction = {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    color = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            )
        },
        endAction = {
            TextButton(
                text = "确定",
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        clear()
                        set(selectedYear, selectedMonth - 1, selectedDay, hour, minute, 0)
                    }
                    onSelected(cal.timeInMillis)
                },
                colors = ButtonDefaults.textButtonColors(
                    color = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.primary
                )
            )
        },
        onDismissRequest = onDismiss,
        backgroundColor = MiuixTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            // 日历
            MiuixCalendar(
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
            MiuixYearMonthPickerDialog(
                initialYear = displayYear, initialMonth = displayMonth, minYear = minYear, maxYear = maxYear,
                onDismiss = { showYearMonthDialog = false },
                onConfirm = { y, m -> displayYear = y; displayMonth = m; showYearMonthDialog = false }
            )
        }
    }
}
