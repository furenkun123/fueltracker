package com.fueltracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.fueltracker.ui.components.scrollToHideBottomBar

private val expenseDateFormat by lazy {
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
}

private const val CATEGORY_FUEL = "加油"

private enum class ExpenseRange(val label: String) {
    ALL("全部"),
    MONTH("本月"),
    YEAR("今年"),
    CUSTOM_MONTH("")
}

/**
 * 明细列表的条目：加油记录 + 非加油费用。
 */
private sealed class ExpenseListItem {
    abstract val timestamp: Long

    data class Fuel(val record: FuelRecord) : ExpenseListItem() {
        override val timestamp: Long get() = record.timestamp
    }

    data class Expense(val record: ExpenseRecord) : ExpenseListItem() {
        override val timestamp: Long get() = record.timestamp
    }
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
    val expenses by viewModel.expenses.collectAsState()
    val fuelRecords by viewModel.records.collectAsState()

    var deletingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var deletingFuel by remember { mutableStateOf<FuelRecord?>(null) }

    var selectedRange by remember { mutableStateOf(ExpenseRange.ALL) }
    var customYear by remember {
        mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR))
    }
    var customMonth by remember { mutableIntStateOf(0) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    val availableYears = remember(expenses, fuelRecords) {
        val expenseYears = expenses.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR)
        }
        val fuelYears = fuelRecords.map {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.YEAR)
        }
        val all = (expenseYears + fuelYears).distinct().sortedDescending()
        all.ifEmpty {
            listOf(Calendar.getInstance().get(Calendar.YEAR))
        }
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

    val monthTrend = remember(expenses, fuelRecords) {
        buildMonthTrend(expenses, fuelRecords)
            .filter { it.value > 0.0 }
    }

    val structureItems = remember(expenses, fuelRecords, rangeBounds) {
        buildStructureData(expenses, fuelRecords, rangeBounds)
    }

    // 明细列表：加油 + 费用，按时间倒序
    val filteredItems: List<ExpenseListItem> = remember(
        expenses,
        fuelRecords,
        rangeBounds,
        selectedCategory
    ) {
        val (start, end) = rangeBounds
        val list = mutableListOf<ExpenseListItem>()

        val includeFuel = selectedCategory == null || selectedCategory == CATEGORY_FUEL
        val includeExpense = selectedCategory == null || selectedCategory != CATEGORY_FUEL

        if (includeFuel) {
            fuelRecords
                .filter { f ->
                    !f.isInitialRecord &&
                            (start == null || f.timestamp >= start) &&
                            (end == null || f.timestamp <= end)
                }
                .forEach { list.add(ExpenseListItem.Fuel(it)) }
        }

        if (includeExpense) {
            expenses
                .filter { e ->
                    val inRange = (start == null || e.timestamp >= start) &&
                            (end == null || e.timestamp <= end)
                    val matchCategory = selectedCategory == null || e.category == selectedCategory
                    inRange && matchCategory
                }
                .forEach { list.add(ExpenseListItem.Expense(it)) }
        }

        list.sortedByDescending { it.timestamp }
    }

    val isEmpty = expenses.isEmpty() && fuelRecords.none { !it.isInitialRecord }

    val listState = rememberLazyListState()


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "费用",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "还没有费用记录\n点右下角 ＋ 添加",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .scrollToHideBottomBar(listState, viewModel),                     // ★ 放到最后
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "summary") {
                        SummaryCard(
                            summary = summary,
                            selectedRange = selectedRange,
                            rangeLabel = rangeLabel,
                            onRangeSelected = { selectedRange = it },
                            onCustomClick = { showYearMonthPicker = true }
                        )
                    }

                    if (monthTrend.isNotEmpty()) {
                        item(key = "trend") {
                            ExpenseTrendCard(points = monthTrend)
                        }
                    }

                    if (structureItems.isNotEmpty()) {
                        item(key = "structure") {
                            ExpenseStructureCard(
                                items = structureItems,
                                selectedCategory = selectedCategory,
                                onCategoryClick = { cat ->
                                    val newCategory = if (selectedCategory == cat) null else cat
                                    onSelectedCategoryChange(newCategory)
                                }
                            )
                        }
                    }

                    // 明细标题
                    item(key = "list_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedCategory == null) {
                                    "明细"
                                } else {
                                    "明细 · $selectedCategory"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedCategory != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectedCategoryChange(null) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "清除",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        item(key = "list_empty") {
                            MiuiCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "该范围内没有记录",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredItems,
                            key = { item ->
                                when (item) {
                                    is ExpenseListItem.Fuel -> "fuel_${item.record.id}"
                                    is ExpenseListItem.Expense -> "exp_${item.record.id}"
                                }
                            }
                        ) { item ->
                            when (item) {
                                is ExpenseListItem.Fuel -> {
                                    FuelItemCard(
                                        record = item.record,
                                        onEdit = { onEditFuel(item.record) },
                                        onDelete = { deletingFuel = item.record }
                                    )
                                }

                                is ExpenseListItem.Expense -> {
                                    ExpenseItemCard(
                                        record = item.record,
                                        onEdit = { onEditExpense(item.record) },
                                        onDelete = { deletingExpense = item.record }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = viewModel.isBottomBarVisible,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(
                    initialScale = 0.8f
                ),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(
                    targetScale = 0.8f
                ),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                FloatingActionButton(
                    onClick = onAddExpense,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp),
                    containerColor = Color(0xFFFF9500),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = "记费用"
                    )
                }
            }
        }
    }

    if (showYearMonthPicker) {
        ExpenseYearMonthSheet(
            initialYear = customYear,
            initialMonth = customMonth,
            availableYears = availableYears,
            onDismiss = { showYearMonthPicker = false },
            onConfirm = { y, m ->
                customYear = y
                customMonth = m
                selectedRange = ExpenseRange.CUSTOM_MONTH
                showYearMonthPicker = false
            }
        )
    }

    // 删除费用
    deletingExpense?.let { record ->
        DeleteConfirmDialog(
            title = "删除费用记录",
            message = "确定删除这条 ${record.category} ¥%.2f 的记录吗？".format(record.amount),
            onConfirm = {
                viewModel.deleteExpense(record)
                deletingExpense = null
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
                viewModel.deleteRecord(record)
                deletingFuel = null
            },
            onDismiss = { deletingFuel = null }
        )
    }
}

// ============================================================
// 删除确认对话框
// ============================================================

@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "删除",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 范围计算
// ============================================================

private fun computeExpenseRange(
    range: ExpenseRange,
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
        ExpenseRange.ALL -> return null to null
        ExpenseRange.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        ExpenseRange.YEAR -> {
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        ExpenseRange.CUSTOM_MONTH -> {
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
        ExpenseRange.ALL -> return null to null
        ExpenseRange.MONTH -> endCal.add(Calendar.MONTH, 1)
        ExpenseRange.YEAR -> endCal.add(Calendar.YEAR, 1)
        ExpenseRange.CUSTOM_MONTH -> {
            if (customMonth == 0) endCal.add(Calendar.YEAR, 1)
            else endCal.add(Calendar.MONTH, 1)
        }
    }

    return start to (endCal.timeInMillis - 1)
}

// ============================================================
// 汇总
// ============================================================

private data class ExpenseSummary(
    val rangeLabel: String,
    val totalAmount: Double,
    val count: Int,
    val dailyAverage: Double,
    val showDaily: Boolean
)

private fun computeSummary(
    expenses: List<ExpenseRecord>,
    fuelRecords: List<FuelRecord>,
    rangeBounds: Pair<Long?, Long?>,
    rangeLabel: String
): ExpenseSummary {
    val (start, end) = rangeBounds

    val filteredExpenses = expenses.filter { e ->
        (start == null || e.timestamp >= start) &&
                (end == null || e.timestamp <= end)
    }
    val filteredFuel = fuelRecords.filter { f ->
        !f.isInitialRecord &&
                (start == null || f.timestamp >= start) &&
                (end == null || f.timestamp <= end)
    }

    val expenseTotal = filteredExpenses.sumOf { it.amount }
    val fuelTotal = filteredFuel.sumOf { it.actualPaidAmount }
    val total = expenseTotal + fuelTotal

    val count = filteredExpenses.size + filteredFuel.size

    val daysInRange: Long? = when {
        start == null || end == null -> null
        else -> {
            val diffDays = ((end - start) / 86_400_000L) + 1
            diffDays.coerceAtLeast(1)
        }
    }

    val daily = if (daysInRange != null && daysInRange > 0) total / daysInRange else 0.0

    return ExpenseSummary(
        rangeLabel = rangeLabel,
        totalAmount = total,
        count = count,
        dailyAverage = daily,
        showDaily = daysInRange != null
    )
}

@Composable
private fun SummaryCard(
    summary: ExpenseSummary,
    selectedRange: ExpenseRange,
    rangeLabel: String,
    onRangeSelected: (ExpenseRange) -> Unit,
    onCustomClick: () -> Unit
) {
    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpenseRangeChip(
                    text = "全部",
                    selected = selectedRange == ExpenseRange.ALL,
                    onClick = { onRangeSelected(ExpenseRange.ALL) }
                )
                ExpenseRangeChip(
                    text = "本月",
                    selected = selectedRange == ExpenseRange.MONTH,
                    onClick = { onRangeSelected(ExpenseRange.MONTH) }
                )
                ExpenseRangeChip(
                    text = "今年",
                    selected = selectedRange == ExpenseRange.YEAR,
                    onClick = { onRangeSelected(ExpenseRange.YEAR) }
                )
                ExpenseRangeChip(
                    text = if (selectedRange == ExpenseRange.CUSTOM_MONTH) rangeLabel else "选年月",
                    selected = selectedRange == ExpenseRange.CUSTOM_MONTH,
                    onClick = onCustomClick
                )
            }

            Text(
                text = "${summary.rangeLabel}支出",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "¥",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "%.2f".format(summary.totalAmount),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column {
                    Text(
                        text = "笔数",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${summary.count} 笔",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (summary.showDaily) {
                    Column {
                        Text(
                            text = "日均",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "¥%.2f".format(summary.dailyAverage),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRangeChip(
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

// ============================================================
// 月度趋势
// ============================================================

private data class MonthTrendPoint(
    val year: Int,
    val month: Int,
    val value: Double
)

private fun buildMonthTrend(
    expenses: List<ExpenseRecord>,
    fuelRecords: List<FuelRecord>,
): List<MonthTrendPoint> {
    val monthsBack = 12
    val now = Calendar.getInstance()

    val keys = (monthsBack - 1 downTo 0).map { offset ->
        val c = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.MONTH, -offset)
        }
        c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
    }

    val grouped = mutableMapOf<Pair<Int, Int>, Double>()

    fuelRecords
        .filter { !it.isInitialRecord }
        .forEach { r ->
            val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
            val key = c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
            grouped[key] = (grouped[key] ?: 0.0) + r.actualPaidAmount
        }

    expenses.forEach { e ->
        val c = Calendar.getInstance().apply { timeInMillis = e.timestamp }
        val key = c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
        grouped[key] = (grouped[key] ?: 0.0) + e.amount
    }

    return keys.map { (y, m) ->
        MonthTrendPoint(y, m, grouped[y to m] ?: 0.0)
    }
}

@Composable
private fun ExpenseTrendCard(points: List<MonthTrendPoint>) {
    if (points.isEmpty()) return

    val maxValue = points.maxOf { it.value }
    val yMax = if (maxValue > 0) maxValue * 1.15 else 1.0

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val valueLabelStyle = remember {
        TextStyle(
            color = onSurfaceColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
    val timeLabelStyle = remember {
        TextStyle(
            color = onSurfaceVariantColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Normal
        )
    }

    val isSameYear = remember(points) {
        points.map { it.year }.distinct().size <= 1
    }

    val valueLayouts = remember(points, textMeasurer) {
        points.map {
            textMeasurer.measure(
                text = "%.0f".format(it.value),
                style = valueLabelStyle
            )
        }
    }

    val timeLayouts = remember(points, isSameYear, textMeasurer) {
        points.map { p ->
            val label = if (isSameYear) "${p.month}月"
            else "%02d/%02d".format(p.year % 100, p.month)
            textMeasurer.measure(text = label, style = timeLabelStyle)
        }
    }

    val topPaddingDp = remember(valueLayouts, density) {
        val maxH = valueLayouts.maxOfOrNull { it.size.height } ?: 0
        with(density) {
            (maxH.toDp() + 4.dp).coerceAtLeast(16.dp)
        }
    }

    val chartAreaHeight = 94.dp
    val timeAxisHeight = 18.dp
    val canvasHeight = topPaddingDp + chartAreaHeight + timeAxisHeight

    val scrollState = rememberScrollState()
    val visibleCount = 10
    val barCount = points.size

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
                    text = "费用趋势",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(近 12 个月)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
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

                        val barWidth = (slotWidth * 0.5f).coerceIn(4.dp.toPx(), 40.dp.toPx())
                        val minBarHeight = 2.dp.toPx()
                        val timeLabelY = topPadding + chartHeight + 4.dp.toPx()

                        points.forEachIndexed { index, point ->
                            val slotLeft = index * slotWidth
                            val barCenterX = slotLeft + slotWidth / 2f
                            val barLeft = barCenterX - barWidth / 2f

                            val heightFactor = (point.value / yMax).toFloat().coerceIn(0f, 1f)
                            val barHeight = (chartHeight * heightFactor).coerceAtLeast(minBarHeight)
                            val barTop = topPadding + (chartHeight - barHeight)

                            val isCurrentMonth = index == points.size - 1
                            val barColor = if (isCurrentMonth)
                                primaryColor
                            else
                                primaryColor.copy(alpha = 0.55f)

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            val vLayout = valueLayouts[index]
                            val vW = vLayout.size.width.toFloat()
                            val vH = vLayout.size.height.toFloat()
                            val maxX = (width - vW).coerceAtLeast(0f)
                            val vX = (barCenterX - vW / 2f).coerceIn(0f, maxX)
                            val vY = barTop - vH - 2.dp.toPx()
                            drawText(vLayout, topLeft = Offset(vX, vY))

                            val tLayout = timeLayouts[index]
                            val tW = tLayout.size.width.toFloat()
                            val maxTX = (width - tW).coerceAtLeast(0f)
                            val tX = (barCenterX - tW / 2f).coerceIn(0f, maxTX)
                            drawText(tLayout, topLeft = Offset(tX, timeLabelY))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 费用结构
// ============================================================

private data class StructureItem(
    val name: String,
    val amount: Double,
    val ratio: Float
)

private fun buildStructureData(
    expenses: List<ExpenseRecord>,
    fuelRecords: List<FuelRecord>,
    rangeBounds: Pair<Long?, Long?>
): List<StructureItem> {
    val (start, end) = rangeBounds

    val map = mutableMapOf<String, Double>()

    val fuelTotal = fuelRecords
        .filter { f ->
            !f.isInitialRecord &&
                    (start == null || f.timestamp >= start) &&
                    (end == null || f.timestamp <= end)
        }
        .sumOf { it.actualPaidAmount }

    if (fuelTotal > 0) map[CATEGORY_FUEL] = fuelTotal

    expenses
        .filter { e ->
            (start == null || e.timestamp >= start) &&
                    (end == null || e.timestamp <= end)
        }
        .groupBy { it.category }
        .forEach { (cat, list) ->
            map[cat] = list.sumOf { it.amount }
        }

    val total = map.values.sum()

    return map
        .map { (name, amount) ->
            StructureItem(
                name = name,
                amount = amount,
                ratio = if (total > 0) (amount / total).toFloat() else 0f
            )
        }
        .filter { it.amount > 0 }
        .sortedByDescending { it.amount }
}

@Composable
private fun ExpenseStructureCard(
    items: List<StructureItem>,
    selectedCategory: String?,
    onCategoryClick: (String) -> Unit
) {
    if (items.isEmpty()) return

    MiuiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "费用结构",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (selectedCategory == null) "点击筛选" else "已筛选 $selectedCategory",
                    fontSize = 11.sp,
                    color = if (selectedCategory == null)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            items.forEach { item ->
                StructureRow(
                    item = item,
                    selected = item.name == selectedCategory,
                    onClick = { onCategoryClick(item.name) }
                )
            }
        }
    }
}

@Composable
private fun StructureRow(
    item: StructureItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¥%.2f".format(item.amount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "%.0f%%".format(item.ratio * 100),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(item.ratio.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
            )
        }
    }
}

// ============================================================
// 明细列表项：费用
// ============================================================

@Composable
private fun ExpenseItemCard(
    record: ExpenseRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(record.timestamp) {
        expenseDateFormat.format(Date(record.timestamp))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.category,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CompactExpenseTextButton(
                        text = "编辑",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onEdit
                    )
                    CompactExpenseTextButton(
                        text = "删除",
                        color = Color(0xFFFF3B30).copy(alpha = 0.8f),
                        onClick = onDelete
                    )
                }
            }

            Text(
                text = "¥%.2f".format(record.amount),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ============================================================
// 明细列表项：加油
// ============================================================

@Composable
private fun FuelItemCard(
    record: FuelRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(record.timestamp) {
        expenseDateFormat.format(Date(record.timestamp))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "加油",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CompactExpenseTextButton(
                        text = "编辑",
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onEdit
                    )
                    CompactExpenseTextButton(
                        text = "删除",
                        color = Color(0xFFFF3B30).copy(alpha = 0.8f),
                        onClick = onDelete
                    )
                }
            }

            Text(
                text = "¥%.2f".format(record.actualPaidAmount),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CompactExpenseTextButton(
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

// ============================================================
// 年月选择
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseYearMonthSheet(
    initialYear: Int,
    initialMonth: Int,
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }

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
                WheelList(
                    selectedValue = year,
                    items = availableYears,
                    visibleCount = 5,
                    modifier = Modifier.weight(1.4f),
                    style = WheelStyle.WithBackground,
                    itemLabel = { "${it}年" },
                    onChange = { year = it }
                )
                WheelList(
                    selectedValue = month,
                    items = monthValues,
                    visibleCount = 5,
                    modifier = Modifier.weight(1f),
                    style = WheelStyle.WithBackground,
                    itemLabel = { if (it == 0) "全年" else "%02d 月".format(it) },
                    onChange = { month = it }
                )
            }
        }
    }
}