package com.fueltracker.ui

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 预设选择列表
private val FuelTypeOptions = listOf("汽油", "柴油", "插电混动", "油电混动", "增程式", "纯电动")
private val GasolineGradeOptions = listOf("92#", "95#", "98#", "89#", "其他")
private val DieselGradeOptions = listOf("0# 柴油", "-10# 柴油", "-20# 柴油", "-35# 柴油", "其他")

private val YearOptions = (2005..Calendar.getInstance().get(Calendar.YEAR) + 1)
    .map { "${it}年" }
    .reversed()
private val MonthOptions = (1..12).map { "${it}月" }

private fun normalizeEnergyType(raw: String): String {
    if (raw.isBlank()) return ""
    return when {
        raw.contains("插电") -> "插电混动"
        raw.contains("油电") -> "油电混动"
        raw.contains("增程") -> "增程式"
        raw.contains("柴油") -> "柴油"
        raw.contains("纯电") -> "纯电动"
        raw.contains("汽油") -> "汽油"
        else -> raw
    }
}

private fun normalizeFuelGrade(raw: String): String {
    if (raw.isBlank()) return ""
    val num = Regex("""(-?\d+)""").find(raw)?.groupValues?.get(1) ?: return raw
    return when (num) {
        "92"  -> "92#"
        "95"  -> "95#"
        "98"  -> "98#"
        "89"  -> "89#"
        "0"   -> "0# 柴油"
        "-10" -> "-10# 柴油"
        "-20" -> "-20# 柴油"
        "-35" -> "-35# 柴油"
        else  -> raw
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
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        carDatabase.ensureCatalogLoaded()
        val list = carDatabase.brandsByLetter().flatMap { (l, brands) ->
            brands.map { CarBrand(it, l) }
        }
        allBrands = list
        isLoading = false
    }

    var brand by remember(vehicle) { mutableStateOf(vehicle?.brand ?: "") }
    var series by remember(vehicle) { mutableStateOf(vehicle?.series ?: "") }
    var model by remember(vehicle) { mutableStateOf(vehicle?.model ?: "") }
    var year by remember(vehicle) {
        mutableStateOf(
            vehicle?.year?.let { "${it}年" } ?: "${Calendar.getInstance().get(Calendar.YEAR)}年"
        )
    }

    var fuelType by remember(vehicle) { mutableStateOf(vehicle?.fuelType ?: "汽油") }
    var fuelGrade by remember(vehicle) { mutableStateOf(vehicle?.fuelGrade ?: "95#") }
    var tankCapacity by remember(vehicle) {
        mutableStateOf(
            vehicle?.tankCapacity?.let { if (it > 0) it.toString().removeSuffix(".0") else "" } ?: ""
        )
    }

    var initialOdometer by remember(vehicle) {
        mutableStateOf(
            vehicle?.initialOdometer?.toString()?.removeSuffix(".0") ?: "20"
        )
    }
    var deliveryDateMillis by remember(vehicle) { mutableStateOf(vehicle?.deliveryDate) }

    var showCarPicker by remember { mutableStateOf(false) }
    var pickerStartLevel by remember { mutableStateOf(PickerLevel.BRAND) }

    var showDatePickerSheet by remember { mutableStateOf(false) }
    var showYearPickerSheet by remember { mutableStateOf(false) }
    var showFuelTypePickerSheet by remember { mutableStateOf(false) }
    var showFuelGradePickerSheet by remember { mutableStateOf(false) }

    val selectedBrand by remember(brand, allBrands) {
        derivedStateOf { allBrands.find { it.brand == brand } }
    }

    val currentFuelGradeOptions by remember(fuelType) {
        derivedStateOf {
            if (fuelType == "柴油") DieselGradeOptions else GasolineGradeOptions
        }
    }

    LaunchedEffect(fuelType) {
        if (fuelType == "纯电动") {
            fuelGrade = ""
            return@LaunchedEffect
        }
        if (fuelGrade.isNotBlank() && fuelGrade !in currentFuelGradeOptions) {
            fuelGrade = if (fuelType == "柴油") "0# 柴油" else "95#"
        }
    }

    val formattedDeliveryDate by remember(deliveryDateMillis) {
        derivedStateOf {
            deliveryDateMillis?.let { millis ->
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
            } ?: "未填写"
        }
    }

    val canSave by remember {
        derivedStateOf {
            brand.isNotBlank() &&
                    model.isNotBlank() &&
                    (fuelType == "纯电动" || tankCapacity.toDoubleOrNull() != null) &&
                    initialOdometer.toDoubleOrNull() != null
        }
    }

    val openPicker: (PickerLevel) -> Unit = { target ->
        pickerStartLevel = when {
            brand.isBlank()  -> PickerLevel.BRAND
            series.isBlank() -> PickerLevel.SERIES
            year.isBlank()   -> PickerLevel.YEAR
            else             -> target
        }
        showCarPicker = true
    }

    if (showCarPicker) {
        BackHandler { showCarPicker = false }
        CarPickerFlow(
            startLevel = pickerStartLevel,
            initialBrand = brand,
            initialSeries = series,
            initialYear = year.removeSuffix("年").trim().let {
                if (it.isEmpty()) "" else "${it}款"
            },
            onBack = { showCarPicker = false },
            onPicked = { sel ->
                when (sel) {
                    is CarSelection.Full -> {
                        brand  = sel.brand
                        series = sel.series
                        model  = sel.trim.name
                        val yNum = sel.year.removeSuffix("款").removeSuffix("年").trim()
                        if (yNum.isNotEmpty()) year = "${yNum}年"

                        val et = normalizeEnergyType(sel.trim.energyType)
                        if (et.isNotBlank()) fuelType = et

                        val fg = normalizeFuelGrade(sel.trim.fuelGrade)
                        if (fg.isNotBlank()) fuelGrade = fg

                        if (sel.trim.fuelTank.isNotBlank()) {
                            tankCapacity = sel.trim.fuelTank
                        }
                    }
                    is CarSelection.BrandSeriesYear -> {
                        brand  = sel.brand
                        series = sel.series
                        val yNum = sel.year.removeSuffix("款").removeSuffix("年").trim()
                        if (yNum.isNotEmpty()) year = "${yNum}年"
                        model = ""
                    }
                    is CarSelection.BrandSeries -> {
                        brand  = sel.brand
                        series = sel.series
                        model = ""
                    }
                    is CarSelection.Manual -> {
                        brand  = sel.brand
                        series = ""
                        model  = ""
                    }
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
                title = {
                    Text(
                        text = if (isEditMode) "编辑车辆信息" else "添加新车辆",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            MuiXSectionHeader(title = "车辆详细信息")
            MuiXGroupCard {
                MuiXClickableItem(
                    label = "品牌",
                    value = brand.ifBlank { "点击从车型库选择" },
                    onClick = { openPicker(PickerLevel.BRAND) }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 0.8.dp
                )

                val currentBrandSeries = selectedBrand?.series ?: emptyList()
                var seriesMenuExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth()) {
                    MuiXClickableItem(
                        label = "车系",
                        value = series.ifBlank { "点击从车型库选择" },
                        onClick = { openPicker(PickerLevel.SERIES) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.8.dp
                    )

                    if (currentBrandSeries.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "选择预置车系",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { seriesMenuExpanded = true }
                            )

                            DropdownMenu(
                                expanded = seriesMenuExpanded,
                                onDismissRequest = { seriesMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                currentBrandSeries.forEach { brandSeries ->
                                    DropdownMenuItem(
                                        text = { Text(text = brandSeries) },
                                        onClick = {
                                            series = brandSeries
                                            seriesMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 0.8.dp
                )

                MuiXClickableItem(
                    label = "车型",
                    value = model.ifBlank { "点击从车型库选择" },
                    onClick = { openPicker(PickerLevel.TRIM) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 0.8.dp
                )

                MuiXClickableItem(
                    label = "年款",
                    value = year,
                    onClick = { openPicker(PickerLevel.YEAR) }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 0.8.dp
                )

                MuiXClickableItem(
                    label = "能源类型",
                    value = fuelType,
                    onClick = { showFuelTypePickerSheet = true }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 0.8.dp
                )

                if (fuelType != "纯电动") {
                    MuiXClickableItem(
                        label = "燃油标号",
                        value = fuelGrade.ifBlank { "未设置" },
                        onClick = { showFuelGradePickerSheet = true }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.8.dp
                    )
                }

                if (fuelType != "纯电动") {
                    MuiXInputItem(
                        label = "油箱容量 (L)",
                        value = tankCapacity,
                        onValueChange = { tankCapacity = it },
                        placeholder = "例如：50",
                        keyboardType = KeyboardType.Decimal
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.8.dp
                    )
                }

                MuiXClickableItem(
                    label = "提车日期",
                    value = formattedDeliveryDate,
                    onClick = { showDatePickerSheet = true }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 0.8.dp
                )

                MuiXInputItem(
                    label = "提车里程 (km)",
                    value = initialOdometer,
                    onValueChange = { initialOdometer = it },
                    placeholder = "例如：20",
                    keyboardType = KeyboardType.Decimal
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (!canSave) return@Button

                    val parsedYear = year.removeSuffix("年").toIntOrNull()
                        ?: Calendar.getInstance().get(Calendar.YEAR)
                    val parsedCapacity = tankCapacity.toDoubleOrNull() ?: 0.0
                    val parsedOdometer = initialOdometer.toDoubleOrNull() ?: 20.0

                    if (isEditMode) {
                        val updatedVehicle = vehicle.copy(
                            brand = brand.trim(),
                            series = series.trim(),
                            model = model.trim(),
                            year = parsedYear,
                            fuelType = fuelType.trim(),
                            fuelGrade = fuelGrade.trim(),
                            tankCapacity = parsedCapacity,
                            initialOdometer = parsedOdometer,
                            deliveryDate = deliveryDateMillis
                        )
                        viewModel.updateVehicle(updatedVehicle)
                        onSaved()
                    } else {
                        viewModel.addVehicle(
                            Vehicle(
                                brand = brand.trim(),
                                series = series.trim(),
                                model = model.trim(),
                                year = parsedYear,
                                fuelType = fuelType.trim(),
                                fuelGrade = fuelGrade.trim(),
                                tankCapacity = parsedCapacity,
                                initialOdometer = parsedOdometer,
                                deliveryDate = deliveryDateMillis,
                                isCurrent = true
                            )
                        ) { onSaved() }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                ),
                enabled = canSave
            ) {
                Text(
                    text = if (isEditMode) "保存修改" else "确认添加",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showYearPickerSheet) {
        MuiXWheelPickerBottomSheet(
            title = "选择年款",
            options = YearOptions,
            currentValue = year,
            onDismiss = { showYearPickerSheet = false },
            onConfirm = { selected ->
                year = selected
                showYearPickerSheet = false
            }
        )
    }

    if (showFuelTypePickerSheet) {
        MuiXWheelPickerBottomSheet(
            title = "选择能源类型",
            options = FuelTypeOptions,
            currentValue = fuelType,
            onDismiss = { showFuelTypePickerSheet = false },
            onConfirm = { selected ->
                fuelType = selected
                showFuelTypePickerSheet = false
            }
        )
    }

    if (showFuelGradePickerSheet) {
        MuiXWheelPickerBottomSheet(
            title = "选择燃油标号",
            options = currentFuelGradeOptions,
            currentValue = fuelGrade,
            onDismiss = { showFuelGradePickerSheet = false },
            onConfirm = { selected ->
                fuelGrade = selected
                showFuelGradePickerSheet = false
            }
        )
    }

    if (showDatePickerSheet) {
        MuiXDateWheelPickerBottomSheet(
            initialMillis = deliveryDateMillis,
            onDismiss = { showDatePickerSheet = false },
            onClear = {
                deliveryDateMillis = null
                showDatePickerSheet = false
            },
            onConfirm = { selectedMillis ->
                deliveryDateMillis = selectedMillis
                showDatePickerSheet = false
            }
        )
    }
}

@Composable
private fun MuiXDateWheelPickerBottomSheet(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val calendar = remember(initialMillis) {
        Calendar.getInstance().apply {
            timeInMillis = initialMillis ?: System.currentTimeMillis()
        }
    }

    var selectedYear by remember { mutableStateOf("${calendar.get(Calendar.YEAR)}年") }
    var selectedMonth by remember { mutableStateOf("${calendar.get(Calendar.MONTH) + 1}月") }
    var selectedDay by remember { mutableStateOf("${calendar.get(Calendar.DAY_OF_MONTH)}日") }

    val days by remember(selectedYear, selectedMonth) {
        derivedStateOf {
            val y = selectedYear.removeSuffix("年").toIntOrNull() ?: calendar.get(Calendar.YEAR)
            val m = (selectedMonth.removeSuffix("月").toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
            val c = Calendar.getInstance()
            c.clear()
            c.set(y, m, 1)
            val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            (1..max).map { "${it}日" }
        }
    }

    LaunchedEffect(days) {
        val currentDayNum = selectedDay.removeSuffix("日").toIntOrNull() ?: 1
        if (currentDayNum > days.size) selectedDay = "${days.size}日"
    }

    MuiXBottomSheetDialog(
        title = "选择提车日期",
        onDismiss = onDismiss,
        onConfirm = {
            val y = selectedYear.removeSuffix("年").toIntOrNull() ?: calendar.get(Calendar.YEAR)
            val m = (selectedMonth.removeSuffix("月").toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
            val d = selectedDay.removeSuffix("日").toIntOrNull() ?: 1
            val result = Calendar.getInstance().apply {
                clear()
                set(y, m, d)
            }
            onConfirm(result.timeInMillis)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClear) {
                Text(
                    "不选择（设为空）",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            MuiXWheelPickerColumn(
                options = YearOptions,
                currentValue = selectedYear,
                onValueChange = { selectedYear = it },
                modifier = Modifier.weight(1f)
            )
            MuiXWheelPickerColumn(
                options = MonthOptions,
                currentValue = selectedMonth,
                onValueChange = { selectedMonth = it },
                modifier = Modifier.weight(1f)
            )
            MuiXWheelPickerColumn(
                options = days,
                currentValue = selectedDay,
                onValueChange = { selectedDay = it },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

