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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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

        // ✅ 1. 标题永远固定在外部（包含红星和右侧按钮）
        if (label.isNotEmpty() || isRequired || labelTrailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixFieldLabel(
                    text = label,
                    isRequired = isRequired
                )
                labelTrailing?.invoke()
            }
        }

        // 2. 将 suffix 和 trailingIcon 合并处理
        val resolvedTrailingIcon: (@Composable () -> Unit)? = if (suffix != null || trailingIcon != null) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 12.dp) // 统一右侧留白
                ) {
                    suffix?.let {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                    trailingIcon?.invoke()
                }
            }
        } else null

        // 3. 纯输入框
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
            textStyle = TextStyle( // ✅ 应用颜色
                color = textColor ?: MiuixTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 4. 底部提示
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MiuixTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        } else if (supportingText != null) {
            Text(
                text = supportingText,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
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
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixFieldLabel(
            text = label,
            modifier = Modifier.width(110.dp)
        )
        // 使用透明的 BasicTextField，让输入内容直接融入卡片，不再绘制独立灰色输入框背景。
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
                .height(52.dp)
                .padding(horizontal = 12.dp),
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
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

// ==========================================
// 3. 点击选择行 (外观与 TextField 完全一致，但不响应输入)
// ==========================================
// ==========================================
// 3. 点击选择行 (左对齐，紧挨标签)
// ==========================================
@Composable
fun MiuixClickableItem(
    label: String,
    value: String,
    placeholder: String = "请选择",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }, // 整个 Row 响应点击
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixFieldLabel(
            text = label,
            modifier = Modifier.width(110.dp)
        )

        // 右侧值（占据剩余空间，靠左显示）
        Text(
            text = value.ifEmpty { placeholder },
            fontSize = 15.sp,
            textAlign = TextAlign.Start, // ✅ 靠左对齐（紧挨着左侧标签）
            // ✅ 未选择用次要色（灰），已选择用主题色（亮）
            color = if (value.isEmpty()) {
                MiuixTheme.colorScheme.onSurfaceSecondary
            } else {
                MiuixTheme.colorScheme.primary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f) // ✅ 占据右侧空间，内容靠左
        )
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
                // ✅ 改为 fillMaxSize，保证点输入框的任何地方（哪怕没字）都能唤起键盘
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
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
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
