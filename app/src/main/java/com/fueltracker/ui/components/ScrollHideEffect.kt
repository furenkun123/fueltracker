package com.fueltracker.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fueltracker.viewmodel.MainViewModel

@Composable
fun Modifier.scrollToHideBottomBar(
    listState: LazyListState,
    viewModel: MainViewModel
): Modifier {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 150.dp.toPx() }

    // 1. 监听滑动停止状态：如果停在最顶部或最底部，强制显示底栏
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.collect { (isScrolling, index, offset) ->
            if (!isScrolling) {
                val isAtTop = index == 0 && offset == 0
                val isAtBottom = !listState.canScrollForward

                if (isAtTop || isAtBottom) {
                    viewModel.updateBottomBarVisibility(true)
                }
            }
        }
    }

    // 2. 拦截滚动距离：处理中间的滑动逻辑
    val connection = remember {
        object : NestedScrollConnection {
            var accumulatedUpScroll = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val deltaY = consumed.y

                if (deltaY < 0) {
                    // 向上推（看新内容） -> 立刻隐藏，清零累积
                    if (viewModel.isBottomBarVisible) {
                        viewModel.updateBottomBarVisibility(false)
                    }
                    accumulatedUpScroll = 0f
                } else if (deltaY > 0) {
                    // 向下拉（看旧内容） -> 只有手指主动拖动才累加距离
                    if (source == NestedScrollSource.UserInput) {
                        accumulatedUpScroll += deltaY
                        // 往回拉了 150dp 以上，显示底栏
                        if (accumulatedUpScroll > thresholdPx && !viewModel.isBottomBarVisible) {
                            viewModel.updateBottomBarVisibility(true)
                            accumulatedUpScroll = 0f
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    return this.nestedScroll(connection)
}