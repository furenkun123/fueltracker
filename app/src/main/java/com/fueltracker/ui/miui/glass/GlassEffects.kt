package com.fueltracker.ui.miui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.Backdrop

/**
 * 所有内容卡片共用的独立背景采样源。
 * 采样层只绘制 AppGlassBackground，不包含任何卡片，避免循环采样。
 */
val LocalCardGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalCardGlassEnabled = staticCompositionLocalOf { false }
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/**
 * 导航栏玻璃背景
 */
fun Modifier.drawGlassNavigationBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    isLight: Boolean
): Modifier {
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(GlassDefaults.Navigation.backdropBlur.toPx())
        },
        onDrawSurface = {
            drawRect(
                color = if (isLight) {
                    GlassDefaults.Navigation.lightBackdropTint
                } else {
                    GlassDefaults.Navigation.darkBackdropTint
                }
            )
        }
    )
}

/**
 * 导航栏底部渐变叠加
 */
fun Modifier.drawGlassNavigationOverlay(
    isLight: Boolean
): Modifier {
    return drawBehind {

        val overlayColor = if (isLight) {
            GlassDefaults.Navigation.lightBottomOverlay
        } else {
            GlassDefaults.Navigation.darkBottomOverlay
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
fun Modifier.drawGlassNavigationHighlight(
    isLight: Boolean
): Modifier {
    return drawBehind {

        val highlightAlpha = if (isLight) {
            GlassDefaults.Navigation.LIGHTHIGHLIGHTALPHA
        } else {
            GlassDefaults.Navigation.DARKHIGHLIGHTALPHA
        }

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = highlightAlpha),

                    GlassDefaults.Navigation.HIGHLIGHTMIDDLEPOSITION to
                            Color.White.copy(alpha = highlightAlpha * 0.35f),

                    GlassDefaults.Navigation.HIGHLIGHTENDPOSITION to
                            Color.Transparent
                ),
                startY = 0f,
                endY = size.height *
                        GlassDefaults.Navigation.HIGHLIGHTGRADIENTEND
            )
        )

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(
                        alpha = GlassDefaults.Navigation.HIGHLIGHTLINEMIDDLEALPHA
                    ),
                    Color.White.copy(
                        alpha = GlassDefaults.Navigation.HIGHLIGHTLINEALPHA
                    ),
                    Color.White.copy(
                        alpha = GlassDefaults.Navigation.HIGHLIGHTLINEMIDDLEALPHA
                    ),
                    Color.Transparent
                )
            ),
            start = Offset(
                size.width * 0.06f,
                GlassDefaults.Navigation.highlightLineHeight.toPx()
            ),
            end = Offset(
                size.width * 0.94f,
                GlassDefaults.Navigation.highlightLineHeight.toPx()
            ),
            strokeWidth = GlassDefaults.Navigation.highlightLineWidth.toPx()
        )
    }
}

/**
 * 选中导航项的轻量玻璃胶囊
 *
 * 不再单独采样 Backdrop，避免增加采样消费者。
 */
fun Modifier.drawGlassSelectionPill(
    isLight: Boolean
): Modifier {
    return drawBehind {
        val cornerRadius =
            GlassDefaults.Navigation.pillCornerRadius.toPx()

        val tint = if (isLight) {
            GlassDefaults.Navigation.lightPillTint
        } else {
            GlassDefaults.Navigation.darkPillTint
        }

        val highlight = if (isLight) {
            GlassDefaults.Navigation.lightPillHighlight
        } else {
            GlassDefaults.Navigation.darkPillHighlight
        }

        drawRoundRect(
            color = tint,
            cornerRadius = CornerRadius(cornerRadius)
        )

        drawRoundRect(
            color = highlight,
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}

/**
 * 首页车辆卡片液态玻璃。
 *
 * backdrop 必须来自独立的背景采样层，不能传入包含当前卡片的 appBackdrop。
 * 通过卡片内部的纵向明暗变化建立体积感，不依赖高亮描边。
 */
fun Modifier.drawLiquidGlassCard(
    backdrop: Backdrop?,
    shape: Shape,
    isLight: Boolean
): Modifier {
    if (backdrop == null) return this

    val surfaceBrush = if (isLight) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.15f),
                0.38f to Color(0xFFE7EFFB).copy(alpha = 0.09f),
                0.78f to Color(0xFFDCE8F8).copy(alpha = 0.12f),
                1.00f to Color(0xFFB9CBE3).copy(alpha = 0.17f)
            )
        )
    } else {
        // 暗色模式提高卡片上半部的亮度，底部逐渐压暗，形成柔和的悬浮层次。
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF52627A).copy(alpha = 0.34f),
                0.30f to Color(0xFF34445B).copy(alpha = 0.30f),
                0.72f to Color(0xFF202D40).copy(alpha = 0.34f),
                1.00f to Color(0xFF101A2A).copy(alpha = 0.48f)
            )
        )
    }

    val topHighlightAlpha = if (isLight) 0.10f else 0.075f
    val bottomShadeAlpha = if (isLight) 0.035f else 0.12f

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(12.dp.toPx())
            lens(
                refractionHeight = 14.dp.toPx(),
                refractionAmount = 24.dp.toPx()
            )
        },
        highlight = { Highlight.Plain },
        onDrawSurface = {
            drawRect(brush = surfaceBrush)

            // 只保留顶部柔光，不再绘制完整高亮边框。
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topHighlightAlpha),
                    0.22f to Color.White.copy(alpha = topHighlightAlpha * 0.28f),
                    0.52f to Color.Transparent
                )
            )

            // 底部轻微压暗，增强卡片与背景的分层。
            drawRect(
                brush = Brush.verticalGradient(
                    0.48f to Color.Transparent,
                    1f to Color.Black.copy(alpha = bottomShadeAlpha)
                )
            )
        }
    )
}
