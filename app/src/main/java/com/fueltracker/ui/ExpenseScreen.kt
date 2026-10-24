package com.fueltracker.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.window.Dialog
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.components.scrollToHideBottomBar
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext


private val expenseDateFormat by lazy { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
private const val CATEGORY_FUEL = "加油"

private enum class ExpenseRange(val label: String) {
    ALL("全部"), MONTH("本月"), YEAR("今年"), CUSTOM_MONTH("")
}

private sealed class ExpenseListItem(open val timestamp: Long) {
    data class Fuel(val record: FuelRecord) : ExpenseListItem(record.timestamp)
    data class Expense(val record: ExpenseRecord) : ExpenseListItem(record.timestamp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: MainViewModel,
    selectedCategory: String?,
    onSelectedCategoryChange: (String?) -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (ExpenseRecord) -> Unit,
    onEditFuel: (FuelRecord) -> Unit
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val fuelRecords by viewModel.records.collectAsState()

    var deletingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var deletingFuel by remember { mutableStateOf<FuelRecord?>(null) }

    var selectedRange by remember { mutableStateOf(ExpenseRange.ALL) }
    var customYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var customMonth by remember { mutableIntStateOf(0) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    // 👇 修复 1：强制加入当前年份，防止在只有旧记录时无法查看今年空白月份
    val availableYears = remember(expenses, fuelRecords) {
        val currentYear = getYear(System.currentTimeMillis())
        (expenses.map { getYear(it.timestamp) } + fuelRecords.map { getYear(it.timestamp) } + currentYear)
            .distinct().sortedDescending()
    }

    val rangeBounds = remember(selectedRange, customYear, customMonth) {
        computeExpenseRange(selectedRange, customYear, customMonth)
    }

    val rangeLabel = when (selectedRange) {
        ExpenseRange.CUSTOM_MONTH if customMonth == 0 -> "${customYear}全年"
        ExpenseRange.CUSTOM_MONTH -> "${customYear}年${customMonth}月"
        else -> selectedRange.label
    }

    val summary = remember(expenses, fuelRecords, rangeBounds, rangeLabel) {
        computeSummary(expenses, fuelRecords, rangeBounds, rangeLabel)
    }

    // 趋势图独立的年份筛选状态，null 表示全部
    var selectedTrendYear by remember { mutableStateOf<Int?>(null) }

    val trendYears = remember(expenses, fuelRecords) {
        (
                expenses.map { getYear(it.timestamp) } +
                        fuelRecords.filter { !it.isInitialRecord }.map { getYear(it.timestamp) }
                ).distinct().sortedDescending()
    }



    // 年份被删空后自动回退到“全部”
    LaunchedEffect(trendYears) {
        if (selectedTrendYear != null && selectedTrendYear !in trendYears) {
            selectedTrendYear = null
        }
    }

    val structureItems = remember(expenses, fuelRecords, rangeBounds) {
        buildStructureData(expenses, fuelRecords, rangeBounds)
    }

    val filteredItems: List<ExpenseListItem> = remember(expenses, fuelRecords, rangeBounds, selectedCategory) {
        val (start, end) = rangeBounds
        val list = mutableListOf<ExpenseListItem>()
        val includeFuel = selectedCategory == null || selectedCategory == CATEGORY_FUEL
        val includeExpense = selectedCategory == null || selectedCategory != CATEGORY_FUEL

        if (includeFuel) {
            fuelRecords.filter { !it.isInitialRecord && inRange(it.timestamp, start, end) }
                .forEach { list.add(ExpenseListItem.Fuel(it)) }
        }
        if (includeExpense) {
            expenses.filter { inRange(it.timestamp, start, end) && (selectedCategory == null || it.category == selectedCategory) }
                .forEach { list.add(ExpenseListItem.Expense(it)) }
        }
        list.sortedByDescending { it.timestamp }
    }



    val monthTrend = remember(expenses, fuelRecords, selectedTrendYear) {
        buildMonthTrend(expenses, fuelRecords, selectedTrendYear)
    }



    val isEmpty = expenses.isEmpty() && fuelRecords.none { !it.isInitialRecord }
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("费用", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有费用记录\n点右下角 ＋ 添加", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollToHideBottomBar(listState, viewModel),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item("summary") {
                        SummaryCard(summary, selectedRange, rangeLabel, { selectedRange = it }, { showYearMonthPicker = true })
                    }
                    // 即便全是 0 也保留，强制展示时间轴
                    if (trendYears.isNotEmpty()) {
                        item("trend") {
                            ExpenseTrendCard(
                                points = monthTrend,
                                years = trendYears,
                                selectedYear = selectedTrendYear,
                                onYearSelected = { selectedTrendYear = it }
                            )
                        }
                    }
                    if (structureItems.isNotEmpty()) {
                        item("structure") {
                            ExpenseStructureCard(structureItems, selectedCategory) { cat ->
                                onSelectedCategoryChange(if (selectedCategory == cat) null else cat)
                            }
                        }
                    }

                    item("list_header") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selectedCategory == null) "明细" else "明细 · $selectedCategory", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (selectedCategory != null) {
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelectedCategoryChange(null) }.padding(8.dp)) {
                                    Text("清除", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        item("list_empty") {
                            MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("该范围内没有记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                            }
                        }
                    } else {
                        items(filteredItems, key = { if (it is ExpenseListItem.Fuel) "fuel_${it.record.id}" else "exp_${(it as ExpenseListItem.Expense).record.id}" }) { item ->
                            when (item) {
                                is ExpenseListItem.Fuel -> ExpenseDetailItemCard(
                                    title = "加油", time = expenseDateFormat.format(Date(item.record.timestamp)),
                                    amount = item.record.actualPaidAmount,
                                    onEdit = { onEditFuel(item.record) }, onDelete = { deletingFuel = item.record }
                                )
                                is ExpenseListItem.Expense -> ExpenseDetailItemCard(
                                    title = item.record.category, time = expenseDateFormat.format(Date(item.record.timestamp)),
                                    amount = item.record.amount,
                                    onEdit = { onEditExpense(item.record) }, onDelete = { deletingExpense = item.record }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = viewModel.isBottomBarVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                FloatingActionButton(
                    onClick = onAddExpense, modifier = Modifier.padding(end = 16.dp, bottom = 24.dp),
                    containerColor = Color(0xFFFF9500), contentColor = Color.White, shape = CircleShape
                ) { Icon(Icons.Default.Receipt, "记费用") }
            }
        }
    }

    if (showYearMonthPicker) {
        ExpenseYearMonthSheet(customYear, customMonth, availableYears, { showYearMonthPicker = false }) { y, m ->
            customYear = y; customMonth = m; selectedRange = ExpenseRange.CUSTOM_MONTH; showYearMonthPicker = false
        }
    }

    // 删除费用
    deletingExpense?.let { record ->
        DeleteConfirmDialog(
            title = "删除费用记录",
            message = "确定删除这条 ${record.category} ¥%.2f 的记录吗？".format(record.amount),
            onConfirm = {
                viewModel.deleteExpense(record) { result ->
                    if (result.isSuccess) {
                        deletingExpense = null
                    } else {
                        Toast.makeText(context, "删除失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { deletingExpense = null }
        )
    }

    // 删除加油
    deletingFuel?.let { record ->
        DeleteConfirmDialog(
            title = "删除加油记录",
            message = "确定删除这条 ¥%.2f 的加油记录吗？\n\n删除后相关油耗计算会重新推导。".format(record.actualPaidAmount),
            onConfirm = {
                // 👇 加上回调，等待真正删除成功后再关闭弹窗
                viewModel.deleteRecord(record) { result ->
                    if (result.isSuccess) {
                        deletingFuel = null
                    } else {
                        Toast.makeText(context, "删除失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { deletingFuel = null }
        )
    }
}

@Composable
private fun ExpenseDetailItemCard(title: String, time: String, amount: Double, onEdit: () -> Unit, onDelete: () -> Unit) {
    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    CompactExpenseTextButton("编辑", MaterialTheme.colorScheme.primary, onEdit)
                    CompactExpenseTextButton("删除", Color(0xFFFF3B30).copy(alpha = 0.8f), onDelete)
                }
            }
            Text("¥%.2f".format(amount), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun getYear(timestamp: Long): Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
private fun inRange(timestamp: Long, start: Long?, end: Long?) = (start == null || timestamp >= start) && (end == null || timestamp <= end)

private fun computeExpenseRange(range: ExpenseRange, customYear: Int, customMonth: Int): Pair<Long?, Long?> {
    if (range == ExpenseRange.ALL) return null to null
    val cal = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    when (range) {
        ExpenseRange.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        ExpenseRange.YEAR -> { cal.set(Calendar.MONTH, Calendar.JANUARY); cal.set(Calendar.DAY_OF_MONTH, 1) }
        ExpenseRange.CUSTOM_MONTH -> {
            cal.set(Calendar.YEAR, customYear)
            if (customMonth == 0) { cal.set(Calendar.MONTH, Calendar.JANUARY); cal.set(Calendar.DAY_OF_MONTH, 1) }
            else { cal.set(Calendar.MONTH, customMonth - 1); cal.set(Calendar.DAY_OF_MONTH, 1) }
        }
        else -> {}
    }
    val start = cal.timeInMillis
    val endCal = cal.clone() as Calendar
    when (range) {
        ExpenseRange.MONTH -> endCal.add(Calendar.MONTH, 1)
        ExpenseRange.YEAR -> endCal.add(Calendar.YEAR, 1)
        ExpenseRange.CUSTOM_MONTH -> if (customMonth == 0) endCal.add(Calendar.YEAR, 1) else endCal.add(Calendar.MONTH, 1)
        else -> {}
    }
    return start to (endCal.timeInMillis - 1)
}

private data class ExpenseSummary(val rangeLabel: String, val totalAmount: Double, val count: Int, val dailyAverage: Double, val showDaily: Boolean)

private fun computeSummary(expenses: List<ExpenseRecord>, fuelRecords: List<FuelRecord>, rangeBounds: Pair<Long?, Long?>, rangeLabel: String): ExpenseSummary {
    val (start, end) = rangeBounds
    val filteredExpenses = expenses.filter { inRange(it.timestamp, start, end) }
    val filteredFuel = fuelRecords.filter { !it.isInitialRecord && inRange(it.timestamp, start, end) }
    val total = filteredExpenses.sumOf { it.amount } + filteredFuel.sumOf { it.actualPaidAmount }
    val count = filteredExpenses.size + filteredFuel.size

    // 👇 修复 3：使用 Calendar 逐日计算天数，彻底消除夏令时误差
    val daysInRange = if (start == null || end == null) null else {
        val s = Calendar.getInstance().apply { timeInMillis = start }
        val e = Calendar.getInstance().apply { timeInMillis = end }
        var days = 1
        while (s.before(e)) { s.add(Calendar.DAY_OF_MONTH, 1); days++ }
        days
    }

    return ExpenseSummary(rangeLabel, total, count, if (daysInRange != null) total / daysInRange else 0.0, daysInRange != null)
}

@Composable
private fun SummaryCard(summary: ExpenseSummary, selectedRange: ExpenseRange, rangeLabel: String, onRangeSelected: (ExpenseRange) -> Unit, onCustomClick: () -> Unit) {
    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpenseRangeChip("全部", selectedRange == ExpenseRange.ALL) { onRangeSelected(ExpenseRange.ALL) }
                ExpenseRangeChip("本月", selectedRange == ExpenseRange.MONTH) { onRangeSelected(ExpenseRange.MONTH) }
                ExpenseRangeChip("今年", selectedRange == ExpenseRange.YEAR) { onRangeSelected(ExpenseRange.YEAR) }
                ExpenseRangeChip(if (selectedRange == ExpenseRange.CUSTOM_MONTH) rangeLabel else "选年月", selectedRange == ExpenseRange.CUSTOM_MONTH, onCustomClick)
            }
            Text("${summary.rangeLabel}支出", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("¥", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                Text("%.2f".format(summary.totalAmount), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { Text("笔数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${summary.count} 笔", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (summary.showDaily) Column { Text("日均", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("¥%.2f".format(summary.dailyAverage), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun ExpenseRangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun DeleteConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onDismiss, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text("取消", fontSize = 15.sp) }
                    TextButton(onConfirm, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除", fontSize = 15.sp, color = MaterialTheme.colorScheme.onError) }
                }
            }
        }
    }
}

@Composable
private fun CompactExpenseTextButton(text: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(8.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

private data class MonthTrendPoint(val year: Int, val month: Int, val value: Double)

private fun buildMonthTrend(
    expenses: List<ExpenseRecord>,
    fuelRecords: List<FuelRecord>,
    year: Int? // null = 全部
): List<MonthTrendPoint> {
    val grouped = mutableMapOf<Pair<Int, Int>, Double>()

    fun accumulate(timestamp: Long, amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) return
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        val y = c.get(Calendar.YEAR)
        if (year != null && y != year) return
        val key = y to (c.get(Calendar.MONTH) + 1)
        grouped[key] = (grouped[key] ?: 0.0) + amount
    }

    fuelRecords.filter { !it.isInitialRecord }
        .forEach { accumulate(it.timestamp, it.actualPaidAmount) }
    expenses.forEach { accumulate(it.timestamp, it.amount) }

    return grouped.entries
        .filter { it.value > 0.0 }
        .map { (key, value) -> MonthTrendPoint(key.first, key.second, value) }
        .sortedWith(compareBy<MonthTrendPoint> { it.year }.thenBy { it.month })
}

@Composable
private fun ExpenseTrendCard(
    points: List<MonthTrendPoint>,
    years: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    MiuiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (selectedYear == null) "费用趋势 · 全部" else "费用趋势 · ${selectedYear}年",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpenseRangeChip("全部", selectedYear == null) { onYearSelected(null) }
                years.forEach { y ->
                    ExpenseRangeChip("${y}年", selectedYear == y) { onYearSelected(y) }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (points.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "该范围暂无费用记录",
                        fontSize = 12.sp,
                        color = onSurfaceVariantColor
                    )
                }
                return@Column
            }

            val maxValue = points.maxOf { it.value }
            val yMax = if (maxValue > 0) maxValue * 1.15 else 1.0

            val valueLayouts = remember(points, onSurfaceColor) {
                points.map {
                    textMeasurer.measure(
                        "%.0f".format(it.value),
                        TextStyle(color = onSurfaceColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
            val timeLayouts = remember(points, onSurfaceVariantColor) {
                points.mapIndexed { index, point ->
                    val label = if (index == 0 || points[index - 1].year != point.year) {
                        "${point.year % 100}年${point.month}月"
                    } else {
                        "${point.month}月"
                    }
                    textMeasurer.measure(
                        label,
                        TextStyle(color = onSurfaceVariantColor, fontSize = 8.sp)
                    )
                }
            }

            val chartAreaHeight = 94.dp
            val timeAxisHeight = 18.dp
            val scrollState = rememberScrollState()
            val visibleCount = 13
            val barCount = points.size

            LaunchedEffect(barCount, selectedYear) {
                if (barCount > visibleCount) {
                    snapshotFlow { scrollState.maxValue }
                        .firstOrNull { it > 0 }
                        ?.let { scrollState.scrollTo(it) }
                } else {
                    scrollState.scrollTo(0)
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chartWidth =
                    if (barCount <= visibleCount) maxWidth
                    else (maxWidth / visibleCount) * barCount
                Box(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                    Canvas(
                        Modifier
                            .width(chartWidth)
                            .height(chartAreaHeight + timeAxisHeight + 16.dp)
                    ) {
                        val slotWidth = size.width / barCount
                        val barWidth = (slotWidth * 0.5f).coerceIn(4.dp.toPx(), 40.dp.toPx())
                        val chartHeight = size.height - timeAxisHeight.toPx() - 16.dp.toPx()
                        val topPadding = 16.dp.toPx()

                        points.forEachIndexed { index, point ->
                            val center = index * slotWidth + slotWidth / 2f
                            val barHeight = (chartHeight * (point.value / yMax).toFloat().coerceIn(0f, 1f))
                                .coerceAtLeast(2.dp.toPx())
                            val barTop = topPadding + chartHeight - barHeight

                            drawRoundRect(
                                if (index == barCount - 1) primaryColor
                                else primaryColor.copy(alpha = 0.55f),
                                Offset(center - barWidth / 2f, barTop),
                                Size(barWidth, barHeight),
                                CornerRadius(4.dp.toPx())
                            )
                            drawText(
                                textLayoutResult = valueLayouts[index],
                                topLeft = Offset(
                                    x = center - valueLayouts[index].size.width / 2f,
                                    y = barTop - valueLayouts[index].size.height - 2.dp.toPx()
                                )
                            )
                            drawText(
                                textLayoutResult = timeLayouts[index],
                                topLeft = Offset(
                                    x = center - timeLayouts[index].size.width / 2f,
                                    y = topPadding + chartHeight + 4.dp.toPx()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class StructureItem(val name: String, val amount: Double, val ratio: Float)

private fun buildStructureData(expenses: List<ExpenseRecord>, fuelRecords: List<FuelRecord>, rangeBounds: Pair<Long?, Long?>): List<StructureItem> {
    val (start, end) = rangeBounds
    val map = mutableMapOf<String, Double>()
    fuelRecords.filter { !it.isInitialRecord && inRange(it.timestamp, start, end) }.sumOf { it.actualPaidAmount }.takeIf { it > 0 }?.let { map[CATEGORY_FUEL] = it }

    // 👇 修复 4：累加而不是覆盖，防止用户自定义“加油”分类时数据被顶掉
    expenses.filter { inRange(it.timestamp, start, end) }.groupBy { it.category }.forEach { (cat, list) ->
        map[cat] = (map[cat] ?: 0.0) + list.sumOf { it.amount }
    }

    val total = map.values.sum()
    return map.map { (name, amount) -> StructureItem(name, amount, if (total > 0) (amount / total).toFloat() else 0f) }.filter { it.amount > 0 }.sortedByDescending { it.amount }
}

@Composable
private fun ExpenseStructureCard(items: List<StructureItem>, selectedCategory: String?, onCategoryClick: (String) -> Unit) {
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
                            Text("¥%.2f".format(item.amount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("%.0f%%".format(item.ratio * 100), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseYearMonthSheet(initialYear: Int, initialMonth: Int, availableYears: List<Int>, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("取消", Modifier.clickable(onClick = onDismiss).padding(8.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("选择年月", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("确定", Modifier.clickable { onConfirm(year, month) }.padding(8.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelList(selectedValue = year, items = availableYears, visibleCount = 3, modifier = Modifier.weight(1.4f), style = WheelStyle.WithBackground, itemLabel = { "${it}年" }, onChange = { year = it })
                WheelList(selectedValue = month, items = (0..12).toList(), visibleCount = 3, modifier = Modifier.weight(1f), style = WheelStyle.WithBackground, itemLabel = { if (it == 0) "全年" else "%02d 月".format(it) }, onChange = { month = it })
            }
        }
    }
}