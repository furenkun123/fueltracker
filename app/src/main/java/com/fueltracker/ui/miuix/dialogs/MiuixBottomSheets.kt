package com.fueltracker.ui.miuix.dialogs

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet


@Composable
fun MiuixConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    confirmColor: Color? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val effectiveConfirmColor = confirmColor ?: MiuixTheme.colorScheme.error
    var actionTriggered by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        backgroundColor = MiuixTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(message, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    cornerRadius = 24.dp,
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.surfaceVariant,
                        textColor = MiuixTheme.colorScheme.onSurface
                    )
                )
                TextButton(
                    text = confirmText,
                    onClick = {
                        if (actionTriggered) return@TextButton
                        actionTriggered = true
                        onConfirm()
                    },
                    enabled = !actionTriggered,
                    modifier = Modifier.weight(1f).height(48.dp),
                    cornerRadius = 24.dp,
                    colors = ButtonDefaults.textButtonColors(
                        color = effectiveConfirmColor.copy(alpha = 0.12f),
                        textColor = effectiveConfirmColor
                    )
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun MiuixYearMonthSheet(initialYear: Int, initialMonth: Int, availableYears: List<Int>, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    WindowBottomSheet(
        show = true,
        title = "选择年月",
        startAction = {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    color = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            )
        },
        endAction = {
            TextButton(
                text = "确定",
                onClick = { onConfirm(year, month) },
                colors = ButtonDefaults.textButtonColors(
                    color = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.primary
                )
            )
        },
        onDismissRequest = onDismiss,
        backgroundColor = MiuixTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelList(selectedValue = year, items = availableYears, visibleCount = 3, modifier = Modifier.weight(1.4f), style = WheelStyle.WithBackground, itemLabel = { "${it}年" }, onChange = { year = it })
                WheelList(selectedValue = month, items = (0..12).toList(), visibleCount = 3, modifier = Modifier.weight(1f), style = WheelStyle.WithBackground, itemLabel = { if (it == 0) "全年" else String.format("%02d 月", it) }, onChange = { month = it })
            }
        }
    }
}
@Composable
fun MiuixBottomSheetWrapper(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
    content: @Composable () -> Unit
) {
    WindowBottomSheet(
        show = true,
        title = title,
        startAction = {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    color = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            )
        },
        endAction = {
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    color = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.primary
                )
            )
        },
        onDismissRequest = onDismiss,
        backgroundColor = MiuixTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            content()
        }
    }
}
