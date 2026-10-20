package com.fueltracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fueltracker.notif.ParkingNotification
import com.fueltracker.ui.ExpenseEditScreen
import com.fueltracker.ui.ExpenseScreen
import com.fueltracker.ui.FuelScreen
import com.fueltracker.ui.HomeScreen
import com.fueltracker.ui.InitialRecordScreen
import com.fueltracker.ui.ParkingScreen
import com.fueltracker.ui.RecordsScreen
import com.fueltracker.ui.SettingsScreen
import com.fueltracker.ui.VehicleScreen
import com.fueltracker.ui.miui.MiuiNavigationBar
import com.fueltracker.ui.miui.MiuiNavigationItem
import com.fueltracker.util.UpdateLogManager
import com.fueltracker.viewmodel.MainViewModel
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn

@Composable
fun FuelTrackerApp(
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    // ---------- 启动弹窗（局部状态，配置变更时重置无妨） ----------
    val startState = remember {
        UpdateLogManager.getStartState(context, BuildConfig.VERSION_CODE)
    }
    var showPrivacyOnStart by remember {
        mutableStateOf(startState is UpdateLogManager.StartState.Privacy)
    }
    var showUpdateLog by remember {
        mutableStateOf(startState is UpdateLogManager.StartState.UpdateLog)
    }

    // ---------- 导航状态从 ViewModel 读取 ----------
    val screen = viewModel.currentScreen
    val editingRecord = viewModel.editingRecord
    val editingVehicle = viewModel.editingVehicle
    val editingExpense = viewModel.editingExpense
    val editFuelOrigin = viewModel.editFuelOrigin
    val addVehicleOrigin = viewModel.addVehicleOrigin
    val expenseSelectedCategory = viewModel.expenseSelectedCategory

    val activeParking by viewModel.activeParking.collectAsState()

    LaunchedEffect(activeParking) {
        if (activeParking == null) {
            ParkingNotification.cancel(context)
        }
    }

    // ---------- 返回键处理 ----------
    BackHandler(enabled = screen != AppScreen.HOME) {
        when (screen) {
            AppScreen.RECORDS -> {
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.ADD_VEHICLE -> {
                viewModel.navigateTo(addVehicleOrigin)
            }

            AppScreen.INITIAL_RECORD -> {
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.ADD_FUEL -> {
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.EDIT_FUEL -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(editFuelOrigin)
            }

            AppScreen.EDIT_VEHICLE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.SETTINGS)
            }

            AppScreen.SETTINGS -> {
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.EXPENSES -> {
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.ADD_EXPENSE -> {
                viewModel.navigateTo(AppScreen.EXPENSES)
            }

            AppScreen.EDIT_EXPENSE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.EXPENSES)
            }

            AppScreen.ADD_PARKING -> {
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.EDIT_PARKING -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.HOME -> {
                // 不会到这里
            }
        }
    }

    val showBottomBar = screen == AppScreen.HOME
            || screen == AppScreen.RECORDS
            || screen == AppScreen.EXPENSES

    if (showBottomBar) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // 包裹底栏，加平滑动画
                androidx.compose.animation.AnimatedVisibility(
                    visible = viewModel.isBottomBarVisible,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    MiuiNavigationBar {
                        MiuiNavigationItem(
                            selected = screen == AppScreen.HOME,
                            onClick = { viewModel.navigateTo(AppScreen.HOME) },
                            icon = Icons.Default.Home,
                            label = "首页"
                        )

                        MiuiNavigationItem(
                            selected = screen == AppScreen.RECORDS,
                            onClick = { viewModel.navigateTo(AppScreen.RECORDS) },
                            icon = Icons.Default.LocalGasStation,
                            label = "记录"
                        )

                        MiuiNavigationItem(
                            selected = screen == AppScreen.EXPENSES,
                            onClick = { viewModel.navigateTo(AppScreen.EXPENSES) },
                            icon = Icons.Default.Receipt,
                            label = "费用"
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (screen) {
                    AppScreen.HOME -> {
                        val vehicle by viewModel.currentVehicle.collectAsState()
                        val records by viewModel.records.collectAsState()
                        val latest by viewModel.latestConsumption.collectAsState()
                        val average by viewModel.averageConsumption.collectAsState()

                        HomeScreen(
                            viewModel = viewModel,
                            vehicle = vehicle,
                            records = records,
                            latestConsumption = latest,
                            averageConsumption = average,
                            activeParking = activeParking,
                            onAddVehicle = {
                                viewModel.navigateToAddVehicle(AppScreen.HOME)
                            },
                            onAddFuel = {
                                viewModel.navigateTo(AppScreen.ADD_FUEL)
                            },
                            onAddParking = {
                                viewModel.navigateTo(AppScreen.ADD_PARKING)
                            },
                            onEditParking = { parking ->
                                viewModel.editExpenseRecord(
                                    parking,
                                    AppScreen.EDIT_PARKING,
                                    AppScreen.HOME
                                )
                            },
                            onClearParking = {
                                viewModel.clearActiveParking()
                                ParkingNotification.cancel(context)
                            },
                            onIgnoreParking = {
                                viewModel.ignoreActiveParkingNotification()
                                ParkingNotification.cancel(context)
                            },
                            onOpenSettings = {
                                viewModel.navigateTo(AppScreen.SETTINGS)
                            }
                        )
                    }

                    AppScreen.RECORDS -> {
                        RecordsScreen(
                            viewModel = viewModel,
                            onEdit = { record ->
                                viewModel.editFuel(record, AppScreen.RECORDS)
                            }
                        )
                    }

                    AppScreen.EXPENSES -> {
                        ExpenseScreen(
                            viewModel = viewModel,
                            selectedCategory = expenseSelectedCategory,
                            onSelectedCategoryChange = {
                                viewModel.updateExpenseSelectedCategory(it)
                            },
                            onAddExpense = {
                                viewModel.navigateTo(AppScreen.ADD_EXPENSE)
                            },
                            onEditExpense = { record ->
                                viewModel.editExpenseRecord(
                                    record,
                                    AppScreen.EDIT_EXPENSE,
                                    AppScreen.EXPENSES
                                )
                            },
                        onEditFuel = { record ->
                            viewModel.editFuel(record, AppScreen.EXPENSES)
                        })
                    }



                    else -> {
                        // 理论不可达（showBottomBar 已过滤），兜底不做任何事
                    }
                }
            }
        }
    } else {
        when (screen) {
            AppScreen.ADD_VEHICLE -> {
                VehicleScreen(
                    viewModel = viewModel,
                    vehicle = null,
                    onBack = { viewModel.navigateTo(addVehicleOrigin) },
                    onSaved = { viewModel.navigateTo(AppScreen.INITIAL_RECORD) }
                )
            }

            AppScreen.INITIAL_RECORD -> {
                InitialRecordScreen(
                    viewModel = viewModel,
                    onCompleted = { viewModel.navigateTo(AppScreen.HOME) },
                    onBack = { viewModel.navigateTo(AppScreen.HOME) }
                )
            }

            AppScreen.ADD_FUEL -> {
                FuelScreen(
                    viewModel = viewModel,
                    record = null,
                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                    onSaved = { viewModel.navigateTo(AppScreen.HOME) }
                )
            }

            AppScreen.EDIT_FUEL -> {

                if (editingRecord == null) {
                    viewModel.navigateTo(editFuelOrigin)
                } else {
                    FuelScreen(
                        viewModel = viewModel,
                        record = editingRecord,
                        onBack = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(editFuelOrigin)
                        },
                        onSaved = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(editFuelOrigin)
                        }
                    )
                }
            }

            AppScreen.EDIT_VEHICLE -> {

                if (editingVehicle == null) {
                    viewModel.navigateTo(AppScreen.SETTINGS)
                } else {
                    VehicleScreen(
                        viewModel = viewModel,
                        vehicle = editingVehicle,
                        onBack = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(AppScreen.SETTINGS)
                        },
                        onSaved = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(AppScreen.SETTINGS)
                        }
                    )
                }
            }

            AppScreen.SETTINGS -> {
                val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())

                SettingsScreen(
                    viewModel = viewModel,
                    vehicles = vehicles,
                    onSetCurrentVehicle = { vehicle ->
                        viewModel.setCurrentVehicle(vehicle)
                    },
                    onDeleteVehicle = { vehicle ->
                        viewModel.deleteVehicle(vehicle)
                    },
                    onAddVehicle = {
                        viewModel.navigateToAddVehicle(AppScreen.SETTINGS)
                    },
                    onEditVehicle = { vehicle ->
                        viewModel.editVehicle(vehicle)
                    },
                    onBack = { viewModel.navigateTo(AppScreen.HOME) }
                )
            }

            AppScreen.ADD_PARKING -> {
                ParkingScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                    onSaved = { viewModel.navigateTo(AppScreen.HOME) }
                )
            }

            AppScreen.EDIT_PARKING -> {
                if (editingExpense == null) {
                    viewModel.navigateTo(AppScreen.HOME)
                } else {
                    ParkingScreen(
                        viewModel = viewModel,
                        record = editingExpense,
                        onBack = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(AppScreen.HOME)
                        },
                        onSaved = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(AppScreen.HOME)
                        }
                    )
                }
            }

            AppScreen.ADD_EXPENSE -> {
                ExpenseEditScreen(
                    viewModel = viewModel,
                    record = null,
                    onBack = { viewModel.navigateTo(AppScreen.EXPENSES) },
                    onSaved = { viewModel.navigateTo(AppScreen.EXPENSES) }
                )
            }

            AppScreen.EDIT_EXPENSE -> {

                if (editingExpense == null) {
                    viewModel.navigateTo(AppScreen.EXPENSES)
                } else {
                    ExpenseEditScreen(
                        viewModel = viewModel,
                        record = editingExpense,
                        onBack = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(AppScreen.EXPENSES)
                        },
                        onSaved = {
                            viewModel.clearEditingStates()
                            viewModel.navigateTo(AppScreen.EXPENSES)
                        }
                    )
                }
            }

            else -> {
                // 理论不可达，兜底不做任何事
            }
        }
    }

    // ---------- 首次安装：隐私声明 ----------
    if (showPrivacyOnStart) {
        FirstRunPrivacyDialog(
            onDismiss = {
                showPrivacyOnStart = false
                UpdateLogManager.markShown(context, BuildConfig.VERSION_CODE)
            }
        )
    }

    // ---------- 版本升级：更新内容 ----------
    if (showUpdateLog) {
        val entries = (startState as? UpdateLogManager.StartState.UpdateLog)?.entries.orEmpty()
        if (entries.isNotEmpty()) {
            UpdateLogDialog(
                entries = entries,
                onDismiss = {
                    showUpdateLog = false
                    UpdateLogManager.markShown(context, BuildConfig.VERSION_CODE)
                }
            )
        }
    }
}

enum class AppScreen {
    HOME,
    ADD_VEHICLE,
    INITIAL_RECORD,
    ADD_FUEL,
    RECORDS,
    EDIT_FUEL,
    EDIT_VEHICLE,
    SETTINGS,
    EXPENSES,
    ADD_EXPENSE,
    EDIT_EXPENSE,
    ADD_PARKING,
    EDIT_PARKING
}

// ============================================================
// 更新内容弹窗
// ============================================================

@Composable
private fun UpdateLogDialog(
    entries: List<UpdateLogManager.UpdateLogEntry>,
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
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f,fill = false),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    entries.forEach { entry ->
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
                        text = "我知道了",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ============================================================
// 首次安装：隐私声明
// ============================================================

@Composable
private fun FirstRunPrivacyDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            // 必须点"我已了解"才能关
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
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
                    text = "隐私权限声明",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "使用本应用前，请先了解以下内容",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacyItem(
                        title = "数据存储",
                        body = "所有数据（车辆、加油、费用）全部保存在本机，不会上传到任何服务器。"
                    )
                    PrivacyItem(
                        title = "信息收集",
                        body = "本应用不收集、不读取、不分享您的任何个人信息。"
                    )
                    PrivacyItem(
                        title = "网络访问",
                        body = "应用本身完全离线。仅在您主动点击\"查看更新\"时打开浏览器跳转网盘。"
                    )
                    PrivacyItem(
                        title = "位置权限",
                        body = "记停车时如需自动填写位置名称，会请求一次系统定位，仅用于填充输入框。"
                    )
                    PrivacyItem(
                        title = "备份提示",
                        body = "卸载应用会删除所有本地数据，请在卸载前导出备份文件。"
                    )
                }

                Spacer(Modifier.height(24.dp))

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
private fun PrivacyItem(
    title: String,
    body: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}