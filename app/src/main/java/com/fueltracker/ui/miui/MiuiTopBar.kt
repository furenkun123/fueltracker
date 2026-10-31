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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

@Composable
fun MiuiTopBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    isGlassEnabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    showActionArea: Boolean = true
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val actionAreaHeight = 48.dp
    val totalHeight = statusBarHeight + if (showActionArea) actionAreaHeight else 0.dp
    val barColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        // 👇 状态栏模糊区域
        if (statusBarHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .align(Alignment.TopCenter)
            ) {
                if (isGlassEnabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawPlainBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = { blur(12.dp.toPx()) },
                                // 👈 建议加上一层半透明底色，让模糊更有质感（可根据喜好调整 alpha）
                                onDrawSurface = { drawRect(barColor.copy(alpha = 0.5f)) }
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            // 👈 替换了原来的测试代码 Color.Red
                            .background(barColor)
                    )
                }
            }
        }

        // 👇 顶部操作区域：透明，不模糊
        if (showActionArea) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(actionAreaHeight)
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 28.dp),
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
            .size(40.dp) // 保持 40dp 的舒适点击热区
            .clip(CircleShape) // 让点击水波纹呈圆形，且不超出边界
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            // 👇 关键修复：改为 onBackground，绝对跟随页面底色反色
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}