package com.fueltracker.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import com.fueltracker.util.XiaoxiongCsvImporter

// ============================================================
// 动画规格
// ============================================================

private const val ExpandDurationMs = 380
private const val ScrollDelayMs: Long = ExpandDurationMs.toLong() + 40L

private val ExpandSpec = expandVertically(
    animationSpec = tween(durationMillis = ExpandDurationMs, easing = FastOutSlowInEasing)
)

private val ShrinkSpec = shrinkVertically(
    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
)

private val FadeInSpec = fadeIn(
    animationSpec = tween(durationMillis = 260, delayMillis = 80)
)

private val FadeOutSpec = fadeOut(
    animationSpec = tween(durationMillis = 180)
)

// ============================================================
// LazyColumn 稳定 Key
// ============================================================

private const val KeyDataSection = "section_data"
private const val KeyThemeSection = "section_theme"
private const val KeyAboutSection = "section_about"

// ============================================================
// SettingsScreen
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

    // 从系统设置返回时刷新通知状态
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

    // 导出备份
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        viewModel.createBackup(uri) { result ->
            if (result.isSuccess) {
                Toast.makeText(context, "备份导出成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "导出失败: ${result.exceptionOrNull()?.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 导入备份
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        viewModel.checkIsJsonFile(uri) { isJson ->
            if (isJson) {
                selectedRestoreUri = uri
            } else {
                Toast.makeText(
                    context,
                    "请选择 JSON 格式的备份文件",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 加油记录导入
    val fuelImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val vehicle = viewModel.currentVehicle.value ?: return@rememberLauncherForActivityResult

        viewModel.viewModelScope.launch {
            val result = XiaoxiongCsvImporter.parseFuelCsv(context, uri, vehicle.id)
            val records = result.getOrNull()
            if (records != null) {
                viewModel.importFuelRecords(records) { importResult ->
                    importResult.getOrNull()?.let { count ->
                        if (count > 0) {
                            Toast.makeText(context, "成功导入 $count 条加油记录", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "没有新记录，数据已存在", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Toast.makeText(
                        context,
                        "导入失败：${importResult.exceptionOrNull()?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    context,
                    "CSV 解析失败：${result.exceptionOrNull()?.localizedMessage ?: "请确认是小熊导出的加油 CSV"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 费用记录导入
    val expenseImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val vehicle = viewModel.currentVehicle.value ?: return@rememberLauncherForActivityResult

        viewModel.viewModelScope.launch {
            val result = XiaoxiongCsvImporter.parseExpenseCsv(context, uri, vehicle.id)
            val records = result.getOrNull()
            if (records != null) {
                viewModel.importExpenseRecords(records) { importResult ->
                    importResult.getOrNull()?.let { count ->
                        if (count > 0) {
                            Toast.makeText(context, "成功导入 $count 条费用记录", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "没有新记录，数据已存在", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Toast.makeText(
                        context,
                        "导入失败：${importResult.exceptionOrNull()?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    context,
                    "CSV 解析失败：${result.exceptionOrNull()?.localizedMessage ?: "请确认是小熊导出的费用 CSV"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        TopAppBar(
            title = {
                Text(
                    text = "设置与数据",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // 内容区
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 车辆管理标题栏
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "车辆管理",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        TextButton(
                            onClick = onAddVehicle,
                            enabled = !viewModel.isBackupOperationRunning
                        ) {
                            Text(
                                text = "＋ 添加车辆",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (vehicles.isEmpty()) {
                        MiuiCardLocal {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无车辆信息，请添加车辆",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // 车辆列表
            items(
                items = vehicles,
                key = { it.id },
                contentType = { "vehicle" }
            ) { vehicle ->
                VehicleItemCard(
                    vehicle = vehicle,
                    onSelect = {
                        if (!viewModel.isBackupOperationRunning) {
                            onSetCurrentVehicle(vehicle)
                        }
                    },
                    onEdit = {
                        if (!viewModel.isBackupOperationRunning) {
                            onEditVehicle(vehicle)
                        }
                    },
                    onDelete = {
                        if (!viewModel.isBackupOperationRunning) {
                            vehicleToDeleteId = vehicle.id
                        }
                    }
                )
            }

            // 数据管理
            item(key = KeyDataSection) {
                ExpandableSettingCard(
                    icon = Icons.Default.Folder,
                    title = "备份与恢复",
                    subtitle = "导出或导入本地 JSON 数据备份文件",
                    expanded = isDataExpanded,
                    enabled = !viewModel.isBackupOperationRunning,
                    onExpandedChange = { isDataExpanded = !isDataExpanded }
                ) {
                    SettingActionRow(
                        icon = Icons.Default.FileUpload,
                        title = "导出本地备份",
                        subtitle = "将车辆及所有加油记录导出为 JSON 文件",
                        enabled = !viewModel.isBackupOperationRunning,
                        onClick = {
                            exportLauncher.launch(BackupManager.generateFileName())
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.6.dp
                    )

                    SettingActionRow(
                        icon = Icons.Default.FileDownload,
                        title = "恢复本地备份",
                        subtitle = "从本地备份文件中恢复数据并覆盖当前记录",
                        enabled = !viewModel.isBackupOperationRunning,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )

                    // ===== 小熊油耗数据（子菜单） =====
                    NestedExpandableGroup(
                        icon = Icons.Default.CloudDownload,
                        expanded = isXiaoxiongExpanded,
                        enabled = !viewModel.isBackupOperationRunning,
                        onExpandedChange = { isXiaoxiongExpanded = !isXiaoxiongExpanded }
                    ) {
                        NestedActionRow(
                            icon = Icons.Default.FileDownload,
                            title = "导入加油 CSV",
                            subtitle = "从小熊油耗导出的 fuel-eco-*.csv 导入",
                            enabled = !viewModel.isBackupOperationRunning,
                            onClick = {
                                fuelImporter.launch(
                                    arrayOf("text/csv", "text/comma-separated-values", "*/*")
                                )
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(start = 48.dp)
                        )

                        NestedActionRow(
                            icon = Icons.Default.FileDownload,
                            title = "导入费用 CSV",
                            subtitle = "从小熊油耗导出的 expense-*.csv 导入",
                            enabled = !viewModel.isBackupOperationRunning,
                            onClick = {
                                expenseImporter.launch(
                                    arrayOf("text/csv", "text/comma-separated-values", "*/*")
                                )
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp,
                            modifier = Modifier.padding(start = 48.dp)
                        )

                        NestedActionRow(
                            icon = Icons.Default.CloudDownload,
                            title = "前往小熊网页导出",
                            subtitle = "登录小熊油耗网页版导出 CSV，再回到本应用导入",
                            enabled = !viewModel.isBackupOperationRunning,
                            onClick = {
                                val url = "https://www.xiaoxiongyouhao.com/carowner/page_data_mgr.php"
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    if (viewModel.isBackupOperationRunning) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp
                        )
                        ProcessingRow()
                    }
                }
            }


            // 主题设置
            item(key = KeyThemeSection) {
                ExpandableSettingCard(
                    icon = Icons.Default.Brightness6,
                    title = "主题",
                    subtitle = currentThemeMode.displayName,
                    expanded = isThemeExpanded,
                    enabled = !viewModel.isBackupOperationRunning,
                    onExpandedChange = { isThemeExpanded = !isThemeExpanded }
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = currentThemeMode == mode

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !viewModel.isBackupOperationRunning) {
                                    ThemePreferences.setMode(context, mode)
                                    currentThemeMode = mode
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = mode.displayName,
                                fontSize = 14.sp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )

                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选中",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(18.dp))
                            }
                        }

                        if (mode != ThemeMode.entries.last()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                thickness = 0.6.dp,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }

            // 关于应用
            item(key = KeyAboutSection) {
                ExpandableSettingCard(
                    icon = Icons.Default.Info,
                    title = "关于应用",
                    subtitle = "版本信息、使用说明、更新",
                    expanded = isAboutExpanded,
                    enabled = !viewModel.isBackupOperationRunning,
                    onExpandedChange = {
                        isAboutExpanded = !isAboutExpanded
                    }
                ) {

                    // 关键：恢复原来的左右内边距
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                        AboutRow(
                            title = "应用说明",
                            subtitle = "使用指南与常见问题",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            enabled = !viewModel.isBackupOperationRunning,
                            onClick = { showInstructions = true }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp
                        )


                        // ==============================
                        // 查看更新
                        // ==============================

                        AboutRow(
                            title = "查看更新",
                            subtitle = "跳转网盘查看与下载最新安装包",
                            icon = Icons.Default.CloudDownload,
                            enabled = !viewModel.isBackupOperationRunning,
                            onClick = {
                                val url =
                                    "https://yun.139.com/shareweb/#/w/i/2xTrJEeQhKJ01"

                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            url.toUri()
                                        )
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "无法打开网盘链接",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )

                        HorizontalDivider(
                            color =
                                MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp
                        )

                        AboutRow(
                            title = "更新历史",
                            subtitle = "查看历次版本更新内容",
                            icon = Icons.Default.History,
                            enabled = !viewModel.isBackupOperationRunning,
                            onClick = { showUpdateHistory = true }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp
                        )

                        // ==============================
                        // 通知权限
                        // ==============================

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !viewModel.isBackupOperationRunning) {
                                    val intent = Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    ).apply {
                                        putExtra(
                                            Settings.EXTRA_APP_PACKAGE,
                                            context.packageName
                                        )
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            "无法打开系统设置",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (notificationsEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "通知权限",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (notificationsEnabled) {
                                            "已开启，记停车时会显示位置提醒"
                                        } else {
                                            "已关闭，点击前往系统设置开启"
                                        },
                                        fontSize = 12.sp,
                                        color = if (notificationsEnabled)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Text(
                                text = if (notificationsEnabled) "已开启" else "去开启",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (notificationsEnabled)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        }

                        HorizontalDivider(
                            color =
                                MaterialTheme.colorScheme.surfaceVariant,
                            thickness = 0.6.dp
                        )

                        // ==============================
                        // 版本号
                        // ==============================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "版本号",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            // 隐私声明
            item(key = "section_privacy") {
                MiuiCardLocal {
                    SettingActionRow(
                        icon = Icons.Default.Security,
                        title = "隐私声明",
                        subtitle = "了解应用信息收集情况",
                        enabled = !viewModel.isBackupOperationRunning,
                        onClick = { showPrivacyDialog = true }
                    )
                }
            }
        }
    }

    // 自动滚动处理
    AutoScrollOnExpand(isDataExpanded, KeyDataSection, listState)
    AutoScrollOnExpand(isThemeExpanded, KeyThemeSection, listState)
    AutoScrollOnExpand(isAboutExpanded, KeyAboutSection, listState)
    AutoScrollOnExpand(isXiaoxiongExpanded, KeyDataSection, listState)
    // 删除车辆确认弹窗
    vehicles.firstOrNull { it.id == vehicleToDeleteId }?.let { vehicle ->
        MiuiConfirmDialog(
            title = "确认删除车辆？",
            message = buildString {
                append("删除“${vehicle.brand} ${vehicle.model}”将同步删除该车辆下的所有加油记录，且不可恢复。")
                if (vehicle.isCurrent) {
                    append("\n注意：这是当前正在使用的车辆。")
                }
            },
            confirmText = "删除",
            onConfirm = {
                vehicleToDeleteId = null
                onDeleteVehicle(vehicle)
            },
            onDismiss = { vehicleToDeleteId = null }
        )
    }

    // 恢复备份确认弹窗
    selectedRestoreUri?.let { uri ->
        MiuiConfirmDialog(
            title = "确认恢复备份？",
            message = "恢复备份将覆盖当前的所有车辆和加油记录，此操作无法撤销。是否继续？",
            confirmText = "覆盖并恢复",
            onConfirm = {
                selectedRestoreUri = null
                viewModel.restoreBackup(uri) { result ->
                    if (result.isSuccess) {
                        Toast.makeText(context, "数据恢复成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "数据恢复失败: ${result.exceptionOrNull()?.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDismiss = { selectedRestoreUri = null }
        )
    }
    // 隐私声明弹窗
    if (showPrivacyDialog) {
        PrivacyDialog(onDismiss = { showPrivacyDialog = false })
    }
    //更新历史弹窗
    if (showUpdateHistory) {
        UpdateHistoryDialog(onDismiss = { showUpdateHistory = false })
    }
    //关于应用弹窗
    if (showInstructions) {
        InstructionsDialog(onDismiss = { showInstructions = false })
    }
}

// ============================================================
// 可展开设置卡片
// ============================================================

@Composable
private fun ExpandableSettingCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    enabled: Boolean = true,
    onExpandedChange: () -> Unit,
    content: @Composable () -> Unit
) {
    MiuiCardLocal {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onExpandedChange)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = ExpandSpec + FadeInSpec,
                exit = ShrinkSpec + FadeOutSpec
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.6.dp
                    )
                    content()
                }
            }
        }
    }
}

// ============================================================
// 自动滚动
// ============================================================

@Composable
private fun AutoScrollOnExpand(
    expanded: Boolean,
    sectionKey: String,
    listState: LazyListState
) {
    val density = androidx.compose.ui.platform.LocalDensity.current

    LaunchedEffect(expanded, sectionKey) {
        if (!expanded) return@LaunchedEffect

        delay(ScrollDelayMs.milliseconds)

        val layoutInfo = listState.layoutInfo
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == sectionKey } ?: return@LaunchedEffect

        val overshoot = item.offset + item.size - layoutInfo.viewportEndOffset
        if (overshoot <= 0) return@LaunchedEffect

        val keepHeaderPx = with(density) { 48.dp.toPx() }
        val maxScroll = (item.offset - layoutInfo.viewportStartOffset + keepHeaderPx).coerceAtLeast(0f)
        val scrollBy = overshoot.toFloat().coerceIn(0f, maxScroll)

        if (scrollBy > 0f) {
            listState.animateScrollBy(
                value = scrollBy,
                animationSpec = tween(
                    durationMillis = 350,
                    easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
                )
            )
        }
    }
}


// ============================================================
// MIUI 卡片
// ============================================================

@Composable
private fun MiuiCardLocal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    MiuiCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        content()
    }
}
// ============================================================
// 车辆卡片
// ============================================================

@Composable
private fun VehicleItemCard(
    vehicle: Vehicle,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    MiuiCardLocal(modifier = Modifier.clickable(onClick = onSelect)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (vehicle.isCurrent) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = if (vehicle.isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOf(vehicle.brand, vehicle.series, vehicle.model)
                        .filter { it.isNotBlank() }
                        .joinToString(" "),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${vehicle.year}款 | ${vehicle.fuelType} (${vehicle.fuelGrade}) | 油箱 ${vehicle.tankCapacity}L",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (vehicle.isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "当前使用",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑车辆",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "删除车辆",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 设置操作行
// ============================================================

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "›",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun NestedExpandableGroup(
    icon: ImageVector,
    expanded: Boolean,
    enabled: Boolean = true,
    onExpandedChange: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onExpandedChange)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    // 👇 这里直接写死
                    Text(
                        text = "小熊油耗数据",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // 👇 这里直接写死
                    Text(
                        text = "导入小熊导出的加油 / 费用 CSV，或前往网页导出",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = ExpandSpec + FadeInSpec,
            exit = ShrinkSpec + FadeOutSpec
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun NestedActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 48.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ============================================================
// 处理中的提示
// ============================================================

@Composable
private fun ProcessingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "正在处理数据，请稍候…",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
// About 行
// ============================================================

@Composable
private fun AboutRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ============================================================
// 使用说明
// ============================================================

@Composable
private fun MiuiInstructionItem(
    index: Int,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$index",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ============================================================
// MIUI 确认弹窗
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiuiConfirmDialog(
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = dismissText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = {
                        if (actionTriggered) return@TextButton
                        actionTriggered = true
                        onConfirm()
                    },
                    enabled = !actionTriggered,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = effectiveConfirmColor.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        text = confirmText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = effectiveConfirmColor
                    )
                }
            }
        }
    }
}
@Composable
private fun PrivacyDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "隐私声明",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                PrivacySection(
                    title = "数据存储",
                    body = "本应用完全离线运行。车辆信息、加油记录、费用记录全部保存在您的手机本地数据库中，不会上传到任何服务器。"
                )

                PrivacySection(
                    title = "信息收集",
                    body = "本应用不收集、不读取、不分享您的任何个人信息，包括但不限于位置、联系人、通话记录、相册。"
                )

                PrivacySection(
                    title = "网络访问",
                    body = "仅在您主动点击\"查看更新\"时打开系统浏览器跳转到网盘页面，此操作不会向本应用回传任何数据。"
                )

                PrivacySection(
                    title = "位置权限",
                    body = "记停车时如需自动填写位置名称，会请求一次系统定位。定位信息仅用于填充停车位置输入框，不保存、不上传。"
                )

                PrivacySection(
                    title = "备份文件",
                    body = "备份文件由您主动导出并保存到本地存储，其内容完全由您掌控。卸载应用会删除所有本地数据，请在卸载前妥善备份。"
                )

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "我已了解",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}


@Composable
private fun UpdateHistoryDialog(
    onDismiss: () -> Unit
) {
    val allLogs = remember { UpdateLogManager.getAllUpdateLogs() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "更新历史",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    allLogs.forEach { entry ->
                        item(key = entry.versionCode) {
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

                                entry.items.forEach { lineText ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                            text = lineText,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 20.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "关闭",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
@Composable
private fun InstructionsDialog(
    onDismiss: () -> Unit
) {
    val instructions = listOf(
        "添加车辆后，每次加油记一笔。填写当前里程和加油量，系统自动算油耗",
        "记加油时勾选\"已加满\"，下次也加满时即可得到准确的百公里油耗",
        "忘记记一次加油？下次记录时勾选\"漏记\"，填入大概里程和油量，系统会一并折算",
        "记停车可保存车位号，首页卡片常驻提醒，取车后点\"已取车\"即可",
        "费用页可记录停车、高速、保养、保险等支出，支持自定义分类",
        "数据全部保存在本机，卸载前请先在设置里导出备份"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "应用说明",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    instructions.forEachIndexed { index, text ->
                        MiuiInstructionItem(index + 1, text)
                    }
                }

                Spacer(Modifier.height(20.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "关闭",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}