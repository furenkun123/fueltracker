package com.fueltracker.ui.miui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃效果统一参数
 *
 * 当前先配置底部导航栏使用的参数。
 * 后续增加 Dialog、BottomSheet 等组件时，可以继续扩展。
 */
object GlassDefaults {

    object Navigation {

        // ---------- 导航栏背景玻璃 ----------

        val backdropBlur = 2.dp

        val backdropRefractionHeight = 6.dp

        val backdropRefractionAmount = 12.dp

        val lightBackdropTint = Color(0xFFF8F8FA).copy(alpha = 0.30f)

        val darkBackdropTint = Color(0xFF202024).copy(alpha = 0.22f)


        // ---------- 底部渐变 ----------

        val lightBottomOverlay = Color(0xFFD1D1D6).copy(alpha = 0.20f)

        val darkBottomOverlay = Color(0xFFB8B8C2).copy(alpha = 0.13f)


        // ---------- 顶部反光 ----------

        const val LIGHTHIGHLIGHTALPHA = 0.16f

        const val DARKHIGHLIGHTALPHA = 0.018f

        const val HIGHLIGHTMIDDLEPOSITION = 0.12f

        const val HIGHLIGHTENDPOSITION = 0.30f

        const val HIGHLIGHTGRADIENTEND = 0.38f

        const val HIGHLIGHTLINEALPHA = 0.12f

        const val HIGHLIGHTLINEMIDDLEALPHA = 0.06f

        val highlightLineHeight = 1.5.dp

        val highlightLineWidth = 2.dp


        // ---------- 选中胶囊 ----------

        val pillBlur = 1.dp

        val pillRefractionHeight = 8.dp

        val pillRefractionAmount = 12.dp

        val lightPillTint = Color.White.copy(alpha = 0.58f)

        val darkPillTint = Color.White.copy(alpha = 0.10f)

        val lightPillHighlight = Color.White.copy(alpha = 0.18f)

        val darkPillHighlight = Color.White.copy(alpha = 0.08f)

        val pillCornerRadius = 28.dp

        val pillHeight = 56.dp

        val pillOffsetY = 4.dp
    }
}