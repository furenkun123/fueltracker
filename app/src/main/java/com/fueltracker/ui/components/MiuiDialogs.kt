package com.fueltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fueltracker.ui.miui.glass.GlassPreferences
import com.fueltracker.util.UpdateLogManager


// ============================================================
// 公共玻璃弹窗容器
// 与 MiuiCard 使用同一套渐变和描边参数。
// Dialog/BottomSheet 属于独立 Window，不跨窗口采样 Backdrop，
// 因此使用与卡片表面一致的静态材质，避免额外 RenderNode 采集。
// ============================================================

private fun glassCardSurfaceBrush(
    isLight: Boolean,
    concentrationScale: Float
): Brush =
    if (isLight) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.15f * concentrationScale),
                0.38f to Color(0xFFE7EFFB).copy(alpha = 0.09f * concentrationScale),
                0.78f to Color(0xFFDCE8F8).copy(alpha = 0.12f * concentrationScale),
                1.00f to Color(0xFFB9CBE3).copy(alpha = 0.17f * concentrationScale)
            )
        )
    } else {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF52627A).copy(alpha = 0.34f * concentrationScale),
                0.30f to Color(0xFF34445B).copy(alpha = 0.30f * concentrationScale),
                0.72f to Color(0xFF202D40).copy(alpha = 0.34f * concentrationScale),
                1.00f to Color(0xFF101A2A).copy(alpha = 0.48f * concentrationScale)
            )
        )
    }

private fun glassCardOutlineColor(
    isLight: Boolean,
    concentrationScale: Float
): Color =
    if (isLight) {
        Color(0xFF475569).copy(alpha = 0.16f * concentrationScale)
    } else {
        Color(0xFF070D16).copy(alpha = 0.38f * concentrationScale)
    }

@Composable
fun GlassDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // 65% 保持当前弹窗材质；浓度只控制表面渐变与描边强度。
    val concentrationScale =
        (GlassPreferences.params.concentration / 65f).coerceIn(0f, 1.8f)
    val surfaceColor = if (isLight) {
        Color(0xFFF7FAFF).copy(alpha = 0.88f)
    } else {
        Color(0xFF182334).copy(alpha = 0.92f)
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = surfaceColor,
        border = BorderStroke(0.2.dp, glassCardOutlineColor(isLight, concentrationScale)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(glassCardSurfaceBrush(isLight, concentrationScale)),
            content = content
        )
    }
}

/**
 * 底部弹出层使用与内容卡片一致的表面渐变和描边。
 * 不跨 Dialog/Window 采样 Backdrop，避免额外 RenderNode 采集。
 */
@Composable
fun GlassBottomSheetSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val concentrationScale =
        (GlassPreferences.params.concentration / 65f).coerceIn(0f, 1.8f)
    val edgeColor = if (isLight) {
        Color.White.copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.20f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.8.dp, edgeColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = if (isLight) {
                            listOf(
                                Color.White.copy(alpha = 0.46f),
                                Color(0xFFDCE9FA).copy(alpha = 0.22f),
                                Color(0xFFB9CBE3).copy(alpha = 0.34f)
                            )
                        } else {
                            listOf(
                                Color(0xFF7186A5).copy(alpha = 0.24f),
                                Color(0xFF263A55).copy(alpha = 0.20f),
                                Color(0xFF0D192B).copy(alpha = 0.42f)
                            )
                        }
                    )
                ),
            content = content
        )
    }
}

// ============================================================
// 公共弹窗外壳
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollableSettingsDialog(
    title: String,
    buttonText: String,
    onDismiss: () -> Unit,
    centeredTitle: Boolean = false,
    spacedContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.16f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null
    ) {
        GlassBottomSheetSurface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 28.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(40.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                    )
                }
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = if (spacedContent) Arrangement.spacedBy(10.dp) else Arrangement.Top,
                    content = content
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("取消", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        )
                    ) {
                        Text(buttonText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ============================================================
// 隐私声明
// ============================================================

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    ScrollableSettingsDialog(title = "隐私声明", buttonText = "我已了解", onDismiss = onDismiss, centeredTitle = true) {
        PrivacySection(
            title = "数据本地化存储",
            body = "本应用为纯本地工具。您的车辆信息、加油记录、费用记录以及停车位置，均仅保存在您手机的本地 SQLite 数据库中。我们不运营任何服务器，不会主动将您的任何记账数据上传至云端。"
        )
        PrivacySection(
            title = "信息收集与使用",
            body = "本应用不收集您的通讯录、短信、通话记录、相册等个人敏感身份信息。应用仅会在您主动记录时，获取您填写的车辆配置、金额、里程等必要记账数据。"
        )
        PrivacySection(
            title = "位置权限说明",
            body = "当您使用“记停车”功能并点击“获取当前位置”时，应用会向系统请求定位权限。获取的经纬度和地址文本，仅用于自动填写停车位置输入框。当您保存该条停车记录后，位置文本将保存在本地数据库中，绝不会上传。"
        )
        PrivacySection(
            title = "网络访问与第三方",
            body = "本应用主体不包含联网上报代码。仅在您主动点击“查看更新”或“前往小熊网页导出”时，应用会调用您手机自带的系统浏览器打开对应的外部网页。该过程不涉及本应用向外部回传任何本地数据。"
        )
        PrivacySection(
            title = "数据备份与安全",
            body = "备份文件由您主动导出并保存在手机本地。请妥善保管您的备份文件。卸载本应用会直接清除应用内的所有本地数据（含未导出的历史记录），建议您定期导出备份。同时，本应用已关闭 Android 系统的云自动备份功能，防止您的敏感数据被自动同步至云端。"
        )
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
    }
}

// ============================================================
// 应用说明
// ============================================================

@Composable
fun InstructionsDialog(onDismiss: () -> Unit) {
    val instructions = remember {
        listOf(
            "【基础记账】添加车辆后，每次加油记一笔。填写当前里程、加油量和金额，系统会自动生成记录",
            "【精准油耗】连续两次勾选“已加满”，即可计算出准确的百公里油耗。若期间有未加满，系统会智能折算，并在列表中标注“预估”",
            "【漏记补录】忘记记某次加油？下次记录时勾选“上次漏记”，填入大概里程和油量，系统会自动还原真实油耗",
            "【智慧停车】记停车可自动获取当前位置并保存车位号。首页会常驻提醒，取车后点击“已取车”即可结束",
            "【费用管理】费用页可记录高速、保养、洗车等支出，支持自定义分类。点击费用结构图表，可快速按分类筛选明细",
            "【数据迁移】如需从小熊油耗迁移数据：请先在小熊网页版导出 CSV 文件，然后在“设置 -> 备份与恢复 -> 小熊油耗数据”中导入（系统会自动去重）",
            "【记录管理】在“全部记录”或“费用明细”页，点击任意记录右侧的“编辑/删除”即可修改或移除数据",
            "【数据安全】所有数据100%保存在手机本地。强烈建议定期在“设置”中导出 JSON 备份，以免换机或卸载时丢失数据"
        )
    }
    ScrollableSettingsDialog(title = "应用说明", buttonText = "关闭", onDismiss = onDismiss, spacedContent = true) {
        instructions.forEachIndexed { index, text -> MiuiInstructionItem(index + 1, text) }
    }
}

@Composable
private fun MiuiInstructionItem(index: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("$index", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 21.sp, modifier = Modifier.weight(1f))
    }
}


// ============================================================
// 更新日志弹窗（启动时）
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateLogDialog(
    entries: List<UpdateLogManager.UpdateLogEntry>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.16f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null
    ) {
        GlassBottomSheetSurface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 28.dp)
            ) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                    )
                }
                Text(
                    text = "更新内容",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
                Spacer(Modifier.height(20.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    )
                ) {
                    Text("我知道了", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ============================================================
// 设置页里的历史更新弹窗
// ============================================================
@Composable
fun UpdateHistoryDialog(onDismiss: () -> Unit) {
    val allLogs = remember { UpdateLogManager.getAllUpdateLogs() }
    ScrollableSettingsDialog(title = "更新历史", buttonText = "关闭", onDismiss = onDismiss, spacedContent = true) {
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
            color = MaterialTheme.colorScheme.primary
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
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = line,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 21.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    confirmColor: Color? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val effectiveConfirmColor = confirmColor ?: MaterialTheme.colorScheme.error
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var actionTriggered by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.16f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null
    ) {
        GlassBottomSheetSurface {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(dismissText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(
                    onClick = {
                        if (actionTriggered) return@TextButton
                        actionTriggered = true
                        onConfirm()
                    },
                    enabled = !actionTriggered,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = effectiveConfirmColor.copy(alpha = 0.12f))
                ) {
                    Text(confirmText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = effectiveConfirmColor)
                }
            }
        }
        }
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiYearMonthSheet(
    initialYear: Int,
    initialMonth: Int,
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.16f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null
    ) {
        GlassBottomSheetSurface {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)))
                }
                Text("选择年月", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WheelList(selectedValue = year, items = availableYears, visibleCount = 3, modifier = Modifier.weight(1.4f), style = WheelStyle.WithBackground, itemLabel = { "${it}年" }, onChange = { year = it })
                    WheelList(selectedValue = month, items = (0..12).toList(), visibleCount = 3, modifier = Modifier.weight(1f), style = WheelStyle.WithBackground, itemLabel = { if (it == 0) "全年" else String.format("%02d 月", it) }, onChange = { month = it })
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { onConfirm(year, month) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))) {
                        Text("确定", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiBottomSheetWrapper(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.16f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null
    ) {
        GlassBottomSheetSurface {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)))
                }
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                content()
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) { Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    ) { Text(confirmText, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
