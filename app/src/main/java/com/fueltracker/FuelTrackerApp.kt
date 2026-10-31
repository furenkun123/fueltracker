
package com.fueltracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
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
import com.fueltracker.ui.components.PrivacyDialog
import com.fueltracker.ui.components.UpdateLogDialog
import com.fueltracker.ui.miui.MiuiNavigationBar
import com.fueltracker.ui.miui.MiuiNavigationItem
import com.fueltracker.ui.miui.MiuiTopBar
import com.fueltracker.ui.miui.MiuiTopBarIconButton
import com.fueltracker.util.UpdateLogManager
import com.fueltracker.viewmodel.MainViewModel
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

    LaunchedEffect(activeParking) {
        if (activeParking == null) {
            ParkingNotification.cancel(context)
        }
    }

    // ---------- 页面配置 ----------
    val showBottomBar =
        screen == AppScreen.HOME ||
                screen == AppScreen.RECORDS ||
                screen == AppScreen.EXPENSES

    val isGlassEnabled = viewModel.isGlassEffectEnabled

    // ---------- 返回键处理 ----------
    BackHandler(enabled = screen != AppScreen.HOME) {
        when (screen) {
            AppScreen.RECORDS ->
                viewModel.navigateTo(AppScreen.HOME)

            AppScreen.ADD_VEHICLE ->
                viewModel.navigateTo(addVehicleOrigin)

            AppScreen.INITIAL_RECORD ->
                viewModel.navigateTo(AppScreen.HOME)

            AppScreen.ADD_FUEL ->
                viewModel.navigateTo(AppScreen.HOME)

            AppScreen.EDIT_FUEL -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(editFuelOrigin)
            }

            AppScreen.EDIT_VEHICLE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.SETTINGS)
            }

            AppScreen.SETTINGS ->
                viewModel.navigateTo(AppScreen.HOME)

            AppScreen.EXPENSES ->
                viewModel.navigateTo(AppScreen.HOME)

            AppScreen.ADD_EXPENSE ->
                viewModel.navigateTo(AppScreen.EXPENSES)

            AppScreen.EDIT_EXPENSE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.EXPENSES)
            }

            AppScreen.ADD_PARKING ->
                viewModel.navigateTo(AppScreen.HOME)

            AppScreen.EDIT_PARKING -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.HOME)
            }

            AppScreen.HOME -> Unit
        }
    }

    // ============================================================
    // 全 App 内容层
    // ============================================================
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(appBackdrop)
        ) {
            if (showBottomBar) {

                // ---------- 主页面 Scaffold ----------
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { padding ->

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (screen) {

                            // ---------- 首页 ----------
                            AppScreen.HOME -> {
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
                                    averageConsumption = average,
                                    activeParking = activeParking,

                                    onAddVehicle = {
                                        viewModel.navigateToAddVehicle(
                                            AppScreen.HOME
                                        )
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

                                    onCollapseProgressChange = {
                                        homeCollapseProgress = it
                                    }
                                )
                            }

                            // ---------- 加油记录 ----------
                            AppScreen.RECORDS -> {
                                RecordsScreen(
                                    viewModel = viewModel,
                                    onEdit = { record ->
                                        viewModel.editFuel(
                                            record,
                                            AppScreen.RECORDS
                                        )
                                    }
                                )
                            }

                            // ---------- 费用列表 ----------
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
                                        viewModel.editFuel(
                                            record,
                                            AppScreen.EXPENSES
                                        )
                                    }

                                )
                            }

                            else -> Unit
                        }
                    }
                }
            } else {

                // ---------- 非底部导航页面 ----------
                when (screen) {

                    // ---------- 添加车辆 ----------
                    AppScreen.ADD_VEHICLE -> {
                        VehicleScreen(
                            viewModel = viewModel,
                            vehicle = null,
                            onBack = {
                                viewModel.navigateTo(addVehicleOrigin)
                            },
                            onSaved = {
                                viewModel.navigateTo(AppScreen.INITIAL_RECORD)
                            }
                        )
                    }

                    // ---------- 初始记录 ----------
                    AppScreen.INITIAL_RECORD -> {
                        InitialRecordScreen(
                            viewModel = viewModel,
                            backdrop = appBackdrop,
                            onCompleted = {
                                viewModel.navigateTo(AppScreen.HOME)
                            },
                            onBack = {
                                viewModel.navigateTo(AppScreen.HOME)
                            }
                        )
                    }

                    // ---------- 添加加油记录 ----------
                    AppScreen.ADD_FUEL -> {
                        FuelScreen(
                            viewModel = viewModel,
                            record = null,
                            onBack = {
                                viewModel.navigateTo(AppScreen.HOME)
                            },
                            onSaved = {
                                viewModel.navigateTo(AppScreen.HOME)
                            }
                        )
                    }

                    // ---------- 编辑加油记录 ----------
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

                    // ---------- 编辑车辆 ----------
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

                    // ---------- 设置 ----------
                    AppScreen.SETTINGS -> {
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
                                viewModel.navigateToAddVehicle(AppScreen.SETTINGS)
                            },

                            onEditVehicle = { vehicle ->
                                viewModel.editVehicle(vehicle)
                            },

                            onBack = {
                                viewModel.navigateTo(AppScreen.HOME)
                            }
                        )
                    }

                    // ---------- 添加停车记录 ----------
                    AppScreen.ADD_PARKING -> {
                        ParkingScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.navigateTo(AppScreen.HOME)
                            },
                            onSaved = {
                                viewModel.navigateTo(AppScreen.HOME)
                            }
                        )
                    }

                    // ---------- 编辑停车记录 ----------
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

                    // ---------- 添加费用 ----------
                    AppScreen.ADD_EXPENSE -> {
                        ExpenseEditScreen(
                            viewModel = viewModel,
                            record = null,
                            onBack = {
                                viewModel.navigateTo(AppScreen.EXPENSES)
                            },
                            onSaved = {
                                viewModel.navigateTo(AppScreen.EXPENSES)
                            },
                        )
                    }

                    // ---------- 编辑费用 ----------
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
                                },
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
        MiuiTopBar(
            backdrop = appBackdrop,
            isGlassEnabled = isGlassEnabled,
            showActionArea = screen == AppScreen.HOME,
            action = if (screen == AppScreen.HOME) {
                {
                    MiuiTopBarIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "设置",
                        onClick = {
                            viewModel.navigateTo(AppScreen.SETTINGS)
                        }
                    )
                }
            } else {
                null
            }
        )

        // ---------- 悬浮底部导航栏 ----------
        if (showBottomBar) {
            MiuiNavigationBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                backdrop = appBackdrop,
                isGlassEnabled = isGlassEnabled,
                isFloating = viewModel.isFloatingBottomBar
            ) {
                MiuiNavigationItem(
                    selected = screen == AppScreen.HOME,
                    onClick = {
                        viewModel.navigateTo(AppScreen.HOME)
                    },
                    icon = Icons.Default.Home,
                    label = "首页"
                )

                MiuiNavigationItem(
                    selected = screen == AppScreen.RECORDS,
                    onClick = {
                        viewModel.navigateTo(AppScreen.RECORDS)
                    },
                    icon = Icons.Default.LocalGasStation,
                    label = "记录"
                )

                MiuiNavigationItem(
                    selected = screen == AppScreen.EXPENSES,
                    onClick = {
                        viewModel.navigateTo(AppScreen.EXPENSES)
                    },
                    icon = Icons.Default.Receipt,
                    label = "费用"
                )
            }
        }
    }

    // ---------- 首次安装：隐私声明 ----------
    if (showPrivacyOnStart) {
        PrivacyDialog(
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
            UpdateLogDialog(
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
