
package com.fueltracker

import androidx.activity.compose.BackHandler
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.fueltracker.notif.ParkingNotification
import com.fueltracker.ui.ExpenseEditScreen
import com.fueltracker.ui.ExpenseScreen
import com.fueltracker.ui.FuelScreen
import com.fueltracker.ui.HomeScreen
import com.fueltracker.ui.ActiveParkingConfirmOverlay
import com.fueltracker.ui.InitialRecordScreen
import com.fueltracker.ui.ParkingScreen
import com.fueltracker.ui.RecordsScreen
import com.fueltracker.ui.SettingsScreen
import com.fueltracker.ui.VehicleScreen
import com.fueltracker.ui.components.PrivacyDialog
import com.fueltracker.ui.components.UpdateLogDialog
import com.fueltracker.ui.miui.MiuiNavigationBar
import com.fueltracker.ui.miui.MiuiNavigationDestination
import com.fueltracker.ui.miui.MiuiStatusBarGlass
import com.fueltracker.ui.miui.MiuiTopBarMoreButton
import com.fueltracker.ui.miui.glass.LocalAppGlassBackdrop
import com.fueltracker.ui.miui.glass.LocalAppGlassEnabled
import com.fueltracker.ui.miui.glass.LocalCardGlassBackdrop
import com.fueltracker.ui.miui.glass.LocalCardGlassEnabled
import com.fueltracker.util.UpdateLogManager
import com.fueltracker.ui.theme.ThemePreferences
import com.fueltracker.viewmodel.MainViewModel
import com.fueltracker.viewmodel.ParkingNotificationState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

@Composable
fun FuelTrackerApp(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentVehicle by viewModel.currentVehicle.collectAsState()

    val backgroundImageUri = ThemePreferences.backgroundImageUri
    val configuration = LocalConfiguration.current
    val backgroundTargetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val backgroundTargetHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val backgroundImage by produceState<ImageBitmap?>(
        initialValue = null,
        backgroundImageUri,
        backgroundTargetWidth,
        backgroundTargetHeight
    ) {
        value = if (backgroundImageUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(
                        context.contentResolver,
                        Uri.parse(backgroundImageUri)
                    )
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val sourceWidth = info.size.width.coerceAtLeast(1)
                        val sourceHeight = info.size.height.coerceAtLeast(1)
                        val scale = minOf(1f, 2048f / maxOf(sourceWidth, sourceHeight))
                        decoder.setTargetSize(
                            (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                        )
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    var homeCollapseProgress by remember {
        mutableFloatStateOf(0f)
    }

    // 底部导航使用 appBackdrop；所有内容卡片共用独立背景采样源。
    val appBackdrop = rememberLayerBackdrop()
    val cardBackdrop = rememberLayerBackdrop()

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

    var showClearParkingConfirm by remember { mutableStateOf(false) }
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
        screen == AppScreen.HOME ||
                screen == AppScreen.RECORDS ||
                screen == AppScreen.EXPENSES

    val isGlassEnabled = viewModel.isGlassEffectEnabled

    // ---------- 返回键处理 ----------
    // ---------- 统一返回逻辑 ----------
    val handleBack: () -> Unit = {
        when (screen) {
            AppScreen.RECORDS,
            AppScreen.SETTINGS,
            AppScreen.EXPENSES,
            AppScreen.INITIAL_RECORD,
            AppScreen.ADD_FUEL,
            AppScreen.ADD_PARKING,
            AppScreen.EDIT_PARKING ->
                viewModel.navigateTo(AppScreen.HOME)

            AppScreen.ADD_VEHICLE ->
                viewModel.navigateTo(addVehicleOrigin)

            AppScreen.EDIT_FUEL -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(editFuelOrigin)
            }

            AppScreen.EDIT_VEHICLE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.SETTINGS)
            }

            AppScreen.ADD_EXPENSE ->
                viewModel.navigateTo(AppScreen.EXPENSES)

            AppScreen.EDIT_EXPENSE -> {
                viewModel.clearEditingStates()
                viewModel.navigateTo(AppScreen.EXPENSES)
            }

            AppScreen.HOME -> Unit
        }
    }

    BackHandler(
        enabled = screen != AppScreen.HOME,
        onBack = handleBack
    )

    BackHandler(
        enabled = screen == AppScreen.HOME && showClearParkingConfirm,
        onBack = { showClearParkingConfirm = false }
    )

    // ============================================================
    // 全 App 内容层
    // ============================================================
    CompositionLocalProvider(
        LocalAppGlassBackdrop provides appBackdrop,
        LocalAppGlassEnabled provides isGlassEnabled
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
        // 车辆卡片专用采样层：只捕获背景，不包含 Scaffold、卡片或导航栏。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(cardBackdrop)
        ) {
            AppGlassBackground(backgroundImage)
        }

        // 页面采样层继续供底部导航栏使用。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(appBackdrop)
        ) {
            AppGlassBackground(backgroundImage)

            // 所有内容卡片共享只包含背景的独立采样层，不采集页面自身。
            CompositionLocalProvider(
                LocalCardGlassBackdrop provides cardBackdrop,
                LocalCardGlassEnabled provides isGlassEnabled
            ) {
            if (showBottomBar) {

                // ---------- 主页面 Scaffold ----------
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
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
                                val vehicle = currentVehicle

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
                                    isClearParkingConfirmVisible = showClearParkingConfirm,
                                    onRequestClearParkingConfirm = { showClearParkingConfirm = true },

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

                }
            }
            }
        }
        // ---------- 悬浮底部导航栏 ----------
        if (showBottomBar) {
            MiuiNavigationBar(
                selectedIndex = when (screen) {
                    AppScreen.HOME -> 0
                    AppScreen.RECORDS -> 1
                    AppScreen.EXPENSES -> 2
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                backdrop = appBackdrop,
                isGlassEnabled = isGlassEnabled,
                isFloating = viewModel.isFloatingBottomBar,
                items = listOf(
                    MiuiNavigationDestination(
                        icon = Icons.Default.Home,
                        label = "首页"
                    ),
                    MiuiNavigationDestination(
                        icon = Icons.Default.LocalGasStation,
                        label = "记录"
                    ),
                    MiuiNavigationDestination(
                        icon = Icons.Default.Receipt,
                        label = "费用"
                    )
                ),
                onTabSelected = { index ->
                    viewModel.navigateTo(
                        when (index) {
                            0 -> AppScreen.HOME
                            1 -> AppScreen.RECORDS
                            else -> AppScreen.EXPENSES
                        }
                    )
                }
            )
        }

        // 全局状态栏材质位于页面采样层之上，避免 Backdrop 递归采样。
        MiuiStatusBarGlass(
            backdrop = appBackdrop,
            modifier = Modifier.align(Alignment.TopCenter),
            isGlassEnabled = isGlassEnabled
        )

        // 首页操作按钮位于状态栏玻璃之上，保持点击与图标清晰度。
        if (screen == AppScreen.HOME) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars
                            .asPaddingValues()
                            .calculateTopPadding() + 4.dp,
                        end = 28.dp
                    ),
                contentAlignment = Alignment.TopEnd
            ) {
                MiuiTopBarMoreButton(
                    onClick = {
                        viewModel.navigateTo(AppScreen.SETTINGS)
                    }
                )
            }
        }
    }

    // 独立的 Backdrop 消费者：弹窗本身不进入 appBackdrop 的采样内容。
    if (screen == AppScreen.HOME && showClearParkingConfirm) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.16f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showClearParkingConfirm = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            ActiveParkingConfirmOverlay(
                backdrop = appBackdrop,
                onDismiss = { showClearParkingConfirm = false },
                onConfirm = {
                    showClearParkingConfirm = false
                    viewModel.clearActiveParking()
                }
            )
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
}

/**
 * 低对比度环境背景，为玻璃材质提供可见的明暗变化。
 * 同一背景绘制到卡片采样层和页面层，保证玻璃采样与实际画面一致。
 */
@Composable
private fun AppGlassBackground(backgroundImage: ImageBitmap?) {
    val background = MaterialTheme.colorScheme.background
    val isLight = background.luminance() > 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        if (backgroundImage != null) {
            Image(
                bitmap = backgroundImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isLight) Color.White.copy(alpha = 0.18f)
                        else Color.Black.copy(alpha = 0.24f)
                    )
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 默认背景使用柔和的色彩光斑，为玻璃材质提供明暗变化。
                val glow = if (isLight) {
                    Color(0xFF78A9F5).copy(alpha = 0.38f)
                } else {
                    Color(0xFF5276A8).copy(alpha = 0.28f)
                }
                val secondaryGlow = if (isLight) {
                    Color(0xFFB9A4F2).copy(alpha = 0.30f)
                } else {
                    Color(0xFF354B68).copy(alpha = 0.24f)
                }
                val tertiaryGlow = if (isLight) {
                    Color(0xFF72D6D0).copy(alpha = 0.22f)
                } else {
                    Color(0xFF286D78).copy(alpha = 0.18f)
                }

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow, Color.Transparent),
                        center = Offset(size.width * 0.84f, size.height * 0.16f),
                        radius = maxOf(size.width, size.height) * 0.58f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.10f, size.height * 0.78f),
                        radius = maxOf(size.width, size.height) * 0.52f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tertiaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height * 0.34f),
                        radius = maxOf(size.width, size.height) * 0.34f
                    )
                )
            }
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
