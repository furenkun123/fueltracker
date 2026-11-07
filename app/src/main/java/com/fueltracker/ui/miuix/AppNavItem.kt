package com.fueltracker.ui.miuix

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 导航栏条目数据类。
 * 放在 miuix 包下，因为它是 AppNavigationBar / MiuixNavigationBar 共享的长期数据模型。
 */
data class AppNavItem(
    val icon: ImageVector,
    val label: String
)