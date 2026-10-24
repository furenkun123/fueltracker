package com.fueltracker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.fueltracker.BuildConfig
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.theme.ThemeMode
import com.fueltracker.ui.theme.ThemePreferences
import com.fueltracker.util.BackupManager
import com.fueltracker.util.UpdateLogManager
import com.fueltracker.util.XiaoxiongCsvImporter
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// ============================================================
// 动画规格 & 常量
// ============================================================

private const val ExpandDurationMs = 380
private val ScrollDelay: Duration = (ExpandDurationMs + 40).milliseconds

private val ExpandSpec = expandVertically(tween(durationMillis = ExpandDurationMs, easing = FastOutSlowInEasing))
private val ShrinkSpec = shrinkVertically(tween(durationMillis = 300, easing = FastOutSlowInEasing))
private val FadeInSpec = fadeIn(tween(durationMillis = 260, delayMillis = 80))
private val FadeOutSpec = fadeOut(tween(durationMillis = 180))

private const val KeyDataSection = "section_data"
private const val KeyThemeSection = "section_theme"
private const val KeyAboutSection = "section_about"

// ============================================================
// 主页面
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    vehicles: List<Vehicle>,
    onSetCurrentVehicle: (Vehicle) -> Unit,
    onDeleteVehicle: (Vehicle) -> Unit,
    onAddVehicle: () -> Unit,
    onEditVehicle: (Vehicle) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var vehicleToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRestoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    var isDataExpanded by rememberSaveable { mutableStateOf(false) }
    var isThemeExpanded by rememberSaveable { mutableStateOf(false) }
    var isAboutExpanded by rememberSaveable { mutableStateOf(false) }
    var isXiaoxiongExpanded by rememberSaveable { mutableStateOf(false) }

    var currentThemeMode by remember { mutableStateOf(ThemePreferences.mode) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showUpdateHistory by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }

    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(isDataExpanded) {
        if (!isDataExpanded) isXiaoxiongExpanded = false
    }

    // --- 各种 Launcher 定义 ---
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.createBackup(uri) { result ->
            result.fold(
                onSuccess = { Toast.makeText(context, "备份导出成功", Toast.LENGTH_SHORT).show() },
                onFailure = { Toast.makeText(context, "导出失败: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.checkIsJsonFile(uri) { isJson ->
            if (isJson) selectedRestoreUri = uri
            else Toast.makeText(context, "请选择 JSON 格式的备份文件", Toast.LENGTH_SHORT).show()
        }
    }

    val fuelImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        launchCsvImport(context, viewModel, uri, "加油",
            parser = { ctx, fileUri, id -> XiaoxiongCsvImporter.parseFuelCsv(ctx, fileUri, id) },
            saveRecords = { records, cb -> viewModel.importFuelRecords(records, cb) }
        )
    }

    val expenseImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        launchCsvImport(context, viewModel, uri, "费用",
            parser = { ctx, fileUri, id -> XiaoxiongCsvImporter.parseExpenseCsv(ctx, fileUri, id) },
            saveRecords = { records, cb -> viewModel.importExpenseRecords(records, cb) }
        )
    }

    // --- 界面结构 ---
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("设置与数据", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. 车辆管理
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("车辆管理", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onAddVehicle, enabled = !viewModel.isBackupOperationRunning) {
                            Text("＋ 添加车辆", fontSize = 14.sp)
                        }
                    }
                    if (vehicles.isEmpty()) {
                        MiuiCardLocal {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("暂无车辆信息，请添加车辆", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            items(vehicles, key = { it.id }) { vehicle ->
                VehicleItemCard(
                    vehicle = vehicle,
                    onSelect = { if (!viewModel.isBackupOperationRunning) onSetCurrentVehicle(vehicle) },
                    onEdit = { if (!viewModel.isBackupOperationRunning) onEditVehicle(vehicle) },
                    onDelete = { if (!viewModel.isBackupOperationRunning) vehicleToDeleteId = vehicle.id }
                )
            }

            // 2. 备份与恢复
            item(key = KeyDataSection) {
                ExpandableSettingsGroup(
                    icon = Icons.Default.Folder,
                    title = "备份与恢复",
                    subtitle = "导出或导入本地 JSON 数据备份文件",
                    expanded = isDataExpanded,
                    enabled = !viewModel.isBackupOperationRunning,
                    onExpandedChange = { isDataExpanded = !isDataExpanded }
                ) {
                    SettingsRow(
                        icon = Icons.Default.FileUpload, title = "导出本地备份", subtitle = "导出为 JSON 文件",
                        useCircleBackground = true,
                        enabled = !viewModel.isBackupOperationRunning, onClick = { exportLauncher.launch(BackupManager.generateFileName()) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)

                    SettingsRow(
                        icon = Icons.Default.FileDownload, title = "恢复本地备份", subtitle = "覆盖当前记录",
                        useCircleBackground = true,
                        enabled = !viewModel.isBackupOperationRunning, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    )

                    ExpandableSettingsGroup(
                        icon = Icons.Default.CloudDownload,
                        title = "小熊油耗数据",
                        subtitle = "导入小熊导出的加油 / 费用 CSV",
                        expanded = isXiaoxiongExpanded,
                        enabled = !viewModel.isBackupOperationRunning,
                        isNested = true,
                        onExpandedChange = { isXiaoxiongExpanded = !isXiaoxiongExpanded }
                    ) {
                        // 导入加油 CSV
                        SettingsRow(
                            icon = Icons.Default.FileDownload, title = "导入加油 CSV", subtitle = "导入 fuel-eco-*.csv",
                            indent = true, enabled = !viewModel.isBackupOperationRunning,
                            onClick = {
                                fuelImporter.launch(arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/vnd.ms-excel"
                                ))
                            }
                        )

// 导入费用 CSV
                        SettingsRow(
                            icon = Icons.Default.FileDownload, title = "导入费用 CSV", subtitle = "导入 expense-*.csv",
                            indent = true, enabled = !viewModel.isBackupOperationRunning,
                            onClick = {
                                expenseImporter.launch(arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/vnd.ms-excel"
                                ))
                            }
                        )
                        SettingsRow(
                            icon = Icons.Default.CloudDownload, title = "前往小熊网页导出", subtitle = "登录网页版导出 CSV",
                            indent = true, enabled = !viewModel.isBackupOperationRunning,
                            onClick = { openExternalPage(context, "https://www.xiaoxiongyouhao.com/carowner/page_data_mgr.php", "无法打开浏览器") }
                        )
                    }

                    if (viewModel.isBackupOperationRunning) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)
                        ProcessingRow()
                    }
                }
            }

            // 3. 主题设置
            item(key = KeyThemeSection) {
                ExpandableSettingsGroup(
                    icon = Icons.Default.Brightness6, title = "主题", subtitle = currentThemeMode.displayName,
                    expanded = isThemeExpanded, enabled = !viewModel.isBackupOperationRunning,
                    onExpandedChange = { isThemeExpanded = !isThemeExpanded }
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = currentThemeMode == mode
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !viewModel.isBackupOperationRunning) {
                                    ThemePreferences.setMode(context, mode)
                                    currentThemeMode = mode
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(mode.displayName, fontSize = 14.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (selected) Icon(Icons.Default.Check, "已选中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            else Spacer(Modifier.size(18.dp))
                        }
                        if (mode != ThemeMode.entries.last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }

            // 4. 关于应用
            item(key = KeyAboutSection) {
                ExpandableSettingsGroup(
                    icon = Icons.Default.Info, title = "关于应用", subtitle = "版本信息、使用说明、更新",
                    expanded = isAboutExpanded, enabled = !viewModel.isBackupOperationRunning,
                    onExpandedChange = { isAboutExpanded = !isAboutExpanded }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SettingsRow(
                            title = "应用说明", subtitle = "使用指南与常见问题",
                            icon = Icons.AutoMirrored.Filled.MenuBook, trailingIcon = null,
                            enabled = !viewModel.isBackupOperationRunning, onClick = { showInstructions = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)

                        SettingsRow(
                            title = "查看更新", subtitle = "跳转网盘查看下载",
                            icon = Icons.Default.CloudDownload, trailingIcon = null,
                            enabled = !viewModel.isBackupOperationRunning,
                            onClick = { openExternalPage(context, "https://yun.139.com/shareweb/#/w/i/2xTrJEeQhKJ01", "无法打开网盘链接") }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)

                        SettingsRow(
                            title = "更新历史", subtitle = "查看历次版本更新内容",
                            icon = Icons.Default.History, trailingIcon = null,
                            enabled = !viewModel.isBackupOperationRunning, onClick = { showUpdateHistory = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)

                        // 通知权限（特殊排版，保留原生 Row）
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !viewModel.isBackupOperationRunning) {
                                try {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                                }
                            }.padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Notifications, null, tint = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("通知权限", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(if (notificationsEnabled) "已开启，记停车时会显示位置提醒" else "已关闭，点击前往系统设置开启", fontSize = 12.sp, color = if (notificationsEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)

                        // 版本号
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("版本号", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 5. 隐私声明入口
            item(key = "section_privacy") {
                MiuiCardLocal {
                    SettingsRow(
                        icon = Icons.Default.Security, title = "隐私声明", subtitle = "了解应用信息收集情况",
                        useCircleBackground = true,
                        enabled = !viewModel.isBackupOperationRunning, onClick = { showPrivacyDialog = true }
                    )
                }
            }
        }
    }

    // --- 自动滚动 ---
    AutoScrollOnExpand(isDataExpanded, KeyDataSection, listState)
    AutoScrollOnExpand(isThemeExpanded, KeyThemeSection, listState)
    AutoScrollOnExpand(isAboutExpanded, KeyAboutSection, listState)
    AutoScrollOnExpand(isXiaoxiongExpanded, KeyDataSection, listState)

    // --- 各种确认与弹窗 ---
    vehicles.firstOrNull { it.id == vehicleToDeleteId }?.let { vehicle ->
        MiuiConfirmDialog(
            title = "确认删除车辆？",
            message = buildString {
                append("删除“${vehicle.brand} ${vehicle.model}”将同步删除该车辆下的所有加油记录，且不可恢复。")
                if (vehicle.isCurrent) append("\n注意：这是当前正在使用的车辆。")
            },
            confirmText = "删除",
            onConfirm = { vehicleToDeleteId = null; onDeleteVehicle(vehicle) },
            onDismiss = { vehicleToDeleteId = null }
        )
    }

    selectedRestoreUri?.let { uri ->
        MiuiConfirmDialog(
            title = "确认恢复备份？",
            message = "恢复备份将覆盖当前的所有车辆和加油记录，此操作无法撤销。是否继续？",
            confirmText = "覆盖并恢复",
            onConfirm = {
                selectedRestoreUri = null
                viewModel.restoreBackup(uri) { result ->
                    result.fold(
                        onSuccess = { Toast.makeText(context, "数据恢复成功！", Toast.LENGTH_SHORT).show() },
                        onFailure = { Toast.makeText(context, "数据恢复失败: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                    )
                }
            },
            onDismiss = { selectedRestoreUri = null }
        )
    }

    if (showPrivacyDialog) PrivacyDialog { showPrivacyDialog = false }
    if (showUpdateHistory) UpdateHistoryDialog { showUpdateHistory = false }
    if (showInstructions) InstructionsDialog { showInstructions = false }
}

// ============================================================
// 核心抽象组件区
// ============================================================

@Composable
private fun ExpandableSettingsGroup(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    enabled: Boolean = true,
    isNested: Boolean = false, // 是否作为内部子菜单
    onExpandedChange: () -> Unit,
    content: @Composable () -> Unit
) {
    val innerContent = @Composable {
        Column(modifier = if (isNested) Modifier else Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onExpandedChange).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (!isNested) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                    } else {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(title, fontSize = if (isNested) 14.sp else 15.sp, fontWeight = if (isNested) FontWeight.Medium else FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "展开", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded, enter = ExpandSpec + FadeInSpec, exit = ShrinkSpec + FadeOutSpec) {
                Column {
                    if (!isNested) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)
                    content()
                }
            }
        }
    }

    if (isNested) innerContent()
    else MiuiCardLocal { innerContent() }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    indent: Boolean = false,
    useCircleBackground: Boolean = false,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(start = if (indent) 48.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                if (useCircleBackground) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (indent) 18.dp else 20.dp))
                }
                Spacer(Modifier.width(if (indent) 10.dp else 12.dp))
            }
            Column {
                Text(title, fontSize = if (indent) 14.sp else 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailingIcon != null) {
            Icon(trailingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ============================================================
// 弹窗区（统一使用 ScrollableSettingsDialog）
// ============================================================

@Composable
private fun ScrollableSettingsDialog(
    title: String,
    buttonText: String,
    onDismiss: () -> Unit,
    centeredTitle: Boolean = false,
    spacedContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = title, fontSize = if (centeredTitle) 18.sp else 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (centeredTitle) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = if (spacedContent) Arrangement.spacedBy(10.dp) else Arrangement.Top,
                    content = content
                )
                Spacer(Modifier.height(20.dp))
                TextButton(
                    onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(buttonText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    ScrollableSettingsDialog(title = "隐私声明", buttonText = "我已了解", onDismiss = onDismiss, centeredTitle = true) {
        PrivacySection(
            title = "数据本地化存储",
            body = "本应用为纯本地工具。您的车辆信息、加油记录、费用记录以及停车位置，均仅保存在您手机的本地 SQLite 数据库中。我们不运营任何服务器，不会主动将您的任何记账数据上传至云端。")
        PrivacySection(
            title = "信息收集与使用",
            body = "本应用不收集您的通讯录、短信、通话记录、相册等个人敏感身份信息。应用仅会在您主动记录时，获取您填写的车辆配置、金额、里程等必要记账数据。")
        PrivacySection(
            title = "位置权限说明",
            body = "当您使用“记停车”功能并点击“获取当前位置”时，应用会向系统请求定位权限。获取的经纬度和地址文本，仅用于自动填写停车位置输入框。当您保存该条停车记录后，位置文本将保存在本地数据库中，绝不会上传。")
        PrivacySection(
            title = "网络访问与第三方",
            body = "本应用主体不包含联网上报代码。仅在您主动点击“查看更新”或“前往小熊网页导出”时，应用会调用您手机自带的系统浏览器打开对应的外部网页。该过程不涉及本应用向外部回传任何本地数据。")
        PrivacySection(
            title = "数据备份与安全",
            body = "备份文件由您主动导出并保存在手机本地。请妥善保管您的备份文件。卸载本应用会直接清除应用内的所有本地数据（含未导出的历史记录），建议您定期导出备份。同时，本应用已关闭 Android 系统的云自动备份功能，防止您的敏感数据被自动同步至云端。")
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

@Composable
private fun InstructionsDialog(onDismiss: () -> Unit) {
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
private fun UpdateHistoryDialog(onDismiss: () -> Unit) {
    val allLogs = remember { UpdateLogManager.getAllUpdateLogs() }
    ScrollableSettingsDialog(title = "更新历史", buttonText = "关闭", onDismiss = onDismiss, spacedContent = true) {
        allLogs.forEach { entry ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("v${entry.versionName}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                entry.items.forEach { lineText ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.padding(top = 8.dp).size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(lineText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
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
// 其他基础组件 & 工具
// ============================================================

@Composable
private fun MiuiCardLocal(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    MiuiCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { content() }
}

@Composable
private fun VehicleItemCard(vehicle: Vehicle, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    MiuiCardLocal(modifier = Modifier.clickable(onClick = onSelect)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(if (vehicle.isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsCar, null, tint = if (vehicle.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(listOf(vehicle.brand, vehicle.series, vehicle.model).filter { it.isNotBlank() }.joinToString(" "), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${vehicle.year}款 | ${vehicle.fuelType} (${vehicle.fuelGrade}) | 油箱 ${vehicle.tankCapacity}L", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vehicle.isCurrent) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("当前使用", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f), modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProcessingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("正在处理数据，请稍候…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiuiConfirmDialog(title: String, message: String, confirmText: String = "确定", dismissText: String = "取消", confirmColor: Color? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val effectiveConfirmColor = confirmColor ?: MaterialTheme.colorScheme.error
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var actionTriggered by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) { Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))) } }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(dismissText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(
                    onClick = { if (actionTriggered) return@TextButton; actionTriggered = true; onConfirm() },
                    enabled = !actionTriggered, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = effectiveConfirmColor.copy(alpha = 0.12f))
                ) {
                    Text(confirmText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = effectiveConfirmColor)
                }
            }
        }
    }
}

@Composable
private fun AutoScrollOnExpand(expanded: Boolean, sectionKey: String, listState: LazyListState) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(expanded, sectionKey) {
        if (!expanded) return@LaunchedEffect
        delay(ScrollDelay)
        val layoutInfo = listState.layoutInfo
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == sectionKey } ?: return@LaunchedEffect
        val overshoot = item.offset + item.size - layoutInfo.viewportEndOffset
        if (overshoot <= 0) return@LaunchedEffect
        val keepHeaderPx = with(density) { 48.dp.toPx() }
        val maxScroll = (item.offset - layoutInfo.viewportStartOffset + keepHeaderPx).coerceAtLeast(0f)
        val scrollBy = overshoot.toFloat().coerceIn(0f, maxScroll)
        if (scrollBy > 0f) {
            listState.animateScrollBy(value = scrollBy, animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
        }
    }
}

private fun openExternalPage(
    context: Context,
    url: String,
    errorMessage: String
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            // 👇 加上这个 Flag，防止在非 Activity 上下文中调用时崩溃
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun isCsvFileUri(context: Context, uri: Uri): Boolean {
    val displayName = try {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) {
        null
    }
    return displayName?.endsWith(".csv", ignoreCase = true) == true
}

private fun <T> launchCsvImport(
    context: Context,
    viewModel: MainViewModel,
    uri: Uri?, // 传入的参数叫 uri
    typeName: String,
    parser: suspend (Context, Uri, Long) -> Result<List<T>>,
    saveRecords: (List<T>, (Result<Int>) -> Unit) -> Unit
) {
    // 1. 声明并赋值 fileUri，如果是 null 直接返回
    val fileUri = uri ?: return

    // 2. 校验文件后缀（注意这里用的是 fileUri）
    if (!isCsvFileUri(context, fileUri)) {
        Toast.makeText(context, "请选择 .csv 格式的文件", Toast.LENGTH_SHORT).show()
        return
    }

    // 3. 获取当前车辆
    val vehicleId = viewModel.currentVehicle.value?.id
    if (vehicleId == null) {
        Toast.makeText(context, "请先添加并选择车辆，再导入 CSV", Toast.LENGTH_SHORT).show()
        return
    }

    // 4. 开始解析和导入
    viewModel.viewModelScope.launch {
        val parseResult = try {
            parser(context, fileUri, vehicleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        parseResult.fold(
            onSuccess = { records ->
                saveRecords(records) { result ->
                    viewModel.viewModelScope.launch {
                        val message = result.fold(
                            onSuccess = { count ->
                                if (count > 0) {
                                    "成功导入 $count 条${typeName}记录"
                                } else {
                                    "没有新记录，数据已存在"
                                }
                            },
                            onFailure = { error ->
                                "导入失败：${error.localizedMessage ?: "未知错误"}"
                            }
                        )
                        Toast.makeText(
                            context,
                            message,
                            if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onFailure = { error ->
                Toast.makeText(
                    context,
                    "CSV 解析失败：${error.localizedMessage ?: "请确认文件是小熊油耗导出的${typeName} CSV"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}