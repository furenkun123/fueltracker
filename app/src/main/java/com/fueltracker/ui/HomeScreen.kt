package com.fueltracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.ConsumptionTrendCard
import com.fueltracker.ui.components.ConsumptionTrendPoint
import com.fueltracker.ui.components.MiuixVehicleCard
import com.fueltracker.ui.components.scrollToHideFab
import com.fueltracker.ui.miuix.MiuixButton
import com.fueltracker.ui.miuix.MiuixCard
import com.fueltracker.ui.miuix.MiuixFilterChip
import com.fueltracker.ui.miuix.MiuixTextButton
import com.fueltracker.ui.miuix.MiuixTopBarIconButton
import com.fueltracker.ui.miuix.dialogs.MiuixConfirmDialog
import com.fueltracker.ui.miuix.dialogs.MiuixYearMonthSheet
import com.fueltracker.ui.theme.adaptiveBackgroundTitleColor
import com.fueltracker.ui.theme.adaptiveBackgroundTitleStyle
import com.fueltracker.util.ExpenseRange
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.OutlierSensitivityPreferences
import com.fueltracker.util.StatisticsSummary
import com.fueltracker.util.computeExpenseRange
import com.fueltracker.util.toMoneyText
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds


@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    vehicle: Vehicle?,
    records: List<FuelRecord>,
    activeParking: ExpenseRecord? = null,
    onAddVehicle: () -> Unit,
    onAddFuel: () -> Unit,
    onAddParking: () -> Unit,
    onEditParking: (ExpenseRecord) -> Unit,
    onClearParking: () -> Unit,
    onIgnoreParking: () -> Unit,
    onCollapseProgressChange: (Float) -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val deliveryDate = vehicle?.deliveryDate
    var fabExpanded by remember { mutableStateOf(false) }

    // 当前异常识别模式
    val outlierSensitivity = OutlierSensitivityPreferences.mode

    val displayRecords = remember(records, deliveryDate) {
        records
            .filter { (deliveryDate == null || it.timestamp >= deliveryDate) && !it.isInitialRecord }
            .sortedByDescending { it.timestamp }
    }

    val scope = rememberCoroutineScope()

    // 👈 统一使用 ExpenseRange
    val selectedRange = viewModel.homeStatsSelectedRange
    val customYear = viewModel.homeStatsCustomYear
    val customMonth = viewModel.homeStatsCustomMonth

    var showYearMonthPicker by remember { mutableStateOf(false) }

    val availableYears = remember(records, customYear) {
        (records.map { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR) } + customYear)
            .distinct().sortedDescending()
    }

    // 👈 统一使用 computeExpenseRange
    val statsRange = remember(selectedRange, customYear, customMonth) {
        computeExpenseRange(selectedRange, customYear, customMonth)
    }

    // 首页统计和趋势图共用一次完整油耗分析
    val homeSummaries by produceState<Pair<StatisticsSummary, StatisticsSummary>?>(
        initialValue = null,
        vehicle,
        records,
        statsRange,
        outlierSensitivity
    ) {
        value = withContext(Dispatchers.Default) {
            FuelCalculator.calculateSummaryPair(
                allRecords = records,
                vehicle = vehicle,
                startTime = statsRange.first,
                endTime = statsRange.second,
                sensitivity = outlierSensitivity
            )
        }
    }

    // 当前筛选范围的统计结果
    val statsSummary = homeSummaries?.first

    // 完整历史统计结果
    val consumptionTrendSummary = homeSummaries?.second

    // 从完整分析结果中提取最新可靠油耗
    val resolvedLatestConsumption = remember(
        consumptionTrendSummary,
        displayRecords
    ) {
        consumptionTrendSummary
            ?.rangeResults
            ?.lastOrNull { FuelCalculator.isTrendUsable(it) }
            ?.consumption
            ?: displayRecords.firstOrNull { it.consumption != null }?.consumption
    }

    // 趋势图年份筛选
    val selectedConsumptionYears = viewModel.homeConsumptionYears

    // 提取所有符合趋势图条件的油耗数据
    val consumptionTrendPoints = remember(consumptionTrendSummary) {
        consumptionTrendSummary?.rangeResults.orEmpty()
            .filter { FuelCalculator.isTrendUsable(it) }
            .map {
                ConsumptionTrendPoint(
                    timestamp = it.endTimestamp,
                    value = it.consumption,
                    distance = it.distance,
                    recordId = it.recordId
                )
            }
            .sortedBy { it.timestamp }
    }
    // 只显示存在有效油耗数据的年份
    val consumptionYears = remember(consumptionTrendPoints) {
        consumptionTrendPoints
            .map {
                Calendar.getInstance().apply {
                    timeInMillis = it.timestamp
                }.get(Calendar.YEAR)
            }
            .distinct()
            .sortedDescending()
    }

    val scrollKey = "home:${vehicle?.id ?: "none"}"

    val savedScroll = remember(viewModel, scrollKey) {
        viewModel.getPageScrollPosition(scrollKey)
    }

    val listState = key(scrollKey) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = savedScroll.first,
            initialFirstVisibleItemScrollOffset = savedScroll.second
        )
    }

    LaunchedEffect(listState, scrollKey) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to
                    listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.savePageScrollPosition(scrollKey, index, offset)
        }
    }

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
                val titleColor = adaptiveBackgroundTitleColor(
                    MiuixTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "随记油耗",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        style = adaptiveBackgroundTitleStyle(),
                        modifier = Modifier.weight(1f)
                    )

                    MiuixTopBarIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "设置",
                        onClick = onOpenSettings,
                        containerSize = 42.dp,
                        iconSize = 21.dp,
                        iconTint = titleColor,
                        containerColor = if (titleColor.luminance() > 0.5f) {
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)
                        } else {
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f)
                        }
                    )
                }
            }

            if (vehicle == null) {
                item {
                    EmptyVehicleCard(onAddVehicle)
                }
            } else {
                item {
                    MiuixVehicleCard(vehicle)
                }

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

                //加油趋势（按月）
                item {
                    statsSummary?.let { summary ->
                        ConsumptionCard(
                            latestConsumption = resolvedLatestConsumption,
                            summary = summary,
                            selectedRange = selectedRange,
                            customYear = customYear,
                            customMonth = customMonth,
                            onRangeSelected = {
                                viewModel.updateHomeStatsRange(it)
                                viewModel.resetPageScrollPosition(scrollKey)

                                scope.launch {
                                    listState.scrollToItem(0)
                                }
                            },
                            onCustomMonthClick = { showYearMonthPicker = true }
                        )
                    }
                }


                //油耗变化趋势
                if (consumptionTrendPoints.size >= 2) {
                    item(key = "chart_consumption") {
                        ConsumptionTrendCard(
                            points = consumptionTrendPoints,
                            years = consumptionYears,
                            selectedYears = selectedConsumptionYears,
                            onYearsSelected = {
                                viewModel.updateHomeConsumptionYears(it)
                            }
                        )
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
            ) {
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

                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = MiuixTheme.colorScheme.primary,
                        shape = CircleShape,
                        shadowElevation = 0.dp
                    ) {
                        Icon(
                            if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    if (showYearMonthPicker) {
        MiuixYearMonthSheet(
            initialYear = customYear, initialMonth = customMonth, availableYears = availableYears,
            onDismiss = { showYearMonthPicker = false },
            onConfirm = { y, m ->
                viewModel.updateHomeStatsRange(
                    ExpenseRange.CUSTOM_MONTH,
                    y,
                    m
                )

                viewModel.resetPageScrollPosition(scrollKey)

                scope.launch {
                    listState.scrollToItem(0)
                }

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
    MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("还没有车辆", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold)
            Text("添加你的车辆后开始记录油耗", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
            MiuixButton(text = "添加车辆", onClick = onAddVehicle)
        }
    }
}

@Composable
private fun StatItem(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
    }
}

// =================================================
// 统计卡片
// =================================================
@Composable
private fun ConsumptionCard(
    latestConsumption: Double?,
    summary: StatisticsSummary,
    selectedRange: ExpenseRange,
    customYear: Int,
    customMonth: Int,
    onRangeSelected: (ExpenseRange) -> Unit,
    onCustomMonthClick: () -> Unit
) {
    MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ① 顶部：只保留当前油耗（主指标）
            Column {
                Text(
                    text = "当前油耗",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.height(36.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = latestConsumption?.let { "%.2f".format(it) } ?: "--",
                        fontSize = 34.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "L/100km",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }

            // ② 分隔线
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
            )

            // ③ 统计标题 + chips 同一行，chips 靠右
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "油耗统计",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixFilterChip("全部", selectedRange == ExpenseRange.ALL) { onRangeSelected(ExpenseRange.ALL) }
                        MiuixFilterChip("本月", selectedRange == ExpenseRange.MONTH) { onRangeSelected(ExpenseRange.MONTH) }
                        MiuixFilterChip("今年", selectedRange == ExpenseRange.YEAR) { onRangeSelected(ExpenseRange.YEAR) }
                        val customLabel = when (selectedRange) {
                            ExpenseRange.CUSTOM_MONTH if customMonth == 0 -> "${customYear}全年"
                            ExpenseRange.CUSTOM_MONTH if customMonth > 0 -> "${customYear}年${customMonth}月"
                            else -> "选年月"
                        }
                        MiuixFilterChip(customLabel, selectedRange == ExpenseRange.CUSTOM_MONTH, onCustomMonthClick)
                    }
                }
            }

            // ④ 统计三行：平均油耗放第一项
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    "平均油耗",
                    summary.averageConsumption?.let { "%.2f L/100km".format(it) } ?: "--",
                    Modifier.weight(1f)
                )
                StatItem("加油次数", "${summary.totalCount} 次", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem("累计加油", "%.2f L".format(summary.totalVolume), Modifier.weight(1f))
                StatItem("累计花费", summary.totalCost.toMoneyText(), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem("平均油价", summary.averagePrice?.let { "%.2f 元/L".format(it) } ?: "--", Modifier.weight(1f))
                StatItem("累计行驶", "%.0f km".format(summary.totalDistance), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FabActionItem(icon: String, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        color = MiuixTheme.colorScheme.surfaceVariant, shadowElevation = 6.dp, shape = RoundedCornerShape(24.dp)
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

    MiuixCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. 顶部：仅展示地点和详细信息
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("📍 $locationText", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (detailText.isNotBlank()) {
                    Text(detailText, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                }
            }

            // 2. 底部行：左侧时长/金额，右侧操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically // 按钮和左侧信息垂直居中
            ) {
                // 左侧信息
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(durationText, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    if (parking.amount > 0) {
                        Text(parking.amount.toMoneyText(), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }

                // 右侧按钮
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!parking.notificationIgnored) {
                        MiuixTextButton("忽略", MiuixTheme.colorScheme.onSurfaceSecondary, onClick = onIgnore)
                    }
                    MiuixTextButton("取车", MiuixTheme.colorScheme.primary) { showClearConfirm = true }
                }
            }
        }
    }

    if (showClearConfirm) {
        MiuixConfirmDialog(
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
