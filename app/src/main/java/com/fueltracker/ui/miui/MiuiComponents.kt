package com.fueltracker.ui.miui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
/**
 * 基础 MIUI 风格卡片
 * colors / elevation 默认值传 null, 函数体内才取主题色
 * 参数默认值不允许调用 @Composable
 */
@Composable
fun MiuiCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors ?: CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = elevation ?: CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border,
        content = content
    )
}

/**
 * 可点击的 MIUI 风格卡片
 */
@Suppress("unused")
@Composable
fun MiuiCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors ?: CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = elevation ?: CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border,
        content = content
    )
}
// =========================================================
// 卡片布局与表单列表组件
// =========================================================

@Composable
fun MiuiSectionHeader(title: String) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        // 👈 强制反色（灰黑 / 灰白）
        color = if (isLight) Color(0xFF666666) else Color(0xFFAAAAAA),
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun MiuiGroupCard(content: @Composable ColumnScope.() -> Unit) {
    // 确保卡片背景色在不同模式下都有层次感
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardBg = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF1C1C1E)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBg
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            content()
        }
    }
}

@Composable
fun MiuiInputItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val textColor = if (isLight) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
    val placeholderColor = if (isLight) Color(0xFF1F1F1F).copy(alpha = 0.4f) else Color(0xFFE6E6E6).copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            // 👈 强制反色
            color = textColor,
            modifier = Modifier.width(110.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                fontSize = 15.sp,
                // 👈 强制反色
                color = textColor,
                fontWeight = FontWeight.Medium
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            // 👈 占位符颜色
                            color = placeholderColor
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun MiuiClickableItem(
    label: String,
    value: String,
    placeholder: String = "请选择", // 👈 新增占位符参数
    onClick: () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val textColor = if (isLight) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)

    // 👇 占位符用 60% 透明度，清晰且不刺眼
    val placeholderColor =
        if (isLight) Color(0xFF1F1F1F).copy(alpha = 0.6f) else Color(0xFFE6E6E6).copy(alpha = 0.6f)

    // 👇 实际选中的值用主题色，如果是暗色模式觉得太暗，可以使用高亮蓝
    val activeValueColor = if (isLight) MaterialTheme.colorScheme.primary else Color(0xFF66B2FF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = textColor,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value.ifBlank { placeholder }, // 👈 使用传入的 placeholder
            fontSize = 15.sp,
            color = if (value.isBlank()) placeholderColor else activeValueColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
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
/**
 * MIUI 语义色, 全部映射到 Material3 主题色, 自动适配深浅色.
 * 调用处写法不变: MiuiColors.Primary / MiuiColors.Text / ...
 */
object MiuiColors {

    val Primary: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val Text: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    val SecondaryText: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

}