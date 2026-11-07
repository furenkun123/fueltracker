package com.fueltracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.fueltracker.AppScreen.*
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
import com.fueltracker.ui.miuix.dialogs.MiuixPrivacyDialog
import com.fueltracker.ui.miuix.dialogs.MiuixUpdateLogDialog
import com.fueltracker.ui.miuix.navigation.MiuixAppNavigationBar
import com.fueltracker.ui.miuix.navigation.NavigationBackdropDetector
import com.fueltracker.ui.miuix.navigation.AppNavItem
import com.fueltracker.ui.miuix.MiuixTopBar
import com.fueltracker.ui.miuix.MiuixTopBarIconButton
import com.fueltracker.ui.miuix.navigation.NavigationBackdropDetector
import com.fueltracker.util.UpdateLogManager
import com.fueltracker.viewmodel.MainViewModel
import com.fueltracker.viewmodel.ParkingNotificationState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun FuelTrackerApp(
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    var homeCollapseProgress by remember {
        mutableFloatStateOf(0f)
    }

    val appBackdrop = rememberLayerBackdrop()
    val navigationBackdropDetector = remember {
        NavigationBackdropDetector()
    }

    // ---------- 启动弹窗 ----------
    val startState = remember {
        UpdateLogManager.getStartState(context, BuildConfig.VERSION_CODE)
    }

    var showPrivacyOnStart by remember {
        mutableStateOf(startState is UpdateLogManager.StartState.Privacy)
    }

    var showUpdateLog by remember {
        mutableStateOf(startState is UpdateLogManager.StartState.UpdateLog)
    }

    // ---------- 导航状态 ----------
    val screen = viewModel.currentScreen
    val editingRecord = viewModel.editingRecord
    val editingVehicle = viewModel.editingVehicle
    val editingExpense = viewModel.editingExpense
    val editFuelOrigin = viewModel.editFuelOrigin
    val addVehicleOrigin = viewModel.addVehicleOrigin
    val expenseSelectedCategory = viewModel.expenseSelectedCategory

    val activeParking by viewModel.activeParking.collectAsState()
    val parkingNotificationState by viewModel.parkingNotificationState.collectAsState()

    LaunchedEffect(parkingNotificationState) {
        when (val s = parkingNotificationState) {
            ParkingNotificationState.NotReady -> Unit  // 加载中，不动现有通知
            ParkingNotificationState.Hidden -> ParkingNotification.cancel(context)
            is ParkingNotificationState.Visible -> ParkingNotification.show(
                context = context,
                vehicleId = s.vehicleId,
                locationName = s.locationName,
                floor = s.floor,
                area = s.area,
                spotNumber = s.spotNumber
            )
        }
    }

    // ---------- 页面配置 ----------
    val showBottomBar =
        screen == HOME ||
                screen == RECORDS ||
                screen == EXPENSES

    val isGlassEnabled = viewModel.isGlassEffectEnabled

    // ---------- 统一返回逻辑 ----------
    val handleBack: () -> Unit = {
        when (screen) {
            RECORDS,
            SETTINGS,
            EXPENSES,
            INITIAL_RECORD,
            ADD_FUEL,
            ADD_PARKING,
            EDIT_PARKING ->
                viewModel.navigateTo(HOME)

            ADD_VEHICLE ->
                viewModel.navigateTo(addVehicleOrigin)

            EDIT_FUEL -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(editFuelOrigin)
            }

            EDIT_VEHICLE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(SETTINGS)
            }

            ADD_EXPENSE ->
                viewModel.navigateTo(EXPENSES)

            EDIT_EXPENSE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(EXPENSES)
            }

            HOME -> Unit
        }
    }

    BackHandler(
        enabled = screen != HOME,
        onBack = handleBack
    )

    // ============================================================
    // 全 App 内容层
    // ============================================================
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .layerBackdrop(appBackdrop)
        ) {
            if (showBottomBar) {

                // ---------- 主页面 Scaffold ----------
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { padding ->

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (screen) {

                            // ---------- 首页 ----------
                            HOME -> {
                                val vehicle by
                                viewModel.currentVehicle.collectAsState()

                                val records by
                                viewModel.records.collectAsState()

                                val latest by
                                viewModel.latestConsumption.collectAsState()

                                val average by
                                viewModel.averageConsumption.collectAsState()

                                HomeScreen(
                                    viewModel = viewModel,
                                    vehicle = vehicle,
                                    records = records,
                                    latestConsumption = latest,
                                    activeParking = activeParking,
                                    navigationBackdropDetector = navigationBackdropDetector,

                                    onAddVehicle = {
                                        viewModel.navigateToAddVehicle(HOME)
                                    },

                                    onAddFuel = {
                                        viewModel.navigateTo(ADD_FUEL)
                                    },

                                    onAddParking = {
                                        viewModel.navigateTo(ADD_PARKING)
                                    },

                                    onEditParking = { parking ->
                                        viewModel.editExpenseRecord(
                                            parking,
                                            EDIT_PARKING,
                                            HOME
                                        )
                                    },

                                    onClearParking = {
                                        viewModel.clearActiveParking()
                                    },

                                    onIgnoreParking = {
                                        viewModel.ignoreActiveParkingNotification()
                                    },

                                    onCollapseProgressChange = {
                                        homeCollapseProgress = it
                                    }
                                )
                            }

                            // ---------- 加油记录 ----------
                            RECORDS -> {
                                RecordsScreen(
                                    viewModel = viewModel,
                                    navigationBackdropDetector = navigationBackdropDetector,
                                    onEdit = { record ->
                                        viewModel.editFuel(
                                            record,
                                            RECORDS
                                        )
                                    }
                                )
                            }

                            // ---------- 费用列表 ----------
                            EXPENSES -> {
                                ExpenseScreen(
                                    viewModel = viewModel,
                                    navigationBackdropDetector = navigationBackdropDetector,
                                    selectedCategory = expenseSelectedCategory,

                                    onSelectedCategoryChange = {
                                        viewModel.updateExpenseSelectedCategory(it)
                                    },

                                    onAddExpense = {
                                        viewModel.navigateTo(ADD_EXPENSE)
                                    },

                                    onEditExpense = { record ->
                                        viewModel.editExpenseRecord(
                                            record,
                                            EDIT_EXPENSE,
                                            EXPENSES
                                        )
                                    },

                                    onEditFuel = { record ->
                                        viewModel.editFuel(
                                            record,
                                            EXPENSES
                                        )
                                    }
                                )
                            }

                        }
                    }
                }
            } else {

                // ---------- 非底部导航页面 ----------
                when (screen) {

                    // ---------- 添加车辆 ----------
                    ADD_VEHICLE -> {
                        VehicleScreen(
                            viewModel = viewModel,
                            vehicle = null,
                            onBack = {
                                viewModel.navigateTo(addVehicleOrigin)
                            },
                            onSaved = {
                                viewModel.navigateTo(INITIAL_RECORD)
                            }
                        )
                    }

                    // ---------- 初始记录 ----------
                    INITIAL_RECORD -> {
                        InitialRecordScreen(
                            viewModel = viewModel,
                            onCompleted = {
                                viewModel.navigateTo(HOME)
                            },
                            onBack = {
                                viewModel.navigateTo(HOME)
                            }
                        )
                    }

                    // ---------- 添加加油记录 ----------
                    ADD_FUEL -> {
                        FuelScreen(
                            viewModel = viewModel,
                            record = null,
                            onBack = {
                                viewModel.navigateTo(HOME)
                            },
                            onSaved = {
                                viewModel.navigateTo(HOME)
                            }
                        )
                    }

                    // ---------- 编辑加油记录 ----------
                    EDIT_FUEL -> {
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
                                },
                                onDelete = {
                                    viewModel.deleteRecord(editingRecord)
                                    viewModel.clearEditingStates()
                                    viewModel.navigateTo(editFuelOrigin)
                                }
                            )
                        }
                    }

                    // ---------- 编辑车辆 ----------
                    EDIT_VEHICLE -> {
                        if (editingVehicle == null) {
                            viewModel.navigateTo(SETTINGS)
                        } else {
                            VehicleScreen(
                                viewModel = viewModel,
                                vehicle = editingVehicle,
                                onBack = {
                                    viewModel.clearEditingStates()
                                    viewModel.navigateTo(SETTINGS)
                                },
                                onSaved = {
                                    viewModel.clearEditingStates()
                                    viewModel.navigateTo(SETTINGS)
                                }
                            )
                        }
                    }

                    // ---------- 设置 ----------
                    SETTINGS -> {
                        val vehicles by
                        viewModel.vehicles.collectAsState(initial = emptyList())

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
                                viewModel.navigateToAddVehicle(SETTINGS)
                            },

                            onEditVehicle = { vehicle ->
                                viewModel.editVehicle(vehicle)
                            },

                            onBack = {
                                viewModel.navigateTo(HOME)
                            }
                        )
                    }

                    // ---------- 添加停车记录 ----------
                    ADD_PARKING -> {
                        ParkingScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.navigateTo(HOME)
                            },
                            onSaved = {
                                viewModel.navigateTo(HOME)
                            }
                        )
                    }

                    // ---------- 编辑停车记录 ----------
                    EDIT_PARKING -> {
                        if (editingExpense == null) {
                            viewModel.navigateTo(HOME)
                        } else {
                            ParkingScreen(
                                viewModel = viewModel,
                                record = editingExpense,
                                onBack = {
                                    viewModel.clearEditingStates()
                                    viewModel.navigateTo(HOME)
                                },
                                onSaved = {
                                    viewModel.clearEditingStates()
                                    viewModel.navigateTo(HOME)
                                }
                            )
                        }
                    }

                    // ---------- 添加费用 ----------
                    ADD_EXPENSE -> {
                        ExpenseEditScreen(
                            viewModel = viewModel,
                            record = null,
                            onBack = {
                                viewModel.navigateTo(EXPENSES)
                            },
                            onSaved = {
                                viewModel.navigateTo(EXPENSES)
                            },
                        )
                    }

                    // ---------- 编辑费用 ----------
                    EDIT_EXPENSE -> {
                        if (editingExpense == null) {
                            viewModel.navigateTo(EXPENSES)
                        } else {
                            ExpenseEditScreen(
                                viewModel = viewModel,
                                record = editingExpense,
                                onBack = {
                                    viewModel.clearEditingStates()
                                    viewModel.navigateTo(EXPENSES)
                                },
                                onSaved = {
                                    viewModel.clearEditingStates()
                                    viewModel.navigateTo(EXPENSES)
                                },
                            )
                        }
                    }

                }
            }
        }

        MiuixTopBar(
            backdrop = appBackdrop,
            isGlassEnabled = isGlassEnabled,

            // 只有主页面显示设置按钮
            showActionArea = screen == HOME,

            navigationIcon = null,

            action = if (screen == HOME) {
                {
                    MiuixTopBarIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "设置",
                        onClick = {
                            viewModel.navigateTo(SETTINGS)
                        }
                    )
                }
            } else {
                null
            }
        )

        // ---------- 悬浮底部导航栏 ----------
        if (showBottomBar) {
            MiuixAppNavigationBar(
                selectedIndex = when (screen) {
                    HOME -> 0
                    RECORDS -> 1
                    EXPENSES -> 2
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                backdrop = appBackdrop,
                isGlassEnabled = isGlassEnabled,
                isFloating = viewModel.isFloatingBottomBar,

                onSelectedIndexChange = { index ->
                    val targetScreen = when (index) {
                        0 -> HOME
                        1 -> RECORDS
                        2 -> EXPENSES
                        else -> HOME
                    }

                    if (screen != targetScreen) {
                        viewModel.navigateTo(targetScreen)
                    }
                },
                navigationBackdropDetector = navigationBackdropDetector,
                items = listOf(
                    AppNavItem(Icons.Default.Home, "首页"),
                    AppNavItem(Icons.Default.LocalGasStation, "记录"),
                    AppNavItem(Icons.Default.Receipt, "费用")
                )
            )
        }
    }

    // ---------- 首次安装：隐私声明 ----------
    if (showPrivacyOnStart) {
        MiuixPrivacyDialog(
            onDismiss = {
                showPrivacyOnStart = false
                UpdateLogManager.markShown(
                    context,
                    BuildConfig.VERSION_CODE
                )
            }
        )
    }

    // ---------- 版本升级：更新内容 ----------
    if (showUpdateLog) {
        val entries =
            (startState as? UpdateLogManager.StartState.UpdateLog)
                ?.entries
                .orEmpty()

        if (entries.isNotEmpty()) {
            MiuixUpdateLogDialog(
                entries = entries,
                onDismiss = {
                    showUpdateLog = false
                    UpdateLogManager.markShown(
                        context,
                        BuildConfig.VERSION_CODE
                    )
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
