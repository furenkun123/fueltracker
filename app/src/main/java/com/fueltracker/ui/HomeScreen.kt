package com.fueltracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.MiuiConfirmDialog
import com.fueltracker.ui.components.MiuiFilterChip
import com.fueltracker.ui.components.MiuiTextButton
import com.fueltracker.ui.components.MiuiYearMonthSheet
import com.fueltracker.ui.components.scrollToHideFab
import com.fueltracker.ui.miui.MiuiButton
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiTextIconButton
import com.fueltracker.ui.miui.MiuiTopBar
import com.fueltracker.ui.miui.VehicleCard
import com.fueltracker.util.ExpenseRange
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.StatisticsSummary
import com.fueltracker.util.computeExpenseRange
import com.fueltracker.util.toDateTimeText
import com.fueltracker.util.toMoneyText
import com.fueltracker.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
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
    onOpenSettings: () -> Unit,
    hazeState: HazeState? = null,
    isGlassEnabled: Boolean = false
) {
    val deliveryDate = vehicle?.deliveryDate
    var fabExpanded by remember { mutableStateOf(false) }

    val (displayRecords, recentRecords) = remember(records, deliveryDate) {
        val list = records
            .filter { (deliveryDate == null || it.timestamp >= deliveryDate) && !it.isInitialRecord }
            .sortedByDescending { it.timestamp }
        list to list.take(5)
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
    val isFloating = viewModel.isFloatingBottomBar

    // 👈 外层只保留一个 Box
    Box(modifier = Modifier.fillMaxSize()) {

        // 👈 1. 底层：LazyColumn（挂载 hazeSource，顶部留白 96dp）
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
                .scrollToHideFab(listState, viewModel),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 96.dp, bottom = if (isFloating) 120.dp else 80.dp) // 👈 顶部留出悬浮顶栏的高度
        ) {
            if (vehicle == null) {
                item { EmptyVehicleCard(onAddVehicle) }
            } else {
                item { VehicleCard(vehicle) }
                if (activeParking != null) {
                    item(key = "active_parking_${activeParking.id}") {
                        ActiveParkingCard(
                            parking = activeParking,
                            onClick = { onEditParking(activeParking) },
                            onClear = onClearParking,
                            onIgnore = onIgnoreParking
                        )
                    }
                }
                item { ConsumptionCard(statsSummary.totalCount, resolvedLatestConsumption, averageConsumption) }

                item {
                    ConsumptionStatsCard(
                        summary = statsSummary, selectedRange = selectedRange,
                        customYear = customYear, customMonth = customMonth,
                        onRangeSelected = { selectedRange = it },
                        onCustomMonthClick = { showYearMonthPicker = true }
                    )
                }
                if (costData.size >= 2) item { ChartCard(points = costData) }

                item {
                    Text(
                        text = "最近加油", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (recentRecords.isEmpty()) item { EmptyRecordCard() }
                else items(recentRecords, key = { it.id }) { HomeFuelRecordItem(it) }
            }
        }

        // 👈 2. 顶层：悬浮顶栏（对齐到顶部，删除了内部旧的那个 MiuiTopBar）
        MiuiTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = "随记油耗",
            isGlassEnabled = isGlassEnabled
        ) {
            MiuiTextIconButton(text = "", icon = Icons.Default.Settings, onClick = onOpenSettings)
        }

        // 👈 3. 顶层：右下角 FAB 按钮
        if (vehicle != null) {
            AnimatedVisibility(
                visible = viewModel.isFabVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (isFloating) 94.dp else 52.dp)
            ) {
                Column(
                    modifier = Modifier.padding(end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                            FabActionItem("🅿", "停车") { fabExpanded = false; onAddParking() }
                            FabActionItem("⛽", "加油") { fabExpanded = false; onAddFuel() }
                        }
                    }
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape
                    ) {
                        Icon(if (fabExpanded) Icons.Default.Close else Icons.Default.Add, null)
                    }
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
    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("当前油耗", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(latestConsumption?.let { "%.2f".format(it) } ?: "--", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("L/100km", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("平均油耗", averageConsumption?.let { "%.2f L/100km".format(it) } ?: "--")
                StatItem("加油次数", "$recordCount 次")
            }
        }
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
            Text("油耗统计", fontSize = 15.sp, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 👈 全部替换为 MiuiFilterChip
                MiuiFilterChip("全部", selectedRange == ExpenseRange.ALL) { onRangeSelected(ExpenseRange.ALL) }
                MiuiFilterChip("本月", selectedRange == ExpenseRange.MONTH) { onRangeSelected(ExpenseRange.MONTH) }
                MiuiFilterChip("今年", selectedRange == ExpenseRange.YEAR) { onRangeSelected(ExpenseRange.YEAR) }
                val customLabel = when (selectedRange) {
                    ExpenseRange.CUSTOM_MONTH if customMonth == 0 -> "${customYear}全年"
                    ExpenseRange.CUSTOM_MONTH if customMonth > 0 -> "${customYear}年${customMonth}月"
                    else -> "选年月"
                }
                MiuiFilterChip(customLabel, selectedRange == ExpenseRange.CUSTOM_MONTH, onCustomMonthClick)
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

    val topPaddingDp = remember(valueLabelLayouts, density) {
        with(density) { ((valueLabelLayouts.maxOfOrNull { it.size.height } ?: 0).toDp() + 4.dp).coerceAtLeast(16.dp) }
    }
    val chartAreaHeight = 94.dp
    val timeAxisHeight = 18.dp
    val canvasHeight = topPaddingDp + chartAreaHeight + timeAxisHeight

    val scrollState = rememberScrollState()
    val visibleCount = 10

    LaunchedEffect(monthlyPoints.size) {
        if (monthlyPoints.size > visibleCount) {
            snapshotFlow { scrollState.maxValue }
                .firstOrNull { it > 0 }
                ?.let { scrollState.scrollTo(it) }
        }
    }

    MiuiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
                            val barHeight = (chartHeight * (point.value / yMax).toFloat().coerceIn(0f, 1f)).coerceAtLeast(2.dp.toPx())
                            val barTop = topPaddingDp.toPx() + (chartHeight - barHeight)

                            drawRoundRect(
                                color = if (index == monthlyPoints.size - 1) primaryColor else primaryColor.copy(alpha = 0.55f),
                                topLeft = Offset(barLeft, barTop), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(4.dp.toPx())
                            )

                            val vLayout = valueLabelLayouts[index]
                            if (vLayout.size.width <= size.width) {
                                drawText(vLayout, topLeft = Offset((barCenterX - vLayout.size.width / 2f).coerceIn(0f, size.width - vLayout.size.width), barTop - vLayout.size.height - 2.dp.toPx()))
                            }

                            val tLayout = timeLabelLayouts[index]
                            if (tLayout.size.width <= size.width) {
                                drawText(tLayout, topLeft = Offset((barCenterX - tLayout.size.width / 2f).coerceIn(0f, size.width - tLayout.size.width), timeLabelY))
                            }
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
private fun ActiveParkingCard(parking: ExpenseRecord, onClick: () -> Unit, onClear: () -> Unit, onIgnore: () -> Unit) {
    var showClearConfirm by remember(parking.id) { mutableStateOf(false) }
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
                    MiuiTextButton("取车", MaterialTheme.colorScheme.primary) { showClearConfirm = true }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(durationText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (parking.amount > 0) Text(parking.amount.toMoneyText(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showClearConfirm) {
        // 👈 直接复用 MiuiConfirmDialog
        MiuiConfirmDialog(
            title = "已取车",
            message = "确定已取车？卡片会从首页移除。",
            confirmText = "确定",
            onConfirm = {
                showClearConfirm = false
                onClear()
            },
            onDismiss = { showClearConfirm = false }
        )
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