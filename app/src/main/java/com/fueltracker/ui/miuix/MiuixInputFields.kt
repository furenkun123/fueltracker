package com.fueltracker.ui.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ==========================================
// 1. 表单主输入框 (支持 labelTrailing, supportingText)
// ==========================================
@Composable
fun MiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    isRequired: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    labelTrailing: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    horizontalPadding: Dp = 0.dp,
    textColor: Color? = null
) {
    Column(modifier = modifier.padding(horizontal = horizontalPadding)) {
        if (label.isNotEmpty() || isRequired || labelTrailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixFieldLabel(text = label, isRequired = isRequired)
                labelTrailing?.invoke()
            }
        }

        val resolvedTrailingIcon: (@Composable () -> Unit)? = if (suffix != null || trailingIcon != null) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    suffix?.let {
                        Text(text = it, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                    trailingIcon?.invoke()
                }
            }
        } else null

        TextField(
            value = value,
            onValueChange = onValueChange,
            label = placeholder,
            useLabelAsPlaceholder = true,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            trailingIcon = resolvedTrailingIcon,
            textStyle = TextStyle(color = textColor ?: MiuixTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth()
        )

        if (isError && errorMessage != null) {
            Text(text = errorMessage, color = MiuixTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        } else if (supportingText != null) {
            Text(text = supportingText, color = MiuixTheme.colorScheme.onSurfaceSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

// ==========================================
// 2. 列表行输入框
// ==========================================
@Composable
fun MiuixInputItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp), // 👈 统一高度 56dp
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixFieldLabel(
            text = label,
            modifier = Modifier.width(110.dp) // 👈 统一固定宽度 110dp
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .padding(start = 12.dp, end = 16.dp), // 👈 保留间距，并防止内容超出屏幕
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis // 👈 防占位符太长挤爆
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

// ==========================================
// 3. 点击选择行 (支持右侧箭头和自定义尾部)
// ==========================================
@Composable
fun MiuixClickableItem(
    label: String,
    value: String,
    placeholder: String = "请选择",
    showArrow: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixFieldLabel(
            text = label,
            modifier = Modifier.width(110.dp) // 👈 统一固定宽度 110dp
        )

        // 右侧值
        Text(
            text = value.ifEmpty { placeholder },
            fontSize = 15.sp,
            textAlign = TextAlign.Start,
            // 👈 恢复统一灰色，不再用亮蓝色
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )

        // 右侧尾部图标（固定 24dp，防止被挤压）
        Box(
            modifier = Modifier.size(24.dp).padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (trailingContent != null) {
                trailingContent()
            } else if (showArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==========================================
// 4. 轻量级搜索框 (基于 BasicTextField)
// ==========================================
@Composable
fun MiuixSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholderMaxLines: Int = 1,
    placeholderEllipsis: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = placeholderMaxLines,
                        overflow = if (placeholderEllipsis) TextOverflow.Ellipsis else TextOverflow.Clip
                    )
                }
                innerTextField()
            }
        }
    )
}

// ==========================================
// 5. 统一样式左侧标签
// ==========================================
@Composable
fun MiuixFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,        // 👈 绝对不折行
            softWrap = false,    // 👈 绝对不换行
            overflow = TextOverflow.Ellipsis // 👈 屏幕太窄变省略号
        )

        if (isRequired) {
            Text(
                text = " *",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.error
            )
        }
    }
}