
package com.fueltracker.ui.miuix

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

private tailrec fun Context.findActivity(): android.app.Activity? {
    return when (this) {
        is android.app.Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
@Composable
fun MiuixTopBar(
    backdrop: Backdrop,
    isGlassEnabled: Boolean = true,
    showActionArea: Boolean = true,
    statusBarDarkIcons: Boolean? = null
) {
    val view = LocalView.current
    val statusBarHeight =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val actionAreaHeight = 48.dp
    val totalHeight =
        statusBarHeight + if (showActionArea) actionAreaHeight else 0.dp

    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: view.context.findActivity()?.window

        window?.let {
            statusBarDarkIcons?.let { useDarkIcons ->
                WindowCompat.getInsetsController(it, view)
                    .isAppearanceLightStatusBars = useDarkIcons
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        if (isGlassEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = {
                            RoundedCornerShape(0.dp)
                        },
                        effects = {
                            // 轻微模糊
                            blur(3.dp.toPx())

                            // 从顶部向底部逐渐减弱
                            runtimeShaderEffect(
                                "AlphaMask",
                                """
        uniform shader content;
        uniform float2 size;

        half4 main(float2 coord) {
            half4 color = content.eval(coord);

            // 顶部模糊最明显，底部逐渐恢复透明
            float mask = 1.0 - smoothstep(
                0.0,
                size.y,
                coord.y
            );

            return color * mask;
        }
        """,
                                "content"
                            ) {
                                setFloatUniform("size", size.width, size.height)
                            }
                        },
                        highlight = null,
                        shadow = null,
                        onDrawSurface = {
                            drawRect(Color.Transparent)
                        }
                    )
            )
        }
    }
}

@Composable
fun MiuixTopBarIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    containerSize: Dp = 46.dp,
    iconSize: Dp = 24.dp,
    iconTint: Color = MiuixTheme.colorScheme.onSurface,
    containerColor: Color = MiuixTheme.colorScheme.surfaceVariant
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
fun MiuixBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MiuixTheme.colorScheme.onBackground
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}