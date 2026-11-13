
package com.fueltracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CallToAction
import androidx.compose.material.icons.filled.CarRental
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.fueltracker.BuildConfig
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.miuix.MiuixBackButton
import com.fueltracker.ui.miuix.dialogs.MiuixConfirmDialog
import com.fueltracker.ui.miuix.dialogs.MiuixInstructionsDialog
import com.fueltracker.ui.miuix.dialogs.MiuixPrivacyDialog
import com.fueltracker.ui.miuix.dialogs.MiuixUpdateHistoryDialog
import com.fueltracker.ui.theme.BackgroundPreferences
import com.fueltracker.ui.theme.ThemeMode
import com.fueltracker.ui.theme.ThemePreferences
import com.fueltracker.ui.theme.adaptiveBackgroundTitleColor
import com.fueltracker.ui.theme.adaptiveBackgroundTitleStyle
import com.fueltracker.util.BackupManager
import com.fueltracker.util.OutlierSensitivity
import com.fueltracker.util.OutlierSensitivityPreferences
import com.fueltracker.util.XiaoxiongCsvImporter
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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

    // 同一时间只展开一个目录
    var expandedSection by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var vehicleToDeleteId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var selectedRestoreUri by rememberSaveable {
        mutableStateOf<Uri?>(null)
    }

    // 弹窗状态
    var showThemePicker by remember { mutableStateOf(false) }
    var showOutlierSensitivityPicker by remember {
        mutableStateOf(false)
    }
    // 新增：待确认切换的偏离值识别模式
    var pendingOutlierSensitivity by remember {
        mutableStateOf<OutlierSensitivity?>(null)
    }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showUpdateHistory by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    var showClearBackgroundConfirm by remember { mutableStateOf(false) }

    var currentThemeMode by remember {
        mutableStateOf(ThemePreferences.mode)
    }

    // 权限状态
    var notificationsEnabled by remember {
        mutableStateOf(
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }

    var locationPermissionStatus by remember {
        mutableStateOf(getLocationPermissionStatus(context))
    }

    // 从系统设置返回时刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()

                locationPermissionStatus =
                    getLocationPermissionStatus(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // =====================================================
    // 文件选择器
    // =====================================================

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        viewModel.createBackup(uri) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        "备份导出成功",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = {
                    Toast.makeText(
                        context,
                        "导出失败: ${it.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
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

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            BackgroundPreferences.setBackground(context, uri)

            Toast.makeText(
                context,
                "背景图片设置成功",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "设置背景失败：${e.localizedMessage ?: "无法读取图片"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val fuelImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        launchCsvImport(
            context = context,
            viewModel = viewModel,
            uri = uri,
            typeName = "加油",
            parser = { ctx, fileUri, id ->
                XiaoxiongCsvImporter.parseFuelCsv(ctx, fileUri, id)
            },
            saveRecords = { records, callback ->
                viewModel.importFuelRecords(records, callback)
            }
        )
    }

    val expenseImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        launchCsvImport(
            context = context,
            viewModel = viewModel,
            uri = uri,
            typeName = "费用",
            parser = { ctx, fileUri, id ->
                XiaoxiongCsvImporter.parseExpenseCsv(ctx, fileUri, id)
            },
            saveRecords = { records, callback ->
                viewModel.importExpenseRecords(records, callback)
            }
        )
    }

    // =====================================================
    // 设置首页
    // =====================================================

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars
                .asPaddingValues()
                .calculateTopPadding() + 16.dp,
            bottom = 32.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // 页面标题
        item(key = "settings_title") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                MiuixBackButton(
                    onClick = onBack,
                    iconTint = adaptiveBackgroundTitleColor(
                        MiuixTheme.colorScheme.onBackground
                    )
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "设置与数据",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = adaptiveBackgroundTitleColor(
                        MiuixTheme.colorScheme.onBackground
                    ),
                    style = adaptiveBackgroundTitleStyle()
                )
            }
        }

        // =================================================
        // 1. 车辆信息
        // =================================================

        item(key = "directory_vehicle") {
            SettingsDirectory(
                icon = Icons.Default.DirectionsCar,
                title = "车辆信息",
                subtitle = "查看、添加、编辑和管理车辆",
                expanded = expandedSection == "vehicle",
                onClick = {
                    expandedSection =
                        if (expandedSection == "vehicle") null else "vehicle"
                }
            ) {
                if (vehicles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无车辆信息",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                } else {
                    vehicles.forEachIndexed { index, vehicle ->
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

                        if (index != vehicles.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.CarRental,
                    title = "添加新车辆",
                    subtitle = "录入新的车辆信息",
                    enabled = !viewModel.isBackupOperationRunning,
                    onClick = onAddVehicle
                )
            }
        }

        // =================================================
        // 2. 个性化
        // =================================================

        item(key = "directory_personalization") {
            SettingsDirectory(
                icon = Icons.Default.Palette,
                title = "个性化",
                subtitle = "背景、导航栏和主题外观",
                expanded = expandedSection == "personalization",
                onClick = {
                    expandedSection =
                        if (expandedSection == "personalization") {
                            null
                        } else {
                            "personalization"
                        }
                }
            ) {
                SettingsArrowRow(
                    icon = Icons.Default.Folder,
                    title = "个性化背景",
                    subtitle = if (BackgroundPreferences.backgroundUri != null) {
                        "已设置自定义背景，点击更换"
                    } else {
                        "选择图片作为应用背景"
                    },
                    enabled = !viewModel.isBackupOperationRunning,
                    onClick = {
                        backgroundPickerLauncher.launch(arrayOf("image/*"))
                    }
                )

                if (BackgroundPreferences.backgroundUri != null) {
                    SettingsDivider()

                    SettingsArrowRow(
                        icon = Icons.Default.DeleteOutline,
                        title = "恢复默认背景",
                        subtitle = "移除自定义图片，恢复主题默认背景",
                        enabled = !viewModel.isBackupOperationRunning,
                        onClick = {
                            showClearBackgroundConfirm = true
                        }
                    )
                }

                SettingsDivider()

                SettingsSwitchRow(
                    icon = Icons.Default.CallToAction,
                    title = "悬浮式底栏",
                    subtitle = if (viewModel.isFloatingBottomBar) {
                        "当前为悬浮胶囊样式"
                    } else {
                        "当前为经典固定贴底样式"
                    },
                    checked = viewModel.isFloatingBottomBar,
                    onCheckedChange = {
                        viewModel.toggleFloatingBottomBar()
                    }
                )

                SettingsDivider()

                SettingsSwitchRow(
                    icon = Icons.Default.BlurOn,
                    title = "磨砂底栏效果",
                    subtitle = if (viewModel.isGlassEffectEnabled) {
                        "已开启，底栏使用磨砂效果"
                    } else {
                        "已关闭，底栏使用纯色背景"
                    },
                    checked = viewModel.isGlassEffectEnabled,
                    onCheckedChange = {
                        viewModel.toggleGlassEffect()
                    },
                    enabled = viewModel.isFloatingBottomBar
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = when (currentThemeMode) {
                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.DARK -> Icons.Default.DarkMode
                    },
                    title = "主题模式",
                    subtitle = currentThemeMode.displayName,
                    trailingText = currentThemeMode.displayName,
                    trailingIcon = Icons.Default.UnfoldMore,
                    onClick = {
                        showThemePicker = true
                    }
                )
            }
        }

// =================================================
// 3. 油耗计算
// =================================================

        item(key = "directory_calculation") {
            SettingsDirectory(
                icon = Icons.Default.Calculate,
                title = "油耗计算",
                subtitle = "油耗统计和异常识别设置",
                expanded = expandedSection == "calculation",
                onClick = {
                    expandedSection =
                        if (expandedSection == "calculation") {
                            null
                        } else {
                            "calculation"
                        }
                }
            ) {
                SettingsArrowRow(
                    icon = Icons.Default.Tune,
                    title = "偏离值识别",
                    subtitle = when (OutlierSensitivityPreferences.mode) {
                        OutlierSensitivity.STANDARD ->
                            "标准模式：适合日常连续记录"
                        OutlierSensitivity.LOOSE ->
                            "宽松模式：适合补录或导入历史数据"
                    },
                    trailingText = OutlierSensitivityPreferences.mode.displayName,
                    trailingIcon = Icons.Default.UnfoldMore,
                    onClick = {
                        showOutlierSensitivityPicker = true
                    }
                )
            }
        }


        // =================================================
        // 3. 备份
        // =================================================

        item(key = "directory_backup") {
            SettingsDirectory(
                icon = Icons.Default.Backup,
                title = "备份",
                subtitle = "数据导入、导出和小熊油耗数据迁移",
                expanded = expandedSection == "backup",
                onClick = {
                    expandedSection =
                        if (expandedSection == "backup") null else "backup"
                }
            ) {
                SettingsArrowRow(
                    icon = Icons.Default.FileUpload,
                    title = "导出本地备份",
                    subtitle = "导出为 JSON 文件",
                    enabled = !viewModel.isBackupOperationRunning,
                    onClick = {
                        exportLauncher.launch(BackupManager.generateFileName())
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.FileDownload,
                    title = "恢复本地备份",
                    subtitle = "覆盖当前所有数据",
                    enabled = !viewModel.isBackupOperationRunning,
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "*/*")
                        )
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.CloudDownload,
                    title = "导入小熊油耗（加油）",
                    subtitle = "导入 fuel-eco-*.csv",
                    enabled = !viewModel.isBackupOperationRunning,
                    onClick = {
                        fuelImporter.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/vnd.ms-excel"
                            )
                        )
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.CloudDownload,
                    title = "导入小熊油耗（费用）",
                    subtitle = "导入 expense-*.csv",
                    enabled = !viewModel.isBackupOperationRunning,
                    onClick = {
                        expenseImporter.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/vnd.ms-excel"
                            )
                        )
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.CloudDownload,
                    title = "前往小熊网页导出",
                    subtitle = "登录网页版导出 CSV 数据",
                    onClick = {
                        openExternalPage(
                            context,
                            "https://www.xiaoxiongyouhao.com/carowner/page_data_mgr.php",
                            "无法打开浏览器"
                        )
                    }
                )
            }
        }

        // =================================================
        // 4. 权限设置
        // =================================================

        item(key = "directory_permissions") {
            SettingsDirectory(
                icon = Icons.Default.Security,
                title = "权限设置",
                subtitle = "管理应用通知和定位权限",
                expanded = expandedSection == "permissions",
                onClick = {
                    expandedSection =
                        if (expandedSection == "permissions") {
                            null
                        } else {
                            "permissions"
                        }
                }
            ) {
                SettingsArrowRow(
                    icon = Icons.Default.Notifications,
                    title = "通知权限",
                    subtitle = if (notificationsEnabled) {
                        "已开启，记停车时会显示位置提醒"
                    } else {
                        "已关闭，点击前往系统设置开启"
                    },
                    trailingText = if (notificationsEnabled) {
                        "已开启"
                    } else {
                        "未开启"
                    },
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            ).apply {
                                putExtra(
                                    Settings.EXTRA_APP_PACKAGE,
                                    context.packageName
                                )
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "无法打开系统设置",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.LocationOn,
                    title = "定位权限",
                    subtitle = when (locationPermissionStatus) {
                        "精确定位已开启" ->
                            "已允许精确定位，停车位置记录更准确"

                        "仅允许大致位置" ->
                            "当前仅允许大致位置，定位精度可能受影响"

                        else ->
                            "未授权，点击前往系统设置开启"
                    },
                    trailingText = when (locationPermissionStatus) {
                        "精确定位已开启" -> "已开启"
                        "仅允许大致位置" -> "部分授权"
                        else -> "未开启"
                    },
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts(
                                    "package",
                                    context.packageName,
                                    null
                                )
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "无法打开系统设置",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        // =================================================
        // 5. 关于
        // =================================================

        item(key = "directory_about") {
            SettingsDirectory(
                icon = Icons.Default.Info,
                title = "关于",
                subtitle = "版本信息、使用说明和更新历史",
                expanded = expandedSection == "about",
                onClick = {
                    expandedSection =
                        if (expandedSection == "about") null else "about"
                }
            ) {
                SettingsArrowRow(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "应用说明",
                    subtitle = "使用指南与常见问题",
                    onClick = {
                        showInstructions = true
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.CloudDownload,
                    title = "查看更新",
                    subtitle = "跳转 QQ群 下载和反馈",
                    onClick = {
                        openExternalPage(
                            context,
                            "https://qm.qq.com/q/7dkbKfAfgQ",
                            "无法打开更新链接"
                        )
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.Code,
                    title = "查看 GitHub",
                    subtitle = "觉得好用不要吝啬你的Star",
                    onClick = {
                        openExternalPage(
                            context,
                            "https://github.com/furenkun123/fueltracker",
                            "无法打开 GitHub"
                        )
                    }
                )

                SettingsDivider()

                SettingsArrowRow(
                    icon = Icons.Default.History,
                    title = "更新历史",
                    subtitle = "查看历次版本更新内容",
                    onClick = {
                        showUpdateHistory = true
                    }
                )

                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "版本号",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )

                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }

        // =================================================
        // 6. 隐私说明
        // =================================================

        item(key = "directory_privacy") {
            SettingsDirectory(
                icon = Icons.Default.PrivacyTip,
                title = "隐私说明",
                subtitle = "了解应用信息收集情况",
                expanded = false,
                onClick = {
                    showPrivacyDialog = true
                }
            ) {
                // 隐私内容通过独立弹窗展示
            }
        }
    }

    // =====================================================
    // 主题选择弹窗
    // =====================================================

    if (showThemePicker) {
        WindowBottomSheet(
            show = true,
            title = "主题模式",
            onDismissRequest = {
                showThemePicker = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = currentThemeMode == mode

                    val icon = when (mode) {
                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.DARK -> Icons.Default.DarkMode
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ThemePreferences.setMode(context, mode)
                                currentThemeMode = mode
                                showThemePicker = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            null,
                            tint = if (selected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            },
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(Modifier.width(12.dp))

                        Text(
                            mode.displayName,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )

                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                "已选中",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

// =====================================================
// 偏离值识别模式选择
// =====================================================

    if (showOutlierSensitivityPicker) {
        WindowBottomSheet(
            show = true,
            title = "偏离值识别",
            onDismissRequest = {
                showOutlierSensitivityPicker = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                OutlierSensitivity.entries.forEach { mode ->
                    val selected =
                        OutlierSensitivityPreferences.mode == mode

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selected) {
                                    // 当前模式，直接关闭
                                    showOutlierSensitivityPicker = false
                                } else {
                                    // 不立即切换，先关闭面板并弹出确认
                                    showOutlierSensitivityPicker = false
                                    pendingOutlierSensitivity = mode
                                }
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 14.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                mode.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )

                            Text(
                                mode.description,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }

                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已选中",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    // =====================================================
// 偏离值识别模式切换确认
// =====================================================

    pendingOutlierSensitivity?.let { targetMode ->
        val currentMode = OutlierSensitivityPreferences.mode

        MiuixConfirmDialog(
            title = "确认切换识别模式？",
            message = "即将从“${currentMode.displayName}”切换为“${targetMode.displayName}”。切换后会按新的识别规则重新处理异常数据，已有油耗统计结果可能发生变化。建议一般用户保持“标准模式”，确有必要时再调整。",
            confirmText = "确认切换",
            onConfirm = {
                OutlierSensitivityPreferences.setMode(context, targetMode)
                pendingOutlierSensitivity = null
                Toast.makeText(
                    context,
                    "已切换为${targetMode.displayName}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                pendingOutlierSensitivity = null
            }
        )
    }
    // =====================================================
    // 删除车辆确认
    // =====================================================

    vehicles.firstOrNull {
        it.id == vehicleToDeleteId
    }?.let { vehicle ->
        MiuixConfirmDialog(
            title = "确认删除车辆？",
            message = buildString {
                append(
                    "删除“${vehicle.brand} ${vehicle.model}”后，以下数据将被永久删除："
                )

                append("\n\n• 全部加油记录")
                append("\n• 全部费用记录")
                append("\n• 全部停车记录及活动停车状态")
                append("\n\n此操作不可撤销，删除后无法恢复，请确认已经备份重要数据。")
                if (vehicle.isCurrent) {
                    append("\n\n注意：这是当前正在使用的车辆，删除后将自动切换到其他车辆（如果存在）。")
                }
            },
            confirmText = "删除",
            onConfirm = {
                vehicleToDeleteId = null
                onDeleteVehicle(vehicle)
            },
            onDismiss = {
                vehicleToDeleteId = null
            }
        )
    }

    // =====================================================
    // 恢复备份确认
    // =====================================================

    selectedRestoreUri?.let { uri ->
        MiuixConfirmDialog(
            title = "确认恢复备份？",
            message = "恢复备份将覆盖当前的所有车辆和加油记录，此操作无法撤销。是否继续？",
            confirmText = "覆盖并恢复",
            onConfirm = {
                selectedRestoreUri = null

                viewModel.restoreBackup(uri) { result ->
                    result.fold(
                        onSuccess = {
                            Toast.makeText(
                                context,
                                "数据恢复成功！",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFailure = {
                            Toast.makeText(
                                context,
                                "数据恢复失败: ${it.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            },
            onDismiss = {
                selectedRestoreUri = null
            }
        )
    }

    // =====================================================
    // 隐私声明
    // =====================================================

    if (showPrivacyDialog) {
        MiuixPrivacyDialog {
            showPrivacyDialog = false
        }
    }

    // =====================================================
    // 恢复默认背景确认
    // =====================================================

    if (showClearBackgroundConfirm) {
        MiuixConfirmDialog(
            title = "恢复默认背景？",
            message = "清除自定义背景图片后，应用将恢复当前主题的默认背景。",
            confirmText = "恢复默认",
            onConfirm = {
                BackgroundPreferences.clear(context)
                showClearBackgroundConfirm = false

                Toast.makeText(
                    context,
                    "已恢复默认背景",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showClearBackgroundConfirm = false
            }
        )
    }

    // =====================================================
    // 更新历史
    // =====================================================

    if (showUpdateHistory) {
        MiuixUpdateHistoryDialog {
            showUpdateHistory = false
        }
    }

    // =====================================================
    // 应用说明
    // =====================================================

    if (showInstructions) {
        MiuixInstructionsDialog {
            showInstructions = false
        }
    }
}

// =========================================================
// 外部网页
// =========================================================

private fun openExternalPage(
    context: Context,
    url: String,
    errorMessage: String
) {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            url.toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            errorMessage,
            Toast.LENGTH_SHORT
        ).show()
    }
}

// =========================================================
// CSV 文件检查
// =========================================================

private fun isCsvFileUri(
    context: Context,
    uri: Uri
): Boolean {
    val displayName = try {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )

            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    return displayName?.endsWith(
        ".csv",
        ignoreCase = true
    ) == true
}

// =========================================================
// CSV 导入
// =========================================================

private fun <T> launchCsvImport(
    context: Context,
    viewModel: MainViewModel,
    uri: Uri?,
    typeName: String,
    parser: suspend (Context, Uri, Long) -> Result<List<T>>,
    saveRecords: (List<T>, (Result<Int>) -> Unit) -> Unit
) {
    val fileUri = uri ?: return

    if (!isCsvFileUri(context, fileUri)) {
        Toast.makeText(
            context,
            "请选择 .csv 格式的文件",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    val vehicleId = viewModel.currentVehicle.value?.id

    if (vehicleId == null) {
        Toast.makeText(
            context,
            "请先添加并选择车辆，再导入 CSV",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

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
                            if (result.isSuccess) {
                                Toast.LENGTH_SHORT
                            } else {
                                Toast.LENGTH_LONG
                            }
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

// =========================================================
// 定位权限状态
// =========================================================

private fun getLocationPermissionStatus(context: Context): String {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return when {
        fine -> "精确定位已开启"
        coarse -> "仅允许大致位置"
        else -> "定位权限未开启"
    }
}
