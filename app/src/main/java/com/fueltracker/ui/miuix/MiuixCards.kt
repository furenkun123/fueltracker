package com.fueltracker.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.theme.BackgroundPreferences
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
private fun getCardColor(): Color {
    val hasCustomBg = BackgroundPreferences.backgroundUri != null
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f

    return when {
        hasCustomBg -> {
            MiuixTheme.colorScheme.surface.copy(alpha = 0.78f)
        }

        isLight -> {
            Color(0xFFFAFAFC)
        }

        else -> {
            MiuixTheme.colorScheme.surface
        }
    }
}

@Composable
fun MiuixCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    // ✅ 核心修改：加入微弱阴影，在纯白背景下拉开层次
    Box(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(getCardColor())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = CardDefaults.defaultColors(color = Color.Transparent), // Card本体透明，背景由外层Box提供
            content = content
        )
    }
}

@Composable
fun MiuixCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 18.dp,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(getCardColor())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = CardDefaults.defaultColors(color = Color.Transparent),
            pressFeedbackType = if (enabled) PressFeedbackType.Sink else PressFeedbackType.None,
            showIndication = enabled,
            onClick = if (enabled) onClick else null,
            content = content
        )
    }
}

@Composable
fun MiuixSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
    )
}

@Composable
fun MiuixGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    MiuixCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp)
    ) {
        content()
    }
}

@Composable
fun MiuixFormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    MiuixCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(content = content)
    }
}