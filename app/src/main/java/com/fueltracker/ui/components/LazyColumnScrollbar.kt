
package com.fueltracker.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val EstimatedItemHeight = 160.dp

@Composable
fun LazyColumnScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    width: Dp = 6.dp,
    minHeight: Dp = 40.dp
) {
    val density = LocalDensity.current

    val minHeightPx = with(density) {
        minHeight.toPx()
    }

    val estimatedItemHeightPx = with(density) {
        EstimatedItemHeight.toPx()
    }

    // 固定高度估算，不随可见卡片变化
    val info by remember(
        listState,
        minHeightPx,
        estimatedItemHeightPx
    ) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val totalCount = layout.totalItemsCount

            if (totalCount <= 1) {
                return@derivedStateOf null
            }

            val viewportHeight = (
                    layout.viewportEndOffset -
                            layout.viewportStartOffset
                    ).toFloat()

            if (viewportHeight <= 0f) {
                return@derivedStateOf null
            }

            val totalHeight =
                totalCount * estimatedItemHeightPx

            val scrollableHeight = (
                    totalHeight - viewportHeight
                    ).coerceAtLeast(0f)

            if (scrollableHeight <= 0f) {
                return@derivedStateOf null
            }

            val scrollOffset = (
                    listState.firstVisibleItemIndex *
                            estimatedItemHeightPx +
                            listState.firstVisibleItemScrollOffset
                    ).coerceIn(0f, scrollableHeight)

            val fraction = (
                    scrollOffset / scrollableHeight
                    ).coerceIn(0f, 1f)

            val thumbHeight = (
                    viewportHeight * viewportHeight / totalHeight
                    ).coerceIn(
                    minHeightPx.coerceAtMost(viewportHeight),
                    viewportHeight
                )

            val maxOffset = (
                    viewportHeight - thumbHeight
                    ).coerceAtLeast(0f)

            ScrollbarInfo(
                thumbHeight = thumbHeight,
                offsetY = maxOffset * fraction,
                maxOffset = maxOffset,
                scrollableHeight = scrollableHeight
            )
        }
    }

    // 让手势回调始终读取最新布局信息
    val currentInfo by rememberUpdatedState(info)

    val isScrolling by remember(listState) {
        derivedStateOf {
            listState.isScrollInProgress
        }
    }

    var isDragging by remember {
        mutableStateOf(false)
    }

    var isScrollbarVisible by remember {
        mutableStateOf(false)
    }

    // 显示与隐藏
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            isScrollbarVisible = true
        } else {
            delay(1.5.seconds)
            isScrollbarVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isScrollbarVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "scrollbar_alpha"
    )

    val displayWidth by animateDpAsState(
        targetValue = if (isDragging) 10.dp else width,
        animationSpec = tween(150),
        label = "scrollbar_width"
    )

    val scrollbarInfo = info ?: return

    if (alpha <= 0f && !isDragging) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .pointerInput(listState) {
                // 本次拖动固定使用开始时的比例
                var dragScale = 0f

                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true

                        val latestInfo = currentInfo

                        dragScale = if (
                            latestInfo != null &&
                            latestInfo.maxOffset > 0f
                        ) {
                            latestInfo.scrollableHeight /
                                    latestInfo.maxOffset
                        } else {
                            0f
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()

                        if (dragScale == 0f) {
                            return@detectVerticalDragGestures
                        }

                        // 直接同步传递位移，不创建协程
                        listState.dispatchRawDelta(
                            dragAmount * dragScale
                        )
                    },
                    onDragEnd = {
                        isDragging = false
                        dragScale = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragScale = 0f
                    }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Canvas(
            modifier = Modifier
                .width(displayWidth)
                .fillMaxHeight()
        ) {
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(
                    x = 0f,
                    y = scrollbarInfo.offsetY
                ),
                size = Size(
                    width = size.width,
                    height = scrollbarInfo.thumbHeight
                ),
                cornerRadius = CornerRadius(size.width / 2)
            )
        }
    }
}

private data class ScrollbarInfo(
    val thumbHeight: Float,
    val offsetY: Float,
    val maxOffset: Float,
    val scrollableHeight: Float
)
