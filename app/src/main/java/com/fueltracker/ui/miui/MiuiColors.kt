package com.fueltracker.ui.miui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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