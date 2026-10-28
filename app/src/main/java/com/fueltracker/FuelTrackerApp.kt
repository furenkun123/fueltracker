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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.fueltracker.util.UpdateLogManager
import com.fueltracker.viewmodel.MainViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun FuelTrackerApp(
    viewModel: MainViewModel
) {
    val context = LocalContext.current

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

    // ---------- 返回键处理 ----------
    BackHandler(enabled = screen != AppScreen.HOME) {
        when (screen) {
            AppScreen.RECORDS -> viewModel.navigateTo(AppScreen.HOME)
            AppScreen.ADD_VEHICLE -> viewModel.navigateTo(addVehicleOrigin)
            AppScreen.INITIAL_RECORD -> viewModel.navigateTo(AppScreen.HOME)
            AppScreen.ADD_FUEL -> viewModel.navigateTo(AppScreen.HOME)
            AppScreen.EDIT_FUEL -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(editFuelOrigin)
            }
            AppScreen.EDIT_VEHICLE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.SETTINGS)
            }
            AppScreen.SETTINGS -> viewModel.navigateTo(AppScreen.HOME)
            AppScreen.EXPENSES -> viewModel.navigateTo(AppScreen.HOME)
            AppScreen.ADD_EXPENSE -> viewModel.navigateTo(AppScreen.EXPENSES)
            AppScreen.EDIT_EXPENSE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.EXPENSES)
            }
            AppScreen.ADD_PARKING -> viewModel.navigateTo(AppScreen.HOME)
            AppScreen.EDIT_PARKING -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.HOME)
            }
            AppScreen.HOME -> {}
        }
    }

    val showBottomBar = screen == AppScreen.HOME || screen == AppScreen.RECORDS || screen == AppScreen.EXPENSES
    val isGlassEnabled = viewModel.isGlassEffectEnabled

    val hazeState = rememberHazeState()

    if (showBottomBar) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding -> // 👈 补回 Scaffold 的 lambda 参数
            // 最外层的 Box 负责定位
            Box(modifier = Modifier.fillMaxSize()) {

                // 1. 被模糊的内容区（必须包含 padding）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .hazeSource(hazeState)

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
                                onAddVehicle = { viewModel.navigateToAddVehicle(AppScreen.HOME) },
                                onAddFuel = { viewModel.navigateTo(AppScreen.ADD_FUEL) },
                                onAddParking = { viewModel.navigateTo(AppScreen.ADD_PARKING) },
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
                                onOpenSettings = { viewModel.navigateTo(AppScreen.SETTINGS) },

                                hazeState = hazeState,
                                isGlassEnabled = isGlassEnabled
                            )
                        }

                        AppScreen.RECORDS -> {
                            RecordsScreen(
                                viewModel = viewModel,
                                hazeState = hazeState, // 👈 传入
                                isGlassEnabled = isGlassEnabled,
                                onEdit = { record -> viewModel.editFuel(record, AppScreen.RECORDS) }
                            )
                        }

                        AppScreen.EXPENSES -> {
                            ExpenseScreen(
                                viewModel = viewModel,
                                selectedCategory = expenseSelectedCategory,
                                onSelectedCategoryChange = {
                                    viewModel.updateExpenseSelectedCategory(it)
                                },
                                onAddExpense = { viewModel.navigateTo(AppScreen.ADD_EXPENSE) },
                                onEditExpense = { record ->
                                    viewModel.editExpenseRecord(
                                        record,
                                        AppScreen.EDIT_EXPENSE,
                                        AppScreen.EXPENSES
                                    )
                                },
                                hazeState = hazeState, // 👈 传入
                                isGlassEnabled = isGlassEnabled,
                                onEditFuel = { record ->
                                    viewModel.editFuel(record, AppScreen.EXPENSES)
                                }
                            )
                        }

                        else -> {}
                    }
                } // 👈 内容区结束（hazeSource 的 Box 闭合）

                // 2. 悬浮底栏（放在 hazeSource 的外部，最外层 Box 的内部）
                MiuiNavigationBar(
                    modifier = Modifier.align(Alignment.BottomCenter), // 居中对齐
                    hazeState = hazeState,
                    isGlassEnabled = isGlassEnabled,
                    isFloating = viewModel.isFloatingBottomBar
                ) {
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

            } // 👈 最外层 Box 闭合
        } // 👈 Scaffold 闭合
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
                    onSetCurrentVehicle = { vehicle -> viewModel.setCurrentVehicle(vehicle) },
                    onDeleteVehicle = { vehicle -> viewModel.deleteVehicle(vehicle) },
                    onAddVehicle = { viewModel.navigateToAddVehicle(AppScreen.SETTINGS) },
                    onEditVehicle = { vehicle -> viewModel.editVehicle(vehicle) },
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
            else -> {}
        }
    }

    // ---------- 首次安装：隐私声明 ----------
    if (showPrivacyOnStart) {
        PrivacyDialog(
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
    HOME, ADD_VEHICLE, INITIAL_RECORD, ADD_FUEL, RECORDS,
    EDIT_FUEL, EDIT_VEHICLE, SETTINGS, EXPENSES,
    ADD_EXPENSE, EDIT_EXPENSE, ADD_PARKING, EDIT_PARKING
}