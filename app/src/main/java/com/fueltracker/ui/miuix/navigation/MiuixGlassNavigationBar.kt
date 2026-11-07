package com.fueltracker.ui.miuix.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow as BackdropShadow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiuixGlassNavigationBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    isGlassEnabled: Boolean,
    isFloating: Boolean = true,
    selectedIndex: Int = 0,
    onSelectedIndexChange: (Int) -> Unit,
    itemCount: Int = 3,
    navigationBackdropDetector: NavigationBackdropDetector,
    content: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val isLight =
        MiuixTheme.colorScheme.background.luminance() > 0.5f

    val floatingShadow = if (isLight) {
        ComposeShadow(
            radius = 16.dp,
            color = Color.Black,
            alpha = 0.1f
        )
    } else {
        ComposeShadow(
            radius = 14.dp,
            color = Color.Black,
            alpha = 0.38f
        )
    }

    val shape: Shape =
        if (isFloating) {
            RoundedCornerShape(32.dp)
        } else {
            RoundedCornerShape(0.dp)
        }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFloating) {
                        Modifier
                            .navigationBarsPadding()
                            .padding(
                                horizontal = 24.dp,
                                vertical = 16.dp
                            )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (isFloating) {
                        Modifier.dropShadow(
                            shape = shape,
                            shadow = floatingShadow
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.isAttached) {
                        val marginPx = with(density) {
                            24.dp.toPx()
                        }

                        navigationBackdropDetector.updateNavigation(
                            bounds = coordinates.boundsInWindow(),
                            marginPx = marginPx
                        )
                    }
                }
        ) {
            NavigationBarSurface(
                backdrop = backdrop,
                shape = shape,
                isLight = isLight,
                isGlassEnabled = isGlassEnabled,
                isFloating = isFloating,
                navigationBackdropDetector = navigationBackdropDetector,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = onSelectedIndexChange,
                itemCount = itemCount,
                content = content
            )
        }
    }
}

@Composable
private fun NavigationBarSurface(
    backdrop: Backdrop,
    shape: Shape,
    isLight: Boolean,
    isGlassEnabled: Boolean,
    isFloating: Boolean,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    itemCount: Int,
    navigationBackdropDetector: NavigationBackdropDetector,
    content: @Composable RowScope.() -> Unit
) {
    // 浅色模式也使用主题 surface，而不是纯白，缩小玻璃与实色材质的色差。
    val solidColor = MiuixTheme.colorScheme.surface

    // 悬浮导航栏保持稳定的玻璃材质，不再随内容检测在玻璃与实色之间切换。
    val shouldUseGlass = isGlassEnabled && isFloating

    // 使用无过冲的阻尼弹簧，让玻璃与纯色材质缓慢、连续地互相过渡。
    // 相比固定时长 tween，检测状态在滚动中反复变化时也能平滑改变方向。
    val glassProgress by animateFloatAsState(
        targetValue = if (shouldUseGlass) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 45f
        ),
        label = "navigationGlassTransition"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isFloating) {
                    Modifier.border(
                        width = 0.6.dp,
                        color = Color.White.copy(
                            alpha = if (isLight) 0.34f else 0.16f
                        ),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
    ) {
        val tabCount = itemCount.coerceAtLeast(1)
        val horizontalPadding = 4.dp

        val tabWidth =
            (maxWidth - horizontalPadding * 2) / tabCount

        val pillWidth =
            tabWidth - horizontalPadding * 2

        val animationScope = rememberCoroutineScope()

        val safeSelectedIndex = selectedIndex.coerceIn(0, tabCount - 1)

        val animatedIndex = remember {
            Animatable(safeSelectedIndex.toFloat())
        }

        var isDragging by remember {
            mutableStateOf(false)
        }

        var dragIndex by remember {
            mutableFloatStateOf(safeSelectedIndex.toFloat())
        }

        // 拖动速度产生的横向拉伸量：1.0 为原始尺寸
        var dragStretch by remember {
            mutableFloatStateOf(1f)
        }

        // 拖动时平滑追随速度变化，松手后通过弹簧恢复为 1f
        val pillScaleX by animateFloatAsState(
            targetValue = if (isDragging) dragStretch else 1f,
            animationSpec = if (isDragging) {
                tween(durationMillis = 70)
            } else {
                spring(
                    dampingRatio = 0.46f,
                    stiffness = 520f
                )
            },
            label = "navigationPillScaleX"
        )

        val pillScaleY by animateFloatAsState(
            targetValue = if (isDragging) {
                1f - (dragStretch - 1f) * 0.55f
            } else {
                1f
            },
            animationSpec = if (isDragging) {
                tween(durationMillis = 70)
            } else {
                spring(
                    dampingRatio = 0.46f,
                    stiffness = 520f
                )
            },
            label = "navigationPillScaleY"
        )

        val selectionCallback = rememberUpdatedState(onSelectedIndexChange)

        val latestSelectedIndex = rememberUpdatedState(safeSelectedIndex)

        LaunchedEffect(safeSelectedIndex) {
            if (!isDragging) {
                animatedIndex.animateTo(
                    targetValue = latestSelectedIndex.value.toFloat(),
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 380f
                    )
                )
            }
        }


        // 第一层：始终使用同一张背景表面，通过颜色插值在实色与玻璃态之间连续变化。
        // 不再叠加两张不同颜色的底层，避免交叉淡入淡出时出现灰白色混合区。
        Box(
            Modifier
                .matchParentSize()
                .drawGlassNavigationBackdrop(
                    backdrop = backdrop,
                    shape = shape,
                    isLight = isLight,
                    solidColor = solidColor,
                    glassProgress = glassProgress
                )
        )

        // 第二层：底部淡色叠加
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = glassProgress }
                .drawGlassNavigationOverlay(isLight)
        )

        // 第三层：顶部反光
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = glassProgress }
                .drawGlassNavigationHighlight(isLight)
        )

        // 第四层：弹性选中胶囊
        val currentPillIndex = if (isDragging) {
            dragIndex
        } else {
            animatedIndex.value
        }

        val pillOffset = with(LocalDensity.current) {
            currentPillIndex * tabWidth.toPx() +
                    horizontalPadding.toPx()
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = pillOffset.toInt(),
                        y = MiuixGlassDefaults.Navigation.pillOffsetY.roundToPx()
                    )
                }
                .width(pillWidth)
                .height(MiuixGlassDefaults.Navigation.pillHeight)
                .graphicsLayer {
                    scaleX = pillScaleX
                    scaleY = pillScaleY
                    alpha = glassProgress
                }
                .drawGlassSelectionPill(
                    backdrop = backdrop,
                    isLight = isLight
                )
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = pillOffset.toInt(),
                        y = MiuixGlassDefaults.Navigation.pillOffsetY.roundToPx()
                    )
                }
                .width(pillWidth)
                .height(MiuixGlassDefaults.Navigation.pillHeight)
                .graphicsLayer {
                    scaleX = pillScaleX
                    scaleY = pillScaleY
                    alpha = 1f - glassProgress
                }
                .clip(RoundedCornerShape(MiuixGlassDefaults.Navigation.pillCornerRadius))
                .background(
                    if (isLight) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.09f)
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    }
                )
        )

        // 第五层：导航内容
        val horizontalPaddingPx = with(LocalDensity.current) {
            horizontalPadding.toPx()
        }

        val tabWidthPx = with(LocalDensity.current) {
            tabWidth.toPx()
        }

        val pillWidthPx = with(LocalDensity.current) {
            pillWidth.toPx()
        }

        val pillTopPx = with(LocalDensity.current) {
            4.dp.toPx()
        }

        val pillHeightPx = with(LocalDensity.current) {
            56.dp.toPx()
        }

        val density = LocalDensity.current.density

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp)
                .pointerInput(
                    tabCount,
                    tabWidthPx,
                    pillWidthPx,
                    pillTopPx,
                    pillHeightPx,
                    density
                ) {
                    var draggingPill = false
                    var lastDragTimeMillis = 0L
                    var smoothedSpeedDpPerSecond = 0f
                    // 当前拖动过程中，已经通知页面切换到哪个导航项。
                    var lastNotifiedIndex = latestSelectedIndex.value

                    detectHorizontalDragGestures(
                        onDragStart = { position ->

                            // 从手指按下的位置计算起始导航项
                            val startIndex = (
                                    (position.x - horizontalPaddingPx) / tabWidthPx
                                    ).toInt().coerceIn(0, tabCount - 1)

                            // 仅允许在导航栏内容区域内开始拖动
                            draggingPill =
                                position.y in pillTopPx..(pillTopPx + pillHeightPx)

                            if (draggingPill) {
                                lastDragTimeMillis = 0L
                                smoothedSpeedDpPerSecond = 0f

                                // 以手指按下的导航项作为起点
                                lastNotifiedIndex = startIndex
                                dragIndex = startIndex.toFloat()

                                dragStretch = 1f
                                isDragging = true

                                // 如果按下的不是当前页面，立即切换
                                if (startIndex != latestSelectedIndex.value) {
                                    selectionCallback.value(startIndex)
                                }
                            }
                        },

                        onHorizontalDrag = { change, dragAmount ->

                            if (draggingPill) {
                                change.consume()

                                // 根据相邻手势事件估算当前速度，并做平滑，避免形变抖动
                                val currentTime = change.uptimeMillis
                                if (lastDragTimeMillis != 0L) {
                                    val deltaTime = (
                                            currentTime - lastDragTimeMillis
                                            ).coerceAtLeast(1L)

                                    val currentSpeedDpPerSecond =
                                        abs(dragAmount) * 1000f /
                                                deltaTime.toFloat() / density

                                    smoothedSpeedDpPerSecond =
                                        smoothedSpeedDpPerSecond * 0.55f +
                                                currentSpeedDpPerSecond * 0.45f

                                    // 快速拖动时横向拉伸，最高 14%
                                    dragStretch = (
                                            1f + smoothedSpeedDpPerSecond / 3_000f
                                            ).coerceIn(1f, 1.14f)
                                }
                                lastDragTimeMillis = currentTime

                                dragIndex = (
                                        dragIndex + dragAmount / tabWidthPx.coerceAtLeast(1f)
                                        ).coerceIn(
                                        0f,
                                        (tabCount - 1).toFloat()
                                    )

                                // 越过相邻导航项的中点时，立即切换页面。
                                // roundToInt() 以每个导航项的中心作为切换阈值，
                                // 拖回上一项时也会立即切回。
                                val crossedIndex = dragIndex
                                    .roundToInt()
                                    .coerceIn(0, tabCount - 1)

                                if (crossedIndex != lastNotifiedIndex) {
                                    lastNotifiedIndex = crossedIndex
                                    selectionCallback.value(crossedIndex)
                                }
                            }
                        },

                        onDragEnd = {

                            if (draggingPill) {

                                val releasePosition = dragIndex

                                val targetIndex = releasePosition
                                    .roundToInt()
                                    .coerceIn(0, tabCount - 1)

                                // 防止最后一次拖动事件与松手位置之间存在细小差异，
                                // 确保最终吸附项与页面选中项一致。
                                if (targetIndex != lastNotifiedIndex) {
                                    lastNotifiedIndex = targetIndex
                                    selectionCallback.value(targetIndex)
                                }

                                animationScope.launch {
                                    // 从手指松开的位置继续动画
                                    animatedIndex.snapTo(releasePosition)

                                    isDragging = false

                                    // 吸附到最近的导航项
                                    animatedIndex.animateTo(
                                        targetValue = targetIndex.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.72f,
                                            stiffness = 380f
                                        )
                                    )
                                }
                            }

                            draggingPill = false
                        },

                        onDragCancel = {

                            if (draggingPill) {

                                val releasePosition = dragIndex

                                animationScope.launch {

                                    animatedIndex.snapTo(releasePosition)

                                    isDragging = false

                                    // 取消拖动时回到原来的导航项
                                    animatedIndex.animateTo(
                                        targetValue = latestSelectedIndex.value.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.72f,
                                            stiffness = 380f
                                        )
                                    )
                                }
                            }

                            draggingPill = false
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun RowScope.MiuixGlassNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val activeColor =
        if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceSecondary
        }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = activeColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
            color = activeColor
        )
    }
}

private object MiuixGlassDefaults {

    object Navigation {

        // ---------- 底部渐变 ----------
        val lightBottomOverlay = Color.Transparent
        val darkBottomOverlay = Color.Black.copy(alpha = 0.06f)

        // ---------- 顶部反光 ----------
        const val LIGHTHIGHLIGHTALPHA = 0.16f
        const val HIGHLIGHTMIDDLEPOSITION = 0.12f
        const val HIGHLIGHTENDPOSITION = 0.30f
        const val HIGHLIGHTGRADIENTEND = 0.38f
        const val HIGHLIGHTLINEALPHA = 0.12f
        const val HIGHLIGHTLINEMIDDLEALPHA = 0.06f
        val highlightLineHeight = 1.5.dp
        val highlightLineWidth = 2.dp

        val pillCornerRadius = 28.dp
        val pillHeight = 56.dp
        val pillOffsetY = 4.dp
    }
}

// ==================== 原 GlassEffects.kt 内容（内联） ====================

/**
 * 导航栏玻璃背景
 */

private fun Modifier.drawGlassNavigationBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    isLight: Boolean,
    solidColor: Color,
    glassProgress: Float
): Modifier {
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },

        effects = {
            // 保持背景色彩活力
            vibrancy()

            // 模糊与折射跟随同一个进度渐进增强/减弱。
            blur(1.5.dp.toPx() * glassProgress)

            lens(
                refractionHeight = 18.dp.toPx() * glassProgress,
                refractionAmount = 13.dp.toPx() * glassProgress,
                depthEffect = false,
                chromaticAberration = false
            )
        },

        // 边缘统一交给外层柔和阴影，不再绘制亮色描边。
        highlight = null,

        // 外部已经有 Modifier.shadow，避免重复叠加
        shadow = null,

        // 只绘制一张表面：从主题实色连续插值到半透明玻璃色。
        // 避免实色底层与玻璃底层交叉叠加造成颜色发灰、发白。
        onDrawSurface = {
            val glassTint = if (isLight) {
                Color.White.copy(alpha = 0.15f)
            } else {
                Color(0xFF202028).copy(alpha = 0.07f)
            }
            drawRect(lerp(solidColor, glassTint, glassProgress.coerceIn(0f, 1f)))
        }
    )
}


/**
 * 导航栏底部渐变叠加
 */
private fun Modifier.drawGlassNavigationOverlay(
    isLight: Boolean
): Modifier {
    return drawBehind {

        val overlayColor = if (isLight) {
            MiuixGlassDefaults.Navigation.lightBottomOverlay
        } else {
            MiuixGlassDefaults.Navigation.darkBottomOverlay
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    overlayColor
                ),
                startY = size.height * 0.55f,
                endY = size.height
            )
        )
    }
}

/**
 * 导航栏顶部反光
 */
private fun Modifier.drawGlassNavigationHighlight(
    isLight: Boolean
): Modifier {
    return drawBehind {
        // 暗色玻璃只保留轮廓阴影，避免白色高光形成亮带。
        if (!isLight) return@drawBehind

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(
                        alpha = MiuixGlassDefaults.Navigation.LIGHTHIGHLIGHTALPHA
                    ),

                    MiuixGlassDefaults.Navigation.HIGHLIGHTMIDDLEPOSITION to
                            Color.White.copy(
                                alpha = MiuixGlassDefaults.Navigation.LIGHTHIGHLIGHTALPHA * 0.35f
                            ),

                    MiuixGlassDefaults.Navigation.HIGHLIGHTENDPOSITION to
                            Color.Transparent
                ),
                startY = 0f,
                endY = size.height *
                        MiuixGlassDefaults.Navigation.HIGHLIGHTGRADIENTEND
            )
        )

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(
                        alpha = MiuixGlassDefaults.Navigation.HIGHLIGHTLINEMIDDLEALPHA
                    ),
                    Color.White.copy(
                        alpha = MiuixGlassDefaults.Navigation.HIGHLIGHTLINEALPHA
                    ),
                    Color.White.copy(
                        alpha = MiuixGlassDefaults.Navigation.HIGHLIGHTLINEMIDDLEALPHA
                    ),
                    Color.Transparent
                )
            ),
            start = Offset(
                size.width * 0.06f,
                MiuixGlassDefaults.Navigation.highlightLineHeight.toPx()
            ),
            end = Offset(
                size.width * 0.94f,
                MiuixGlassDefaults.Navigation.highlightLineHeight.toPx()
            ),
            strokeWidth = MiuixGlassDefaults.Navigation.highlightLineWidth.toPx()
        )
    }
}

/**
 * 选中导航项的液态玻璃胶囊
 */

private fun Modifier.drawGlassSelectionPill(
    backdrop: Backdrop,
    isLight: Boolean
): Modifier {
    val pillShape = RoundedCornerShape(
        MiuixGlassDefaults.Navigation.pillCornerRadius
    )

    return drawBackdrop(
        backdrop = backdrop,

        shape = { pillShape },

        effects = {
            // 以磨砂为主，弱化折射，避免选中项内部的背景文字仍然过于清晰。
            blur(3.2.dp.toPx())

            lens(
                refractionHeight = 6.dp.toPx(),
                refractionAmount = 5.dp.toPx(),
                depthEffect = false,
                chromaticAberration = false
            )
        },

        highlight = null,

        shadow = {
            BackdropShadow.Default.copy(alpha = if (isLight) 0.20f else 0.24f)
        },

        onDrawSurface = {
            // 底栏本身接近白色，单纯叠加白色只会让胶囊与底栏融在一起。
            // 改用低饱和灰蓝雾面，让选中区域形成明确但不突兀的明度差。
            val frostColor = if (isLight) {
                Color(0xFFDDE3EC).copy(alpha = 0.66f)
            } else {
                Color(0xFF626A78).copy(alpha = 0.38f)
            }
            drawRect(frostColor)
        }
    )
}
