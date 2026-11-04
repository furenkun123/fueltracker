package com.fueltracker.ui.miui.glass

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
 */
fun Modifier.drawLiquidGlassCard(
    backdrop: Backdrop?,
    shape: Shape,
    isLight: Boolean
): Modifier {
    if (backdrop == null) return this

    val surfaceBrush = if (isLight) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color(0xFFE7EFFB).copy(alpha = 0.10f),
                Color(0xFFDCE8F8).copy(alpha = 0.12f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF293548).copy(alpha = 0.24f),
                Color(0xFF1B2432).copy(alpha = 0.20f),
                Color(0xFF182334).copy(alpha = 0.24f)
            )
        )
    }

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
        }
    )
}
