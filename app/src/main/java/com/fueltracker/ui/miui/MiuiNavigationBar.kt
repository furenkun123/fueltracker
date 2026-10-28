package com.fueltracker.ui.miui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
fun MiuiNavigationBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    isGlassEnabled: Boolean,
    isFloating: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    // 悬浮是大圆角，固定是直角
    val capsuleShape = if (isFloating) RoundedCornerShape(32.dp) else RoundedCornerShape(0.dp)
    val useGlass = isGlassEnabled && isFloating

    // 👇 新增：动态底色（浅色模式用微白，深色模式用深灰，这样才能压住纯黑背景）
    // 👇 动态底色
    val bgColor = if (useGlass) {
        if (isLight) {
            Color.White.copy(alpha = 0.15f) // 浅色模式
        } else {
            Color(0xFF1C1C1E).copy(alpha = 0.65f) // 深色模式：给一层 65% 的深灰底色
        }
    } else {
        // 👇 改为 surface，底栏就会和纯白（或纯黑）的卡片一样干净
        MaterialTheme.colorScheme.surface
    }

    val hazeModifier = if (useGlass) {
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                blurRadius(30.dp)
                noiseFactor(0.1f)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            // 👇 暗色下用纯黑提色，浓度提高
                            if (isLight) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.4f)
                        )
                    )
                )
            }
        )
    } else Modifier

    val borderStroke = if (useGlass) {
        BorderStroke(
            width = 0.5.dp,
            // 暗色下边框稍微亮一点点，勾勒轮廓
            color = if (isLight) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
        )
    } else {
        BorderStroke(1.dp, if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.1f))
    }

    // 阴影：固定模式下不需要四周阴影，只需要顶部的一点点投影
    val shadowModifier = if (isFloating) {
        Modifier.shadow(
            elevation = if (useGlass) 16.dp else 24.dp,
            shape = capsuleShape,
            ambientColor = if (isLight) Color.Black.copy(alpha = if (useGlass) 0.08f else 0.15f) else Color.Black.copy(alpha = 0.5f),
            spotColor = if (isLight) Color.Black.copy(alpha = if (useGlass) 0.12f else 0.18f) else Color.Black.copy(alpha = 0.6f),
            clip = false
        )
    } else {
        Modifier.shadow(6.dp) // 固定底栏给一点顶部阴影
    }

//    val borderStroke = if (useGlass) {
//        // 玻璃模式：微弱的高光白边
//        BorderStroke(0.5.dp, if (isLight) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f))
//    } else {
//        // 实心模式：加一条灰色边框，即使底栏和背景都是纯白，也能立刻分辨出来
//        BorderStroke(
//            width = 1.dp,
//            color = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.1f)
//        )
//    }

    // 核心调整：动态设置外部 Box 的宽高和 padding
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFloating) {
                    Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)
                } else {
                    Modifier // 固定模式外部不需要 padding
                }
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // 👇 1. 移除死高度，让高度由内部内容撑开
                .then(shadowModifier)
                .clip(capsuleShape)
                .then(hazeModifier),
            shape = capsuleShape,
            color = bgColor,
            border = borderStroke,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 👇 2. 核心修改：悬浮模式高度 64dp；固定模式：安全区留白 + 64dp 的内容高度
                    .then(
                        if (isFloating) {
                            Modifier.height(64.dp)
                        } else {
                            Modifier.navigationBarsPadding().height(64.dp)
                        }
                    ),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun RowScope.MiuiNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val activeColor = if (selected) MiuiColors.Primary else MiuiColors.SecondaryText

    // 选中状态背景
    val selectedBg = if (selected) {
        if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color.Black.copy(alpha = 0.05f)
        else Color.White.copy(alpha = 0.1f)
    } else Color.Transparent

    Column(
        modifier = Modifier
            .weight(1f) // 均分宽度
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(selectedBg)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = activeColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = activeColor
            )
        }
    }
}