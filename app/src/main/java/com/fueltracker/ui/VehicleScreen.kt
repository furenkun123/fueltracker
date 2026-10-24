package com.fueltracker.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CarBrand
import com.fueltracker.data.CarDatabase
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.miui.MuiXBottomSheetDialog
import com.fueltracker.ui.miui.MuiXClickableItem
import com.fueltracker.ui.miui.MuiXGroupCard
import com.fueltracker.ui.miui.MuiXInputItem
import com.fueltracker.ui.miui.MuiXSectionHeader
import com.fueltracker.ui.miui.MuiXWheelPickerBottomSheet
import com.fueltracker.ui.miui.MuiXWheelPickerColumn
import com.fueltracker.viewmodel.MainViewModel
import java.util.Calendar

// 预设选择列表
private val FuelTypeOptions = listOf("汽油", "柴油", "插电混动", "油电混动", "增程式", "纯电动")
private val GasolineGradeOptions = listOf("92#", "95#", "98#", "89#", "其他")
private val DieselGradeOptions = listOf("0# 柴油", "-10# 柴油", "-20# 柴油", "-35# 柴油", "其他")
private val YearOptions = (2005..Calendar.getInstance().get(Calendar.YEAR) + 1).map { "${it}年" }.reversed()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    viewModel: MainViewModel,
    vehicle: Vehicle? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isEditMode = vehicle != null
    val context = LocalContext.current
    val carDatabase = remember { CarDatabase.get(context) }
    var allBrands by remember { mutableStateOf<List<CarBrand>>(emptyList()) }

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
    var initialOdometer by remember(vehicle) { mutableStateOf(vehicle?.initialOdometer?.toString()?.removeSuffix(".0") ?: "20") }
    var deliveryDateMillis by remember(vehicle) { mutableStateOf(vehicle?.deliveryDate) }

    // 弹窗状态
    var showCarPicker by remember { mutableStateOf(false) }
    var pickerStartLevel by remember { mutableStateOf(PickerLevel.BRAND) }
    var showDatePickerSheet by remember { mutableStateOf(false) }
    var showFuelTypePickerSheet by remember { mutableStateOf(false) }
    var showFuelGradePickerSheet by remember { mutableStateOf(false) }
    var seriesMenuExpanded by remember { mutableStateOf(false) }

    val selectedBrand = remember(brand, allBrands) { allBrands.find { it.brand == brand } }
    val currentFuelGradeOptions = remember(fuelType) { if (fuelType == "柴油") DieselGradeOptions else GasolineGradeOptions }

    // 👇 优化：使用更紧凑的联动判断
    LaunchedEffect(fuelType) {
        if (fuelType == "纯电动") {
            fuelGrade = ""
        } else if (fuelGrade.isNotBlank() && fuelGrade !in currentFuelGradeOptions) {
            fuelGrade = if (fuelType == "柴油") "0# 柴油" else "95#"
        }
    }

    val formattedDeliveryDate = remember(deliveryDateMillis) {
        deliveryDateMillis?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "未填写"
    }

    val canSave = brand.isNotBlank() && model.isNotBlank() &&
            (fuelType == "纯电动" || tankCapacity.toDoubleOrNull() != null) &&
            initialOdometer.toDoubleOrNull() != null

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "编辑车辆信息" else "添加新车辆", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            MuiXSectionHeader(title = "车辆详细信息")

            MuiXGroupCard {
                MuiXClickableItem("品牌", brand.ifBlank { "点击从车型库选择" }) {
                    pickerStartLevel = if (brand.isBlank()) PickerLevel.BRAND else PickerLevel.SERIES
                    showCarPicker = true
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)

                // 👇 优化：车系下拉菜单精简
                Box(modifier = Modifier.fillMaxWidth()) {
                    MuiXClickableItem("车系", series.ifBlank { "点击从车型库选择" }) {
                        pickerStartLevel = when {
                            brand.isBlank() -> PickerLevel.BRAND
                            series.isBlank() -> PickerLevel.SERIES
                            else -> PickerLevel.TRIM
                        }
                        showCarPicker = true
                    }
                    val currentBrandSeries = selectedBrand?.series ?: emptyList()
                    if (currentBrandSeries.isNotEmpty()) {
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                            Icon(
                                Icons.Default.ArrowDropDown, "选择预置车系",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp).clickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = null
                                ) { seriesMenuExpanded = true }
                            )
                            DropdownMenu(seriesMenuExpanded, onDismissRequest = { seriesMenuExpanded = false }) {
                                currentBrandSeries.forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = { series = s; seriesMenuExpanded = false })
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)

                MuiXClickableItem("车型", model.ifBlank { "点击从车型库选择" }) {
                    pickerStartLevel = if (series.isBlank()) PickerLevel.SERIES else PickerLevel.TRIM
                    showCarPicker = true
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)

                MuiXClickableItem("年款", year) {
                    pickerStartLevel = if (model.isBlank()) PickerLevel.SERIES else PickerLevel.YEAR
                    showCarPicker = true
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)

                MuiXClickableItem("能源类型", fuelType) { showFuelTypePickerSheet = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)

                if (fuelType != "纯电动") {
                    MuiXClickableItem("燃油标号", fuelGrade.ifBlank { "未设置" }) { showFuelGradePickerSheet = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)

                    MuiXInputItem("油箱容量 (L)", tankCapacity, { tankCapacity = it }, "例如：50", KeyboardType.Decimal)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)
                }

                MuiXClickableItem("提车日期", formattedDeliveryDate) { showDatePickerSheet = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)

                MuiXInputItem("提车里程 (km)", initialOdometer, { initialOdometer = it }, "例如：20", KeyboardType.Decimal)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (!canSave) return@Button
                    val parsedYear = year.removeSuffix("年").toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                    val parsedCapacity = tankCapacity.toDoubleOrNull() ?: 0.0
                    val parsedOdometer = initialOdometer.toDoubleOrNull() ?: 20.0

                    // 👇 优化：提取共用的构建逻辑
                    val baseVehicle = (vehicle ?: Vehicle(brand="", series="", model="", year=0, fuelType="", fuelGrade="", tankCapacity=0.0, initialOdometer=0.0)).copy(
                        brand = brand.trim(), series = series.trim(), model = model.trim(),
                        year = parsedYear, fuelType = fuelType.trim(), fuelGrade = fuelGrade.trim(),
                        tankCapacity = parsedCapacity, initialOdometer = parsedOdometer, deliveryDate = deliveryDateMillis
                    )

                    if (isEditMode) {
                        viewModel.updateVehicle(baseVehicle)
                        onSaved()
                    } else {
                        viewModel.addVehicle(baseVehicle.copy(isCurrent = true)) { onSaved() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                enabled = canSave
            ) {
                Text(if (isEditMode) "保存修改" else "确认添加", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showFuelTypePickerSheet) {
        MuiXWheelPickerBottomSheet("选择能源类型", FuelTypeOptions, fuelType, { showFuelTypePickerSheet = false }) { fuelType = it; showFuelTypePickerSheet = false }
    }
    if (showFuelGradePickerSheet) {
        MuiXWheelPickerBottomSheet("选择燃油标号", currentFuelGradeOptions, fuelGrade, { showFuelGradePickerSheet = false }) { fuelGrade = it; showFuelGradePickerSheet = false }
    }
    if (showDatePickerSheet) {
        MuiXDateWheelPickerBottomSheet(
            initialMillis = deliveryDateMillis,
            onDismiss = { showDatePickerSheet = false },
            onClear = { deliveryDateMillis = null; showDatePickerSheet = false },
            onConfirm = { deliveryDateMillis = it; showDatePickerSheet = false }
        )
    }
}

// ============================================================
// 重构后的日期选择器（核心优化）
// ============================================================

@Composable
private fun MuiXDateWheelPickerBottomSheet(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val calendar = remember(initialMillis) {
        Calendar.getInstance().apply { timeInMillis = initialMillis ?: System.currentTimeMillis() }
    }

    // 👇 使用 Int 存储，避免海量 String 解析
    var year by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }

    val maxDays = remember(year, month) {
        Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    LaunchedEffect(maxDays) {
        if (day > maxDays) day = maxDays
    }

    MuiXBottomSheetDialog(
        title = "选择提车日期",
        onDismiss = onDismiss,
        onConfirm = {
            val result = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
            onConfirm(result.timeInMillis)
        }
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClear) { Text("不选择（设为空）", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            MuiXWheelPickerColumn(
                options = YearOptions,
                currentValue = "${year}年",
                onValueChange = { year = it.removeSuffix("年").toIntOrNull() ?: year },
                modifier = Modifier.weight(1f)
            )
            MuiXWheelPickerColumn(
                options = (1..12).map { "${it}月" },
                currentValue = "${month}月",
                onValueChange = { month = it.removeSuffix("月").toIntOrNull() ?: month },
                modifier = Modifier.weight(1f)
            )
            MuiXWheelPickerColumn(
                options = (1..maxDays).map { "${it}日" },
                currentValue = "${day}日",
                onValueChange = { day = it.removeSuffix("日").toIntOrNull() ?: day },
                modifier = Modifier.weight(1f)
            )
        }
    }
}