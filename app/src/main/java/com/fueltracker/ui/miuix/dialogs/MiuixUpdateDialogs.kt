package com.fueltracker.ui.miuix.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fueltracker.util.UpdateLogManager
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme


// ============================================================
// 更新日志弹窗（启动时）
// ============================================================
@Composable
fun MiuixUpdateLogDialog(
    entries: List<UpdateLogManager.UpdateLogEntry>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MiuixTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "更新内容",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(entries, key = { it.versionCode }) { entry ->
                        UpdateLogEntryItem(entry)
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(
                    text = "我知道了",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    cornerRadius = 24.dp,
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.primary,
                        textColor = MiuixTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

// ============================================================
// 设置页里的历史更新弹窗
// ============================================================
@Composable
fun MiuixUpdateHistoryDialog(onDismiss: () -> Unit) {
    val allLogs = remember { UpdateLogManager.getAllUpdateLogs() }
    MiuixScrollableSettingsDialog(title = "更新历史", buttonText = "关闭", onDismiss = onDismiss, spacedContent = true) {
        allLogs.forEach { entry ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                UpdateLogEntryItem(entry)
            }
        }
    }
}

// ============================================================
// 内部共用的单个版本记录 UI
// ============================================================
@Composable
private fun UpdateLogEntryItem(entry: UpdateLogManager.UpdateLogEntry) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "v${entry.versionName}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        entry.items.forEach { line ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.onSurfaceSecondary)
                )
                Text(
                    text = line,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    lineHeight = 21.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
