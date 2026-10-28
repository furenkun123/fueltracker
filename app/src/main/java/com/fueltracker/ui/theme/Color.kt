package com.fueltracker.ui.theme

import androidx.compose.ui.graphics.Color

// ---- 品牌主色 ----
val Blue600 = Color(0xFF1677FF)
val Blue400 = Color(0xFF4096FF)

// ---- 浅色 ----
// 👇 1. 页面背景：加深一点点灰度（如 #EFEFF2），让白色卡片能凸显出来，同时降低整体亮度
val BackgroundLight = Color(0xFFE5E5EA)

// 👇 2. 卡片颜色：不用纯白，用非常柔和的“米灰白/微霜白”（如 #F8F8FA），去掉了刺眼感
val SurfaceWhite = Color(0xFFF8F8FA)

// 👇 3. 输入框等内嵌背景：比卡片再深一点，形成略微凹陷的视觉
val SurfaceVariantLight = Color(0xFFE5E5EA)

// 👇 4. 文字颜色可以稍微柔和一点，不要纯黑
val TextPrimary = Color(0xFF2C2C2C)
val TextSecondary = Color(0xFF8E8E93)

val OutlineLight = Color(0xFFD1D1D6)
val PrimaryContainerLight = Color(0xFFD6E4FF)
val OnPrimaryContainerLight = Color(0xFF003A8C)

// ---- 深色 ----
// 深色模式目前的搭配其实很好，保持原样
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1C1C1E)
val TextPrimaryDark = Color(0xFFE6E6E6)
val TextSecondaryDark = Color(0xFF9E9E9E)
val SurfaceVariantDark = Color(0xFF2C2C2C)
val OutlineDark = Color(0xFF3A3A3A)
val PrimaryContainerDark = Color(0xFF11366E)
val OnPrimaryContainerDark = Color(0xFFD6E4FF)

// ---- 语义色 ----
val ErrorLight = Color(0xFFFF3B30)
val ErrorDark = Color(0xFFFF453A)
val OnPrimaryWhite = Color(0xFFFFFFFF)