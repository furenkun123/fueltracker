package com.fueltracker.ui.miui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

@Composable
fun MiuiNavigationBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    isGlassEnabled: Boolean,
    isFloating: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val capsuleShape = if (isFloating) {
        RoundedCornerShape(32.dp)
    } else {
        RoundedCornerShape(0.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFloating) {
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                } else {
                    Modifier
                }
            )
    ) {
        if (isFloating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .softFloatingShadow(
                        isLight = isLight,
                        cornerRadius = 32.dp,
                        blurRadius = 30f,
                        spread = 3f
                    )
            ) {
                NavigationBarSurface(
                    backdrop = backdrop,
                    shape = capsuleShape,
                    isFloating = true,
                    isLight = isLight,
                    isGlassEnabled = isGlassEnabled,
                    content = content
                )
            }
        } else {
            NavigationBarSurface(
                backdrop = backdrop,
                shape = capsuleShape,
                isFloating = false,
                isLight = isLight,
                isGlassEnabled = false,
                content = content
            )
        }
    }
}

private fun Modifier.softFloatingShadow(
    isLight: Boolean,
    cornerRadius: Dp = 32.dp,
    blurRadius: Float = 30f,
    spread: Float = 3f
): Modifier = this.drawBehind {
    val right = size.width - spread
    val bottom = size.height - spread
    val radius = cornerRadius.toPx()

    drawIntoCanvas { canvas ->
        fun drawShadow(color: Color, blur: Float, offsetY: Float) {
            val paint = android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {
                this.color = android.graphics.Color.WHITE
                setShadowLayer(blur, 0f, offsetY, color.toArgb())
            }

            canvas.nativeCanvas.drawRoundRect(
                spread,
                spread,
                right,
                bottom,
                radius,
                radius,
                paint
            )

            paint.clearShadowLayer()
        }

        if (isLight) {
            drawShadow(Color.Black.copy(alpha = 0.22f), blurRadius, 0f)
            drawShadow(Color.Black.copy(alpha = 0.12f), 12f, 3f)
        } else {

            drawShadow(Color.Black.copy(alpha = 0.55f), 22f, 5f)
            drawShadow(Color.Black.copy(alpha = 0.30f), 8f, 2f)
        }
    }
}

@Composable
private fun NavigationBarSurface(
    backdrop: Backdrop,
    shape: RoundedCornerShape,
    isFloating: Boolean,
    isLight: Boolean,
    isGlassEnabled: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val barColor = if (isLight) {
        Color.White
    } else {
        Color(0xFF303034)
    }

    val borderColor = if (isLight) {
        Color.White.copy(alpha = 0.65f)
    } else {
        Color.White.copy(alpha = 0.035f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        if (isFloating && isGlassEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            blur(16.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(barColor.copy(alpha = 0.58f))
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(barColor)
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            if (isFloating) 32.dp.toPx() else 0f
                        ),
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFloating) {
                        Modifier.height(64.dp)
                    } else {
                        Modifier
                            .navigationBarsPadding()
                            .height(64.dp)
                    }
                ),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun RowScope.MiuiNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val activeColor = if (selected) {
        MiuiColors.Primary
    } else {
        MiuiColors.SecondaryText
    }

    val selectedBg = if (selected) {
        if (isLight) {
            Color.Black.copy(alpha = 0.045f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(selectedBg)
                .padding(horizontal = 40.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = activeColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
                color = activeColor
            )
        }
    }
}