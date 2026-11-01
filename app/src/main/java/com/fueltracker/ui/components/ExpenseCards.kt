package com.fueltracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.util.ExpenseRange
import com.fueltracker.util.ExpenseSummary
import com.fueltracker.util.MonthTrendPoint
import com.fueltracker.util.StructureItem
import com.fueltracker.util.toIntMoneyText
import com.fueltracker.util.toMoneyText
import com.fueltracker.util.toPercentText
import com.fueltracker.util.toPlainMoneyText
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale
import com.fueltracker.ui.components.MiuiBottomSheetWrapper
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle

@Composable
fun SummaryCard(
    summary: ExpenseSummary,
    selectedRange: ExpenseRange,
    rangeLabel: String,
    onRangeSelected: (ExpenseRange) -> Unit,
    onCustomClick: () -> Unit
) {
    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiuiFilterChip("全部", selectedRange == ExpenseRange.ALL) { onRangeSelected(ExpenseRange.ALL) }
                MiuiFilterChip("本月", selectedRange == ExpenseRange.MONTH) { onRangeSelected(ExpenseRange.MONTH) }
                MiuiFilterChip("今年", selectedRange == ExpenseRange.YEAR) { onRangeSelected(ExpenseRange.YEAR) }
                MiuiFilterChip(if (selectedRange == ExpenseRange.CUSTOM_MONTH) rangeLabel else "选年月", selectedRange == ExpenseRange.CUSTOM_MONTH, onCustomClick)
            }
            Text("${summary.rangeLabel}支出", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("¥", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                Text(summary.totalAmount.toPlainMoneyText(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { Text("笔数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${summary.count} 笔", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (summary.showDaily) Column { Text("日均", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(summary.dailyAverage.toMoneyText(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun ExpenseTrendCard(
    points: List<MonthTrendPoint>,
    years: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    // 弹窗与临时选中状态
    var showYearPicker by remember { mutableStateOf(false) }
    var tempSelectedYear by remember { mutableStateOf<Int?>(null) }

    val selectedYearText = if (selectedYear == null) "全部年份" else "${selectedYear}年"

    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            // 标题行
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selectedYear == null) "费用趋势 · 全部" else "费用趋势 · ${selectedYear}年",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )

                MiuiFilterChip(
                    text = "$selectedYearText ▼",
                    selected = true,
                    onClick = {
                        tempSelectedYear = selectedYear
                        showYearPicker = true
                    }
                )
            }
            Spacer(Modifier.height(16.dp))

            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("该范围暂无费用记录", fontSize = 12.sp, color = onSurfaceVariantColor)
                }
            } else {
                val maxValue = points.maxOf { it.value }
                val yMax = if (maxValue > 0) maxValue * 1.15 else 1.0

                val valueLayouts = remember(points, onSurfaceColor) {
                    points.map {
                        textMeasurer.measure(
                            it.value.toIntMoneyText(),
                            TextStyle(color = onSurfaceColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                val timeLayouts = remember(points, onSurfaceVariantColor) {
                    points.map { point ->
                        val label = String.format(Locale.getDefault(), "%02d/%02d", point.year % 100, point.month)
                        textMeasurer.measure(label, TextStyle(color = onSurfaceVariantColor, fontSize = 9.sp))
                    }
                }

                val scrollState = rememberScrollState()
                val visibleCount = 10 // 当前你设置的 10 个月
                val barCount = points.size

                LaunchedEffect(barCount, selectedYear) {
                    if (barCount > visibleCount) {
                        snapshotFlow { scrollState.maxValue }.firstOrNull { it > 0 }?.let { scrollState.scrollTo(it) }
                    } else {
                        scrollState.scrollTo(0)
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val chartWidth = if (barCount <= visibleCount) maxWidth else (maxWidth / visibleCount) * barCount

                    Box(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                        Canvas(Modifier.width(chartWidth).height(180.dp)) {
                            val topSpace = 32.dp.toPx()
                            val chartHeight = 110.dp.toPx()
                            val bottomY = topSpace + chartHeight

                            val slotWidth = size.width / barCount

                            val coordinates = points.mapIndexed { index, point ->
                                val x = index * slotWidth + slotWidth / 2f
                                val y = bottomY - (chartHeight * (point.value / yMax).toFloat().coerceIn(0f, 1f))
                                Offset(x, y)
                            }

                            val areaPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(coordinates.first().x, bottomY)
                                coordinates.forEach { lineTo(it.x, it.y) }
                                lineTo(coordinates.last().x, bottomY)
                                close()
                            }
                            drawPath(
                                path = areaPath,
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                                    startY = topSpace,
                                    endY = bottomY
                                )
                            )

                            val linePath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(coordinates.first().x, coordinates.first().y)
                                coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = linePath,
                                color = primaryColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )

                            points.forEachIndexed { index, point ->
                                val centerPoint = coordinates[index]

                                drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = centerPoint)
                                drawCircle(color = primaryColor, radius = 2.5.dp.toPx(), center = centerPoint)

                                val textLayout = valueLayouts[index]
                                val textX = (centerPoint.x - textLayout.size.width / 2f).coerceIn(0f, size.width - textLayout.size.width)

                                var isAbove = index == 0 || point.value >= points[index - 1].value

                                if (isAbove && (centerPoint.y - textLayout.size.height - 4.dp.toPx() < 0f)) {
                                    isAbove = false
                                }
                                if (!isAbove && (centerPoint.y + textLayout.size.height + 4.dp.toPx() > bottomY - 16.dp.toPx())) {
                                    isAbove = true
                                }

                                val textY = if (isAbove) {
                                    centerPoint.y - textLayout.size.height - 4.dp.toPx()
                                } else {
                                    centerPoint.y + 4.dp.toPx()
                                }

                                drawText(textLayout, topLeft = Offset(textX, textY))

                                val timeLayout = timeLayouts[index]
                                val timeX = (centerPoint.x - timeLayout.size.width / 2f).coerceIn(0f, size.width - timeLayout.size.width)
                                val timeY = bottomY + 6.dp.toPx()

                                drawText(timeLayout, topLeft = Offset(timeX, timeY))
                            }
                        }
                    }
                }
            }
        }
    }

    // 👇 核心修复：弹窗放在 Canvas 和 BoxWithConstraints 外部，直接放在 Composable 函数内
    if (showYearPicker) {
        val yearOptions = listOf<Int?>(null) + years

        MiuiBottomSheetWrapper(
            title = "选择年份",
            onDismiss = { showYearPicker = false },
            onConfirm = {
                onYearSelected(tempSelectedYear)
                showYearPicker = false
            }
        ) {
            WheelList(
                selectedValue = tempSelectedYear,
                items = yearOptions,
                modifier = Modifier.fillMaxWidth(),
                visibleCount = 3,
                style = WheelStyle.WithBackground,
                itemLabel = { it?.let { y -> "${y}年" } ?: "全部年份" },
                onChange = { tempSelectedYear = it }
            )
        }
    }
}

@Composable
fun ExpenseStructureCard(items: List<StructureItem>, selectedCategory: String?, onCategoryClick: (String) -> Unit) {
    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("费用结构", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(if (selectedCategory == null) "点击筛选" else "已筛选 $selectedCategory", fontSize = 11.sp, color = if (selectedCategory == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            }
            items.forEach { item ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onCategoryClick(item.name) }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.name, fontSize = 14.sp, fontWeight = if (item.name == selectedCategory) FontWeight.Bold else FontWeight.Normal, color = if (item.name == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.amount.toMoneyText(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(item.ratio.toPercentText(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(item.ratio.coerceIn(0f, 1f)).fillMaxHeight().background(if (item.name == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)))
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseDetailItemCard(title: String, time: String, amount: Double, onEdit: () -> Unit, onDelete: () -> Unit) {
    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MiuiTextButton("编辑", MaterialTheme.colorScheme.primary, onEdit)
                    MiuiTextButton("删除", Color(0xFFFF3B30).copy(alpha = 0.8f), onDelete)
                }
            }
            Text(amount.toMoneyText(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}


