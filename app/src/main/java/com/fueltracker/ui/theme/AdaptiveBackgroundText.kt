package com.fueltracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextStyle

/**
 * 自定义背景下，独立标题使用的自适应颜色。
 * null 表示没有自定义背景，沿用原主题颜色。
 */
val LocalBackgroundTitleColor = staticCompositionLocalOf<Color?> { null }

@Composable
fun adaptiveBackgroundTitleColor(fallback: Color): Color {
    return LocalBackgroundTitleColor.current ?: fallback
}

/**
 * 计算顶部 30% 区域平均亮度，决定标题颜色。
 */
@Composable
fun rememberAdaptiveTitleColor(backgroundBitmap: ImageBitmap?): Color? {
    val scrimAlpha = BackgroundPreferences.scrimAlpha
    return remember(backgroundBitmap, scrimAlpha) {
        backgroundBitmap?.let { bitmap ->
            val pixels = bitmap.toPixelMap()

            // 只截取屏幕顶部 30% 的区域进行亮度计算
            val topRegionHeight = (pixels.height * 0.3f).toInt().coerceAtLeast(1)
            val stepX = (pixels.width / 24).coerceAtLeast(1)
            val stepY = (topRegionHeight / 24).coerceAtLeast(1)

            var totalLuminance = 0f
            var count = 0

            for (y in 0 until topRegionHeight step stepY) {
                for (x in 0 until pixels.width step stepX) {
                    val color = pixels[x, y]
                    val luminance =
                        color.red * 0.2126f +
                                color.green * 0.7152f +
                                color.blue * 0.0722f
                    totalLuminance += luminance
                    count++
                }
            }

            val averageLuminance = if (count > 0) totalLuminance / count else 0.5f
            val actualLuminance = averageLuminance * (1f - scrimAlpha)

            // 只有背景足够亮时才使用深色文字
            if (actualLuminance < 0.72f) {
                Color(0xFFF5F5F5)
            } else {
                Color(0xFF1F1F1F)
            }
        }
    }
}

/**
 * 轻微反差投影，帮助文字从复杂背景中凸显。
 */
@Composable
fun adaptiveBackgroundTitleStyle(
    base: TextStyle = TextStyle()
): TextStyle {
    return base.copy(
        shadow = null
    )
}