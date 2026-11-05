package com.fueltracker.ui.miui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.fueltracker.ui.miui.glass.LocalAppGlassBackdrop
import com.fueltracker.ui.miui.glass.LocalAppGlassEnabled
import com.fueltracker.ui.miui.glass.LocalCardGlassBackdrop
import com.fueltracker.ui.miui.glass.LocalCardGlassEnabled
import com.fueltracker.ui.miui.glass.drawLiquidGlassCard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.Dp


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

/**
 * 页面级悬浮图标按钮。
 *
 * 首页新增、费用新增和设置入口共用同一套材质：开启玻璃时使用实时模糊、
 * 折射与主题染色；关闭玻璃后恢复为对应入口的纯色按钮。
 */
@Composable
fun MiuiFloatingIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    containerSize: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true
) {
    val backdrop = LocalAppGlassBackdrop.current
    val useGlass = enabled && LocalAppGlassEnabled.current && backdrop != null
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val shape = CircleShape
    val effectiveContentColor =
        if (useGlass) MaterialTheme.colorScheme.onSurface else contentColor
    val surfaceModifier = if (useGlass) {
        Modifier.background(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = if (isLight) 0.58f else 0.18f),
                    0.48f to containerColor.copy(alpha = if (isLight) 0.24f else 0.34f),
                    1f to (if (isLight) Color(0xFF52657D) else Color.Black).copy(
                        alpha = if (isLight) 0.10f else 0.24f
                    )
                )
            )
        ).border(
            width = 0.8.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isLight) 0.86f else 0.34f),
                    Color.White.copy(alpha = if (isLight) 0.24f else 0.10f)
                )
            ),
            shape = shape
        )
    } else {
        Modifier.background(
            if (enabled) containerColor else containerColor.copy(alpha = 0.38f)
        )
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(shape)
            .then(surfaceModifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = effectiveContentColor.copy(alpha = if (enabled) 1f else 0.55f),
            modifier = Modifier.size(iconSize)
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
    glassEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalCardGlassBackdrop.current
    val useGlass = glassEnabled && LocalCardGlassEnabled.current && backdrop != null
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val cardModifier = if (useGlass) {
        modifier.drawLiquidGlassCard(
            backdrop = backdrop,
            shape = shape,
            isLight = isLight
        )
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = if (useGlass) {
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        } else {
            colors ?: CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        },
        elevation = elevation ?: CardDefaults.cardElevation(defaultElevation = 0.dp),
        // 卡片统一采用无描边玻璃材质；显式传入的边框仍可用于特殊状态。 
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
    glassEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalCardGlassBackdrop.current
    val useGlass = glassEnabled && LocalCardGlassEnabled.current && backdrop != null
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val cardModifier = if (useGlass) {
        modifier.drawLiquidGlassCard(
            backdrop = backdrop,
            shape = shape,
            isLight = isLight
        )
    } else {
        modifier
    }

    Card(
        onClick = onClick,
        modifier = cardModifier,
        enabled = enabled,
        shape = shape,
        colors = if (useGlass) {
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
            )
        } else {
            colors ?: CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        },
        elevation = elevation ?: CardDefaults.cardElevation(defaultElevation = 0.dp),
        // 卡片统一采用无描边玻璃材质；显式传入的边框仍可用于特殊状态。 
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
    MiuiCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
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
        MiuiCard(
            modifier = Modifier.weight(1f).height(42.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
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
                                    color = placeholderColor
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}


/**
 * 通用 MIUI 风格表单输入框
 *
 * 支持：
 * - 标签及必填标记
 * - 输入错误状态及错误提示
 * - 普通后缀、可点击后缀
 * - 标签右侧自定义内容
 * - 输入框占位提示
 * - 辅助说明
 * - 启用/禁用状态
 * - 键盘类型
 *
 * 不负责业务数据校验、数值计算或保存逻辑。
 */
@Composable
fun MiuiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isRequired: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = keyboardType
    ),
    keyboardActions: KeyboardActions = KeyboardActions(),
    onSuffixClick: (() -> Unit)? = null,
    labelTrailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    horizontalPadding: Dp = 0.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val inputBorder = when {
        isError -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.error)
        isFocused -> BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
        else -> null
    }

    Column(
        modifier = modifier.padding(horizontal = horizontalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isRequired) {
                    Text(
                        text = " *",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            labelTrailing?.let {
                Spacer(Modifier.width(4.dp))
                it()
            }
        }

        Spacer(Modifier.height(6.dp))

        MiuiCard(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            border = inputBorder
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxSize(),
                    singleLine = true,
                    enabled = enabled,
                    isError = isError,
                    shape = RoundedCornerShape(14.dp),
                    interactionSource = interactionSource,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    placeholder = placeholder?.let {
                        {
                            Text(
                                text = it,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    suffix = {
                        if (suffix != null) {
                            if (onSuffixClick != null) {
                                Text(
                                    text = "$suffix ▾",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(onClick = onSuffixClick)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            } else {
                                Text(
                                    text = suffix,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    },
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        errorBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                )
            }
        }
        when {
            isError && errorMessage != null -> {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        top = 2.dp
                    )
                )
            }

            supportingText != null -> {
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        top = 2.dp
                    )
                )
            }
        }
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
fun MiuiFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    MiuiCompactFilterChip(
        text = text,
        selected = selected,
        onClick = onClick
    )
}

@Composable
fun MiuiCompactFilterChip(
    text: String,
    selected: Boolean,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 6.dp,
    fontSize: TextUnit = 12.sp,
    onClick: () -> Unit
) {
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
            .padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
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
@Composable
fun MiuiFieldLabel(
    text: String,
    isRequired: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (isRequired) {
            Text(
                text = " *",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
