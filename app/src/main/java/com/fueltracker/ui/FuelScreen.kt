package com.fueltracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fueltracker.data.FuelRecord
import com.fueltracker.ui.components.MiuiDatePickerDialog
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun String.toValidDouble(allowZero: Boolean = false): Double? {
    val v = toDoubleOrNull() ?: return null
    return if (v.isFinite() && (if (allowZero) v >= 0.0 else v > 0.0)) v else null
}

private fun formatMoney(value: Double): String = if (!value.isFinite()) "" else String.format(Locale.US, "%.2f", value)
private fun formatVolume(value: Double): String = if (!value.isFinite()) "" else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelScreen(
    viewModel: MainViewModel,
    record: FuelRecord? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isEditMode = record != null
    val vehicle by viewModel.currentVehicle.collectAsState()
    val context = LocalContext.current

    val currentVehicle = vehicle
    val isVehicleMatched = currentVehicle == null || !isEditMode || record.vehicleId == currentVehicle.id
    val tankCapacity = currentVehicle?.tankCapacity ?: 0.0

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val defaultFuelGrade = remember(currentVehicle, record) {
        val rawGrade = record?.fuelGrade?.takeIf { it.isNotBlank() }
            ?: currentVehicle?.fuelGrade?.takeIf { it.isNotBlank() } ?: "92#"
        val trimmed = rawGrade.trim()
        if (trimmed.endsWith("#") || trimmed.contains("柴油")) trimmed else "${trimmed}#"
    }

    val fuelGradeOptions = remember(defaultFuelGrade) {
        val baseList = mutableListOf("89#", "92#", "95#", "98#", "0# 柴油", "-10# 柴油")
        if (defaultFuelGrade !in baseList) baseList.add(0, defaultFuelGrade)
        baseList
    }

    var fuelGrade by remember(defaultFuelGrade) { mutableStateOf(defaultFuelGrade) }
    var showFuelGradePicker by remember { mutableStateOf(false) }
    var timestamp by remember(record) { mutableLongStateOf(record?.timestamp?.takeIf { it > 0 } ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 表单状态
    var odometer by remember(record) { mutableStateOf(record?.odometer?.toString() ?: "") }
    var volume by remember(record) { mutableStateOf(record?.volume?.toString() ?: "") }
    var unitPrice by remember(record) { mutableStateOf(record?.unitPrice?.toString() ?: "") }
    var totalAmount by remember(record) { mutableStateOf(record?.totalAmount?.toString() ?: "") }
    var discount by remember(record) { mutableStateOf(record?.discount?.toString() ?: "") }
    var amount by remember(record) { mutableStateOf(record?.actualPaidAmount?.toString() ?: "") }
    var remainingFuel by remember(record) { mutableStateOf(record?.remainingFuel?.toString() ?: "") }
    var isFull by remember(record) { mutableStateOf(record?.isFull ?: false) }
    var hasMissedRecord by remember(record) { mutableStateOf(record?.hasMissedRecord ?: false) }
    var missedOdometer by remember(record) { mutableStateOf(record?.missedOdometer?.toString() ?: "") }
    var missedVolume by remember(record) { mutableStateOf(record?.missedVolume?.toString() ?: "") }
    var note by remember(record) { mutableStateOf(record?.note ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    // 🚨 修复：新增模式下切换车辆时，自动重置表单，防止数据串车
    LaunchedEffect(currentVehicle?.id) {
        if (!isEditMode) {
            odometer = ""; volume = ""; unitPrice = ""; totalAmount = ""
            discount = ""; amount = ""; remainingFuel = ""; isFull = false
            hasMissedRecord = false; missedOdometer = ""; missedVolume = ""; note = ""
        }
    }

    // 解析数值
    val volumeValue by remember(volume) { derivedStateOf { volume.toValidDouble() } }
    val amountValue by remember(amount) { derivedStateOf { amount.toValidDouble(allowZero = true) } }
    val unitPriceValue by remember(unitPrice) { derivedStateOf { unitPrice.toValidDouble() } }
    val totalAmountValue by remember(totalAmount) { derivedStateOf { totalAmount.toValidDouble(allowZero = true) } }
    val discountValue by remember(discount) { derivedStateOf { if (discount.isBlank()) 0.0 else discount.toValidDouble(allowZero = true) } }
    val remainingFuelValue by remember(remainingFuel) { derivedStateOf { remainingFuel.toValidDouble(allowZero = true) } }
    val odometerValue by remember(odometer) { derivedStateOf { odometer.toValidDouble() } }
    val missedOdometerValue by remember(missedOdometer) { derivedStateOf { missedOdometer.toValidDouble() } }
    val missedVolumeValue by remember(missedVolume) { derivedStateOf { missedVolume.toValidDouble() } }

    // 校验逻辑
    val isVolumeExceedsCapacity = volumeValue != null && tankCapacity > 0.0 && volumeValue!! > tankCapacity
    val isVolumeValid = volumeValue != null && !isVolumeExceedsCapacity
    val isOdometerInputValid = odometer.isBlank() || odometerValue != null
    val isRemainingFuelInputValid = remainingFuel.isBlank() || (remainingFuelValue != null && (tankCapacity <= 0.0 || remainingFuelValue!! <= tankCapacity))

    val isMissedValid = !hasMissedRecord || (missedOdometerValue != null && missedVolumeValue != null)
    val isMissedOdometerRangeValid = !hasMissedRecord || odometerValue != null && missedOdometerValue != null && missedOdometerValue!! < odometerValue!!

    val isTotalAmountValid = totalAmountValue != null && totalAmountValue!! > 0.0
    val isDiscountValid = discount.isBlank() || (discountValue != null && totalAmountValue != null && discountValue!! <= totalAmountValue!!)

    // 金额与乘积一致性
    val isAmountConsistent = amountValue != null && totalAmountValue != null && discountValue != null && abs((totalAmountValue!! - discountValue!!) - amountValue!!) <= 0.02
    val isProductConsistent = volumeValue != null && unitPriceValue != null && totalAmountValue != null && abs((volumeValue!! * unitPriceValue!!) - totalAmountValue!!) <= 0.05

    // 🚨 修复：加满状态与剩余油量必须一致
    val isFullConsistent = !(isFull && tankCapacity > 0) || abs((remainingFuelValue ?: 0.0) - tankCapacity) < 0.01

    // 统一收集错误信息（按优先级递进，防止重复提示）
    val errorMessages = buildList {
        if (!isVehicleMatched) add("⛔ 记录所属车辆与当前车辆不一致，禁止保存")
        if (totalAmount.isBlank()) add("请输入原价") else if (!isTotalAmountValid) add("原价必须大于 0")
        if (volume.isBlank()) add("请输入加油量") else if (!isVolumeValid) add(if (isVolumeExceedsCapacity) "加油量超出油箱容量(${tankCapacity}L)" else "加油量无效")
        if (unitPrice.isBlank()) add("请输入单价") else if (unitPriceValue == null) add("单价无效")
        if (amount.isBlank()) add("请输入实付金额") else if (amountValue == null) add("实付金额无效")

        if (discount.isNotBlank() && discountValue == null) add("优惠金额无效")
        else if (!isDiscountValid) add("优惠金额不能超过原价")

        if (isTotalAmountValid && isDiscountValid && isAmountConsistent.not()) add("原价减去优惠金额应等于实付金额")
        if (isTotalAmountValid && volumeValue != null && unitPriceValue != null && isProductConsistent.not()) add("加油量乘以单价应等于原价")

        if (!isRemainingFuelInputValid) add("剩余油量无效或超出容量")
        if (!isFullConsistent) add("已勾选加满，但剩余油量与油箱容量不一致")

        if (hasMissedRecord) {
            if (!isMissedValid) add("请填写完整的漏记里程和油量")
            else if (!isMissedOdometerRangeValid) add("漏记里程必须小于当前里程")
        }
    }

    val isFormValid = isVolumeValid && amountValue != null && unitPriceValue != null &&
            isTotalAmountValid && isDiscountValid && isAmountConsistent && isProductConsistent &&
            fuelGrade.isNotBlank() && isMissedValid && isMissedOdometerRangeValid &&
            isOdometerInputValid && isRemainingFuelInputValid && timestamp > 0L && isVehicleMatched && isFullConsistent

    // 联动计算
    fun autoCalculate(v: Double, p: Double, disc: Double) {
        val total = v * p
        if (total.isFinite()) {
            totalAmount = formatMoney(total)
            amount = formatMoney(total - disc) // 如果为负，界面会由 amountValue 变 null 并报错
        }
    }

    fun onVolumeChange(newVol: String) {
        volume = newVol
        newVol.toValidDouble()?.let { v -> unitPriceValue?.let { p -> autoCalculate(v, p, discountValue ?: 0.0) } }
    }

    fun onUnitPriceChange(newPrice: String) {
        unitPrice = newPrice
        newPrice.toValidDouble()?.let { p ->
            volumeValue?.let { v -> autoCalculate(v, p, discountValue ?: 0.0) }
                ?: totalAmountValue?.let { total ->
                    val calcVol = total / p
                    if (calcVol.isFinite() && calcVol > 0) {
                        volume = formatVolume(calcVol); amount = formatMoney(total - (discountValue ?: 0.0))
                    }
                }
        }
    }

    fun onTotalAmountChange(newTotal: String) {
        totalAmount = newTotal
        newTotal.toValidDouble(allowZero = true)?.let { total ->
            val disc = discountValue ?: 0.0
            amount = formatMoney(total - disc)
            unitPriceValue?.let { p ->
                val calcVol = total / p
                if (calcVol.isFinite() && calcVol > 0) volume = formatVolume(calcVol)
            } ?: volumeValue?.let { v ->
                val calcPrice = total / v
                if (calcPrice.isFinite() && calcPrice > 0) unitPrice = formatMoney(calcPrice)
            }
        }
    }

    fun onAmountChange(newAmt: String) {
        amount = newAmt
        newAmt.toValidDouble(allowZero = true)?.let { amt ->
            val total = amt + (discountValue ?: 0.0)
            if (!total.isFinite()) return
            totalAmount = formatMoney(total)
            unitPriceValue?.let { p ->
                val calcVol = total / p
                if (calcVol.isFinite() && calcVol > 0) volume = formatVolume(calcVol)
            } ?: volumeValue?.let { v ->
                val calcPrice = total / v
                if (calcPrice.isFinite() && calcPrice > 0) unitPrice = formatMoney(calcPrice)
            }
        }
    }

    fun onDiscountChange(newDisc: String) {
        discount = newDisc
        val disc = if (newDisc.isBlank()) 0.0 else newDisc.toValidDouble(allowZero = true) ?: return
        totalAmountValue?.let { amount = formatMoney(it - disc) } // 不强制转正，由校验拦截
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "编辑加油记录" else "记加油", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (currentVehicle == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(padding).imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 车辆信息卡片
            MiuiCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${currentVehicle.brand} ${currentVehicle.series}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(currentVehicle.model, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("油箱容量 ${currentVehicle.tankCapacity} L", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 油品标号卡片
            MiuiCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showFuelGradePicker = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("油品标号 *", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(fuelGrade, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            // 输入区域卡片
            MiuiCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text("加油时间 *", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                    .clickable { showDatePicker = true }.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(dateFormatter.format(Date(timestamp)), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        MiuixInput(odometer, { odometer = it }, "当前公里数", Modifier.weight(1f), suffix = "km", keyboardType = KeyboardType.Decimal)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiuixInput(volume, { onVolumeChange(it) }, "本次加油量", Modifier.weight(1f), isRequired = true, suffix = "L", isError = isVolumeExceedsCapacity, errorMessage = "超出容量(${tankCapacity}L)", keyboardType = KeyboardType.Decimal)
                        MiuixInput(unitPrice, { onUnitPriceChange(it) }, "单价", Modifier.weight(1f), isRequired = true, suffix = "元/L", keyboardType = KeyboardType.Decimal)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiuixInput(totalAmount, { onTotalAmountChange(it) }, "原价", Modifier.weight(1f), isRequired = true, suffix = "元", keyboardType = KeyboardType.Decimal)
                        MiuixInput(discount, { onDiscountChange(it) }, "优惠金额", Modifier.weight(1f), suffix = "元", keyboardType = KeyboardType.Decimal)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiuixInput(amount, { onAmountChange(it) }, "实际支付", Modifier.weight(1f), isRequired = true, suffix = "元", keyboardType = KeyboardType.Decimal)
                        MiuixInput(remainingFuel, { remainingFuel = it }, "加油后剩余", Modifier.weight(1f), suffix = "L", keyboardType = KeyboardType.Decimal)
                    }
                }
            }

            // 计算结果预览
            val vVal = volumeValue
            val pVal = unitPriceValue
            if (vVal != null && pVal != null && totalAmountValue != null && discountValue != null) {
                val displayedAmount = amountValue?.let { formatMoney(it) } ?: "--"
                val displayTotal = if (isProductConsistent) formatMoney(totalAmountValue!!) else "--"
                Text(
                    text = "计算结果：$fuelGrade · ${formatVolume(vVal)} L × ${formatMoney(pVal)} 元/L ≈ $displayTotal 元，实付 $displayedAmount 元",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)
                )
                if (!isProductConsistent) {
                    Text("提示：加油量 × 单价 与 原价不一致，请检查输入", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                }
            }

            // 统一错误提示
            if (errorMessages.isNotEmpty()) {
                Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    errorMessages.forEach { msg ->
                        Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            // 勾选项
            MiuiCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    MiuixCheckRow(isFull, "本次已加满") { checked ->
                        isFull = checked
                        // 🚨 强制同步：勾选时强制等于容量，取消勾选时若等于容量则清空
                        if (checked) {
                            if (tankCapacity > 0) remainingFuel = formatVolume(tankCapacity)
                        } else {
                            if (remainingFuel == formatVolume(tankCapacity)) remainingFuel = ""
                        }
                    }
                    MiuixCheckRow(hasMissedRecord, "上次加油忘记记录 (漏记)") { checked ->
                        hasMissedRecord = checked
                        if (!checked) { missedOdometer = ""; missedVolume = "" }
                    }
                    if (hasMissedRecord) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiuixInput(missedOdometer, { missedOdometer = it }, "漏记期间大概行驶里程", isRequired = true, suffix = "km", keyboardType = KeyboardType.Decimal)
                            MiuixInput(missedVolume, { missedVolume = it }, "漏记期间大概加油量", isRequired = true, suffix = "L", keyboardType = KeyboardType.Decimal)
                        }
                    }
                }
            }

            // 备注
            MiuiCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                MiuixInput(note, { note = it }, "备注 (如: 某某加油站/优惠活动)", Modifier.padding(12.dp))
            }

            Spacer(Modifier.height(4.dp))

            // 保存按钮
            Button(
                onClick = {
                    if (isSaving) return@Button
                    if (!isFormValid) {
                        Toast.makeText(context, "请检查表单错误", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val vol = volumeValue!!
                    val amt = amountValue!!
                    val price = unitPriceValue!!
                    val total = totalAmountValue!!
                    val disc = if (discount.isBlank()) null else discountValue

                    isSaving = true
                    val recordToSave = record?.copy(
                        timestamp = timestamp, fuelGrade = fuelGrade, odometer = odometerValue,
                        volume = vol, totalAmount = total, discount = disc, actualPaidAmount = amt,
                        unitPrice = price, remainingFuel = remainingFuelValue, isFull = isFull,
                        hasMissedRecord = hasMissedRecord, missedOdometer = missedOdometerValue,
                        missedVolume = missedVolumeValue, note = note.trim()
                    ) ?: FuelRecord(
                        vehicleId = currentVehicle.id, timestamp = timestamp, fuelGrade = fuelGrade,
                        odometer = odometerValue, volume = vol, totalAmount = total, discount = disc,
                        actualPaidAmount = amt, unitPrice = price, remainingFuel = remainingFuelValue,
                        isFull = isFull, hasMissedRecord = hasMissedRecord, missedOdometer = missedOdometerValue,
                        missedVolume = missedVolumeValue, note = note.trim()
                    )

                    val onResult: (Result<Unit>) -> Unit = { result ->
                        isSaving = false
                        if (result.isSuccess) onSaved() else Toast.makeText(
                            context, "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_LONG
                        ).show()
                    }

                    try {
                        if (isEditMode) viewModel.updateFuelRecord(recordToSave, onResult)
                        else viewModel.addFuelRecord(recordToSave, onResult)
                    } catch (e: Exception) {
                        isSaving = false
                        Toast.makeText(context, "系统异常：${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = isFormValid && !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Text(if (isEditMode) "保存修改" else "保存加油记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }

        // 浮层
        if (showFuelGradePicker) {
            HyperFuelGradePickerDialog(fuelGrade, fuelGradeOptions, { fuelGrade = it; showFuelGradePicker = false }, { showFuelGradePicker = false })
        }
        // 浮层：日期选择器
        if (showDatePicker) {
            MiuiDatePickerDialog(
                initialTimestamp = timestamp,
                title = "选择加油时间",
                onSelected = { selectedTimestamp ->
                    timestamp = selectedTimestamp
                    showDatePicker = false
                },
                onDismiss = {
                    showDatePicker = false
                }
            )
        }
    }
}

@Composable
private fun MiuixInput(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    isRequired: Boolean = false, isError: Boolean = false, errorMessage: String? = null,
    suffix: String? = null, keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (isRequired) Text(" *", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
            isError = isError, shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            suffix = { suffix?.let { Text(it, fontSize = 14.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent, errorBorderColor = MaterialTheme.colorScheme.error, cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        if (isError && errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    }
}

@Composable
private fun MiuixCheckRow(checked: Boolean, text: String, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun HyperFuelGradePickerDialog(currentValue: String, options: List<String>, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    var selectedGrade by remember { mutableStateOf(currentValue) }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("取消", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
                    Text("选择油品标号", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("确定", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onSelected(selectedGrade) }.padding(8.dp))
                }
                WheelList(selectedGrade, options, Modifier.fillMaxWidth(), 5, WheelStyle.Plain, { it }, { selectedGrade = it })
            }
        }
    }
}