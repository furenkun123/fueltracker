package com.fueltracker.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // ✅ 判断当前是否为浅色模式
    val isLight = MiuixTheme.colorScheme.surface.luminance() > 0.5f

    // 定义不同模式下的颜色
    // 浅色模式：轨道用浅灰，滑块用纯白
    // 深色模式：轨道用深灰，滑块用稍亮的浅灰（避免纯白在暗色下太刺眼）
    val uncheckedTrackColor = if (isLight) Color(0xFFE5E5EA) else Color(0xFF39393D)
    val uncheckedThumbColor = if (isLight) Color.White else Color(0xFFA0A0A5)

    // 选中状态的颜色直接跟随 Miuix 主题
    val checkedTrackColor = MiuixTheme.colorScheme.primary
    val checkedThumbColor = MiuixTheme.colorScheme.onPrimary

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        // ✅ 注意这里的方法名，如果是 switchColors 报错，可以尝试改成 SwitchDefaults.colors(...)
        colors = SwitchDefaults.switchColors(
            checkedTrackColor = checkedTrackColor,
            checkedThumbColor = checkedThumbColor,
            uncheckedTrackColor = uncheckedTrackColor,
            uncheckedThumbColor = uncheckedThumbColor
        )
    )
}