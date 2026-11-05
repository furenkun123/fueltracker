package com.fueltracker.ui.miui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.miui.glass.GlassDefaults
import com.fueltracker.ui.miui.glass.drawGlassNavigationBackdrop
import com.fueltracker.ui.miui.glass.drawGlassNavigationHighlight
import com.fueltracker.ui.miui.glass.drawGlassNavigationOverlay
import com.fueltracker.ui.miui.glass.drawGlassSelectionPill
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MiuiNavigationDestination(
    val icon: ImageVector,
    val label: String
)

@Composable
fun MiuiNavigationBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    items: List<MiuiNavigationDestination>,
    isGlassEnabled: Boolean,
    isFloating: Boolean = true,
    selectedIndex: Int = 0,
    onTabSelected: (Int) -> Unit
) {
    if (items.isEmpty()) return

    val isLight =
        MaterialTheme.colorScheme.background.luminance() > 0.5f

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
                .shadow(
                    elevation = if (isFloating) {
                        if (isLight) 28.dp else 18.dp
                    } else {
                        0.dp
                    },
                    shape = shape,
                    ambientColor = Color.Black.copy(
                        alpha = if (isLight) 0.20f else 0.24f
                    ),
                    spotColor = Color.Black.copy(
                        alpha = if (isLight) 0.28f else 0.32f
                    )
                )
                .clip(shape)
        ) {
            NavigationBarSurface(
                backdrop = backdrop,
                shape = shape,
                isLight = isLight,
                isGlassEnabled = isGlassEnabled,
                isFloating = isFloating,
                items = items,
                selectedIndex = selectedIndex,
                onTabSelected = onTabSelected
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
    items: List<MiuiNavigationDestination>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val solidColor = if (isLight) {
        Color(0xFFF8F8FA)
    } else {
        Color(0xFF1C1C1E)
    }

    val glassEnabled = isGlassEnabled && isFloating

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (glassEnabled) {
                    Color.Transparent
                } else {
                    solidColor
                }
            )
            .then(
                if (isFloating) {
                    Modifier.border(
                        width = 0.8.dp,
                        color = if (isLight) {
                            Color(0xFFB8C5D5).copy(alpha = 0.56f)
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        },
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
    ) {
        val tabCount = items.size
        val trackHeight = 64.dp
        val horizontalPadding = 4.dp
        val density = LocalDensity.current
        val tabWidth = (maxWidth - horizontalPadding * 2) / tabCount
        val basePillWidth = tabWidth - horizontalPadding * 2
        val safeSelectedIndex = selectedIndex.coerceIn(0, tabCount - 1)
        val accentColor = MaterialTheme.colorScheme.primary

        val trackStartPx = with(density) { horizontalPadding.toPx() }
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val basePillWidthPx = with(density) { basePillWidth.toPx() }
        val pillTopPx =
            with(density) { GlassDefaults.Navigation.pillOffsetY.toPx() }
        val pillHeightPx =
            with(density) { GlassDefaults.Navigation.pillHeight.toPx() }
        val pillCornerPx =
            with(density) { GlassDefaults.Navigation.pillCornerRadius.toPx() }
        val barCenterYPx = with(density) { (trackHeight / 2).toPx() }
        val barWidthPx = with(density) { maxWidth.toPx() }

        val animatedIndex = remember {
            Animatable(safeSelectedIndex.toFloat())
        }
        var dragIndex by remember {
            mutableFloatStateOf(safeSelectedIndex.toFloat())
        }
        var isPressed by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var dragDirection by remember { mutableFloatStateOf(0f) }
        val interactionEnergy by animateFloatAsState(
            targetValue = if (isPressed || isDragging) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = 520f
            ),
            label = "navigationInteraction"
        )
        val dragTilt by animateFloatAsState(
            targetValue = if (isDragging) dragDirection else 0f,
            animationSpec = spring(
                dampingRatio = 0.84f,
                stiffness = 420f
            ),
            label = "navigationDragTilt"
        )
        val scope = rememberCoroutineScope()
        val latestOnTabSelected by rememberUpdatedState(onTabSelected)
        val latestSelectedIndex by rememberUpdatedState(safeSelectedIndex)

        LaunchedEffect(safeSelectedIndex, tabCount) {
            animatedIndex.animateTo(
                targetValue = safeSelectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 380f
                )
            )
        }

        // 第一层：玻璃背景
        if (glassEnabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawGlassNavigationBackdrop(
                        backdrop = backdrop,
                        shape = shape,
                        isLight = isLight
                    )
            )
        }

        // 第二层：底部淡色叠加
        if (glassEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawGlassNavigationOverlay(isLight)
            )
        }

        // 第三层：顶部反光
        if (glassEnabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawGlassNavigationHighlight(isLight)
            )
        }

        val renderedIndex =
            if (isDragging) dragIndex else animatedIndex.value
        val pillExpansionPx =
            with(density) { 3.dp.toPx() } * interactionEnergy
        val lensWidthPx = basePillWidthPx + pillExpansionPx * 2f
        val lensHeightPx =
            (pillHeightPx -
                    with(density) { 2.dp.toPx() } * interactionEnergy)
                .coerceAtLeast(pillHeightPx * 0.88f)
        val lensCenterX =
            trackStartPx + renderedIndex * tabWidthPx + tabWidthPx / 2f
        val lensLeftPx = lensCenterX - lensWidthPx / 2f
        val lensTopPx =
            pillTopPx + (pillHeightPx - lensHeightPx) / 2f
        val lensRightPx = lensLeftPx + lensWidthPx
        val lensBottomPx = lensTopPx + lensHeightPx
        val lensShape = NavigationLensShape(
            left = lensLeftPx,
            top = lensTopPx,
            right = lensRightPx,
            bottom = lensBottomPx,
            cornerRadius = (lensHeightPx / 2f)
                .coerceAtMost(pillCornerPx)
        )

        // 胶囊背后的柔光只在按压或拖动时增强，不创建额外 Backdrop 采样。
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val glowRadius =
                lensWidthPx * (0.72f + interactionEnergy * 0.18f)
            val restingAlpha = if (isLight) 0.055f else 0.09f
            val activeAlpha = if (isLight) 0.13f else 0.18f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(
                            alpha = restingAlpha +
                                    (activeAlpha - restingAlpha) *
                                    interactionEnergy
                        ),
                        Color.Transparent
                    ),
                    center = Offset(lensCenterX, barCenterYPx),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(lensCenterX, barCenterYPx)
            )
        }

        // 第四层：弹性选中胶囊
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (lensCenterX - lensWidthPx / 2f).roundToInt(),
                        y = lensTopPx.roundToInt()
                    )
                }
                .width(with(density) { lensWidthPx.toDp() })
                .height(with(density) { lensHeightPx.toDp() })
                .graphicsLayer {
                    scaleX = 0.985f + interactionEnergy * 0.045f
                    scaleY = 1f - interactionEnergy * 0.018f
                    rotationZ = dragTilt * interactionEnergy * 0.24f
                }
                .then(
                    if (glassEnabled) {
                        Modifier.drawGlassSelectionPill(
                            isLight = isLight,
                            accentColor = accentColor,
                            interactionProgress = interactionEnergy
                        )
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(pillCornerPx))
                            .background(
                                if (isLight) {
                                    Color(0xFF1677FF).copy(alpha = 0.09f)
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                }
                            )
                    }
                )
        )

        // 第五层：未选中内容，始终完整铺设。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            NavigationContentRow(
                items = items,
                selectedIndex = safeSelectedIndex,
                onTabSelected = latestOnTabSelected,
                selectedStyle = false
            )
        }

        // 第六层：magic-lens 内容层。只显示在移动胶囊内部，并围绕胶囊中心
        // 轻微放大，形成选中内容透过玻璃的视觉效果。
        val lensTransformOrigin = if (barWidthPx > 0f) {
            (lensCenterX / barWidthPx).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(lensShape)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(
                        pivotFractionX = lensTransformOrigin,
                        pivotFractionY = 0.5f
                    )
                    scaleX = 1f + interactionEnergy * 0.055f
                    scaleY = 1f + interactionEnergy * 0.025f
                }
                .clearAndSetSemantics { }
        ) {
            NavigationContentRow(
                items = items,
                selectedIndex = safeSelectedIndex,
                onTabSelected = latestOnTabSelected,
                selectedStyle = true
            )
        }

        // 手势层覆盖整条导航栏，点击用于离散选择，拖动用于连续跟随与
        // 松手吸附。胶囊本身不注册点击，避免多个手势竞争。
        val visualIndexForX: (Float) -> Float = { x ->
            applyNavigationRubberBand(
                rawIndex = (x - trackStartPx) / tabWidthPx - 0.5f,
                lastIndex = (tabCount - 1).toFloat()
            )
        }
        val discreteIndexForX: (Float) -> Int = { x ->
            ((x - trackStartPx) / tabWidthPx)
                .toInt()
                .coerceIn(0, tabCount - 1)
        }
        val releaseFromDrag: (Int) -> Unit = { target ->
            val currentDragIndex = dragIndex
            scope.launch {
                animatedIndex.snapTo(currentDragIndex)
                isPressed = false
                isDragging = false
                if (target == latestSelectedIndex) {
                    animatedIndex.animateTo(
                        targetValue = target.toFloat(),
                        animationSpec = spring(
                            dampingRatio = 0.70f,
                            stiffness = 420f
                        )
                    )
                } else {
                    latestOnTabSelected(target)
                }
            }
        }

        val velocityTracker = remember { VelocityTracker() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .pointerInput(tabCount, tabWidthPx) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            if (!isDragging) {
                                isPressed = false
                            }
                        },
                        onTap = { position ->
                            latestOnTabSelected(
                                discreteIndexForX(position.x)
                            )
                        }
                    )
                }
                .pointerInput(tabCount, tabWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { position ->
                            velocityTracker.resetTracking()
                            dragIndex = visualIndexForX(position.x)
                            dragDirection = 0f
                            isPressed = true
                            isDragging = true
                        },
                        onDragEnd = {
                            val nearestIndex = dragIndex
                                .roundToInt()
                                .coerceIn(0, tabCount - 1)
                            val velocityX =
                                velocityTracker.calculateVelocity().x
                            val strongFling =
                                kotlin.math.abs(velocityX) >
                                        tabWidthPx * 0.65f
                            val target = when {
                                strongFling && velocityX > 0f ->
                                    (nearestIndex + 1)
                                        .coerceAtMost(tabCount - 1)
                                strongFling && velocityX < 0f ->
                                    (nearestIndex - 1)
                                        .coerceAtLeast(0)
                                else -> nearestIndex
                            }
                            releaseFromDrag(target)
                        },
                        onDragCancel = {
                            val target = dragIndex
                                .roundToInt()
                                .coerceIn(0, tabCount - 1)
                            releaseFromDrag(target)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragIndex = visualIndexForX(change.position.x)
                            dragDirection =
                                dragAmount.coerceIn(-1f, 1f)
                            velocityTracker.addPosition(
                                change.uptimeMillis,
                                change.position
                            )
                        }
                    )
                }
        )
    }
}

@Composable
private fun NavigationContentRow(
    items: List<MiuiNavigationDestination>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    selectedStyle: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            NavigationTabItem(
                modifier = Modifier.weight(1f),
                item = item,
                selectedStyle = selectedStyle,
                semanticSelected = index == selectedIndex,
                onSemanticClick = { onTabSelected(index) }
            )
        }
    }
}

@Composable
private fun NavigationTabItem(
    modifier: Modifier,
    item: MiuiNavigationDestination,
    selectedStyle: Boolean,
    semanticSelected: Boolean,
    onSemanticClick: () -> Unit
) {
    val color =
        if (selectedStyle) MiuiColors.Primary else MiuiColors.SecondaryText

    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                selected = semanticSelected
                onClick(label = item.label) {
                    onSemanticClick()
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = if (selectedStyle) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
            color = color
        )
    }
}

private fun applyNavigationRubberBand(
    rawIndex: Float,
    lastIndex: Float
): Float {
    if (rawIndex < 0f) {
        val resisted = rawIndex * 0.36f
        return resisted.coerceIn(-0.24f, 0f)
    }
    if (rawIndex > lastIndex) {
        val resisted = lastIndex + (rawIndex - lastIndex) * 0.36f
        return resisted.coerceIn(lastIndex, lastIndex + 0.24f)
    }
    return rawIndex
}

private data class NavigationLensShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadius: Float
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val safeLeft = left.coerceIn(0f, size.width)
        val safeTop = top.coerceIn(0f, size.height)
        val safeRight = right.coerceIn(safeLeft, size.width)
        val safeBottom = bottom.coerceIn(safeTop, size.height)
        val radius = cornerRadius.coerceAtLeast(0f)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(
                        left = safeLeft,
                        top = safeTop,
                        right = safeRight,
                        bottom = safeBottom
                    ),
                    radiusX = radius,
                    radiusY = radius
                )
            )
        }
        return Outline.Generic(path)
    }
}
