package com.fueltracker.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiuixChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    backgroundColor: Color = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    },
    contentColor: Color = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurface
    },
    cornerRadius: Dp = 12.dp,
    paddingH: Dp = 16.dp,
    paddingV: Dp = 10.dp,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = contentColor,
            maxLines = 1
        )
    }
}
/**
 * 通用筛选标签（类似于 MIUI 的 Tab/Chip）
 */
@Composable
fun MiuixFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 自动判断深浅色
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f

    // 未选中时，用带透明度的黑/白，确保在任何卡片背景上都能明显看出被“底色包围”
    val unselectedBgColor = if (isLight) {
        Color.Black.copy(alpha = 0.06f) // 浅色模式下，用 6% 的微黑底色
    } else {
        Color.White.copy(alpha = 0.12f) // 深色模式下，用 12% 的微白底色
    }

    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary else unselectedBgColor
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1
        )
    }
}