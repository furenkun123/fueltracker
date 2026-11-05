package com.fueltracker.ui.miui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

@Composable
fun MiuiStatusBarGlass(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isGlassEnabled: Boolean = true
) {
    val statusBarHeight =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val backgroundColor = MaterialTheme.colorScheme.background
    val isLight = backgroundColor.luminance() > 0.5f
    val glassTintAlpha = if (isLight) 0.28f else 0.36f
    val glassHeight = statusBarHeight + 48.dp

    if (!isGlassEnabled || statusBarHeight <= 0.dp) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(glassHeight)
    ) {
        // 第一层保留原始背景，避免渐变边缘出现色块断层。
        Box(
            Modifier
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

        // 第二层只在状态栏顶部保留完整模糊，向下渐隐到页面背景。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()

                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.White,
                                0.25f to Color.White,
                                0.45f to Color.White.copy(alpha = 0.65f),
                                0.65f to Color.White.copy(alpha = 0.15f),
                                0.75f to Color.Transparent,
                                1f to Color.Transparent
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
                            backgroundColor.copy(alpha = glassTintAlpha)
                        )
                    }
                )
        )
    }
}
