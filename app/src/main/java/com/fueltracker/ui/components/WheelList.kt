package com.fueltracker.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 滚轮选择样式。
 */
enum class WheelStyle {
    /** 无背景，选中项用主色文字（FuelScreen 风格） */
    Plain,

    /** 灰色高亮条（HomeScreen / ExpenseScreen 风格） */
    WithBackground,
    DatePicker
}

/**
 * 通用滚轮选择器。
 *
 * 关键行为：滚动中**实时**回调中心项，不等到完全停止。
 * 这样用户松手后立刻点"确定"，也能拿到正确的选中值。
 */
@SuppressLint("FrequentlyChangingValue")
@Composable
fun <T> WheelList(
    selectedValue: T,
    items: List<T>,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    style: WheelStyle = WheelStyle.Plain,
    itemLabel: (T) -> String = { it.toString() },
    onChange: (T) -> Unit
) {
    if (items.isEmpty()) return

    val itemHeight = 38.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    val initialIndex = remember(items, selectedValue) {
        items.indexOf(selectedValue).coerceAtLeast(0)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 实时监听"中心项索引"，每次变化都回调 onChange。
    // distinctUntilChanged 过滤掉相同值，实际只在跨越中心线时触发。
    LaunchedEffect(listState, items) {
        snapshotFlow {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (offset > itemHeightPx / 2f) firstVisible + 1 else firstVisible
        }
            .distinctUntilChanged()
            .collect { centerIndex ->
                if (centerIndex in items.indices) {
                    val item = items[centerIndex]
                    if (item != selectedValue) {
                        onChange(item)
                    }
                }
            }
    }

    // 外部值变化 → 同步滚轮位置。
    // 加 !isScrollInProgress 判断：滚动中不强制拽回，否则用户滚不动。
    LaunchedEffect(selectedValue, items) {
        val targetIndex = items.indexOf(selectedValue).coerceAtLeast(0)
        if (items.isNotEmpty() &&
            targetIndex in items.indices &&
            listState.firstVisibleItemIndex != targetIndex &&
            !listState.isScrollInProgress
        ) {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 高亮条（可选）
        if (style != WheelStyle.Plain) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(
                        color = if (style == WheelStyle.DatePicker)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }

        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(
                vertical = itemHeight * (visibleCount / 2)
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(items.size) { index ->
                val centerIndex = listState.firstVisibleItemIndex +
                        if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2f) 1 else 0
                val isSelected = index == centerIndex
                val item = items[index]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        fontSize = if (isSelected) 15.sp else 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected && style != WheelStyle.Plain ->
                                MaterialTheme.colorScheme.onSurface
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}