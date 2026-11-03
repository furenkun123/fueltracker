package com.fueltracker.ui.miui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 全应用液态玻璃参数。所有组件共享同一套参数，便于统一调节。 */
object GlassDefaults {
    object Surface {
        val cornerRadius = 18.dp
        val blurRadius = 5.dp
        val refractionHeight = 12.dp
        val refractionAmount = 18.dp
        val borderWidth = 0.8.dp
        val lightTint = Color(0xFFF8FAFF).copy(alpha = 0.30f)
        val darkTint = Color(0xFF252A35).copy(alpha = 0.30f)
    }

    object Card {
        val cornerRadius = 18.dp
        val lightTint = Color(0xFFF8FAFF).copy(alpha = 0.25f)
        val darkTint = Color(0xFF252A35).copy(alpha = 0.24f)
    }

    object Button {
        val cornerRadius = 25.dp
        val blurRadius = 3.dp
        val refractionHeight = 8.dp
        val refractionAmount = 12.dp
        val lightTint = Color(0xFF1683F8).copy(alpha = 0.82f)
        val darkTint = Color(0xFF1677E8).copy(alpha = 0.72f)
    }

    object Input {
        val cornerRadius = 14.dp
        val lightTint = Color.White.copy(alpha = 0.24f)
        val darkTint = Color.White.copy(alpha = 0.07f)
    }

    object Chip {
        val cornerRadius = 10.dp
        val selectedLightTint = Color(0xFF1683F8).copy(alpha = 0.78f)
        val selectedDarkTint = Color(0xFF1677E8).copy(alpha = 0.70f)
    }

    object Dialog {
        val cornerRadius = 24.dp
        val blurRadius = 8.dp
        val lightTint = Color(0xFFF8FAFF).copy(alpha = 0.76f)
        val darkTint = Color(0xFF20232B).copy(alpha = 0.82f)
    }

    object Navigation {
        val backdropBlur = 5.dp
        val backdropRefractionHeight = 18.dp
        val backdropRefractionAmount = 38.dp
        val lightBackdropTint = Color(0xFFF8F8FA).copy(alpha = 0.16f)
        val darkBackdropTint = Color(0xFF202024).copy(alpha = 0.18f)
        val lightBottomOverlay = Color(0xFFD1D1D6).copy(alpha = 0.20f)
        val darkBottomOverlay = Color(0xFFB8B8C2).copy(alpha = 0.13f)
        const val LIGHTHIGHLIGHTALPHA = 0.32f
        const val DARKHIGHLIGHTALPHA = 0.12f
        const val HIGHLIGHTMIDDLEPOSITION = 0.12f
        const val HIGHLIGHTENDPOSITION = 0.30f
        const val HIGHLIGHTGRADIENTEND = 0.38f
        const val HIGHLIGHTLINEALPHA = 0.12f
        const val HIGHLIGHTLINEMIDDLEALPHA = 0.06f
        val highlightLineHeight = 1.5.dp
        val highlightLineWidth = 2.dp
        val pillBlur = 3.dp
        val pillRefractionHeight = 12.dp
        val pillRefractionAmount = 28.dp
        val lightPillTint = Color.White.copy(alpha = 0.24f)
        val darkPillTint = Color.White.copy(alpha = 0.16f)
        val lightPillHighlight = Color.White.copy(alpha = 0.18f)
        val darkPillHighlight = Color.White.copy(alpha = 0.08f)
        val pillCornerRadius = 28.dp
        val pillHeight = 56.dp
        val pillOffsetY = 4.dp
    }
}
