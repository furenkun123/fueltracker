package com.fueltracker.ui.miui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

@Composable
fun MiuiNavigationBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    isGlassEnabled: Boolean,
    isFloating: Boolean = true,
    selectedIndex: Int = 0,
    content: @Composable RowScope.() -> Unit
) {
    val isLight =
        MaterialTheme.colorScheme.background.luminance() > 0.5f

    val shape: Shape =
        if (isFloating) RoundedCornerShape(32.dp)
        else RoundedCornerShape(0.dp)

    Box(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFloating) {
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    } else {
                        Modifier
                    }
                )
                .shadow(
                    elevation = if (isFloating) {
                        if (isLight) 22.dp else 18.dp
                    } else {
                        0.dp
                    },
                    shape = shape,
                    ambientColor = Color.Black.copy(
                        alpha = if (isLight) 0.16f else 0.24f
                    ),
                    spotColor = Color.Black.copy(
                        alpha = if (isLight) 0.22f else 0.32f
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
                selectedIndex = selectedIndex,
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
    content: @Composable RowScope.() -> Unit
) {
    val solidColor =
        if (isLight) Color(0xFFF8F8FA)
        else Color(0xFF1C1C1E)

    val glassEnabled = isGlassEnabled && isFloating

    BoxWithConstraints(

            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (glassEnabled) Color.Transparent else solidColor
                )
                .then(
                    if (isFloating) {
                        Modifier.border(
                            width = 0.8.dp,
                            color = if (isLight) {
                                Color(0xFFD1D1D6).copy(alpha = 0.65f)
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                            shape = shape
                        )
                    } else {
                        Modifier
                    }
                )

    ){
        val tabCount = 3
        val horizontalPadding = 4.dp
        val tabWidth = (maxWidth - horizontalPadding * 2) / tabCount
        val pillWidth = tabWidth - horizontalPadding * 2

        val animatedIndex = remember {
            Animatable(selectedIndex.toFloat())
        }

        LaunchedEffect(selectedIndex) {
            animatedIndex.animateTo(
                targetValue = selectedIndex.coerceIn(0, tabCount - 1).toFloat(),
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
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            blur(2.dp.toPx())
                            lens(
                                refractionHeight = 6.dp.toPx(),
                                refractionAmount = 12.dp.toPx(),
                                depthEffect = false,
                                chromaticAberration = false
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                if (isLight) {
                                    Color(0xFFF8F8FA).copy(alpha = 0.30f)
                                } else {
                                    Color(0xFF202024).copy(alpha = 0.22f)
                                }
                            )
                        }
                    )
            )
        }

        // 第二层：底部淡色叠加
        if (isFloating && glassEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val overlayColor = if (isLight) {
                            Color(0xFFD1D1D6).copy(alpha = 0.20f)
                        } else {
                            Color(0xFFB8B8C2).copy(alpha = 0.13f)
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
            )
        }

        // 第三层：顶部反光
        if (isFloating && glassEnabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val highlight =
                            if (isLight) 0.16f else 0.018f

                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.White.copy(alpha = highlight),
                                    0.12f to Color.White.copy(alpha = highlight * 0.35f),
                                    0.30f to Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.38f
                            )
                        )

                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.06f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(size.width * 0.06f, 1.5.dp.toPx()),
                            end = Offset(size.width * 0.94f, 1.5.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
            )
        }

        // 第四层：弹性选中胶囊
        val pillOffset = with(LocalDensity.current) {
            animatedIndex.value * tabWidth.toPx() + horizontalPadding.toPx()
        }
        if (glassEnabled) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = pillOffset.toInt(),
                            y = 4.dp.roundToPx()
                        )
                    }
                    .width(pillWidth)
                    .height(56.dp)
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(28.dp) },
                        effects = {
                            blur(1.dp.toPx())
                            lens(
                                refractionHeight = 8.dp.toPx(),
                                refractionAmount = 12.dp.toPx(),
                                depthEffect = false,
                                chromaticAberration = false
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                if (isLight) {
                                    Color(0xFFFFFFFF).copy(alpha = 0.58f)
                                } else {
                                    Color.White.copy(alpha = 0.10f)
                                }
                            )
                        }
                    )

                    .drawBehind {
                        drawRoundRect(
                            color = if (isLight) {
                                Color.White.copy(alpha = 0.18f)
                            } else {
                                Color.White.copy(alpha = 0.08f)
                            },
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                28.dp.toPx()
                            )
                        )
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = pillOffset.toInt(),
                            y = 4.dp.roundToPx()
                        )
                    }
                    .width(pillWidth)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (isLight) {
                            Color(0xFF1677FF).copy(alpha = 0.09f)
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        }
                    )
            )
        }

        // 第五层：导航内容
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun RowScope.MiuiNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val activeColor =
        if (selected) MiuiColors.Primary
        else MiuiColors.SecondaryText

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