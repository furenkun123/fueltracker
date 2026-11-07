package com.fueltracker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowListPopup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarBrand
import com.fueltracker.data.CarDatabase
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.miuix.dialogs.MiuixBottomSheetWrapper
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.miuix.MiuixBackButton
import com.fueltracker.ui.miuix.MiuixButton
import com.fueltracker.ui.miuix.MiuixGroupCard
import com.fueltracker.ui.miuix.MiuixSectionHeader
import com.fueltracker.ui.miuix.MiuixTextButton
import com.fueltracker.ui.miuix.MiuixClickableItem
import com.fueltracker.ui.miuix.MiuixInputItem
import com.fueltracker.viewmodel.MainViewModel
import java.util.Calendar
import java.util.Locale

// 预设选择列表
private val FuelTypeOptions = listOf("汽油", "柴油", "插电混动", "油电混动", "增程式", "纯电动")
private val GasolineGradeOptions = listOf("92#", "95#", "98#", "89#", "其他")
private val DieselGradeOptions = listOf("0# 柴油", "-10# 柴油", "-20# 柴油", "-35# 柴油", "其他")

private fun normalizeEnergyType(raw: String): String = when {
    raw.contains("插电") -> "插电混动"
    raw.contains("油电") -> "油电混动"
    raw.contains("增程") -> "增程式"
    raw.contains("柴油") -> "柴油"
    raw.contains("纯电") -> "纯电动"
    raw.contains("汽油") -> "汽油"
    else -> raw
}

private fun normalizeFuelGrade(raw: String): String {
    val num = Regex("""(-?\d+)""").find(raw)?.groupValues?.get(1) ?: return raw
    return when (num) {
        "92" -> "92#"; "95" -> "95#"; "98" -> "98#"; "89" -> "89#"
        "0" -> "0# 柴油"; "-10" -> "-10# 柴油"; "-20" -> "-20# 柴油"; "-35" -> "-35# 柴油"
        else -> raw
    }
}

@Composable
fun VehicleScreen(
    viewModel: MainViewModel,
    vehicle: Vehicle? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isEditMode = vehicle != null
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }
    val carDatabase = remember { CarDatabase.get(context) }
    var allBrands by remember { mutableStateOf<List<CarBrand>>(emptyList()) }



    // 👇 双保险：直接判断当前背景的亮度，决定文字用黑还是白
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val textColor = if (isLight) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
    val dividerColor = if (isLight) Color(0xFFE0E0E0) else Color(0xFF3A3A3C)

    LaunchedEffect(Unit) {
        carDatabase.ensureCatalogLoaded()
        allBrands = carDatabase.brandsByLetter().flatMap { (l, brands) -> brands.map { CarBrand(it, l) } }
    }

    var brand by remember(vehicle) { mutableStateOf(vehicle?.brand ?: "") }
    var series by remember(vehicle) { mutableStateOf(vehicle?.series ?: "") }
    var model by remember(vehicle) { mutableStateOf(vehicle?.model ?: "") }
    var year by remember(vehicle) { mutableStateOf(vehicle?.year?.let { "${it}年" } ?: "${Calendar.getInstance().get(Calendar.YEAR)}年") }
    var fuelType by remember(vehicle) { mutableStateOf(vehicle?.fuelType ?: "汽油") }
    var fuelGrade by remember(vehicle) { mutableStateOf(vehicle?.fuelGrade ?: "95#") }
    var tankCapacity by remember(vehicle) { mutableStateOf(vehicle?.tankCapacity?.takeIf { it > 0 }?.toString()?.removeSuffix(".0") ?: "") }
    var initialOdometer by remember(vehicle) { mutableStateOf(vehicle?.initialOdometer?.toString()?.removeSuffix(".0") ?: "") }
    var deliveryDateMillis by remember(vehicle) { mutableStateOf(vehicle?.deliveryDate) }

    var showCarPicker by remember { mutableStateOf(false) }
    var pickerStartLevel by remember { mutableStateOf(PickerLevel.BRAND) }
    var showDatePickerSheet by remember { mutableStateOf(false) }
    var showFuelTypePickerSheet by remember { mutableStateOf(false) }
    var showFuelGradePickerSheet by remember { mutableStateOf(false) }
    var seriesMenuExpanded by remember { mutableStateOf(false) }

    val selectedBrand = remember(brand, allBrands) { allBrands.find { it.brand == brand } }
    val currentFuelGradeOptions = remember(fuelType) { if (fuelType == "柴油") DieselGradeOptions else GasolineGradeOptions }

    LaunchedEffect(fuelType) {
        if (fuelType == "纯电动") {
            fuelGrade = ""
        } else if (fuelGrade.isNotBlank() && fuelGrade !in currentFuelGradeOptions) {
            fuelGrade = if (fuelType == "柴油") "0# 柴油" else "95#"
        }
    }

    val formattedDeliveryDate = remember(deliveryDateMillis) {
        deliveryDateMillis?.let { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it)) } ?: "未填写"
    }

    // 提前解析数值
    val parsedCapacity = tankCapacity.toDoubleOrNull()
    val parsedOdometer = initialOdometer.toDoubleOrNull()

// 校验逻辑：
// 1. 品牌、车型必填
// 2. 非纯电：油箱容量必填且必须 > 0
// 3. 提车里程必填且必须 >= 0
    val isCapacityValid = fuelType == "纯电动" || (parsedCapacity != null && parsedCapacity.isFinite() && parsedCapacity > 0.0)
    val isOdometerValid = parsedOdometer != null && parsedOdometer.isFinite() && parsedOdometer >= 0.0

    val canSave = brand.isNotBlank() && model.isNotBlank() && isCapacityValid && isOdometerValid

    if (showCarPicker) {
        BackHandler { showCarPicker = false }
        CarPickerFlow(
            startLevel = pickerStartLevel,
            initialBrand = brand,
            initialSeries = series,
            initialYear = year.removeSuffix("年").trim().let { if (it.isEmpty()) "" else "${it}款" },
            onBack = { showCarPicker = false },
            onPicked = { sel ->
                when (sel) {
                    is CarSelection.Full -> {
                        brand = sel.brand; series = sel.series; model = sel.trim.name
                        sel.year.removeSuffix("款").removeSuffix("年").trim().toIntOrNull()?.let { year = "${it}年" }
                        normalizeEnergyType(sel.trim.energyType).takeIf { it.isNotBlank() }?.let { fuelType = it }
                        normalizeFuelGrade(sel.trim.fuelGrade).takeIf { it.isNotBlank() }?.let { fuelGrade = it }
                        if (sel.trim.fuelTank.isNotBlank()) tankCapacity = sel.trim.fuelTank
                    }
                    is CarSelection.BrandSeriesYear -> {
                        brand = sel.brand; series = sel.series; model = ""
                        sel.year.removeSuffix("款").removeSuffix("年").trim().toIntOrNull()?.let { year = "${it}年" }
                    }
                    is CarSelection.BrandSeries -> { brand = sel.brand; series = sel.series; model = "" }
                    is CarSelection.Manual -> { brand = sel.brand; series = ""; model = "" }
                }
                showCarPicker = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixBackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isEditMode) "编辑车辆信息" else "添加新车辆",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        MiuixSectionHeader(title = "车辆详细信息")

        MiuixGroupCard {
            MiuixClickableItem("品牌", brand.ifBlank { "选择品牌" }) {
                pickerStartLevel = PickerLevel.BRAND
                showCarPicker = true
            }
            // 👈 改为强对比度分割线
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

            Box(modifier = Modifier.fillMaxWidth()) {
                MiuixClickableItem("车系", series.ifBlank { "选择车系" }) {
                    pickerStartLevel = if (brand.isBlank()) PickerLevel.BRAND else PickerLevel.SERIES
                    showCarPicker = true
                }
                val currentBrandSeries = selectedBrand?.series ?: emptyList()
                if (currentBrandSeries.isNotEmpty()) {
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                        Icon(
                            Icons.Default.ArrowDropDown, "选择预置车系",
                            tint = textColor, // 👈 双保险
                            modifier = Modifier.size(24.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null
                            ) { seriesMenuExpanded = true }
                        )
                        WindowListPopup(
                            show = seriesMenuExpanded,
                            alignment = PopupPositionProvider.Align.End,
                            enableWindowDim = false,
                            onDismissRequest = { seriesMenuExpanded = false }
                        ) {
                            ListPopupColumn {
                                currentBrandSeries.forEachIndexed { index, option ->
                                    DropdownImpl(
                                        text = option,
                                        optionSize = currentBrandSeries.size,
                                        isSelected = series == option,
                                        index = index,
                                        onSelectedIndexChange = {
                                            series = currentBrandSeries[it]
                                            seriesMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

            MiuixClickableItem("车型", model.ifBlank { "选择车型" }) {
                pickerStartLevel = when {
                    brand.isBlank() -> PickerLevel.BRAND
                    series.isBlank() -> PickerLevel.SERIES
                    else -> PickerLevel.TRIM
                }
                showCarPicker = true
            }
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

            MiuixClickableItem("年款", year) {
                pickerStartLevel = when {
                    brand.isBlank() -> PickerLevel.BRAND
                    series.isBlank() -> PickerLevel.SERIES
                    model.isBlank() -> PickerLevel.TRIM
                    else -> PickerLevel.YEAR
                }
                showCarPicker = true
            }
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

            MiuixClickableItem("能源类型", fuelType) { showFuelTypePickerSheet = true }
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

            if (fuelType != "纯电动") {
                MiuixClickableItem("燃油标号", fuelGrade.ifBlank { "未设置" }) { showFuelGradePickerSheet = true }
                HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

                MiuixInputItem("油箱容量 (L)", tankCapacity, { tankCapacity = it }, "例如：50", KeyboardType.Decimal)
                // 👇 新增：油箱错误提示
                if (tankCapacity.isNotBlank() && (parsedCapacity == null || parsedCapacity <= 0.0)) {
                    Text(
                        text = "油箱容量必须大于 0",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
                    )
                }
                HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
            }

            MiuixClickableItem("提车日期", formattedDeliveryDate) { showDatePickerSheet = true }
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

            MiuixInputItem("提车里程 (km)", initialOdometer, { initialOdometer = it }, "例如：20", KeyboardType.Decimal)
            // 👇 新增：里程错误提示
            if (initialOdometer.isNotBlank() && (parsedOdometer == null || parsedOdometer < 0.0)) {
                Text(
                    text = "提车里程不能为负数",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        MiuixButton(
            text = if (isEditMode) "保存修改" else "确认添加",
            enabled = canSave && !isSaving,  // 👈 增加防抖
            onClick = {
                isSaving = true
                val parsedYear = year.removeSuffix("年").toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)

                // 纯电车型 parsedCapacity 为 null 时，保留旧的 tankCapacity，防止被覆盖成 0
                val finalCapacity = parsedCapacity ?: (vehicle?.tankCapacity ?: 0.0)
                val finalOdometer = parsedOdometer ?: 0.0

                val baseVehicle = (vehicle ?: Vehicle(brand="", series="", model="", year=0, fuelType="", fuelGrade="", tankCapacity=0.0, initialOdometer=0.0)).copy(
                    brand = brand.trim(), series = series.trim(), model = model.trim(),
                    year = parsedYear, fuelType = fuelType.trim(), fuelGrade = fuelGrade.trim(),
                    tankCapacity = finalCapacity, initialOdometer = finalOdometer, deliveryDate = deliveryDateMillis
                )

                // 👇 统一回调处理
                val onResult: (Result<Unit>) -> Unit = { result ->
                    isSaving = false
                    if (result.isSuccess) {
                        onSaved()
                    } else {
                        Toast.makeText(
                            context,
                            "保存失败：${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                if (isEditMode) {
                    viewModel.updateVehicle(baseVehicle, onResult)
                } else {
                    viewModel.addVehicle(baseVehicle.copy(isCurrent = true), onResult)
                }
            }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }

    // 使用统一的底部滚轮弹窗
    if (showFuelTypePickerSheet) {
        MiuixSingleWheelPickerSheet(
            title = "选择能源类型",
            options = FuelTypeOptions,
            currentValue = fuelType,
            onDismiss = { showFuelTypePickerSheet = false },
            onConfirm = { fuelType = it; showFuelTypePickerSheet = false }
        )
    }
    if (showFuelGradePickerSheet) {
        MiuixSingleWheelPickerSheet(
            title = "选择燃油标号",
            options = currentFuelGradeOptions,
            currentValue = fuelGrade,
            onDismiss = { showFuelGradePickerSheet = false },
            onConfirm = { fuelGrade = it; showFuelGradePickerSheet = false }
        )
    }
    if (showDatePickerSheet) {
        MiuixDateWheelPickerBottomSheet(
            initialMillis = deliveryDateMillis,
            onDismiss = { showDatePickerSheet = false },
            onClear = { deliveryDateMillis = null; showDatePickerSheet = false },
            onConfirm = { deliveryDateMillis = it; showDatePickerSheet = false }
        )
    }
}

@Composable
private fun MiuixSingleWheelPickerSheet(
    title: String,
    options: List<String>,
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedValue by remember { mutableStateOf(currentValue.ifBlank { options.firstOrNull() ?: "" }) }
    MiuixBottomSheetWrapper(title = title, onDismiss = onDismiss, onConfirm = { onConfirm(selectedValue) }) {
        WheelList(selectedValue = selectedValue, items = options, modifier = Modifier.fillMaxWidth(), visibleCount = 5, style = WheelStyle.WithBackground, onChange = { selectedValue = it })
    }
}

@Composable
private fun MiuixDateWheelPickerBottomSheet(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val calendar = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis ?: System.currentTimeMillis() } }
    var year by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    val maxDays = remember(year, month) { Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH) }

    LaunchedEffect(maxDays) { if (day > maxDays) day = maxDays }

    MiuixBottomSheetWrapper(title = "选择提车日期", onDismiss = onDismiss, onConfirm = {
        val result = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
        onConfirm(result.timeInMillis)
    }) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
            MiuixTextButton("不选择（设为空）", MiuixTheme.colorScheme.error, onClick = onClear)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WheelList(selectedValue = year, items = (2005..Calendar.getInstance().get(Calendar.YEAR) + 1).toList().reversed(), itemLabel = { "${it}年" }, modifier = Modifier.weight(1f), style = WheelStyle.Plain, onChange = { year = it })
            WheelList(selectedValue = month, items = (1..12).toList(), itemLabel = { "${it}月" }, modifier = Modifier.weight(1f), style = WheelStyle.Plain, onChange = { month = it })
            WheelList(selectedValue = day, items = (1..maxDays).toList(), itemLabel = { "${it}日" }, modifier = Modifier.weight(1f), style = WheelStyle.Plain, onChange = { day = it })
        }
    }
}
