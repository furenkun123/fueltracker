package com.fueltracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.miuix.MiuixCard
import com.fueltracker.ui.miuix.MiuixFilterChip
import kotlinx.coroutines.flow.firstOrNull
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar

data class CostPoint(
    val timestamp: Long,
    val value: Double
)

data class ConsumptionTrendPoint(
    val timestamp: Long,
    val value: Double,
    val distance: Double,
    val recordId: Long
)

// =================================================
// 趋势图
// =================================================

private data class MonthCostPoint(val year: Int, val month: Int, val value: Double)

private data class MonthlyConsumptionPoint(
    val year: Int,
    val month: Int,
    val value: Double,
    val distance: Double,
    val count: Int
)

private val consumptionYearColors = listOf(
    Color(0xFF3988E8),
    Color(0xFF20A67A),
    Color(0xFFFF9845),
    Color(0xFF9B75D7),
    Color(0xFFE36B82),
    Color(0xFF20A6B8)
)

@Composable
fun ChartCard(points: List<CostPoint>) {
    val monthlyPoints = remember(points) {
        val grouped = mutableMapOf<Pair<Int, Int>, Double>()
        points.forEach {
            if (!it.value.isFinite()) return@forEach
            val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val key = c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
            grouped[key] = (grouped[key] ?: 0.0) + it.value
        }
        grouped.map { MonthCostPoint(it.key.first, it.key.second, it.value) }
            .filter { it.value > 0.0 && it.value.isFinite() }
            .sortedWith(compareBy({ it.year }, { it.month }))
    }
    if (monthlyPoints.isEmpty()) return

    val values = remember(monthlyPoints) { monthlyPoints.map { it.value } }
    val maxValue = values.maxOrNull() ?: return
    val yMax = maxValue * 1.15
    if (!yMax.isFinite() || yMax <= 0.0) return

    val primaryColor = MiuixTheme.colorScheme.primary
    val onSurfaceColor = MiuixTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MiuixTheme.colorScheme.onSurfaceSecondary

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val baseValueLabelStyle = remember(onSurfaceColor) {
        TextStyle(color = onSurfaceColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
    val baseTimeLabelStyle = remember(onSurfaceVariantColor) {
        TextStyle(color = onSurfaceVariantColor, fontSize = 8.sp)
    }

    val valueLabelLayouts = remember(values, textMeasurer, baseValueLabelStyle) {
        values.map { textMeasurer.measure("%.0f".format(it), baseValueLabelStyle) }
    }
    val isSameYear = remember(monthlyPoints) { monthlyPoints.map { it.year }.distinct().size <= 1 }
    val timeLabelLayouts = remember(monthlyPoints, isSameYear, textMeasurer, baseTimeLabelStyle) {
        monthlyPoints.map {
            textMeasurer.measure(
                if (isSameYear) "${it.month}月" else "%02d/%02d".format(it.year % 100, it.month),
                baseTimeLabelStyle
            )
        }
    }

    val topPaddingDp = remember(valueLabelLayouts, density) {
        with(density) {
            ((valueLabelLayouts.maxOfOrNull { it.size.height } ?: 0).toDp() + 4.dp)
                .coerceAtLeast(16.dp)
        }
    }
    val chartAreaHeight = 94.dp
    val timeAxisHeight = 18.dp
    val canvasHeight = topPaddingDp + chartAreaHeight + timeAxisHeight

    val visibleCount = 10

    LaunchedEffect(monthlyPoints.size) {
        if (monthlyPoints.size > visibleCount) {
            snapshotFlow { scrollState.maxValue }
                .firstOrNull { it > 0 }
                ?.let { scrollState.scrollTo(it) }
        }
    }

    MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("加油趋势 (按月 · 元)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = maxWidth / visibleCount
                val chartWidth = if (monthlyPoints.size <= visibleCount) maxWidth else itemWidth * monthlyPoints.size
                Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                    Canvas(modifier = Modifier.width(chartWidth).height(canvasHeight)) {
                        val slotWidth = size.width / monthlyPoints.size
                        val barWidth = (slotWidth * 0.5f).coerceIn(4.dp.toPx(), 40.dp.toPx())
                        val chartHeight = size.height - topPaddingDp.toPx() - timeAxisHeight.toPx()
                        val timeLabelY = topPaddingDp.toPx() + chartHeight + 4.dp.toPx()

                        monthlyPoints.forEachIndexed { index, point ->
                            val barCenterX = index * slotWidth + slotWidth / 2f
                            val barLeft = barCenterX - barWidth / 2f
                            val barHeight = (chartHeight * (point.value / yMax).toFloat().coerceIn(0f, 1f))
                                .coerceAtLeast(2.dp.toPx())
                            val barTop = topPaddingDp.toPx() + (chartHeight - barHeight)

                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.85f),
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )

                            val vLayout = valueLabelLayouts[index]
                            if (vLayout.size.width <= size.width) {
                                drawText(
                                    vLayout,
                                    topLeft = Offset(
                                        (barCenterX - vLayout.size.width / 2f)
                                            .coerceIn(0f, size.width - vLayout.size.width),
                                        barTop - vLayout.size.height - 2.dp.toPx()
                                    )
                                )
                            }

                            val tLayout = timeLabelLayouts[index]
                            if (tLayout.size.width <= size.width) {
                                drawText(
                                    tLayout,
                                    topLeft = Offset(
                                        (barCenterX - tLayout.size.width / 2f)
                                            .coerceIn(0f, size.width - tLayout.size.width),
                                        timeLabelY
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsumptionTrendCard(
    points: List<ConsumptionTrendPoint>,
    years: List<Int>,
    selectedYears: Set<Int>,
    onYearsSelected: (Set<Int>) -> Unit
) {
    val onSurfaceColor = MiuixTheme.colorScheme.onSurface
    val secondaryColor = MiuixTheme.colorScheme.onSurfaceSecondary

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 空集合代表全部年份
    val activeYears = remember(years, selectedYears) {
        if (selectedYears.isEmpty()) {
            years
        } else {
            years.filter { it in selectedYears }
        }
    }

    // 按年份、月份分组，计算加权平均油耗
    val monthlyPoints = remember(points, activeYears) {
        points
            .filter { point ->
                val year = Calendar.getInstance().apply {
                    timeInMillis = point.timestamp
                }.get(Calendar.YEAR)

                year in activeYears &&
                        point.value.isFinite() &&
                        point.distance.isFinite() &&
                        point.distance > 0.0
            }
            .groupBy { point ->
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = point.timestamp
                }
                calendar.get(Calendar.YEAR) to (calendar.get(Calendar.MONTH) + 1)
            }
            .mapNotNull { (key, monthRecords) ->
                val totalDistance = monthRecords.sumOf { it.distance }
                if (!totalDistance.isFinite() || totalDistance <= 0.0) {
                    return@mapNotNull null
                }

                val weightedConsumption = monthRecords.sumOf {
                    it.value * it.distance
                } / totalDistance

                if (!weightedConsumption.isFinite()) {
                    return@mapNotNull null
                }

                MonthlyConsumptionPoint(
                    year = key.first,
                    month = key.second,
                    value = weightedConsumption,
                    distance = totalDistance,
                    count = monthRecords.size
                )
            }
            .sortedWith(compareBy({ it.year }, { it.month }))
    }

    // Y 轴范围
    val minValue = monthlyPoints.minOfOrNull { it.value } ?: 0.0
    val maxValue = monthlyPoints.maxOfOrNull { it.value } ?: 0.0

    val valueRange = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val yMin = (minValue - valueRange * 0.15).coerceAtLeast(0.0)
    val yMax = maxValue + valueRange * 0.15

    val actualRange = (yMax - yMin).takeIf { it > 0.0 } ?: 1.0

    val chartHeight = 160.dp

    // 点击命中半径
    val touchRadiusPx = with(density) { 18.dp.toPx() }
    val overlapRadiusPx = with(density) { 7.dp.toPx() }

    // 选中的月份，年份 + 月份；数据或筛选年份变化时自动清空选择
    val selectedKeyState = remember(points, activeYears) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    // 点击位置存在多个候选点时，暂存候选年份和月份
    val overlapCandidatesState = remember(points, activeYears) {
        mutableStateOf<List<Pair<Int, Int>>>(emptyList())
    }

    val selectedPoint = remember(monthlyPoints, selectedKeyState.value) {
        monthlyPoints.firstOrNull {
            (it.year to it.month) == selectedKeyState.value
        }
    }

    val valueTextStyle = remember(onSurfaceColor) {
        TextStyle(color = onSurfaceColor, fontSize = 9.sp)
    }

    val monthTextStyle = remember(secondaryColor) {
        TextStyle(color = secondaryColor, fontSize = 9.sp)
    }

    // 根据年份固定颜色
    fun yearColor(year: Int): Color {
        val index = years.indexOf(year).coerceAtLeast(0)
        return consumptionYearColors[index % consumptionYearColors.size]
    }

    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "油耗趋势",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            // 年份多选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiuixFilterChip(
                    "全部",
                    selectedYears.isEmpty()
                ) {
                    onYearsSelected(emptySet())
                }

                years.forEach { year ->
                    val isSelected = selectedYears.isEmpty() || year in selectedYears

                    MiuixFilterChip(
                        "${year}年",
                        isSelected
                    ) {
                        val nextSelection = when {
                            selectedYears.isEmpty() -> setOf(year)
                            year in selectedYears -> selectedYears - year
                            else -> selectedYears + year
                        }
                        onYearsSelected(nextSelection)
                    }
                }
            }

            if (monthlyPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有可比较的有效油耗数据",
                        fontSize = 12.sp,
                        color = secondaryColor
                    )
                }
            } else {
                // 年份图例
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeYears.forEach { year ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Canvas(modifier = Modifier.width(9.dp).height(9.dp)) {
                                drawCircle(color = yearColor(year))
                            }
                            Text(
                                text = "${year}年",
                                fontSize = 11.sp,
                                color = secondaryColor
                            )
                        }
                    }
                }

                val plotPointsByYear = remember(monthlyPoints) {
                    monthlyPoints.groupBy { it.year }
                }

                // 固定 Y 轴 + 固定 12 个月图表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // 左侧 Y 轴：加宽到 42dp，数字右对齐留 8dp 内边距
                    Canvas(
                        modifier = Modifier
                            .width(18.dp)
                            .height(chartHeight)
                    ) {
                        val topPadding = 12.dp.toPx()
                        val bottomPadding = 25.dp.toPx()

                        val plotHeight = size.height - topPadding - bottomPadding

                        for (i in 0..2) {
                            val fraction = i / 2f
                            val y = topPadding + plotHeight * fraction
                            val value = yMax - actualRange * fraction

                            val label = textMeasurer.measure(
                                "%.1f".format(value),
                                valueTextStyle
                            )

                            drawText(
                                label,
                                topLeft = Offset(
                                    0f,
                                    y - label.size.height / 2f
                                )
                            )
                        }
                    }

                    // 右侧图表：固定显示 12 个月
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(chartHeight)
                            .pointerInput(monthlyPoints, yMin, yMax) {
                                detectTapGestures { tapOffset ->
                                    val slotWidth = size.width / 12f

                                    val topPadding = 12.dp.toPx()
                                    val bottomPadding = 25.dp.toPx()
                                    val plotHeight = size.height - topPadding - bottomPadding

                                    // 找出点击范围内的所有数据点
                                    val hitPoints = monthlyPoints.mapNotNull { point ->
                                        val x = (point.month - 0.5f) * slotWidth
                                        val y = topPadding +
                                                plotHeight *
                                                ((yMax - point.value) / actualRange).toFloat()

                                        val dx = tapOffset.x - x
                                        val dy = tapOffset.y - y
                                        val distanceSquared = dx * dx + dy * dy

                                        if (distanceSquared <= touchRadiusPx * touchRadiusPx) {
                                            point to distanceSquared
                                        } else {
                                            null
                                        }
                                    }.sortedBy { it.second }

                                    when {
                                        hitPoints.isEmpty() -> {
                                            selectedKeyState.value = null
                                            overlapCandidatesState.value = emptyList()
                                        }

                                        hitPoints.size == 1 -> {
                                            val point = hitPoints.first().first
                                            selectedKeyState.value = point.year to point.month
                                            overlapCandidatesState.value = emptyList()
                                        }

                                        else -> {
                                            val nearestDistance = hitPoints.first().second

                                            // 只把距离最近点足够近的其他点视为重叠
                                            val overlappingPoints = hitPoints.filter {
                                                it.second <= nearestDistance + overlapRadiusPx * overlapRadiusPx
                                            }

                                            if (overlappingPoints.size > 1) {
                                                selectedKeyState.value = null
                                                overlapCandidatesState.value = overlappingPoints.map {
                                                    it.first.year to it.first.month
                                                }.distinct()
                                            } else {
                                                val point = hitPoints.first().first
                                                selectedKeyState.value = point.year to point.month
                                                overlapCandidatesState.value = emptyList()
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val topPadding = 12.dp.toPx()
                        val bottomPadding = 25.dp.toPx()

                        val plotHeight = size.height - topPadding - bottomPadding
                        val slotWidth = size.width / 12f

                        // 横向网格线
                        for (i in 0..2) {
                            val fraction = i / 2f
                            val y = topPadding + plotHeight * fraction

                            drawLine(
                                color = onSurfaceColor.copy(alpha = 0.10f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // 1～12 月标签
                        for (month in 1..12) {
                            val x = (month - 0.5f) * slotWidth

                            val label = textMeasurer.measure(
                                "${month}月",
                                monthTextStyle
                            )

                            drawText(
                                label,
                                topLeft = Offset(
                                    x - label.size.width / 2f,
                                    size.height - label.size.height
                                )
                            )
                        }

                        // 每年一条折线
                        activeYears.forEach { year ->
                            val color = yearColor(year)

                            // 选中某个年份后，其他年份降低透明度
                            val isDimmed = selectedKeyState.value
                                ?.first
                                ?.let { it != year } == true

                            val displayColor = color.copy(
                                alpha = if (isDimmed) 0.18f else 1f
                            )

                            val yearPoints = plotPointsByYear[year]
                                .orEmpty()
                                .associateBy { it.month }

                            var previousPoint: MonthlyConsumptionPoint? = null

                            for (month in 1..12) {
                                val point = yearPoints[month] ?: continue

                                val x = (month - 0.5f) * slotWidth
                                val y = topPadding +
                                        plotHeight *
                                        ((yMax - point.value) / actualRange).toFloat()

                                // 连接相邻月份
                                previousPoint?.let { previous ->
                                    val previousX = (previous.month - 0.5f) * slotWidth
                                    val previousY = topPadding +
                                            plotHeight *
                                            ((yMax - previous.value) / actualRange).toFloat()

                                    drawLine(
                                        color = displayColor,
                                        start = Offset(previousX, previousY),
                                        end = Offset(x, y),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }

                                // 选中点增加外圈
                                val isSelected = selectedKeyState.value == (year to month)

                                if (isSelected) {
                                    drawCircle(
                                        color = color.copy(alpha = 0.22f),
                                        radius = 9.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                }

                                drawCircle(
                                    color = displayColor,
                                    radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                                previousPoint = point
                            }
                        }
                    }
                }
                // 多个数据点重叠时，显示候选年份
                if (overlapCandidatesState.value.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "选择年份",
                            fontSize = 10.sp,
                            color = secondaryColor
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            overlapCandidatesState.value.forEach { key ->
                                val candidate = monthlyPoints.firstOrNull {
                                    it.year == key.first && it.month == key.second
                                }

                                if (candidate != null) {
                                    MiuixFilterChip(
                                        "${candidate.year}年 · %.2f".format(candidate.value),
                                        false
                                    ) {
                                        selectedKeyState.value = key
                                        overlapCandidatesState.value = emptyList()
                                    }
                                }
                            }
                        }
                    }
                }

                // 点击数据点后显示对应月份的详情
                if (selectedPoint != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "${selectedPoint.year}年${selectedPoint.month}月",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = yearColor(selectedPoint.year)
                        )
                        Text(
                            text = "平均油耗：%.2f L/100km".format(selectedPoint.value),
                            fontSize = 12.sp,
                            color = onSurfaceColor
                        )
                        Text(
                            text = "行驶距离：%.1f km    有效区间：%d 个".format(
                                selectedPoint.distance,
                                selectedPoint.count
                            ),
                            fontSize = 11.sp,
                            color = secondaryColor
                        )
                    }
                } else {
                    Text(
                        text = "点击折线上的圆点查看当月数据",
                        fontSize = 10.sp,
                        color = secondaryColor
                    )
                }
            }

            Text(
                text = "单位：L/100km · 按月份加权平均 · 每个点代表当月有效油耗",
                fontSize = 10.sp,
                color = secondaryColor
            )
        }
    }
}