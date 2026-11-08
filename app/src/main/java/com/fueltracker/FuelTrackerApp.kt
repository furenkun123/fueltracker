package com.fueltracker

import android.graphics.ImageDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.fueltracker.AppScreen.*
import com.fueltracker.notif.ParkingNotification
import com.fueltracker.ui.ExpenseEditScreen
import com.fueltracker.ui.ExpenseScreen
import com.fueltracker.ui.FuelScreen
import com.fueltracker.ui.HomeScreen
import com.fueltracker.ui.ParkingScreen
import com.fueltracker.ui.RecordsScreen
import com.fueltracker.ui.SettingsScreen
import com.fueltracker.ui.VehicleScreen
import com.fueltracker.ui.miuix.MiuixTopBar
import com.fueltracker.ui.miuix.MiuixTopBarIconButton
import com.fueltracker.ui.miuix.dialogs.MiuixPrivacyDialog
import com.fueltracker.ui.miuix.dialogs.MiuixUpdateLogDialog
import com.fueltracker.ui.miuix.navigation.AppNavItem
import com.fueltracker.ui.miuix.navigation.MiuixAppNavigationBar
import com.fueltracker.ui.theme.BackgroundPreferences
import com.fueltracker.ui.theme.LocalBackgroundTitleColor
import com.fueltracker.ui.theme.rememberAdaptiveTitleColor
import com.fueltracker.util.UpdateLogManager
import com.fueltracker.viewmodel.MainViewModel
import com.fueltracker.viewmodel.ParkingNotificationState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun FuelTrackerApp(
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    var homeCollapseProgress by remember {
        mutableFloatStateOf(0f)
    }

    val appBackdrop = rememberLayerBackdrop()

    // 个性化背景
    val backgroundUri = BackgroundPreferences.backgroundUri
    val windowSize = LocalWindowInfo.current.containerSize

    val targetWidthPx = windowSize.width.coerceAtLeast(1)
    val targetHeightPx = windowSize.height.coerceAtLeast(1)

    val backgroundBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = backgroundUri,
        key2 = targetWidthPx,
        key3 = targetHeightPx
    ) {
        value = if (backgroundUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(
                        context.contentResolver,
                        backgroundUri
                    )

                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val sourceWidth = info.size.width.coerceAtLeast(1)
                        val sourceHeight = info.size.height.coerceAtLeast(1)

                        val scale = maxOf(
                            targetWidthPx.toFloat() / sourceWidth,
                            targetHeightPx.toFloat() / sourceHeight
                        )

                        decoder.setTargetSize(
                            (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                        )

                        decoder.isMutableRequired = false
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
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
            ParkingNotificationState.NotReady -> Unit
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
    val adaptiveTitleColor = if (showBottomBar) rememberAdaptiveTitleColor(backgroundBitmap) else null

    val isGlassEnabled = viewModel.isGlassEffectEnabled

    // ✅ Pager 的核心状态提升到顶层
    val mainScreens = listOf(HOME, RECORDS, EXPENSES)
    val initialPage = mainScreens.indexOf(screen).takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = initialPage) { mainScreens.size }
    val scope = rememberCoroutineScope()

    // 1. 监听外部 screen 改变，驱动 Pager 滑动
    LaunchedEffect(screen) {
        val targetIndex = mainScreens.indexOf(screen)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    // 2. 监听 Pager 滑动结束，同步 screen 状态到 ViewModel
    LaunchedEffect(pagerState.settledPage) {
        val targetScreen = mainScreens[pagerState.settledPage]
        if (screen != targetScreen) {
            viewModel.navigateTo(targetScreen)
        }
    }

    // ---------- 统一返回逻辑 ----------
    val handleBack: () -> Unit = {
        when (screen) {
            RECORDS, SETTINGS, EXPENSES, ADD_FUEL, ADD_PARKING, EDIT_PARKING ->
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
    CompositionLocalProvider(
        LocalBackgroundTitleColor provides adaptiveTitleColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 最底层：图片背景 + 遮罩
            if (backgroundBitmap != null) {
                Image(
                    bitmap = backgroundBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = BackgroundPreferences.scrimAlpha))
                )
            }

            // 2. Backdrop 采集层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (backgroundBitmap == null) {
                            // ✅ 如果没有背景图，页面底色给浅灰
                            if (MiuixTheme.colorScheme.background.luminance() > 0.5f) {
                                Color(0xFFF2F2F7) // 页面背景（浅灰）
                            } else {
                                MiuixTheme.colorScheme.background // 深色模式
                            }
                        } else {
                            Color.Transparent // 有背景图时透明
                        }
                    )
                    .layerBackdrop(appBackdrop)
            ) {
                if (showBottomBar) {
                    // 主页面 Pager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) { page ->
                        when (page) {
                            0 -> { // 首页
                                val vehicle by viewModel.currentVehicle.collectAsState()
                                val records by viewModel.records.collectAsState()
                                val latest by viewModel.latestConsumption.collectAsState()

                                HomeScreen(
                                    viewModel = viewModel,
                                    vehicle = vehicle,
                                    records = records,
                                    latestConsumption = latest,
                                    activeParking = activeParking,
                                    onAddVehicle = { viewModel.navigateToAddVehicle(HOME) },
                                    onAddFuel = { viewModel.navigateTo(ADD_FUEL) },
                                    onAddParking = { viewModel.navigateTo(ADD_PARKING) },
                                    onEditParking = { parking ->
                                        viewModel.editExpenseRecord(parking, EDIT_PARKING, HOME)
                                    },
                                    onClearParking = { viewModel.clearActiveParking() },
                                    onIgnoreParking = { viewModel.ignoreActiveParkingNotification() },
                                    onCollapseProgressChange = { homeCollapseProgress = it }
                                )
                            }
                            1 -> { // 加油记录
                                RecordsScreen(
                                    viewModel = viewModel,
                                    onEdit = { record -> viewModel.editFuel(record, RECORDS) }
                                )
                            }
                            2 -> { // 费用列表
                                ExpenseScreen(
                                    viewModel = viewModel,
                                    selectedCategory = expenseSelectedCategory,
                                    onSelectedCategoryChange = { viewModel.updateExpenseSelectedCategory(it) },
                                    onAddExpense = { viewModel.navigateTo(ADD_EXPENSE) },
                                    onEditExpense = { record ->
                                        viewModel.editExpenseRecord(record, EDIT_EXPENSE, EXPENSES)
                                    },
                                    onEditFuel = { record ->
                                        viewModel.editFuel(record, EXPENSES)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // 非底部导航页面
                    when (screen) {
                        ADD_VEHICLE -> {
                            VehicleScreen(
                                viewModel = viewModel,
                                vehicle = null,
                                onBack = { viewModel.navigateTo(addVehicleOrigin) },
                                onSaved = { viewModel.navigateTo(HOME) }
                            )
                        }
                        ADD_FUEL -> {
                            FuelScreen(
                                viewModel = viewModel,
                                record = null,
                                onBack = { viewModel.navigateTo(HOME) },
                                onSaved = { viewModel.navigateTo(HOME) }
                            )
                        }
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
                        SETTINGS -> {
                            val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
                            SettingsScreen(
                                viewModel = viewModel,
                                vehicles = vehicles,
                                onSetCurrentVehicle = { viewModel.setCurrentVehicle(it) },
                                onDeleteVehicle = { viewModel.deleteVehicle(it) },
                                onAddVehicle = { viewModel.navigateToAddVehicle(SETTINGS) },
                                onEditVehicle = { viewModel.editVehicle(it) },
                                onBack = { viewModel.navigateTo(HOME) }
                            )
                        }
                        ADD_PARKING -> {
                            ParkingScreen(
                                viewModel = viewModel,
                                onBack = { viewModel.navigateTo(HOME) },
                                onSaved = { viewModel.navigateTo(HOME) }
                            )
                        }
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
                        ADD_EXPENSE -> {
                            ExpenseEditScreen(
                                viewModel = viewModel,
                                record = null,
                                onBack = { viewModel.navigateTo(EXPENSES) },
                                onSaved = { viewModel.navigateTo(EXPENSES) }
                            )
                        }
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
                                    }
                                )
                            }
                        }
                    }
                }
            } // ✅ 闭合采集层

            // 3. 顶栏
            MiuixTopBar(
                backdrop = appBackdrop,
                isGlassEnabled = isGlassEnabled,
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
                } else null
            )

            // 4. 底部导航栏
            if (showBottomBar) {
                MiuixAppNavigationBar(
                    selectedIndex = when (screen) {
                        HOME -> 0
                        RECORDS -> 1
                        EXPENSES -> 2
                        else -> 0
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    backdrop = appBackdrop,
                    isGlassEnabled = isGlassEnabled,
                    isFloating = viewModel.isFloatingBottomBar,
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    items = listOf(
                        AppNavItem(Icons.Default.Home, "首页"),
                        AppNavItem(Icons.Default.LocalGasStation, "记录"),
                        AppNavItem(Icons.Default.Receipt, "费用")
                    )
                )
            }
        } // ✅ 闭合最外层 Box
    } // ✅ 闭合 CompositionLocalProvider

    // ---------- 弹窗 ----------
    if (showPrivacyOnStart) {
        MiuixPrivacyDialog(
            onDismiss = {
                showPrivacyOnStart = false
                UpdateLogManager.markShown(context, BuildConfig.VERSION_CODE)
            }
        )
    }

    if (showUpdateLog) {
        val entries = (startState as? UpdateLogManager.StartState.UpdateLog)?.entries.orEmpty()
        if (entries.isNotEmpty()) {
            MiuixUpdateLogDialog(
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