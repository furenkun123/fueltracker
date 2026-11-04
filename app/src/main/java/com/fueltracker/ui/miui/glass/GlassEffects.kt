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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/**
 * 所有内容卡片共用的独立背景采样源。
 * 采样层只绘制 AppGlassBackground，不包含任何卡片，避免循环采样。
 */
val LocalCardGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalCardGlassEnabled = staticCompositionLocalOf { false }
/**
 * 通用玻璃背景材质。
 *
 * 导航栏、弹窗等需要统一外观的组件共用此实现，所有模糊、折射和染色参数
 * 均由 GlassPreferences / GlassDefaults.Navigation 提供。
 */
fun Modifier.drawGlassBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    isLight: Boolean
): Modifier {
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val params = GlassPreferences.params
            blur(params.blur.dp.toPx())
            lens(
                refractionHeight = params.refractionHeight.dp.toPx(),
                refractionAmount = params.refractionAmount.dp.toPx()
            )
        },
        onDrawSurface = {
            val concentrationScale = GlassPreferences.params.concentration / 65f
            val tint = if (isLight) {
                GlassDefaults.Navigation.lightBackdropTint
            } else {
                GlassDefaults.Navigation.darkBackdropTint
            }
            drawRect(color = tint.copy(alpha = (tint.alpha * concentrationScale).coerceIn(0f, 1f)))
        }
    )
}

/**
 * 导航栏玻璃背景：保留原有 API，内部直接复用通用材质。
 */
fun Modifier.drawGlassNavigationBackdrop(
    backdrop: Backdrop,
    shape: Shape,
    isLight: Boolean
): Modifier = drawGlassBackdrop(backdrop, shape, isLight)

/**
 * 导航栏底部渐变叠加
 */
fun Modifier.drawGlassNavigationOverlay(
    isLight: Boolean
): Modifier {
    return drawBehind {

        val concentrationScale = GlassPreferences.params.concentration / 65f
        val baseOverlayColor = if (isLight) {
            GlassDefaults.Navigation.lightBottomOverlay
        } else {
            GlassDefaults.Navigation.darkBottomOverlay
        }
        val overlayColor = baseOverlayColor.copy(
            alpha = (baseOverlayColor.alpha * concentrationScale).coerceIn(0f, 1f)
        )

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

        val concentrationScale = GlassPreferences.params.concentration / 65f
        val highlightAlpha = (if (isLight) {
            GlassDefaults.Navigation.LIGHTHIGHLIGHTALPHA
        } else {
            GlassDefaults.Navigation.DARKHIGHLIGHTALPHA
        }) * concentrationScale

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
                        alpha = GlassDefaults.Navigation.HIGHLIGHTLINEMIDDLEALPHA * concentrationScale
                    ),
                    Color.White.copy(
                        alpha = GlassDefaults.Navigation.HIGHLIGHTLINEALPHA * concentrationScale
                    ),
                    Color.White.copy(
                        alpha = GlassDefaults.Navigation.HIGHLIGHTLINEMIDDLEALPHA * concentrationScale
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
        val concentrationScale = GlassPreferences.params.concentration / 65f

        val baseTint = if (isLight) {
            GlassDefaults.Navigation.lightPillTint
        } else {
            GlassDefaults.Navigation.darkPillTint
        }

        val tint = baseTint.copy(
            alpha = (baseTint.alpha * concentrationScale).coerceIn(0f, 1f)
        )
        val baseHighlight = if (isLight) {
            GlassDefaults.Navigation.lightPillHighlight
        } else {
            GlassDefaults.Navigation.darkPillHighlight
        }
        val highlight = baseHighlight.copy(
            alpha = (baseHighlight.alpha * concentrationScale).coerceIn(0f, 1f)
        )

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

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            // Backdrop 会观察 effects 回调中的 Snapshot 读取。
            // 必须在这里读取参数，才能在设置变化时触发效果缓存刷新。
            val params = GlassPreferences.params
            vibrancy()
            blur(params.blur.dp.toPx())
            lens(
                refractionHeight = params.refractionHeight.dp.toPx(),
                refractionAmount = params.refractionAmount.dp.toPx()
            )
        },
        onDrawSurface = {
            // 在绘制回调中读取浓度，确保材质覆盖层同步刷新。
            // 65% 为当前视觉基准；0% 不叠加材质色，100% 明显增强玻璃层次。
            val concentrationScale =
                (GlassPreferences.params.concentration / 65f).coerceIn(0f, 1.8f)

            val surfaceBrush = if (isLight) {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.15f * concentrationScale),
                        0.38f to Color(0xFFE7EFFB).copy(alpha = 0.09f * concentrationScale),
                        0.78f to Color(0xFFDCE8F8).copy(alpha = 0.12f * concentrationScale),
                        1.00f to Color(0xFFB9CBE3).copy(alpha = 0.17f * concentrationScale)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF52627A).copy(alpha = 0.34f * concentrationScale),
                        0.30f to Color(0xFF34445B).copy(alpha = 0.30f * concentrationScale),
                        0.72f to Color(0xFF202D40).copy(alpha = 0.34f * concentrationScale),
                        1.00f to Color(0xFF101A2A).copy(alpha = 0.48f * concentrationScale)
                    )
                )
            }

            val topHighlightAlpha =
                (if (isLight) 0.10f else 0.075f) * concentrationScale
            val bottomShadeAlpha =
                (if (isLight) 0.035f else 0.12f) * concentrationScale

            drawRect(brush = surfaceBrush)

            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topHighlightAlpha),
                    0.22f to Color.White.copy(alpha = topHighlightAlpha * 0.28f),
                    0.52f to Color.Transparent
                )
            )

            drawRect(
                brush = Brush.verticalGradient(
                    0.48f to Color.Transparent,
                    1f to Color.Black.copy(alpha = bottomShadeAlpha)
                )
            )
        }
    )
}
