
package com.fueltracker.ui.miuix

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    statusBarDarkIcons: Boolean
) {
    val view = LocalView.current

    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: view.context.findActivity()?.window

        window?.let {
            WindowCompat.getInsetsController(it, view)
                .isAppearanceLightStatusBars = statusBarDarkIcons
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