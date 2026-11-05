package com.fueltracker.ui.miui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃效果统一参数。
 *
 * Card、Dialog/BottomSheet 与 Navigation 分别维护材质参数，
 * 让相同光线方向下的不同尺寸组件保持一致观感。
 */
object GlassDefaults {

    /**
     * 页面内容卡片共用材质。
     * 卡片只采样应用背景，因此可以用更明显的反光和边缘体现玻璃厚度。
     */
    object Card {

        val lightTint = Color(0xFFF8FBFF)
        val darkTint = Color(0xFF091321)

        const val LIGHT_TINT_ALPHA = 0.20f
        const val DARK_TINT_ALPHA = 0.28f

        const val LIGHT_TOP_HIGHLIGHT_ALPHA = 0.20f
        const val DARK_TOP_HIGHLIGHT_ALPHA = 0.08f

        const val LIGHT_BOTTOM_SHADE_ALPHA = 0.07f
        const val DARK_BOTTOM_SHADE_ALPHA = 0.16f

        const val LIGHT_EDGE_ALPHA = 0.62f
        const val DARK_EDGE_ALPHA = 0.20f

        const val EDGE_WIDTH = 0.75f
    }

    /**
     * Dialog 与 BottomSheet 位于独立 Window，无法跨窗口采样 Backdrop。
     * 这组参数提供可读性更稳定的静态玻璃表面。
     */
    object Dialog {

        const val LIGHT_BASE_ALPHA = 0.88f
        const val DARK_BASE_ALPHA = 0.94f

        const val LIGHT_TOP_HIGHLIGHT_ALPHA = 0.18f
        const val DARK_TOP_HIGHLIGHT_ALPHA = 0.06f

        const val LIGHT_EDGE_ALPHA = 0.72f
        const val DARK_EDGE_ALPHA = 0.20f

        const val EDGE_WIDTH = 0.8f
    }

    object Navigation {

        // ---------- 导航栏背景玻璃 ----------

        val backdropBlur = 2.dp

        val backdropRefractionHeight = 6.dp

        val backdropRefractionAmount = 12.dp

        val lightBackdropTint = Color(0xFFF8FBFF).copy(alpha = 0.46f)

        val darkBackdropTint = Color(0xFF202024).copy(alpha = 0.22f)


        // ---------- 底部渐变 ----------

        val lightBottomOverlay = Color(0xFFB9C5D5).copy(alpha = 0.30f)

        val darkBottomOverlay = Color(0xFFB8B8C2).copy(alpha = 0.13f)


        // ---------- 顶部反光 ----------

        const val LIGHTHIGHLIGHTALPHA = 0.20f

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
