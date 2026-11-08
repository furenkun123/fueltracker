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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarBrand
import com.fueltracker.data.CarDatabase
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.miuix.MiuixBackButton
import com.fueltracker.ui.miuix.MiuixButton
import com.fueltracker.ui.miuix.MiuixClickableItem
import com.fueltracker.ui.miuix.MiuixFieldLabel
import com.fueltracker.ui.miuix.MiuixGroupCard
import com.fueltracker.ui.miuix.MiuixInputItem
import com.fueltracker.ui.miuix.MiuixSectionHeader
import com.fueltracker.ui.miuix.MiuixTextButton
import com.fueltracker.ui.miuix.dialogs.MiuixBottomSheetWrapper
import com.fueltracker.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.util.Calendar
import java.util.Locale

// 预设选择列表
private val FuelTypeOptions = listOf("汽油", "柴油", "插电混动", "油电混动", "增程式")
private val GasolineGradeOptions = listOf("92#", "95#", "98#", "89#", "其他")
private val DieselGradeOptions = listOf("0# 柴油", "-10# 柴油", "-20# 柴油", "-35# 柴油", "其他")

private fun normalizeEnergyType(raw: String): String = when {
    raw.contains("插电") -> "插电混动"
    raw.contains("油电") -> "油电混动"
    raw.contains("增程") -> "增程式"
    raw.contains("柴油") -> "柴油"
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

    LaunchedEffect(Unit) {
        carDatabase.ensureCatalogLoaded()
        allBrands = carDatabase.brandsByLetter().flatMap { (l, brands) -> brands.map { CarBrand(it, l) } }
    }

    // 基础信息
    var brand by remember(vehicle) { mutableStateOf(vehicle?.brand ?: "") }
    var series by remember(vehicle) { mutableStateOf(vehicle?.series ?: "") }
    var model by remember(vehicle) { mutableStateOf(vehicle?.model ?: "") }
    var year by remember(vehicle) { mutableStateOf(vehicle?.year?.let { "${it}年" } ?: "${Calendar.getInstance().get(Calendar.YEAR)}年") }
    var fuelType by remember(vehicle) { mutableStateOf(vehicle?.fuelType ?: "汽油") }
    var fuelGrade by remember(vehicle) { mutableStateOf(vehicle?.fuelGrade ?: "95#") }
    var tankCapacity by remember(vehicle) { mutableStateOf(vehicle?.tankCapacity?.takeIf { it > 0 }?.toString()?.removeSuffix(".0") ?: "") }
    var deliveryDateMillis by remember(vehicle) { mutableStateOf(vehicle?.deliveryDate) }

    // 初始/当前状态
    // 初始数据仅在添加车辆时录入，编辑车辆资料不改写历史初始化记录。
    var initialOdometer by remember(vehicle) { mutableStateOf("") }
    var initialFuel by remember(vehicle) { mutableStateOf("") }
    var fuelUnit by remember(vehicle) { mutableStateOf("L") }

    // UI 状态
    var showCarPicker by remember { mutableStateOf(false) }
    var pickerStartLevel by remember { mutableStateOf(PickerLevel.BRAND) }
    var showDatePickerSheet by remember { mutableStateOf(false) }
    var showFuelTypePickerSheet by remember { mutableStateOf(false) }
    var showFuelGradePickerSheet by remember { mutableStateOf(false) }
    var seriesMenuExpanded by remember { mutableStateOf(false) }

    val selectedBrand = remember(brand, allBrands) { allBrands.find { it.brand == brand } }
    val currentFuelGradeOptions = remember(fuelType) { if (fuelType == "柴油") DieselGradeOptions else GasolineGradeOptions }

    LaunchedEffect(fuelType) {
        if (fuelGrade.isBlank() || fuelGrade !in currentFuelGradeOptions) {
            fuelGrade = if (fuelType == "柴油") "0# 柴油" else "95#"
        }
    }

    val formattedDeliveryDate = remember(deliveryDateMillis) {
        deliveryDateMillis?.let { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it)) } ?: "未填写"
    }

    // === 校验逻辑 ===
    val parsedCapacity = tankCapacity.toDoubleOrNull()
    val parsedOdometer = initialOdometer.toDoubleOrNull()
    val parsedFuel = initialFuel.toDoubleOrNull()

    val isSupportedFuelType = fuelType in FuelTypeOptions
    val isCapacityValid = parsedCapacity != null && parsedCapacity.isFinite() && parsedCapacity > 0.0
    val isOdometerValid = isEditMode ||
            (parsedOdometer != null && parsedOdometer.isFinite() && parsedOdometer >= 0.0)

    val isFuelValid = isEditMode || (
            parsedFuel != null &&
                    parsedFuel.isFinite() &&
                    parsedFuel >= 0.0 &&
                    parsedCapacity != null &&
                    parsedCapacity.isFinite() &&
                    parsedCapacity > 0.0 &&
                    if (fuelUnit == "L") parsedFuel <= parsedCapacity else parsedFuel <= 100.0
            )

    val isFuelGradeValid = fuelGrade.isNotBlank()
    val canSave = brand.isNotBlank() && model.isNotBlank() && isSupportedFuelType &&
            isFuelGradeValid && isCapacityValid && isOdometerValid && isFuelValid

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
                color = MiuixTheme.colorScheme.onBackground
            )
        }

        MiuixSectionHeader(title = "车辆详细信息")

        MiuixGroupCard {
            // 品牌
            MiuixClickableItem("品牌", brand.ifBlank { "" }, "选择品牌") {
                pickerStartLevel = PickerLevel.BRAND
                showCarPicker = true
            }

            // 车系（带下拉菜单）
            Box(modifier = Modifier.fillMaxWidth()) {
                MiuixClickableItem(
                    label = "车系",
                    value = series,
                    placeholder = "选择车系",
                    showArrow = selectedBrand?.series.isNullOrEmpty(), // 没下拉菜单就显示箭头
                    onClick = {
                        pickerStartLevel = if (brand.isBlank()) PickerLevel.BRAND else PickerLevel.SERIES
                        showCarPicker = true
                    },
                    trailingContent = if (!selectedBrand?.series.isNullOrEmpty()) {
                        {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "选择预置车系",
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { seriesMenuExpanded = true }
                            )
                        }
                    } else null
                )

                WindowListPopup(
                    show = seriesMenuExpanded,
                    alignment = PopupPositionProvider.Align.End,
                    enableWindowDim = false,
                    onDismissRequest = { seriesMenuExpanded = false }
                ) {
                    ListPopupColumn {
                        (selectedBrand?.series ?: emptyList()).forEachIndexed { index, option ->
                            DropdownImpl(
                                text = option,
                                optionSize = selectedBrand?.series?.size ?: 0,
                                isSelected = series == option,
                                index = index,
                                onSelectedIndexChange = {
                                    series = selectedBrand?.series?.get(it) ?: ""
                                    seriesMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 年款
            MiuixClickableItem("年款", year) {
                pickerStartLevel = when {
                    brand.isBlank() -> PickerLevel.BRAND
                    series.isBlank() -> PickerLevel.SERIES
                    else -> PickerLevel.YEAR
                }
                showCarPicker = true
            }

            // 车型
            MiuixClickableItem("车型", model, "选择车型") {
                pickerStartLevel = when {
                    brand.isBlank() -> PickerLevel.BRAND
                    series.isBlank() -> PickerLevel.SERIES
                    else -> PickerLevel.TRIM
                }
                showCarPicker = true
            }

            // 能源类型
            MiuixClickableItem("能源类型", fuelType) { showFuelTypePickerSheet = true }

            MiuixClickableItem("燃油标号", fuelGrade, "未设置") { showFuelGradePickerSheet = true }

            MiuixInputItem(
                label = "油箱容量(L)",
                value = tankCapacity,
                onValueChange = { tankCapacity = it },
                placeholder = "例如：50",
                keyboardType = KeyboardType.Decimal
            )
            if (tankCapacity.isNotBlank() && (parsedCapacity == null || parsedCapacity <= 0.0)) {
                Text("油箱容量必须大于 0", fontSize = 13.sp, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
            }

            HorizontalDivider(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            MiuixClickableItem("交车日期", formattedDeliveryDate, "未填写") { showDatePickerSheet = true }
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (!isEditMode) {
            MiuixSectionHeader(title = "初始数据（必填）")
            MiuixGroupCard {
                MiuixInputItem(
                    label = "当前里程",
                    value = initialOdometer,
                    onValueChange = { initialOdometer = it },
                    placeholder = "必填，单位：km",
                    keyboardType = KeyboardType.Decimal
                )
                if (initialOdometer.isNotBlank() && (parsedOdometer == null || parsedOdometer < 0.0)) {
                    Text("当前里程必须是有效的非负数", fontSize = 13.sp, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
                }

                HorizontalDivider(
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                MiuixFuelInputListItem(
                    title = "当前油量",
                    value = initialFuel,
                    onValueChange = { initialFuel = it },
                    unit = fuelUnit,
                    onUnitToggle = {
                        if (initialFuel.isNotBlank()) {
                            initialFuel = if (parsedFuel != null && parsedCapacity != null && parsedCapacity > 0.0) {
                                val converted = if (fuelUnit == "L") {
                                    parsedFuel / parsedCapacity * 100.0
                                } else {
                                    parsedFuel / 100.0 * parsedCapacity
                                }
                                String.format(Locale.US, "%.2f", converted).removeSuffix(".00").removeSuffix(".0")
                            } else ""
                        }
                        fuelUnit = if (fuelUnit == "L") "%" else "L"
                    },
                    placeholder = "必填"
                )
                if (initialFuel.isNotBlank() && !isFuelValid) {
                    Text(
                        text = if (fuelUnit == "L") "剩余油量不能超过油箱容量" else "百分比必须在 0～100 之间",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        MiuixButton(
            text = if (isEditMode) "保存修改" else "确认添加",
            enabled = canSave && !isSaving,
            onClick = {
                isSaving = true
                val parsedYear = year.removeSuffix("年").toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                val finalCapacity = parsedCapacity ?: (vehicle?.tankCapacity ?: 0.0)
                val finalOdometer = parsedOdometer ?: 0.0
                val inputFuel = parsedFuel ?: 0.0
                val finalFuel = if (fuelUnit == "%") (inputFuel / 100.0) * finalCapacity else inputFuel

                val baseVehicle = (vehicle ?: Vehicle(brand="", series="", model="", year=0, fuelType="", fuelGrade="", tankCapacity=0.0)).copy(
                    brand = brand.trim(), series = series.trim(), model = model.trim(),
                    year = parsedYear, fuelType = fuelType.trim(), fuelGrade = fuelGrade.trim(),
                    tankCapacity = finalCapacity,
                    deliveryDate = deliveryDateMillis
                )

                val onResult: (Result<Unit>) -> Unit = { result ->
                    isSaving = false
                    if (result.isSuccess) {
                        onSaved()
                    } else {
                        Toast.makeText(context, "保存失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }

                if (isEditMode) {
                    viewModel.updateVehicle(baseVehicle, onResult)
                } else {
                    viewModel.addVehicle(
                        baseVehicle.copy(isCurrent = true),
                        currentOdometer = finalOdometer,
                        currentFuel = finalFuel,
                        onComplete = onResult
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }

    // 弹窗
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

// 在你的 VehicleScreen.kt 文件最底部，只保留这一个自己写的组件
@Composable
private fun MiuixFuelInputListItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    onUnitToggle: () -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp), // 👈 高度与 MiuixInputItem 统一
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixFieldLabel(
            text = title,
            modifier = Modifier.width(110.dp) // 👈 复用公共标签，统一宽度
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .padding(start = 12.dp),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )

        // 切换小按钮
        Row(
            modifier = Modifier
                .padding(start = 8.dp, end = 16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable(onClick = onUnitToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = "切换单位",
                modifier = Modifier.size(14.dp),
                tint = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (unit == "L") "%" else "L",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1
            )
        }
    }
}