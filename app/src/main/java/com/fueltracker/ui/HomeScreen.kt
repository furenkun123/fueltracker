package com.fueltracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.MiuiYearMonthSheet
import com.fueltracker.ui.components.scrollToHideFab
import com.fueltracker.ui.miui.MiuiButton
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiCompactFilterChip
import com.fueltracker.ui.miui.MiuiFilterChip
import com.fueltracker.ui.miui.MiuiFloatingIconButton
import com.fueltracker.ui.miui.MiuiTextButton
import com.fueltracker.ui.miui.VehicleCard
import com.fueltracker.ui.miui.glass.drawGlassBackdrop
import com.fueltracker.ui.miui.glass.drawGlassDialogOverlay
import com.fueltracker.ui.miui.glass.LocalAppGlassEnabled
import com.fueltracker.util.ExpenseRange
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.StatisticsSummary
import com.fueltracker.util.computeExpenseRange
import com.fueltracker.util.toDateTimeText
import com.fueltracker.util.toMoneyText
import com.fueltracker.viewmodel.MainViewModel
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.collect
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds


private data class CostPoint(val timestamp: Long, val value: Double)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    vehicle: Vehicle?,
    records: List<FuelRecord>,
    latestConsumption: Double? = null,
    averageConsumption: Double? = null,
    activeParking: ExpenseRecord? = null,
    onAddVehicle: () -> Unit,
    onAddFuel: () -> Unit,
    onAddParking: () -> Unit,
    onEditParking: (ExpenseRecord) -> Unit,
    onClearParking: () -> Unit,
    onIgnoreParking: () -> Unit,
    isClearParkingConfirmVisible: Boolean = false,
    onRequestClearParkingConfirm: () -> Unit,
    onCollapseProgressChange: (Float) -> Unit = {},
) {
    val deliveryDate = vehicle?.deliveryDate
    var fabExpanded by remember { mutableStateOf(false) }

    val (displayRecords, recentRecords) = remember(records, deliveryDate) {
        val list = records
            .filter { (deliveryDate == null || it.timestamp >= deliveryDate) && !it.isInitialRecord }
            .sortedByDescending { it.timestamp }
        list to list.take(15)
    }

    val costData = remember(displayRecords) {
        displayRecords.asReversed()
            .map { CostPoint(it.timestamp, it.actualPaidAmount) }
            .filter { it.value >= 0.0 && it.value.isFinite() }
    }

    val resolvedLatestConsumption = remember(latestConsumption, displayRecords) {
        latestConsumption ?: displayRecords.firstOrNull { it.consumption != null }?.consumption
    }

    // 👈 统一使用 ExpenseRange
    var selectedRange by remember { mutableStateOf(ExpenseRange.ALL) }
    var customYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var customMonth by remember { mutableIntStateOf(0) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    val availableYears = remember(records, customYear) {
        (records.map { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR) } + customYear)
            .distinct().sortedDescending()
    }

    // 👈 统一使用 computeExpenseRange
    val statsRange = remember(selectedRange, customYear, customMonth) {
        computeExpenseRange(selectedRange, customYear, customMonth)
    }

    val statsSummary = remember(vehicle, records, statsRange) {
        FuelCalculator.calculateSummary(
            allRecords = records,
            vehicle = vehicle,
            startTime = statsRange.first,
            endTime = statsRange.second
        )
    }

    val listState = rememberLazyListState()

    val density = LocalDensity.current

    val targetCollapseProgress by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val threshold = with(density) { 60.dp.toPx() }
                (
                        listState.firstVisibleItemScrollOffset.toFloat() /
                                threshold
                        ).coerceIn(0f, 1f)
            }
        }
    }




// 平滑过渡，避免标题尺寸突然变化
    val collapseProgress by animateFloatAsState(
        targetValue = targetCollapseProgress,
        animationSpec = tween(durationMillis = 180),
        label = "topBarCollapse"
    )
    val isFloating = viewModel.isFloatingBottomBar

    LaunchedEffect(collapseProgress) {
        onCollapseProgressChange(collapseProgress)
    }

    // 外层 Box：用于放置独立悬浮的 FAB
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val bottomBarPadding = if (isFloating) {
            112.dp
        } else {
            80.dp
        }
        // 1. 列表铺满整个屏幕
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollToHideFab(listState, viewModel),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars
                    .asPaddingValues()
                    .calculateTopPadding() + 24.dp,
                bottom = bottomBarPadding
            )
        ) {
            item(key = "home_title") {
                Text(
                    text = "随记油耗",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-15).dp)
                        .padding(
                            horizontal = 15.dp,
                            vertical = 0.dp
                        )
                )
            }

            if (vehicle == null) {
                item {
                    EmptyVehicleCard(onAddVehicle)
                }
            } else {
                item(key = "vehicle_card") {
                    VehicleCard(vehicle = vehicle)
                }

                if (activeParking != null) {
                    item(key = "active_parking_${activeParking.id}") {
                        ActiveParkingCard(
                            parking = activeParking,
                            onClick = { onEditParking(activeParking) },
                            onRequestClear = onRequestClearParkingConfirm,
                            onIgnore = onIgnoreParking
                        )
                    }
                }

                item {
                    ConsumptionCard(
                        statsSummary.totalCount,
                        resolvedLatestConsumption,
                        averageConsumption
                    )
                }

                item {
                    ConsumptionStatsCard(
                        summary = statsSummary,
                        selectedRange = selectedRange,
                        customYear = customYear,
                        customMonth = customMonth,
                        onRangeSelected = { selectedRange = it },
                        onCustomMonthClick = {
                            showYearMonthPicker = true
                        }
                    )
                }

                if (costData.size >= 2) {
                    item {
                        ChartCard(points = costData)
                    }
                }

                item {
                    Text(
                        text = "最近加油",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (recentRecords.isEmpty()) {
                    item {
                        EmptyRecordCard()
                    }
                } else {
                    items(recentRecords, key = { it.id }) {
                        HomeFuelRecordItem(it)
                    }
                }
            }
        }

        // 3. FAB：仍然独立悬浮在右下角
        if (vehicle != null) {
            AnimatedVisibility(
                visible = viewModel.isFabVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 28.dp,
                        bottom = if (isFloating) 104.dp else 84.dp
                    )
                    )
             {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            FabActionItem("🅿", "停车") {
                                fabExpanded = false
                                onAddParking()
                            }

                            FabActionItem("⛽", "加油") {
                                fabExpanded = false
                                onAddFuel()
                            }
                        }
                    }

                    MiuiFloatingIconButton(
                        icon = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        contentDescription = if (fabExpanded) "关闭快捷操作" else "添加记录"
                    )
                }
            }
        }

    }

    if (showYearMonthPicker) {
        // 👈 直接复用公共的年月选择弹窗（需在 components 里重命名为 MiuiYearMonthSheet，参数保持一致）
        MiuiYearMonthSheet(
            initialYear = customYear, initialMonth = customMonth, availableYears = availableYears,
            onDismiss = { showYearMonthPicker = false },
            onConfirm = { y, m ->
                customYear = y; customMonth = m
                selectedRange = ExpenseRange.CUSTOM_MONTH
                showYearMonthPicker = false
            }
        )
    }
}

// =================================================
// 统一化统计组件
// =================================================

@Composable
private fun EmptyVehicleCard(onAddVehicle: () -> Unit) {
    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("还没有车辆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("添加你的车辆后开始记录油耗", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MiuiButton(text = "添加车辆", onClick = onAddVehicle)
        }
    }
}

@Composable
private fun ConsumptionCard(recordCount: Int, latestConsumption: Double?, averageConsumption: Double?) {
    MiuiCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 15.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前油耗",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = latestConsumption
                            ?.let { "%.2f".format(it) }
                            ?: "--",
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "L/100km",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                CompactConsumptionStat(
                    title = "平均油耗",
                    value = averageConsumption
                        ?.let { "%.2f L/100km".format(it) }
                        ?: "--"
                )
                CompactConsumptionStat(
                    title = "加油次数",
                    value = "$recordCount 次"
                )
            }
        }
    }
}

@Composable
private fun CompactConsumptionStat(
    title: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 18.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatItem(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// =================================================
// 统计卡片
// =================================================

@Composable
private fun ConsumptionStatsCard(
    summary: StatisticsSummary, selectedRange: ExpenseRange, customYear: Int, customMonth: Int,
    onRangeSelected: (ExpenseRange) -> Unit, onCustomMonthClick: () -> Unit
) {
    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val customLabel = when (selectedRange) {
                ExpenseRange.CUSTOM_MONTH if customMonth == 0 ->
                    "${customYear}全年"
                ExpenseRange.CUSTOM_MONTH if customMonth > 0 ->
                    "${customYear}年${customMonth}月"
                else -> "选年月"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "油耗统计",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 5.dp,
                        alignment = Alignment.End
                    )
                ) {
                    MiuiCompactFilterChip(
                        "全部",
                        selectedRange == ExpenseRange.ALL,
                        onClick = {
                            onRangeSelected(ExpenseRange.ALL)
                        },
                        horizontalPadding = 7.dp,
                        verticalPadding = 5.dp,
                        fontSize = 11.sp
                    )
                    MiuiCompactFilterChip(
                        "本月",
                        selectedRange == ExpenseRange.MONTH,
                        onClick = {
                            onRangeSelected(ExpenseRange.MONTH)
                        },
                        horizontalPadding = 7.dp,
                        verticalPadding = 5.dp,
                        fontSize = 11.sp
                    )
                    MiuiCompactFilterChip(
                        "今年",
                        selectedRange == ExpenseRange.YEAR,
                        onClick = {
                            onRangeSelected(ExpenseRange.YEAR)
                        },
                        horizontalPadding = 7.dp,
                        verticalPadding = 5.dp,
                        fontSize = 11.sp
                    )
                    MiuiCompactFilterChip(
                        customLabel,
                        selectedRange == ExpenseRange.CUSTOM_MONTH,
                        onClick = onCustomMonthClick,
                        horizontalPadding = 7.dp,
                        verticalPadding = 5.dp,
                        fontSize = 11.sp
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("加油次数", "${summary.totalCount} 次", Modifier.weight(1f))
                StatItem("累计加油", "%.2f L".format(summary.totalVolume), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("累计花费", summary.totalCost.toMoneyText(), Modifier.weight(1f))
                StatItem("平均油价", summary.averagePrice?.let { "%.2f 元/L".format(it) } ?: "--", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("累计行驶", "%.0f km".format(summary.totalDistance), Modifier.weight(1f))
                StatItem("平均油耗", summary.averageConsumption?.let { "%.2f L/100km".format(it) } ?: "--", Modifier.weight(1f))
            }
        }
    }
}
// =================================================
// 趋势图
// =================================================

private data class MonthCostPoint(val year: Int, val month: Int, val value: Double)

@Composable
private fun ChartCard(points: List<CostPoint>) {
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

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
        monthlyPoints.map { textMeasurer.measure(if (isSameYear) "${it.month}月" else "%02d/%02d".format(it.year % 100, it.month), baseTimeLabelStyle) }
    }

    val valueLabelClearance = remember(valueLabelLayouts, density) {
        with(density) {
            (
                    (valueLabelLayouts.maxOfOrNull { it.size.height } ?: 0)
                        .toDp() + 12.dp
                    ).coerceAtLeast(22.dp)
        }
    }
    val minimumItemWidth = remember(valueLabelLayouts, density) {
        with(density) {
            (
                    (valueLabelLayouts.maxOfOrNull { it.size.width } ?: 0)
                        .toDp() + 14.dp
                    ).coerceAtLeast(46.dp)
        }
    }
    val lineTopGap = 10.dp
    val chartAreaHeight = 96.dp
    val timeAxisHeight = 18.dp
    val canvasHeight =
        valueLabelClearance + chartAreaHeight + timeAxisHeight
    val valueLabelBackground =
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val valueLabelBorder =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    val scrollState = rememberScrollState()

    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "加油趋势 (按月 · 元)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val visibleCount =
                    (maxWidth / minimumItemWidth)
                        .toInt()
                        .coerceIn(4, 7)
                val itemWidth = maxWidth / visibleCount
                val chartWidth =
                    if (monthlyPoints.size <= visibleCount) {
                        maxWidth
                    } else {
                        itemWidth * monthlyPoints.size
                    }

                LaunchedEffect(monthlyPoints.size, visibleCount) {
                    if (monthlyPoints.size > visibleCount) {
                        snapshotFlow { scrollState.maxValue }
                            .firstOrNull { it > 0 }
                            ?.let { scrollState.scrollTo(it) }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(chartWidth)
                            .height(canvasHeight)
                    ) {
                        val slotWidth = size.width / monthlyPoints.size
                        val plotTop = valueLabelClearance.toPx()
                        val plotBottom =
                            plotTop + chartAreaHeight.toPx()
                        val plotHeight =
                            (plotBottom - plotTop).coerceAtLeast(1f)
                        val gridColor =
                            onSurfaceVariantColor.copy(alpha = 0.13f)

                        repeat(3) { gridIndex ->
                            val fraction = (gridIndex + 1) / 4f
                            val gridY = plotTop + plotHeight * fraction
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, gridY),
                                end = Offset(size.width, gridY),
                                strokeWidth = 0.75.dp.toPx()
                            )
                        }

                        val pointOffsets = monthlyPoints.mapIndexed { index, point ->
                            val x = index * slotWidth + slotWidth / 2f
                            val normalized =
                                (point.value / yMax)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                            val y = plotBottom - plotHeight * normalized
                            Offset(x, y)
                        }

                        val firstPoint = pointOffsets.first()
                        val lastPoint = pointOffsets.last()
                        val areaPath = Path().apply {
                            moveTo(firstPoint.x, plotBottom)
                            pointOffsets.forEach { point ->
                                lineTo(point.x, point.y)
                            }
                            lineTo(lastPoint.x, plotBottom)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.18f),
                                    primaryColor.copy(alpha = 0.025f)
                                ),
                                startY = plotTop,
                                endY = plotBottom
                            )
                        )

                        val linePath = Path().apply {
                            moveTo(firstPoint.x, firstPoint.y)
                            pointOffsets.drop(1).forEach { point ->
                                lineTo(point.x, point.y)
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        monthlyPoints.forEachIndexed { index, _ ->
                            val point = pointOffsets[index]
                            val isLatest = index == monthlyPoints.lastIndex
                            drawCircle(
                                color = primaryColor.copy(
                                    alpha = if (isLatest) 0.22f else 0.13f
                                ),
                                radius = if (isLatest) {
                                    7.dp.toPx()
                                } else {
                                    5.5.dp.toPx()
                                },
                                center = point
                            )
                            drawCircle(
                                color = primaryColor,
                                radius = if (isLatest) {
                                    3.5.dp.toPx()
                                } else {
                                    2.8.dp.toPx()
                                },
                                center = point
                            )

                            val valueLayout = valueLabelLayouts[index]
                            val valueTop = (
                                    point.y -
                                            valueLayout.size.height -
                                            lineTopGap.toPx()
                                    ).coerceAtLeast(0f)
                            val labelHorizontalPadding = 4.dp.toPx()
                            val labelVerticalPadding = 2.dp.toPx()
                            val labelLeft = (
                                    point.x -
                                            valueLayout.size.width / 2f -
                                            labelHorizontalPadding
                                    ).coerceIn(
                                0f,
                                size.width -
                                        valueLayout.size.width -
                                        labelHorizontalPadding * 2f
                                    )
                            val labelTop = (
                                    valueTop -
                                            labelVerticalPadding
                                    ).coerceAtLeast(0f)
                            val labelWidth =
                                valueLayout.size.width +
                                        labelHorizontalPadding * 2f
                            val labelHeight =
                                valueLayout.size.height +
                                        labelVerticalPadding * 2f
                            val labelCorner = 5.dp.toPx()
                            drawRoundRect(
                                color = valueLabelBackground,
                                topLeft = Offset(labelLeft, labelTop),
                                size = androidx.compose.ui.geometry.Size(
                                    labelWidth,
                                    labelHeight
                                ),
                                cornerRadius = CornerRadius(labelCorner)
                            )
                            drawRoundRect(
                                color = valueLabelBorder,
                                topLeft = Offset(labelLeft, labelTop),
                                size = androidx.compose.ui.geometry.Size(
                                    labelWidth,
                                    labelHeight
                                ),
                                cornerRadius = CornerRadius(labelCorner),
                                style = Stroke(width = 0.65.dp.toPx())
                            )
                            drawText(
                                textLayoutResult = valueLayout,
                                topLeft = Offset(
                                    x = labelLeft +
                                            labelHorizontalPadding,
                                    y = labelTop +
                                            labelVerticalPadding
                                )
                            )

                            val timeLayout = timeLabelLayouts[index]
                            drawText(
                                textLayoutResult = timeLayout,
                                topLeft = Offset(
                                    x = (
                                            point.x -
                                                    timeLayout.size.width / 2f
                                            ).coerceIn(
                                        0f,
                                        size.width -
                                                timeLayout.size.width
                                    ),
                                    y = plotBottom + 4.dp.toPx()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// =================================================
// 最近记录卡片及其他
// =================================================

@Composable
private fun HomeFuelRecordItem(record: FuelRecord) {
    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    // 👈 日期用扩展函数
                    Text(record.timestamp.toDateTimeText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (record.isFull) "已加满" else "未加满", style = MaterialTheme.typography.labelSmall, color = if (record.isFull) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.hasMissedRecord) Text("含漏记 %.2f L".format(record.missedVolume ?: 0.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(record.actualPaidAmount.toMoneyText(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("%.2f L".format(record.volume), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val odoText = record.odometer?.takeIf { it > 0.0 }?.let { if (it % 1.0 == 0.0) "%.0f km".format(it) else "%.1f km".format(it) }
                    Text(if (odoText != null) "$odoText · %.2f 元/L".format(record.unitPrice) else "%.2f 元/L".format(record.unitPrice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                record.consumption?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.2f".format(it), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("L/100km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            record.remainingFuel?.let {
                Text("加油后剩余 %.2f L".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyRecordCard() {
    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("还没有加油记录", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FabActionItem(icon: String, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 6.dp, shape = RoundedCornerShape(24.dp)
    ) {
        Row(modifier = Modifier.padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActiveParkingCard(
    parking: ExpenseRecord,
    onClick: () -> Unit,
    onRequestClear: () -> Unit,
    onIgnore: () -> Unit
) {
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(parking.timestamp) {
        while (true) {
            delay(60.seconds)
            nowTick = System.currentTimeMillis()
        }
    }

    val durationText = remember(parking.timestamp, nowTick) { formatDuration(parking.timestamp, nowTick) }
    val locationText = parking.locationName?.takeIf { it.isNotBlank() } ?: "停车场"
    val detailText = listOfNotNull(parking.floor, parking.area, parking.spotNumber).joinToString(" · ")

    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📍 $locationText", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (detailText.isNotBlank()) Text(detailText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 👈 统一替换为 MiuiTextButton
                    if (!parking.notificationIgnored) MiuiTextButton("忽略", MaterialTheme.colorScheme.onSurfaceVariant, onIgnore)
                    MiuiTextButton("取车", MaterialTheme.colorScheme.primary, onRequestClear)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(durationText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (parking.amount > 0) Text(parking.amount.toMoneyText(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

}

@Composable
fun ActiveParkingConfirmOverlay(
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var actionTriggered by remember { mutableStateOf(false) }
    val glassEnabled = LocalAppGlassEnabled.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val edgeColor = if (glassEnabled) {
        if (isLight) Color.White.copy(alpha = 0.82f)
        else Color.White.copy(alpha = 0.20f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
    }
    val surfaceModifier = if (glassEnabled) {
        Modifier.drawGlassBackdrop(
            backdrop = backdrop,
            shape = shape,
            isLight = isLight
        )
    } else {
        Modifier
    }
    val contentModifier = if (glassEnabled) {
        Modifier.drawGlassDialogOverlay(isLight)
    } else {
        Modifier
    }

    Surface(
        // Surface 本身延伸到屏幕底部；系统导航栏 inset 只留给内容，
        // 避免渐变背景在导航栏区域额外形成一块浅色矩形。
        modifier = Modifier
            .fillMaxWidth()
            .then(surfaceModifier),
        shape = shape,
        color = if (glassEnabled) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.8.dp, edgeColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(contentModifier)
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 28.dp)
                // 将导航栏安全区放在渐变背景之外，避免底部 inset 出现独立色块。
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
            )
            Text(
                text = "已取车",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "确定已取车？卡片会从首页移除。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "取消",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = {
                        if (actionTriggered) return@TextButton
                        actionTriggered = true
                        onConfirm()
                    },
                    enabled = !actionTriggered,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    )
                ) {
                    Text(
                        "确定",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatDuration(fromMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = nowMillis - fromMillis
    if (diff < 0) return "刚刚"
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        hours < 24 -> "$hours 小时前"
        days < 30 -> "$days 天前"
        else -> "很久以前"
    }
}
