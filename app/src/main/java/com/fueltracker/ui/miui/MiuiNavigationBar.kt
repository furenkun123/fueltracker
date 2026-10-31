
package com.fueltracker.ui.miui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy


@Composable
fun MiuiNavigationBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    isGlassEnabled: Boolean,
    isFloating: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val capsuleShape = if (isFloating) {
        RoundedCornerShape(32.dp)
    } else {
        RoundedCornerShape(0.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFloating) {
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                } else {
                    Modifier
                }
            )
    ) {
        val shadowModifier = if (isFloating) {
            if (isGlassEnabled) {
                Modifier.shadow(
                    elevation = 8.dp,
                    shape = capsuleShape,
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                    clip = false
                )
            } else {
                Modifier.shadow(
                    elevation = 16.dp,
                    shape = capsuleShape,
                    ambientColor = if (isLight) {
                        Color.Black.copy(alpha = 0.08f)
                    } else {
                        Color.Black.copy(alpha = 0.4f)
                    },
                    spotColor = if (isLight) {
                        Color.Black.copy(alpha = 0.12f)
                    } else {
                        Color.Black.copy(alpha = 0.6f)
                    },
                    clip = false
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(shadowModifier)
                .clip(capsuleShape)
        ) {
            NavigationBarSurface(
                backdrop = backdrop,
                shape = capsuleShape,
                isFloating = isFloating,
                isLight = isLight,
                isGlassEnabled = isGlassEnabled,
                content = content
            )
        }
    }
}

@Composable
private fun NavigationBarSurface(
    backdrop: Backdrop,
    shape: RoundedCornerShape,
    isFloating: Boolean,
    isLight: Boolean,
    isGlassEnabled: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    // 玻璃雾面：保留背景颜色层次，但避免背景清晰透出

    val solidColor = if (isLight) {
        Color.White
    } else {
        Color(0xFF1C1C1E)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isFloating && isGlassEnabled) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一层：带透镜折射的玻璃背景
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .drawPlainBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()

                                blur(2.dp.toPx())

                                // 真正的背景透镜折射
                                lens(
                                    refractionHeight = 28.dp.toPx(),
                                    refractionAmount = 40.dp.toPx()
                                )
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (isLight) {
                                        Color.White.copy(alpha = 0.10f)
                                    } else {
                                        Color(0xFF25252B).copy(alpha = 0.14f)
                                    }
                                )
                            }
                        )
                )


                // 第三层：柔和轮廓
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 0.7.dp,
                            color = if (isLight) {
                                Color.Black.copy(alpha = 0.07f)
                            } else {
                                Color.White.copy(alpha = 0.09f)
                            },
                            shape = shape
                        )
                )
                // 中央水滴透镜：单独测试中心折射效果
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .drawPlainBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(32.dp) },
                            effects = {
                                vibrancy()
                                blur(1.dp.toPx())
                                lens(
                                    refractionHeight = 30.dp.toPx(),
                                    refractionAmount = 49.dp.toPx()
                                )
                            },
                            onDrawSurface = {
                                // 不额外绘制明显的白色表面
                                drawRect(Color.Transparent)
                            }
                        )
                )

                // 第四层：导航内容，必须最后绘制
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(solidColor)
            )
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFloating) {
                        Modifier.height(64.dp)
                    } else {
                        Modifier
                            .navigationBarsPadding()
                            .height(64.dp)
                    }
                ),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun RowScope.MiuiNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val activeColor = if (selected) {
        MiuiColors.Primary
    } else {
        MiuiColors.SecondaryText
    }

    val selectedBg = if (selected) {
        if (isLight) {
            Color.Black.copy(alpha = 0.045f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
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
                fontWeight = if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
                color = activeColor
            )
        }
    }
}
