
package com.fueltracker.ui.miui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/**
 * 仅为系统状态栏区域提供玻璃背景，不包含标题、返回按钮或操作区。
 * 页面标题仍由各个 Screen 自己布局。
 */
@Composable
fun MiuiStatusBarGlass(
    backdrop: Backdrop,
    isGlassEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // MaterialTheme 只能在 Composable 上下文中读取，不能放在绘制回调内。
    val statusBarBackground = MaterialTheme.colorScheme.background
    if (isGlassEnabled && statusBarHeight > 0.dp) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(statusBarHeight)
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        vibrancy()
                        blur(18.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(statusBarBackground.copy(alpha = 0.16f))
                    }
                )
        )
    }
}

@Composable
fun MiuiTopBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    isGlassEnabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    showActionArea: Boolean = true
) {
    val statusBarHeight =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val actionAreaHeight = 48.dp
    val totalHeight =
        statusBarHeight + if (showActionArea) actionAreaHeight else 0.dp

    val barColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        // 整体连续玻璃背景
        if (isGlassEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
            ) {
                // 第一层：清晰背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawPlainBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = {},
                            onDrawSurface = {
                                drawRect(Color.Transparent)
                            }
                        )
                )

                // 第二层：模糊背景，使用渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .height(totalHeight)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()

                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.White,
                                        0.25f to Color.White,
                                        0.45f to Color.White.copy(alpha = 0.65f),
                                        0.65f to Color.White.copy(alpha = 0.15f),
                                        0.75f to Color.Transparent,
                                        1.0f to Color.Transparent
                                    )
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        .drawPlainBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = {
                                vibrancy()
                                blur(24.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(
                                    barColor.copy(
                                        alpha = if (barColor.luminance() > 0.5f) {
                                            0.28f
                                        } else {
                                            0.36f
                                        }
                                    )
                                )
                            }
                        )
                )
            }

        } else {
            // 关闭玻璃效果时不绘制不透明背景，
            // 保留首页内容在 TopBar 背后的可见性
            Box(
                modifier = Modifier.fillMaxSize()
            )
        }

        // 操作区域：保留原来的布局
        if (showActionArea) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = statusBarHeight,
                        start = 16.dp,
                        end = 28.dp
                    )
                    .height(actionAreaHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (action != null) {
                    Box(Modifier.offset(y = 4.dp)) {
                        action()
                    }
                }
            }
        }
    }
}

@Composable
fun MiuiTopBarIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    containerSize: Dp = 46.dp,
    iconSize: Dp = 24.dp,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun MiuiBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}