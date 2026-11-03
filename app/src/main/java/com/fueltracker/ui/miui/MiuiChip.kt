package com.fueltracker.ui.miui

import com.fueltracker.ui.miui.glass.liquidGlass
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiuiChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    backgroundColor: Color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    },
    contentColor: Color = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    },
    cornerRadius: Dp = 12.dp,
    paddingH: Dp = 16.dp,
    paddingV: Dp = 10.dp,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .liquidGlass(shape = RoundedCornerShape(cornerRadius), tint = backgroundColor.copy(alpha = 0.72f))
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = contentColor,
            maxLines = 1
        )
    }
}