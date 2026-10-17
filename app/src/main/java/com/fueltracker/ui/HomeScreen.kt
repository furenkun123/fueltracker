package com.fueltracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
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
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.miui.MiuiButton
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiTextIconButton
import com.fueltracker.ui.miui.MiuiTopBar
import com.fueltracker.ui.miui.VehicleCard
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.StatisticsSummary
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds


private data class CostPoint(
    val timestamp: Long,
    val value: Double
)

private val homeRecordDateFormat by lazy {
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
}

@Composable
fun HomeScreen(
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
    onOpenSettings: () -> Unit
) {
    val deliveryDate = vehicle?.deliveryDate

    var fabExpanded by remember { mutableStateOf(false) }

    val sortedRecords = remember(records, deliveryDate) {
        val validRecords = if (deliveryDate != null) {
            records.filter { it.timestamp >= deliveryDate }
        } else {
            records
        }
        validRecords.sortedByDescending { it.timestamp }
    }

    val fuelRecords = remember(sortedRecords) {
        sortedRecords.filter { !it.isInitialRecord }
    }

    val recentRecords = remember(fuelRecords) {
        fuelRecords.take(5)
    }

    val chronologicalRecords = remember(fuelRecords) {
        fuelRecords.asReversed()
    }

    val costData = remember(chronologicalRecords) {
        chronologicalRecords
            .map { record ->
                CostPoint(
                    timestamp = record.timestamp,
                    value = record.actualPaidAmount
                )
            }
            .filter { it.value >= 0.0 }
    }

    val canShowCost = costData.size >= 2

    val resolvedLatestConsumption = remember(latestConsumption, fuelRecords) {
        latestConsumption
            ?: fuelRecords.firstOrNull { it.consumption != null }?.consumption
    }

    // ---------------- 统计范围状态 ----------------
    var selectedRange by remember { mutableStateOf(StatsRange.ALL) }
    var customYear by remember {
        mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))
    }
    // customMonth: 0 = 全年，1-12 = 具体月份
    var customMonth by remember { mutableIntStateOf(0) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    // 只显示已有记录的年份（从加油记录中提取）
    val availableYears = remember(sortedRecords) {
        val years = sortedRecords
            .map {
                Calendar.getInstance()
                    .apply { timeInMillis = it.timestamp }
                    .get(Calendar.YEAR)
            }
            .distinct()
            .sortedDescending()

        years.ifEmpty {
            listOf(Calendar.getInstance().get(Calendar.YEAR))
        }
    }

    val statsRange = remember(selectedRange, customYear, customMonth) {
        computeStatsRange(
            range = selectedRange,
            customYear = customYear,
            customMonth = customMonth
        )
    }

    val statsSummary = remember(vehicle, records, statsRange) {
        FuelCalculator.calculateSummary(
            vehicle = vehicle,
            allRecords = records,
            startTime = statsRange.first,
            endTime = statsRange.second
        )
    }
    // ------------------------------------------------

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MiuiTopBar {
                MiuiTextIconButton(
                    text = "",
                    icon = Icons.Default.Settings,
                    onClick = onOpenSettings
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
            ) {
                if (vehicle == null) {
                    item {
                        EmptyVehicleCard(onAddVehicle = onAddVehicle)
                    }
                } else {
                    item {
                        VehicleCard(vehicle = vehicle)
                    }

                    if (activeParking != null) {
                        item(key = "active_parking") {
                            ActiveParkingCard(
                                parking = activeParking,
                                onClick = { onEditParking(activeParking) },
                                onClear = onClearParking,
                                onIgnore = onIgnoreParking
                            )
                        }
                    }


                    item {
                        ConsumptionCard(
                            recordCount = fuelRecords.size,
                            latestConsumption = resolvedLatestConsumption,
                            averageConsumption = averageConsumption
                        )
                    }

                    item {
                        ConsumptionStatsCard(
                            summary = statsSummary,
                            selectedRange = selectedRange,
                            customYear = customYear,
                            customMonth = customMonth,
                            onRangeSelected = { selectedRange = it },
                            onCustomMonthClick = { showYearMonthPicker = true }
                        )
                    }

                    if (canShowCost) {
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
                        items(
                            items = recentRecords,
                            key = { record: FuelRecord -> record.id }
                        ) { record: FuelRecord ->
                            HomeFuelRecordItem(record = record)
                        }
                    }
                }
            }
        }

        if (vehicle != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // 展开后的两个子按钮
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        FabActionItem(
                            icon = "🅿",
                            label = "停车",
                            onClick = {
                                fabExpanded = false
                                onAddParking()
                            }
                        )
                        FabActionItem(
                            icon = "⛽",
                            label = "加油",
                            onClick = {
                                fabExpanded = false
                                onAddFuel()
                            }
                        )
                    }
                }

                // 主按钮
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (fabExpanded) "收起" else "展开"
                    )
                }
            }
        }
    }

    if (showYearMonthPicker) {
        YearMonthPickerSheet(
            initialYear = customYear,
            initialMonth = customMonth,
            availableYears = availableYears,
            onDismiss = { showYearMonthPicker = false },
            onConfirm = { y, m ->
                customYear = y
                customMonth = m
                selectedRange = StatsRange.CUSTOM_MONTH
                showYearMonthPicker = false
            }
        )
    }
}

// =================================================
// 无车辆
// =================================================

@Composable
private fun EmptyVehicleCard(onAddVehicle: () -> Unit) {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "还没有车辆",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "添加你的车辆后开始记录油耗",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MiuiButton(text = "添加车辆", onClick = onAddVehicle)
        }
    }
}

// =================================================
// 当前油耗卡片
// =================================================

@Composable
private fun ConsumptionCard(
    recordCount: Int,
    latestConsumption: Double?,
    averageConsumption: Double?
) {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "当前油耗",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = latestConsumption?.let { "%.2f".format(it) } ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "L/100km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiuiStatisticItem(
                    title = "平均油耗",
                    value = averageConsumption?.let { "%.2f L/100km".format(it) } ?: "--"
                )
                MiuiStatisticItem(
                    title = "加油次数",
                    value = "$recordCount 次"
                )
            }
        }
    }
}

@Composable
private fun MiuiStatisticItem(title: String, value: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// =================================================
// 油耗统计卡片
// =================================================

private enum class StatsRange {
    ALL,
    MONTH,
    YEAR,
    CUSTOM_MONTH
}

private fun computeStatsRange(
    range: StatsRange,
    customYear: Int,
    customMonth: Int
): Pair<Long?, Long?> {
    val now = System.currentTimeMillis()

    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    when (range) {
        StatsRange.ALL -> return null to null
        StatsRange.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        StatsRange.YEAR -> {
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        StatsRange.CUSTOM_MONTH -> {
            cal.set(Calendar.YEAR, customYear)
            if (customMonth == 0) {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
            } else {
                cal.set(Calendar.MONTH, customMonth - 1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    val start = cal.timeInMillis
    val endCal = cal.clone() as Calendar

    when (range) {
        StatsRange.ALL -> return null to null
        StatsRange.MONTH -> endCal.add(Calendar.MONTH, 1)
        StatsRange.YEAR -> endCal.add(Calendar.YEAR, 1)
        StatsRange.CUSTOM_MONTH -> {
            if (customMonth == 0) {
                endCal.add(Calendar.YEAR, 1)
            } else {
                endCal.add(Calendar.MONTH, 1)
            }
        }
    }

    return start to (endCal.timeInMillis - 1)
}

@Composable
private fun ConsumptionStatsCard(
    summary: StatisticsSummary,
    selectedRange: StatsRange,
    customYear: Int,
    customMonth: Int,
    onRangeSelected: (StatsRange) -> Unit,
    onCustomMonthClick: () -> Unit
) {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "油耗统计",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 范围 chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatsChip(
                    text = "全部",
                    selected = selectedRange == StatsRange.ALL,
                    onClick = { onRangeSelected(StatsRange.ALL) }
                )
                StatsChip(
                    text = "本月",
                    selected = selectedRange == StatsRange.MONTH,
                    onClick = { onRangeSelected(StatsRange.MONTH) }
                )
                StatsChip(
                    text = "今年",
                    selected = selectedRange == StatsRange.YEAR,
                    onClick = { onRangeSelected(StatsRange.YEAR) }
                )

                val customLabel = when (selectedRange) {
                    StatsRange.CUSTOM_MONTH if customMonth == 0 ->
                        "${customYear}全年"

                    StatsRange.CUSTOM_MONTH if customMonth > 0 ->
                        "${customYear}年${customMonth}月"

                    else -> "选年月"
                }
                StatsChip(
                    text = customLabel,
                    selected = selectedRange == StatsRange.CUSTOM_MONTH,
                    onClick = onCustomMonthClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("加油次数", "${summary.totalCount} 次")
                StatItem("累计加油", "%.2f L".format(summary.totalVolume))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("累计花费", "¥%.2f".format(summary.totalCost))
                StatItem(
                    "平均油价",
                    summary.averagePrice?.let { "%.2f 元/L".format(it) } ?: "--"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("累计行驶", "%.0f km".format(summary.totalDistance))
                StatItem(
                    "平均油耗",
                    summary.averageConsumption?.let { "%.2f L/100km".format(it) } ?: "--"
                )
            }
        }
    }
}

@Composable
private fun StatsChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun StatItem(title: String, value: String) {
    Column(modifier = Modifier.width(150.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// =================================================
// 年月选择底部弹窗
// =================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearMonthPickerSheet(
    initialYear: Int,
    initialMonth: Int,
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }

    // 0 表示全年，1-12 表示具体月份
    val monthValues = remember { listOf(0) + (1..12).toList() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "选择年月",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm(year, month) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "确定",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeWheelList(
                    selectedValue = year,
                    items = availableYears,
                    visibleCount = 3,
                    modifier = Modifier.weight(1.4f),
                    itemLabel = { "${it}年" },
                    onChange = { year = it }
                )
                HomeWheelList(
                    selectedValue = month,
                    items = monthValues,
                    visibleCount = 3,
                    modifier = Modifier.weight(1f),
                    itemLabel = { if (it == 0) "全年" else "%02d 月".format(it) },
                    onChange = { month = it }
                )
            }
        }
    }
}

@Composable
private fun <T> HomeWheelList(
    selectedValue: T,
    items: List<T>,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    itemLabel: (T) -> String = { it.toString() },
    onChange: (T) -> Unit
) {
    WheelList(
        selectedValue = selectedValue,
        items = items,
        modifier = modifier,
        visibleCount = visibleCount,
        style = WheelStyle.WithBackground,
        itemLabel = itemLabel,
        onChange = onChange
    )
}
// =================================================
// 加油趋势图
// =================================================

private data class MonthCostPoint(
    val year: Int,
    val month: Int,
    val value: Double
)

private fun buildMonthlyCostPoints(records: List<CostPoint>): List<MonthCostPoint> {
    if (records.isEmpty()) return emptyList()

    val grouped = mutableMapOf<Pair<Int, Int>, Double>()
    records.forEach { point ->
        val c = Calendar.getInstance().apply { timeInMillis = point.timestamp }
        val key = c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
        grouped[key] = (grouped[key] ?: 0.0) + point.value
    }

    return grouped
        .map { (key, value) -> MonthCostPoint(key.first, key.second, value) }
        .filter { it.value > 0.0 }
        .sortedWith(compareBy({ it.year }, { it.month }))
}
@Composable
private fun ChartCard(points: List<CostPoint>) {
    val monthlyPoints = remember(points) { buildMonthlyCostPoints(points) }
    if (monthlyPoints.isEmpty()) return

    val values = remember(monthlyPoints) { monthlyPoints.map { it.value } }
    val maxValue = values.maxOrNull() ?: return

    val yMax = if (maxValue > 0) maxValue * 1.15 else 1.0

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val baseValueLabelStyle = remember(onSurfaceVariantColor) {
        TextStyle(
            color = onSurfaceVariantColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Normal
        )
    }
    val baseTimeLabelStyle = remember(onSurfaceVariantColor) {
        TextStyle(
            color = onSurfaceVariantColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Normal
        )
    }

    val valueLabelLayouts = remember(values, baseValueLabelStyle, textMeasurer) {
        values.map { value ->
            textMeasurer.measure(
                text = "%.0f".format(value),
                style = baseValueLabelStyle.copy(
                    color = onSurfaceColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    val isSameYear = remember(monthlyPoints) {
        monthlyPoints.map { it.year }.distinct().size <= 1
    }

    val timeLabelLayouts = remember(
        monthlyPoints,
        isSameYear,
        baseTimeLabelStyle,
        textMeasurer
    ) {
        monthlyPoints.map { p ->
            val label = if (isSameYear) "${p.month}月"
            else "%02d/%02d".format(p.year % 100, p.month)
            textMeasurer.measure(text = label, style = baseTimeLabelStyle)
        }
    }

    val topPaddingDp = remember(valueLabelLayouts, density) {
        val maxLabelHeightPx = valueLabelLayouts.maxOfOrNull { it.size.height } ?: 0
        with(density) {
            (maxLabelHeightPx.toDp() + 4.dp).coerceAtLeast(16.dp)
        }
    }

    val chartAreaHeight = 94.dp
    val timeAxisHeight = 18.dp
    val canvasHeight = topPaddingDp + chartAreaHeight + timeAxisHeight

    val scrollState = rememberScrollState()
    val visibleCount = 10
    val barCount = monthlyPoints.size

    LaunchedEffect(barCount) {
        if (barCount > visibleCount) {
            val maxScroll = snapshotFlow { scrollState.maxValue }
                .firstOrNull { it > 0 }
                ?: return@LaunchedEffect
            scrollState.scrollTo(maxScroll)
        }
    }

    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "加油趋势",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(按月 · 元)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = maxWidth / visibleCount
                val chartWidth = if (barCount <= visibleCount) {
                    maxWidth
                } else {
                    itemWidth * barCount
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
                        val width = size.width
                        val topPadding = topPaddingDp.toPx()
                        val timeAxis = timeAxisHeight.toPx()
                        val chartHeight = size.height - topPadding - timeAxis
                        val slotWidth = width / barCount

                        val barWidth = (slotWidth * 0.5f)
                            .coerceIn(4.dp.toPx(), 40.dp.toPx())

                        val minBarHeight = 2.dp.toPx()
                        val timeLabelY = topPadding + chartHeight + 4.dp.toPx()

                        monthlyPoints.forEachIndexed { index, point ->
                            val slotLeft = index * slotWidth
                            val barCenterX = slotLeft + slotWidth / 2f
                            val barLeft = barCenterX - barWidth / 2f

                            val heightFactor = (point.value / yMax).toFloat().coerceIn(0f, 1f)
                            val barHeight = (chartHeight * heightFactor).coerceAtLeast(minBarHeight)
                            val barTop = topPadding + (chartHeight - barHeight)

                            val isLatest = index == monthlyPoints.size - 1
                            val barColor = if (isLatest) primaryColor
                            else primaryColor.copy(alpha = 0.55f)

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            val valueLayout = valueLabelLayouts[index]
                            val vWidth = valueLayout.size.width.toFloat()
                            val vHeight = valueLayout.size.height.toFloat()
                            val vMaxX = (width - vWidth).coerceAtLeast(0f)
                            val valueX = (barCenterX - vWidth / 2f).coerceIn(0f, vMaxX)
                            val valueY = barTop - vHeight - 2.dp.toPx()
                            drawText(valueLayout, topLeft = Offset(valueX, valueY))

                            val timeLayout = timeLabelLayouts[index]
                            val tWidth = timeLayout.size.width.toFloat()
                            val tMaxX = (width - tWidth).coerceAtLeast(0f)
                            val timeX = (barCenterX - tWidth / 2f).coerceIn(0f, tMaxX)
                            drawText(timeLayout, topLeft = Offset(timeX, timeLabelY))
                        }
                    }
                }
            }
        }
    }
}

// =================================================
// 首页最近加油记录
// =================================================

@Composable
private fun HomeFuelRecordItem(record: FuelRecord) {
    val timeText = remember(record.timestamp) {
        homeRecordDateFormat.format(Date(record.timestamp))
    }

    val singleConsumption = record.consumption
    val paidAmount = record.actualPaidAmount
    val volume = record.volume
    val unitPrice = record.unitPrice

    // odometer 为 null 或 <= 0 时视为"未记录"，返回 null
    val odometerText: String? = record.odometer
        ?.takeIf { it > 0.0 }
        ?.let { odometer ->
            val rounded = odometer.toInt()
            if (abs(odometer - rounded) < 0.001) {
                "%.0f".format(odometer)
            } else {
                "%.1f".format(odometer)
            }
        }

    // 副标题：有里程就拼进去，没有就只显示单价
    val subtitleText = if (odometerText != null) {
        "$odometerText km · %.2f 元/L".format(unitPrice)
    } else {
        "%.2f 元/L".format(unitPrice)
    }

    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ============ 顶行：时间 + 状态标签 + 金额 ============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    if (record.isFull) {
                        Text(
                            text = "已加满",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = "未加满",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    if (record.hasMissedRecord) {
                        val missedVolume = record.missedVolume ?: 0.0
                        Text(
                            text = "含漏记 %.2f L".format(missedVolume),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "¥%.2f".format(paidAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // ============ 油量 + 油耗 ============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "%.2f L".format(volume),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                singleConsumption?.let { consumption ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.2f".format(consumption),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "L/100km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ============ 加油后剩余（有才显示） ============
            record.remainingFuel?.let { remaining ->
                Text(
                    text = "加油后剩余 %.2f L".format(remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyRecordCard() {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "还没有加油记录",
            modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
/**
 * 展开式 FAB 里的单个操作项。
 * 左侧一个小方形 icon 块，右侧文字标签，整体是一颗带阴影的胶囊。
 */
@Composable
private fun FabActionItem(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp,
                end = 18.dp,
                top = 10.dp,
                bottom = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(6.dp))

            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
/**
 * 首页"当前车位"卡片。
 */
@Composable
private fun ActiveParkingCard(
    parking: ExpenseRecord,
    onClick: () -> Unit,
    onClear: () -> Unit,
    onIgnore: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    // 每 60 秒刷新一次时长显示
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(parking.timestamp) {
        while (true) {
            kotlinx.coroutines.delay(60_000L.milliseconds)
            nowTick = System.currentTimeMillis()
        }
    }

    val durationText = remember(parking.timestamp, nowTick) {
        formatDuration(parking.timestamp, nowTick)
    }


    val locationText = parking.locationName?.takeIf { it.isNotBlank() } ?: "停车场"

    val detailText = buildString {
        parking.floor?.let { if (isNotEmpty()) append(" · "); append(it) }
        parking.area?.let { if (isNotEmpty()) append(" · "); append(it) }
        parking.spotNumber?.let { if (isNotEmpty()) append(" · "); append(it) }
    }.trimStart(' ', '·')

    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶栏：位置 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📍 $locationText",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (detailText.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = detailText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!parking.notificationIgnored) {
                        CompactTextButton(
                            text = "忽略",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onIgnore
                        )
                    }
                    CompactTextButton(
                        text = "取车",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { showClearConfirm = true }
                    )
                }
            }

            // 时长 + 费用
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = durationText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (parking.amount > 0) {
                    Text(
                        text = "¥%.2f".format(parking.amount),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "已取车",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text("确定已取车？卡片会从首页移除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun CompactTextButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1
        )
    }
}

private fun formatDuration
            (fromMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
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