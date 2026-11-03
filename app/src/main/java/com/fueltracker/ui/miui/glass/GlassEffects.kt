package com.fueltracker.ui.miui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
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

            lens(
                refractionHeight =
                    GlassDefaults.Navigation.backdropRefractionHeight.toPx(),
                refractionAmount =
                    GlassDefaults.Navigation.backdropRefractionAmount.toPx(),
                depthEffect = true,
                chromaticAberration = true
            )
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
 * 选中导航项的液态玻璃胶囊
 */
fun Modifier.drawGlassSelectionPill(
    backdrop: Backdrop,
    isLight: Boolean
): Modifier {
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = {
            RoundedCornerShape(
                GlassDefaults.Navigation.pillCornerRadius
            )
        },
        effects = {
            blur(GlassDefaults.Navigation.pillBlur.toPx())

            lens(
                refractionHeight =
                    GlassDefaults.Navigation.pillRefractionHeight.toPx(),
                refractionAmount =
                    GlassDefaults.Navigation.pillRefractionAmount.toPx(),
                depthEffect = true,
                chromaticAberration = true
            )
        },
        onDrawSurface = {
            drawRect(
                color = if (isLight) {
                    GlassDefaults.Navigation.lightPillTint
                } else {
                    GlassDefaults.Navigation.darkPillTint
                }
            )
        }
    ).drawBehind {

        drawRoundRect(
            color = if (isLight) {
                GlassDefaults.Navigation.lightPillHighlight
            } else {
                GlassDefaults.Navigation.darkPillHighlight
            },
            cornerRadius = CornerRadius(
                GlassDefaults.Navigation.pillCornerRadius.toPx()
            )
        )
    }
}