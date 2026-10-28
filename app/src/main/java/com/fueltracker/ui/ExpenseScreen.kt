package com.fueltracker.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.ui.components.ExpenseDetailItemCard
import com.fueltracker.ui.components.ExpenseStructureCard
import com.fueltracker.ui.components.ExpenseTrendCard
import com.fueltracker.ui.components.MiuiConfirmDialog
import com.fueltracker.ui.components.MiuiYearMonthSheet
import com.fueltracker.ui.components.SummaryCard
import com.fueltracker.ui.components.scrollToHideFab
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiTopBar
import com.fueltracker.util.ExpenseRange
import com.fueltracker.util.buildMonthTrend
import com.fueltracker.util.buildStructureData
import com.fueltracker.util.computeExpenseRange
import com.fueltracker.util.computeSummary
import com.fueltracker.util.getYear
import com.fueltracker.util.inRange
import com.fueltracker.util.toDateTimeText
import com.fueltracker.util.toMoneyText
import com.fueltracker.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Calendar

private const val CATEGORY_FUEL = "加油"

private sealed class ExpenseListItem {
    abstract val id: Long
    abstract val timestamp: Long
    abstract val title: String
    abstract val amount: Double

    data class Fuel(val record: FuelRecord) : ExpenseListItem() {
        override val id = record.id
        override val timestamp = record.timestamp
        override val title = "加油"
        override val amount = record.actualPaidAmount
    }

    data class Expense(val record: ExpenseRecord) : ExpenseListItem() {
        override val id = record.id
        override val timestamp = record.timestamp
        override val title = record.category
        override val amount = record.amount
    }
}

@Composable
fun ExpenseScreen(
    viewModel: MainViewModel,
    selectedCategory: String?,
    onSelectedCategoryChange: (String?) -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (ExpenseRecord) -> Unit,
    onEditFuel: (FuelRecord) -> Unit,
    hazeState: HazeState? = null,
    isGlassEnabled: Boolean = false
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val fuelRecords by viewModel.records.collectAsState()

    // 合并为一个删除状态
    var deletingItem by remember { mutableStateOf<ExpenseListItem?>(null) }
    var selectedRange by remember { mutableStateOf(ExpenseRange.ALL) }
    var customYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var customMonth by remember { mutableIntStateOf(0) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    val availableYears = remember(expenses, fuelRecords) {
        val currentYear = System.currentTimeMillis().getYear()
        (expenses.map { it.timestamp.getYear() } + fuelRecords.map { it.timestamp.getYear() } + currentYear)
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

    var selectedTrendYear by remember { mutableStateOf<Int?>(null) }

    val trendYears = remember(expenses, fuelRecords) {
        (
                expenses.map { it.timestamp.getYear() } +
                        fuelRecords.filter { !it.isInitialRecord }.map { it.timestamp.getYear() }
                ).distinct().sortedDescending()
    }

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
    val isFloating = viewModel.isFloatingBottomBar

    // 👈 替换为纯净的 Box 结构
    Box(modifier = Modifier.fillMaxSize()) {
        if (isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有费用记录\n点右下角 ＋ 添加", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
                    .scrollToHideFab(listState, viewModel),
                contentPadding = PaddingValues(top = 96.dp, bottom = if (isFloating) 120.dp else 80.dp), // 👈 顶部留白
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item("summary") {
                    SummaryCard(summary, selectedRange, rangeLabel, { selectedRange = it }, { showYearMonthPicker = true })
                }
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
                    items(
                        items = filteredItems,
                        key = { item ->
                            when (item) {
                                is ExpenseListItem.Fuel -> "fuel_${item.id}"
                                is ExpenseListItem.Expense -> "expense_${item.id}"
                            }
                        }
                    ) { item ->
                        ExpenseDetailItemCard(
                            title = item.title,
                            time = item.timestamp.toDateTimeText(),
                            amount = item.amount,
                            onEdit = {
                                if (item is ExpenseListItem.Fuel) onEditFuel(item.record)
                                else onEditExpense((item as ExpenseListItem.Expense).record)
                            },
                            onDelete = { deletingItem = item }
                        )
                    }
                }
            }
        }

        // 👈 悬浮顶栏放在 Box 内部
        MiuiTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = "费用",
            isGlassEnabled = isGlassEnabled
        )

        // 👈 悬浮按钮
        AnimatedVisibility(
            visible = viewModel.isFabVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (isFloating) 104.dp else 52.dp)
        ) {
            FloatingActionButton(
                onClick = onAddExpense, modifier = Modifier.padding(end = 16.dp, bottom = 24.dp),
                containerColor = Color(0xFFFF9500), contentColor = Color.White, shape = CircleShape
            ) { Icon(Icons.Default.Receipt, "记费用") }
        }
    }

    if (showYearMonthPicker) {
        MiuiYearMonthSheet(customYear, customMonth, availableYears, { showYearMonthPicker = false }) { y, m ->
            customYear = y; customMonth = m; selectedRange = ExpenseRange.CUSTOM_MONTH; showYearMonthPicker = false
        }
    }

    // 高度合并的删除弹窗
    deletingItem?.let { item ->
        val isFuel = item is ExpenseListItem.Fuel
        val title = if (isFuel) "删除加油记录" else "删除费用记录"
        val message = if (isFuel) {
            "确定删除这条 ${item.amount.toMoneyText()} 的加油记录吗？\n\n删除后相关油耗计算会重新推导。"
        } else {
            "确定删除这条 ${item.title} ${item.amount.toMoneyText()} 的记录吗？"
        }

        MiuiConfirmDialog(
            title = title,
            message = message,
            confirmText = "删除",
            onConfirm = {
                val callback: (Result<*>) -> Unit = { result ->
                    if (result.isSuccess) {
                        deletingItem = null
                    } else {
                        Toast.makeText(context, "删除失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                if (item is ExpenseListItem.Fuel) {
                    viewModel.deleteRecord(item.record, callback)
                } else {
                    viewModel.deleteExpense((item as ExpenseListItem.Expense).record, callback)
                }
            },
            onDismiss = { deletingItem = null }
        )
    }
}