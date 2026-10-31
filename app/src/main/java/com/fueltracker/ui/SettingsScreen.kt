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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CallToAction
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.fueltracker.BuildConfig
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.InstructionsDialog
import com.fueltracker.ui.components.MiuiConfirmDialog
import com.fueltracker.ui.components.PrivacyDialog
import com.fueltracker.ui.components.UpdateHistoryDialog
import com.fueltracker.ui.miui.MiuiBackButton
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.ui.miui.MiuiSwitch
import com.fueltracker.ui.theme.ThemeMode
import com.fueltracker.ui.theme.ThemePreferences
import com.fueltracker.util.BackupManager
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
        // 👇 替换掉原本的 TopAppBar，高度严格对齐首页的 48dp
        // 检查外层 Box/Column 的背景色，必须是 MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background) // 👈 确保这里是动态背景，不要写死 Color.White
                .statusBarsPadding()
                .height(48.dp)
                .padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuiBackButton(onClick = onBack)

                Text(
                    text = "设置与数据",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // 1. 顶部留白稍微留一点（8dp）
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            // 2. 把间距从 20.dp 改为 12.dp（或者 14.dp），让整体变得紧凑
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 车辆管理
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "车辆管理",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground // 👈 强制使用与背景相反的颜色
                        )
                        TextButton(
                            onClick = onAddVehicle,
                            enabled = !viewModel.isBackupOperationRunning,
                            // 👈 可选：为了让按钮在禁用时更清晰，可以显式设置颜色（如果是启用状态，用 Primary 就行）
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = "＋ 添加车辆",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium // 加一点字重，更好看
                            )
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
                    icon = Icons.Default.Brightness6,
                    title = "主题",
                    subtitle = currentThemeMode.displayName,
                    expanded = isThemeExpanded,
                    enabled = !viewModel.isBackupOperationRunning,
                    onExpandedChange = { isThemeExpanded = !isThemeExpanded }
                ) {


                    // 👇 1. 第一行：悬浮式底栏开关（主开关）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleFloatingBottomBar() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CallToAction,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("悬浮式底栏", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (viewModel.isFloatingBottomBar) "当前为悬浮胶囊样式" else "当前为经典固定贴底样式",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MiuiSwitch(
                            checked = viewModel.isFloatingBottomBar,
                            onCheckedChange = { viewModel.toggleFloatingBottomBar() }
                        )
                    }

                    // 👇 2. 只有开启悬浮底栏时，才显示磨砂效果开关
                    if (viewModel.isFloatingBottomBar) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(start = 16.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleGlassEffect() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BlurOn,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("磨砂底栏效果", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (viewModel.isGlassEffectEnabled) "已开启，底栏使用磨砂效果" else "已关闭，底栏使用纯色背景",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MiuiSwitch(
                                checked = viewModel.isGlassEffectEnabled,
                                onCheckedChange = { viewModel.toggleGlassEffect() }
                            )
                        }
                    }

                    // 👇 3. 这里的底部分割线，用来隔开下面的主题模式选项
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.6.dp)
                    // 👇 2. 第二行开始：深色/浅色模式选项
                    ThemeMode.entries.forEach { mode ->
                        val selected = currentThemeMode == mode

                        // 👇 根据模式匹配对应的小图标
                        val icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        }

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
                            // 👇 左侧：图标 + 文字
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    // 选中的时候图标变主题色，未选中时变灰色
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    mode.displayName,
                                    fontSize = 14.sp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 右侧：打钩
                            if (selected) {
                                Icon(Icons.Default.Check, "已选中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                        }

                        // 分割线保持不变
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