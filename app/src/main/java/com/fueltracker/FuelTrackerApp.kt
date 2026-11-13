package com.fueltracker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.ContextCompat
import com.fueltracker.AppScreen.ADD_EXPENSE
import com.fueltracker.AppScreen.ADD_FUEL
import com.fueltracker.AppScreen.ADD_PARKING
import com.fueltracker.AppScreen.ADD_VEHICLE
import com.fueltracker.AppScreen.EDIT_EXPENSE
import com.fueltracker.AppScreen.EDIT_FUEL
import com.fueltracker.AppScreen.EDIT_PARKING
import com.fueltracker.AppScreen.EDIT_VEHICLE
import com.fueltracker.AppScreen.EXPENSES
import com.fueltracker.AppScreen.HOME
import com.fueltracker.AppScreen.RECORDS
import com.fueltracker.AppScreen.SETTINGS
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
import com.fueltracker.ui.miuix.dialogs.MiuixPrivacyDialog
import com.fueltracker.ui.miuix.dialogs.MiuixUpdateLogDialog
import com.fueltracker.ui.miuix.navigation.AppNavItem
import com.fueltracker.ui.miuix.navigation.MiuixAppNavigationBar
import com.fueltracker.ui.theme.BackgroundPreferences
import com.fueltracker.ui.theme.LocalBackgroundTitleColor
import com.fueltracker.ui.theme.rememberAdaptiveStatusBarIcons
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

// 防止权限被拒绝后，停车状态变化时反复弹出权限请求
    var hasRequestedNotificationPermission by rememberSaveable {
        mutableStateOf(false)
    }

// 统一显示停车通知
    val showParkingNotification: (ParkingNotificationState.Visible) -> Unit = { s ->
        ParkingNotification.show(
            context = context,
            vehicleId = s.vehicleId,
            locationName = s.locationName,
            floor = s.floor,
            area = s.area,
            spotNumber = s.spotNumber
        )
    }

// Android 13+ 通知权限请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            (parkingNotificationState as? ParkingNotificationState.Visible)
                ?.let(showParkingNotification)
        }
    }

    LaunchedEffect(parkingNotificationState) {
        when (val s = parkingNotificationState) {
            ParkingNotificationState.NotReady -> Unit

            ParkingNotificationState.Hidden -> {
                ParkingNotification.cancel(context)
            }

            is ParkingNotificationState.Visible -> {
                // Android 13（API 33）及以上需要运行时通知权限
                val needsPermission =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED

                if (needsPermission) {
                    if (!hasRequestedNotificationPermission) {
                        hasRequestedNotificationPermission = true
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                } else {
                    showParkingNotification(s)
                }
            }
        }
    }

    // ---------- 页面配置 ----------
    val showBottomBar =
        screen == HOME ||
                screen == RECORDS ||
                screen == EXPENSES
    val adaptiveTitleColor = rememberAdaptiveTitleColor(backgroundBitmap)

// 当前应用实际主题
    val isDarkTheme =
        MiuixTheme.colorScheme.background.luminance() < 0.5f

    val isGlassEnabled = viewModel.isGlassEffectEnabled

// 暗色主题使用白色状态栏图标，浅色主题使用黑色图标
    val isVehicleScreen =
        screen == ADD_VEHICLE || screen == EDIT_VEHICLE

    val statusBarDarkIcons = if (isVehicleScreen) {
        !isDarkTheme
    } else {
        rememberAdaptiveStatusBarIcons(backgroundBitmap)
            ?: !isDarkTheme
    }

    // ---------- Pager 与导航栏状态 ----------
    val mainScreens = listOf(HOME, RECORDS, EXPENSES)

    val initialPage = mainScreens.indexOf(screen).takeIf { it >= 0 } ?: 0

    val pagerState = rememberPagerState(
        initialPage = initialPage
    ) {
        mainScreens.size
    }

    val scope = rememberCoroutineScope()

// 导航栏选中状态：点击时立即更新，不等待 Pager 动画结束
    var navigationSelectedIndex by remember {
        mutableIntStateOf(initialPage)
    }

// 外部页面跳转时同步导航栏和 Pager
    LaunchedEffect(screen) {
        val targetIndex = mainScreens.indexOf(screen)

        if (targetIndex >= 0) {
            navigationSelectedIndex = targetIndex

            if (
                pagerState.currentPage != targetIndex &&
                !pagerState.isScrollInProgress
            ) {
                pagerState.animateScrollToPage(targetIndex)
            }
        }
    }

// Pager 滑动结束后同步 ViewModel 和导航栏
    LaunchedEffect(pagerState.settledPage, showBottomBar) {
        if (!showBottomBar) return@LaunchedEffect
        val targetIndex = pagerState.settledPage

        navigationSelectedIndex = targetIndex

        val targetScreen = mainScreens[targetIndex]

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

    // 全 App 内容层
    CompositionLocalProvider(
        LocalBackgroundTitleColor provides adaptiveTitleColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {


            // 2. Backdrop 采集层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (backgroundBitmap == null) {
                            // ✅ 如果没有背景图，页面底色给浅灰
                            if (MiuixTheme.colorScheme.background.luminance() > 0.5f) {
                                Color(0xFFEFEFF4) // 页面背景（浅灰）
                            } else {
                                MiuixTheme.colorScheme.background // 深色模式
                            }
                        } else {
                            Color.Transparent // 有背景图时透明
                        }
                    )
                    .layerBackdrop(appBackdrop)
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
                                    onCollapseProgressChange = { homeCollapseProgress = it },
                                    onOpenSettings = { viewModel.navigateTo(SETTINGS) }
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
            }

            // 3. 顶栏
            MiuixTopBar(
                statusBarDarkIcons = statusBarDarkIcons
            )

            // 5. 底部导航栏
            if (showBottomBar) {
                MiuixAppNavigationBar(
                    selectedIndex = navigationSelectedIndex,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    backdrop = appBackdrop,
                    isGlassEnabled = isGlassEnabled,
                    isFloating = viewModel.isFloatingBottomBar,
                    onSelectedIndexChange = { index ->
                        navigationSelectedIndex = index

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