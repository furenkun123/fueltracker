package com.fueltracker.ui.miui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 普通主按钮
 */
@Composable
fun MiuiButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,

    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(25.dp) //统一圆角为 25.dp

    // 👇 核心修改：完全对齐 FuelScreen 的 Button 配色
    val bgColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) // 禁用态：主题色 40% 透明度
    }

    // 文字颜色：无论是否禁用，都用 onPrimary
    val textColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp) // 高度也是 50dp
            .clip(shape)
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold // 同步字重为 SemiBold
        )
    }
}
@Composable
fun MiuiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    // 👇 核心修改：把未开启的轨道颜色，换成和 MiuiButton 禁用状态一样的深灰
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isLight) Color(0xFFB8B8BD) else Color(0xFF4A4A4E)
    }

    val thumbColor = Color.White // 滑块始终保持纯白

    // 滑块的位置偏移量：开启时靠右，关闭时靠左。使用 spring 弹簧动画，模拟 MIUI 的 Q 弹感
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(50)) // 胶囊形轨道
            .background(trackColor) // 应用深灰色轨道
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(20.dp)
                .shadow(elevation = 2.dp, shape = CircleShape) // 滑块的轻微阴影
                .background(thumbColor, CircleShape)
        )
    }
}