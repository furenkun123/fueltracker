
package com.fueltracker.ui.miui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiuiTopBar(
    modifier: Modifier = Modifier,
    title: String = "随记油耗",
    isGlassEnabled: Boolean = false,
    navigationIcon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val statusBarHeight = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val topBarBackground = when {
        !isGlassEnabled -> MaterialTheme.colorScheme.background
        isLight -> Color.White.copy(alpha = 0.88f)
        else -> Color(0xFF18181A).copy(alpha = 0.88f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(topBarBackground)
    ) {
        // 背景覆盖状态栏区域，标题不与系统图标重叠
        Spacer(modifier = Modifier.height(statusBarHeight))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            action?.invoke()
        }
    }
    }
