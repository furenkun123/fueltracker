package com.fueltracker.ui.miui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

val LocalFuelTrackerBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalFuelTrackerGlassEnabled = staticCompositionLocalOf { true }

@Composable
fun ProvideFuelTrackerGlass(
    backdrop: Backdrop,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalFuelTrackerBackdrop provides backdrop,
        LocalFuelTrackerGlassEnabled provides enabled,
        content = content
    )
}

/** 液态玻璃表面：从应用 Backdrop 采样，叠加模糊、色彩活力、折射和边缘高光。 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(GlassDefaults.Surface.cornerRadius),
    tint: Color? = null,
    blur: Boolean = true
): Modifier {
    val backdrop = LocalFuelTrackerBackdrop.current
    val enabled = LocalFuelTrackerGlassEnabled.current
    if (!enabled || backdrop == null) return this

    val isLight = androidx.compose.material3.MaterialTheme.colorScheme.background
        .let { it.red * 0.2126f + it.green * 0.7152f + it.blue * 0.0722f > 0.5f }
    val surfaceTint = tint ?: if (isLight) {
        GlassDefaults.Surface.lightTint
    } else {
        GlassDefaults.Surface.darkTint
    }
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                if (blur) blur(GlassDefaults.Surface.blurRadius.toPx())
                lens(
                    refractionHeight = GlassDefaults.Surface.refractionHeight.toPx(),
                    refractionAmount = GlassDefaults.Surface.refractionAmount.toPx()
                )
            },
            onDrawSurface = { drawRect(surfaceTint) }
        )
        .drawBehind {
            val edge = if (isLight) Color.White.copy(alpha = 0.68f)
            else Color.White.copy(alpha = 0.18f)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(edge, Color.Transparent, Color.Transparent)
                )
            )
        }
        .border(
            width = GlassDefaults.Surface.borderWidth,
            brush = Brush.verticalGradient(
                colors = if (isLight) listOf(
                    Color.White.copy(alpha = 0.78f),
                    Color.White.copy(alpha = 0.12f)
                ) else listOf(
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape = shape
        )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassDefaults.Surface.cornerRadius),
    tint: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlass(shape = shape, tint = tint),
        content = content
    )
}
