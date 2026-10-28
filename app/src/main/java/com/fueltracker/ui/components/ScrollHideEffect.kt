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
fun Modifier.scrollToHideFab(
    listState: LazyListState,
    viewModel: MainViewModel
): Modifier {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 150.dp.toPx() }

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
                    viewModel.updateFabVisibility(true)
                }
            }
        }
    }

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
                    // 向上滑动隐藏加号
                    if (viewModel.isFabVisible) {
                        viewModel.updateFabVisibility(false)
                    }
                    accumulatedUpScroll = 0f
                } else if (deltaY > 0) {
                    // 向下滑动超过 150dp 显示加号
                    if (source == NestedScrollSource.UserInput) {
                        accumulatedUpScroll += deltaY
                        if (accumulatedUpScroll > thresholdPx && !viewModel.isFabVisible) {
                            viewModel.updateFabVisibility(true)
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