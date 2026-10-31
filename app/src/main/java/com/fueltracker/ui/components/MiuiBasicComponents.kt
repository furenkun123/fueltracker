package com.fueltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用筛选标签（类似于 MIUI 的 Tab/Chip）
 */
@Composable
fun MiuiFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 自动判断深浅色
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

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
                if (selected) MaterialTheme.colorScheme.primary else unselectedBgColor
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 通用紧凑型文字按钮（用于列表项右侧的“编辑”、“删除”）
 */
@Composable
fun MiuiTextButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}