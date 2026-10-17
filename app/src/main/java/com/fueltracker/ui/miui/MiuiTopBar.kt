package com.fueltracker.ui.miui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiuiTopBar(
    modifier: Modifier = Modifier,
    title: String = "随记油耗",
    navigationIcon: (@Composable () -> Unit)? = null, // 注意这里的 @Composable 声明
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MiuiColors.Background) // 背景防穿透
            .statusBarsPadding()               // 撑开状态栏高度
            .height(48.dp)                     // 紧凑高度
            .padding(horizontal = 12.dp),      // 左右边距
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧返回/导航图标
        if (navigationIcon != null) {
            navigationIcon()
            Spacer(modifier = Modifier.width(4.dp))
        }

        // 标题
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = MiuiColors.Text,
            modifier = Modifier.weight(1f)
        )

        // 右侧操作按钮
        action?.invoke()
    }
}