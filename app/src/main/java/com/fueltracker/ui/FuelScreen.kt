package com.fueltracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
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
    val tankCapacity = vehicle?.tankCapacity ?: 0.0
    val context = LocalContext.current

    // 格式化日期 Formatter 缓存，避免反复重组重复创建
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val defaultFuelGrade = remember(vehicle, record) {
        val rawGrade = record?.fuelGrade?.takeIf { it.isNotBlank() }
            ?: vehicle?.fuelGrade?.takeIf { it.isNotBlank() }
            ?: "92#"
        val trimmed = rawGrade.trim()
        if (trimmed.endsWith("#") || trimmed.contains("柴油")) trimmed else "${trimmed}#"
    }

    val fuelGradeOptions = remember(defaultFuelGrade) {
        val baseList = mutableListOf("89#", "92#", "95#", "98#", "0# 柴油", "-10# 柴油")
        if (defaultFuelGrade !in baseList) {
            baseList.add(0, defaultFuelGrade)
        }
        baseList
    }

    var fuelGrade by remember(defaultFuelGrade) { mutableStateOf(defaultFuelGrade) }
    var showFuelGradePicker by remember { mutableStateOf(false) }

    // 修复：防止编辑模式传入非法时间戳
    var timestamp by remember(record) {
        mutableLongStateOf(record?.timestamp?.takeIf { it > 0 } ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    var odometer by remember(record) { mutableStateOf(record?.odometer?.let { formatNumber(it) } ?: "") }
    var volume by remember(record) { mutableStateOf(record?.volume?.let { formatNumber(it) } ?: "") }
    var unitPrice by remember(record) { mutableStateOf(record?.unitPrice?.let { formatNumber(it) } ?: "") }
    var totalAmount by remember(record) { mutableStateOf(record?.totalAmount?.let { formatNumber(it) } ?: "") }
    var discount by remember(record) { mutableStateOf(record?.discount?.let { formatNumber(it) } ?: "") }
    var amount by remember(record) { mutableStateOf(record?.actualPaidAmount?.let { formatNumber(it) } ?: "") }
    var remainingFuel by remember(record) { mutableStateOf(record?.remainingFuel?.let { formatNumber(it) } ?: "") }
    var isFull by remember(record) { mutableStateOf(record?.isFull ?: false) }
    var hasMissedRecord by remember(record) { mutableStateOf(record?.hasMissedRecord ?: false) }
    var missedOdometer by remember(record) { mutableStateOf(record?.missedOdometer?.let { formatNumber(it) } ?: "") }
    var missedVolume by remember(record) { mutableStateOf(record?.missedVolume?.let { formatNumber(it) } ?: "") }
    var note by remember(record) { mutableStateOf(record?.note ?: "") }

    // 修复：保存按钮防抖状态
    var isSaving by remember { mutableStateOf(false) }

    // 数值解析统一检查有限值，避免 NaN / Infinity 进入数据库。
    val volumeValue by remember(volume) {
        derivedStateOf {
            volume.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        }
    }
    val amountValue by remember(amount) {
        derivedStateOf {
            amount.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        }
    }
    val unitPriceValue by remember(unitPrice) {
        derivedStateOf {
            unitPrice.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        }
    }
    val totalAmountValue by remember(totalAmount) {
        derivedStateOf {
            totalAmount.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        }
    }
    val discountValue by remember(discount) {
        derivedStateOf {
            if (discount.isBlank()) {
                0.0
            } else {
                discount.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
            }
        }
    }
    val remainingFuelValue by remember(remainingFuel) {
        derivedStateOf {
            remainingFuel.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        }
    }
    val odometerValue by remember(odometer) {
        derivedStateOf {
            odometer.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        }
    }

    val missedOdometerValue by remember(missedOdometer) {
        derivedStateOf {
            missedOdometer.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        }
    }
    val missedVolumeValue by remember(missedVolume) {
        derivedStateOf {
            missedVolume.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        }
    }

    val isVolumeExceedsCapacity by remember(volumeValue, tankCapacity) {
        derivedStateOf {
            volumeValue != null && tankCapacity.isFinite() && tankCapacity > 0.0 &&
                    volumeValue!! > tankCapacity
        }
    }

    val isMissedValid by remember(hasMissedRecord, missedOdometerValue, missedVolumeValue) {
        derivedStateOf {
            !hasMissedRecord ||
                    (missedOdometerValue != null && missedVolumeValue != null)
        }
    }

    val isVolumeValid by remember(volumeValue, isVolumeExceedsCapacity) {
        derivedStateOf {
            volumeValue != null && !isVolumeExceedsCapacity
        }
    }

    // 可选数字字段允许留空，但不允许输入了非法文本后被静默保存为 null。
    val isOdometerInputValid by remember(odometer, odometerValue) {
        derivedStateOf { odometer.isBlank() || odometerValue != null }
    }
    val isRemainingFuelInputValid by remember(remainingFuel, remainingFuelValue, tankCapacity) {
        derivedStateOf {
            remainingFuel.isBlank() || (
                    remainingFuelValue != null &&
                            (tankCapacity <= 0.0 || remainingFuelValue!! <= tankCapacity)
                    )
        }
    }
    val isTotalAmountValid by remember(totalAmountValue) {
        derivedStateOf { totalAmountValue != null && totalAmountValue!! > 0.0 }
    }
    val isDiscountValid by remember(discount, discountValue, totalAmountValue) {
        derivedStateOf {
            discount.isBlank() || (
                    discountValue != null && totalAmountValue != null &&
                            discountValue!! <= totalAmountValue!!
                    )
        }
    }
    val isAmountConsistent by remember(amountValue, totalAmountValue, discountValue) {
        derivedStateOf {
            amountValue != null && totalAmountValue != null && discountValue != null &&
                    abs((totalAmountValue!! - discountValue!!) - amountValue!!) <= 0.02
        }
    }

    val isFormValid by remember(
        isVolumeValid,
        amountValue,
        unitPriceValue,
        totalAmountValue,
        discountValue,
        isTotalAmountValid,
        isDiscountValid,
        isAmountConsistent,
        fuelGrade,
        isMissedValid,
        isOdometerInputValid,
        isRemainingFuelInputValid,
        timestamp
    ) {
        derivedStateOf {
            isVolumeValid && amountValue != null && unitPriceValue != null &&
                    isTotalAmountValid && isDiscountValid && isAmountConsistent &&
                    fuelGrade.isNotBlank() && isMissedValid &&
                    isOdometerInputValid && isRemainingFuelInputValid && timestamp > 0L
        }
    }

    // 联动计算：基于“体积”和“单价”推导“原价”；基于“原价”和“单价”推导“体积”。
    fun onVolumeChange(newVol: String) {
        volume = newVol
        val v = newVol.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return
        val p = unitPriceValue
        val disc = discountValue ?: 0.0

        if (p != null) {
            val total = v * p
            if (total.isFinite()) {
                totalAmount = formatNumber(total)
                amount = formatNumber(total - disc)
            }
        }
    }

    fun onUnitPriceChange(newPrice: String) {
        unitPrice = newPrice
        val p = newPrice.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return
        val vol = volumeValue
        val knownTotal = totalAmountValue
        val disc = discountValue ?: 0.0

        if (vol != null) {
            val total = vol * p
            if (total.isFinite()) {
                totalAmount = formatNumber(total)
                amount = formatNumber(total - disc)
            }
        } else if (knownTotal != null) {
            val calculatedVolume = knownTotal / p
            if (calculatedVolume.isFinite() && calculatedVolume > 0.0) {
                volume = formatNumber(calculatedVolume)
                amount = formatNumber(knownTotal - disc)
            }
        }
    }

    fun onTotalAmountChange(newTotal: String) {
        totalAmount = newTotal
        val total = newTotal.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return
        val disc = discountValue ?: 0.0
        amount = formatNumber(total - disc)

        val p = unitPriceValue
        val vol = volumeValue

        // 核心修复：优先保持单价不变，强制覆盖更新体积，解决输入中间态导致的值锁死。
        if (p != null) {
            val calculatedVolume = total / p
            if (calculatedVolume.isFinite() && calculatedVolume > 0.0) {
                volume = formatNumber(calculatedVolume)
            }
        } else if (vol != null) {
            // 如果单价为空，但有体积，则用原价和体积反推单价
            val calculatedPrice = total / vol
            if (calculatedPrice.isFinite() && calculatedPrice > 0.0) {
                unitPrice = formatNumber(calculatedPrice)
            }
        }
    }

    fun onAmountChange(newAmt: String) {
        amount = newAmt
        val amt = newAmt.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return
        val disc = discountValue ?: 0.0
        val total = amt + disc
        if (!total.isFinite()) return
        totalAmount = formatNumber(total)

        // 修改实付导致原价变化，如同上面的 onTotalAmountChange 一样重新计算体积或单价
        val p = unitPriceValue
        val vol = volumeValue
        if (p != null) {
            val calculatedVolume = total / p
            if (calculatedVolume.isFinite() && calculatedVolume > 0.0) {
                volume = formatNumber(calculatedVolume)
            }
        } else if (vol != null) {
            val calculatedPrice = total / vol
            if (calculatedPrice.isFinite() && calculatedPrice > 0.0) {
                unitPrice = formatNumber(calculatedPrice)
            }
        }
    }

    fun onDiscountChange(newDisc: String) {
        discount = newDisc
        val disc = if (newDisc.isBlank()) {
            0.0
        } else {
            newDisc.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        } ?: return

        val total = totalAmountValue
        if (total != null) {
            amount = formatNumber(total - disc)
        }
        // 优惠金额的变动只影响实付，不影响体积和单价
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "编辑加油记录" else "记加油",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
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
        val currentVehicle = vehicle
        if (currentVehicle == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                // 修复：添加 IME 和导航栏 Padding，防止键盘遮挡底部按钮
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 车辆信息卡片
            MiuixCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${currentVehicle.brand} ${currentVehicle.series}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentVehicle.model,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "油箱容量 ${currentVehicle.tankCapacity} L",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 油品标号
            MiuixCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFuelGradePicker = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "油品标号",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " *",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = fuelGrade,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 输入区域
            MiuixCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "加油时间",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = " *",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = remember(timestamp) { dateFormatter.format(Date(timestamp)) },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }

                        MiuixInput(
                            value = odometer,
                            onValueChange = { odometer = it },
                            label = "当前公里数",
                            suffix = "km",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiuixInput(
                            value = volume,
                            onValueChange = { onVolumeChange(it) },
                            label = "本次加油量",
                            isRequired = true,
                            suffix = "L",
                            isError = isVolumeExceedsCapacity,
                            errorMessage = if (isVolumeExceedsCapacity) "超出容量(${tankCapacity}L)" else null,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        MiuixInput(
                            value = unitPrice,
                            onValueChange = { onUnitPriceChange(it) },
                            label = "单价",
                            isRequired = true,
                            suffix = "元/L",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiuixInput(
                            value = totalAmount,
                            onValueChange = { onTotalAmountChange(it) },
                            label = "原价",
                            isRequired = true,
                            suffix = "元",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        MiuixInput(
                            value = discount,
                            onValueChange = { onDiscountChange(it) },
                            label = "优惠金额",
                            suffix = "元",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiuixInput(
                            value = amount,
                            onValueChange = { onAmountChange(it) },
                            label = "实际支付",
                            isRequired = true,
                            suffix = "元",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        MiuixInput(
                            value = remainingFuel,
                            onValueChange = { remainingFuel = it },
                            label = "加油后剩余",
                            suffix = "L",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val vVal = volumeValue
            val pVal = unitPriceValue
            if (vVal != null && pVal != null) {
                val calculatedTotal = vVal * pVal
                val total = totalAmountValue ?: calculatedTotal
                val disc = discountValue
                if (calculatedTotal.isFinite() && disc != null) {
                    val paid = total - disc
                    Text(
                        text = "计算结果：$fuelGrade · %.2f L × %.2f 元/L ≈ %.2f 元，实付 %.2f 元"
                            .format(LocalLocale.current.platformLocale, vVal, pVal, total, paid),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
            if (discount.isNotBlank() && !isDiscountValid) {
                Text(
                    text = "优惠金额必须是非负数，且不能超过原价",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (
                amount.isNotBlank() && totalAmount.isNotBlank() && discountValue != null &&
                amountValue != null && totalAmountValue != null && !isAmountConsistent
            ) {
                Text(
                    text = "原价减去优惠金额应等于实际支付金额，请检查金额",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (remainingFuel.isNotBlank() && !isRemainingFuelInputValid) {
                Text(
                    text = "加油后剩余油量无效或超出油箱容量",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // 勾选项
            MiuixCard {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    MiuixCheckRow(
                        checked = isFull,
                        text = "本次已加满",
                        onChecked = { checked ->
                            isFull = checked
                            if (checked && tankCapacity > 0) {
                                remainingFuel = formatNumber(tankCapacity)
                            } else if (!checked) {
                                remainingFuel = ""
                            }
                        }
                    )
                    MiuixCheckRow(
                        checked = hasMissedRecord,
                        text = "上次加油忘记记录 (漏记)",
                        onChecked = { checked ->
                            hasMissedRecord = checked
                            if (!checked) {
                                missedOdometer = ""
                                missedVolume = ""
                            }
                        }
                    )
                    if (hasMissedRecord) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiuixInput(
                                value = missedOdometer,
                                onValueChange = { missedOdometer = it },
                                label = "漏记期间大概行驶里程",
                                isRequired = true,
                                suffix = "km",
                                keyboardType = KeyboardType.Decimal
                            )
                            MiuixInput(
                                value = missedVolume,
                                onValueChange = { missedVolume = it },
                                label = "漏记期间大概加油量",
                                isRequired = true,
                                suffix = "L",
                                keyboardType = KeyboardType.Decimal
                            )
                        }
                    }
                }
            }

            // 备注
            MiuixCard {
                MiuixInput(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注 (如: 某某加油站/优惠活动)",
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // 保存按钮
            Button(
                onClick = {
                    if (isSaving) return@Button

                    val vol = volumeValue ?: return@Button
                    val amt = amountValue ?: return@Button
                    val price = unitPriceValue ?: return@Button
                    val total = totalAmountValue ?: return@Button
                    val disc = if (discount.isBlank()) null else discountValue
                    val odo = odometerValue
                    val remaining = remainingFuelValue

                    // 防止有限数值相乘溢出，或金额字段在 UI 外被改为不一致。
                    if (!total.isFinite() || totalAmount.isBlank() || timestamp <= 0L) {
                        Toast.makeText(context, "请检查原价和加油时间", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (disc != null && (!disc.isFinite() || disc < 0.0 || disc > total)) {
                        Toast.makeText(context, "优惠金额无效", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (abs((total - (disc ?: 0.0)) - amt) > 0.02) {
                        Toast.makeText(context, "原价减优惠金额应等于实际支付金额", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSaving = true

                    val recordToSave = record?.copy(
                        timestamp = timestamp,
                        fuelGrade = fuelGrade,
                        odometer = odo,
                        volume = vol,
                        totalAmount = total,
                        discount = disc,
                        actualPaidAmount = amt,
                        unitPrice = price,
                        remainingFuel = remaining,
                        isFull = isFull,
                        hasMissedRecord = hasMissedRecord,
                        missedOdometer = missedOdometerValue,
                        missedVolume = missedVolumeValue,
                        note = note.trim()
                    ) ?: FuelRecord(
                        vehicleId = currentVehicle.id,
                        timestamp = timestamp,
                        fuelGrade = fuelGrade,
                        odometer = odo,
                        volume = vol,
                        totalAmount = total,
                        discount = disc,
                        actualPaidAmount = amt,
                        unitPrice = price,
                        remainingFuel = remaining,
                        isFull = isFull,
                        hasMissedRecord = hasMissedRecord,
                        missedOdometer = missedOdometerValue,
                        missedVolume = missedVolumeValue,
                        note = note.trim()
                    )

                    val onResult: (Result<Unit>) -> Unit = { result ->
                        isSaving = false
                        if (result.isSuccess) {
                            onSaved()
                        } else {
                            Toast.makeText(
                                context,
                                "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    if (isEditMode) {
                        viewModel.updateFuelRecord(recordToSave, onResult)
                    } else {
                        viewModel.addFuelRecord(recordToSave, onResult)
                    }
                },
                enabled = isFormValid && !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = if (isEditMode) "保存修改" else "保存加油记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 浮层：油品选择器
        if (showFuelGradePicker) {
            HyperFuelGradePickerDialog(
                currentValue = fuelGrade,
                options = fuelGradeOptions,
                onSelected = {
                    fuelGrade = it
                    showFuelGradePicker = false
                },
                onDismiss = { showFuelGradePicker = false }
            )
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
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

// ============================================================
// 通用输入组件
// ============================================================

@Composable
private fun MiuixCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    MiuiCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        content()
    }
}

@Composable
private fun MiuixInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isRequired) {
                Text(
                    text = " *",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(14.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            suffix = {
                suffix?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = MaterialTheme.colorScheme.error,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun MiuixCheckRow(checked: Boolean, text: String, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================================
// 工具函数
// ============================================================

private fun formatNumber(value: Double): String {
    if (!value.isFinite()) return ""
    return String.format(Locale.US, "%.2f", value)
}

// ============================================================
// 油品标号选择弹窗
// ============================================================

@Composable
private fun HyperFuelGradePickerDialog(
    currentValue: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGrade by remember { mutableStateOf(currentValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "取消",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                    Text(
                        text = "选择油品标号",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "确定",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onSelected(selectedGrade) }
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                HyperWheelList(
                    selectedValue = selectedGrade,
                    items = options,
                    visibleCount = 5,
                    modifier = Modifier.fillMaxWidth(),
                    onChange = { selectedGrade = it }
                )
            }
        }
    }
}
// ============================================================
// 滚轮选择
// ============================================================

@Composable
private fun <T> HyperWheelList(
    selectedValue: T,
    items: List<T>,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    itemLabel: (T) -> String = { it.toString() },
    onChange: (T) -> Unit
) {
    WheelList(
        selectedValue = selectedValue,
        items = items,
        modifier = modifier,
        visibleCount = visibleCount,
        style = WheelStyle.Plain,
        itemLabel = itemLabel,
        onChange = onChange
    )
}
