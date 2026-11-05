package com.fueltracker.ui.miui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

/**
 * 所有内容卡片共用的独立背景采样源。
 * 采样层只绘制 AppGlassBackground，不包含任何卡片，避免循环采样。
 */
val LocalCardGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalCardGlassEnabled = staticCompositionLocalOf { false }

/**
 * 全页面实时背景采样源。
 *
 * 弹窗位于 Activity 窗口时可直接复用该 Backdrop，使其模糊内容与
 * “已取车”确认层保持一致。
 */
val LocalAppGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalAppGlassEnabled = staticCompositionLocalOf { false }
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
    return drawBackdrop(
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
 * 弹窗背景染色。
 *
 * 与“已取车”确认层使用同一组明暗渐变：它只负责拉开正文与背景的层次，
 * 不覆盖下层采样画面，因此仍能保留实时模糊和折射。
 */
fun Modifier.drawGlassDialogOverlay(
    isLight: Boolean
): Modifier {
    return drawBehind {
        val colors = if (isLight) {
            listOf(
                Color.White.copy(alpha = 0.46f),
                Color(0xFFDCE9FA).copy(alpha = 0.22f),
                Color(0xFFB9CBE3).copy(alpha = 0.34f)
            )
        } else {
            listOf(
                Color(0xFF7186A5).copy(alpha = 0.24f),
                Color(0xFF263A55).copy(alpha = 0.20f),
                Color(0xFF0D192B).copy(alpha = 0.42f)
            )
        }

        drawRect(
            brush = Brush.verticalGradient(colors = colors)
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
    isLight: Boolean,
    accentColor: Color = Color.Transparent,
    interactionProgress: Float = 0f
): Modifier {
    return drawBehind {
        val cornerRadius =
            GlassDefaults.Navigation.pillCornerRadius.toPx()
        val concentrationScale = GlassPreferences.params.concentration / 65f
        val interaction =
            interactionProgress.coerceIn(0f, 1f)

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

        if (interaction > 0f && accentColor.alpha > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.045f * interaction),
                        accentColor.copy(alpha = 0.105f * interaction),
                        accentColor.copy(alpha = 0.055f * interaction)
                    )
                ),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        drawRoundRect(
            color = highlight,
            cornerRadius = CornerRadius(cornerRadius)
        )

        val borderAlpha =
            (if (isLight) 0.14f else 0.09f) +
                    interaction * (if (isLight) 0.12f else 0.08f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = borderAlpha),
                    Color.White.copy(alpha = borderAlpha * 0.24f),
                    Color.Transparent,
                    Color.White.copy(alpha = borderAlpha * 0.42f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = 0.75.dp.toPx())
        )
    }
}

/**
 * 在 Backdrop 采样结果上补齐卡片材质的厚度感。
 *
 * 染色负责让内容与背景分离；顶部反光和底部暗边提供光线方向；
 * 细描边只勾勒边缘，避免大块白色边框把半透明效果压成实色卡片。
 */
private fun DrawScope.drawLiquidGlassCardMaterial(
    shape: Shape,
    isLight: Boolean
) {
    val concentrationScale =
        (GlassPreferences.params.concentration / 65f).coerceIn(0f, 1.8f)

    val tint = if (isLight) {
        GlassDefaults.Card.lightTint
    } else {
        GlassDefaults.Card.darkTint
    }
    val tintAlpha = (if (isLight) {
        GlassDefaults.Card.LIGHT_TINT_ALPHA
    } else {
        GlassDefaults.Card.DARK_TINT_ALPHA
    } * concentrationScale).coerceIn(0f, 0.86f)

    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to tint.copy(alpha = tintAlpha * 0.76f),
                0.34f to tint.copy(alpha = tintAlpha),
                1f to tint.copy(alpha = (tintAlpha * 1.12f).coerceAtMost(0.92f))
            )
        )
    )

    val topHighlightAlpha = (if (isLight) {
        GlassDefaults.Card.LIGHT_TOP_HIGHLIGHT_ALPHA
    } else {
        GlassDefaults.Card.DARK_TOP_HIGHLIGHT_ALPHA
    } * concentrationScale).coerceIn(0f, 0.42f)

    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = topHighlightAlpha),
                0.11f to Color.White.copy(alpha = topHighlightAlpha * 0.46f),
                0.38f to Color.Transparent
            ),
            startY = 0f,
            endY = size.height * 0.52f
        )
    )

    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.16f to Color.White.copy(alpha = topHighlightAlpha * 0.18f),
                0.34f to Color.White.copy(alpha = topHighlightAlpha * 0.08f),
                0.62f to Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
    )

    val bottomShadeAlpha = (if (isLight) {
        GlassDefaults.Card.LIGHT_BOTTOM_SHADE_ALPHA
    } else {
        GlassDefaults.Card.DARK_BOTTOM_SHADE_ALPHA
    } * concentrationScale).coerceIn(0f, 0.38f)

    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.48f to Color.Transparent,
                1f to (if (isLight) Color(0xFF52657D) else Color.Black)
                    .copy(alpha = bottomShadeAlpha)
            ),
            startY = size.height * 0.48f,
            endY = size.height
        )
    )

    val edgeAlpha = (if (isLight) {
        GlassDefaults.Card.LIGHT_EDGE_ALPHA
    } else {
        GlassDefaults.Card.DARK_EDGE_ALPHA
    } * concentrationScale).coerceIn(0f, 0.90f)

    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = edgeAlpha),
                0.38f to Color.White.copy(alpha = edgeAlpha * 0.24f),
                0.72f to Color.White.copy(alpha = edgeAlpha * 0.08f),
                1f to Color.White.copy(alpha = edgeAlpha * 0.42f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        style = Stroke(width = GlassDefaults.Card.EDGE_WIDTH.dp.toPx())
    )
}

/**
 * 内容卡片玻璃背景。
 *
 * 使用独立背景采样层完成模糊与折射，再叠加统一的反光、暗边和描边。
 * 所有内容卡片共享同一套材质，不增加额外的 Backdrop 采样消费者。
 */
fun Modifier.drawLiquidGlassCard(
    backdrop: Backdrop?,
    shape: Shape,
    isLight: Boolean
): Modifier {
    return if (backdrop == null) {
        this
    } else {
        val params = GlassPreferences.params
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(params.blur.dp.toPx())
                lens(
                    refractionHeight = params.refractionHeight.dp.toPx(),
                    refractionAmount = params.refractionAmount.dp.toPx()
                )
            },
            onDrawSurface = {
                drawLiquidGlassCardMaterial(
                    shape = shape,
                    isLight = isLight
                )
            }
        )
    }
}
