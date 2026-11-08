package com.fueltracker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fueltracker.data.FuelRecord
import com.fueltracker.ui.miuix.dialogs.MiuixConfirmDialog
import com.fueltracker.ui.miuix.dialogs.MiuixDatePickerDialog
import com.fueltracker.ui.components.WheelList
import com.fueltracker.ui.components.WheelStyle
import com.fueltracker.ui.miuix.MiuixBackButton
import com.fueltracker.ui.miuix.MiuixCard
import com.fueltracker.ui.miuix.MiuixFieldLabel
import com.fueltracker.ui.miuix.MiuixSwitch
import com.fueltracker.ui.miuix.MiuixTextField
import com.fueltracker.ui.theme.adaptiveBackgroundTitleColor
import com.fueltracker.ui.theme.adaptiveBackgroundTitleStyle
import com.fueltracker.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
private fun formatPercent(value: Double): String = if (!value.isFinite()) "" else String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')

@Composable
fun FuelScreen(
    viewModel: MainViewModel,
    record: FuelRecord? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isEditMode = record != null
    val vehicle by viewModel.currentVehicle.collectAsState()
    val context = LocalContext.current

    val currentVehicle = vehicle
    val isVehicleMatched =
        currentVehicle == null || !isEditMode || record.vehicleId == currentVehicle.id
    val tankCapacity = currentVehicle?.tankCapacity ?: 0.0
    var showDeleteDialog by remember { mutableStateOf(false) }

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
    var timestamp by remember(record) {
        mutableLongStateOf(record?.timestamp?.takeIf { it > 0 } ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    // 表单状态
    var odometer by remember(record) { mutableStateOf(record?.odometer?.toString() ?: "") }
    var volume by remember(record) { mutableStateOf(record?.volume?.toString() ?: "") }
    var unitPrice by remember(record) { mutableStateOf(record?.unitPrice?.toString() ?: "") }
    var totalAmount by remember(record) { mutableStateOf(record?.totalAmount?.toString() ?: "") }
    var discount by remember(record) { mutableStateOf(record?.discount?.toString() ?: "") }
    var amount by remember(record) { mutableStateOf(record?.actualPaidAmount?.toString() ?: "") }
    var remainingFuel by remember(record) {
        mutableStateOf(record?.remainingFuel?.toString() ?: "")
    }
    var isFull by remember(record) { mutableStateOf(record?.isFull ?: false) }
    var hasMissedRecord by remember(record) { mutableStateOf(record?.hasMissedRecord ?: false) }
    var missedOdometer by remember(record) {
        mutableStateOf(record?.missedOdometer?.toString() ?: "")
    }
    var missedVolume by remember(record) { mutableStateOf(record?.missedVolume?.toString() ?: "") }
    var note by remember(record) { mutableStateOf(record?.note ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    var remainingFuelUsePercent by remember { mutableStateOf(false) }

    LaunchedEffect(currentVehicle?.id) {
        if (!isEditMode) {
            odometer = ""; volume = ""; unitPrice = ""; totalAmount = ""
            discount = ""; amount = ""; remainingFuel = ""; isFull = false
            hasMissedRecord = false; missedOdometer = ""; missedVolume = ""; note = ""
            remainingFuelUsePercent = false
        }
    }

    // 解析数值
    val volumeValue by remember(volume) { derivedStateOf { volume.toValidDouble() } }
    val amountValue by remember(amount) { derivedStateOf { amount.toValidDouble(allowZero = true) } }
    val unitPriceValue by remember(unitPrice) { derivedStateOf { unitPrice.toValidDouble() } }
    val totalAmountValue by remember(totalAmount) { derivedStateOf { totalAmount.toValidDouble(allowZero = true) } }
    val discountValue by remember(discount) { derivedStateOf { if (discount.isBlank()) 0.0 else discount.toValidDouble(allowZero = true) } }
    val effectiveTotalAmount = totalAmountValue ?: if (discount.isBlank()) amountValue else null

    val remainingFuelRawValue by remember { derivedStateOf { remainingFuel.toValidDouble(allowZero = true) } }
    val remainingFuelValue: Double? by remember(tankCapacity) {
        derivedStateOf {
            val raw = remainingFuelRawValue ?: return@derivedStateOf null
            if (remainingFuelUsePercent) {
                if (tankCapacity > 0.0) (raw / 100.0) * tankCapacity else null
            } else raw
        }
    }
    val isRemainingPercentInRange = !remainingFuelUsePercent || remainingFuel.isBlank() ||
            (remainingFuelRawValue != null && remainingFuelRawValue!! in 0.0..100.0)

    val odometerValue by remember(odometer) { derivedStateOf { odometer.toValidDouble() } }
    val missedOdometerValue by remember(missedOdometer) { derivedStateOf { missedOdometer.toValidDouble() } }
    val missedVolumeValue by remember(missedVolume) { derivedStateOf { missedVolume.toValidDouble() } }

    // 校验逻辑
    val isVolumeExceedsCapacity = volumeValue != null && tankCapacity > 0.0 && volumeValue!! > tankCapacity
    val isVolumeValid = volumeValue != null && !isVolumeExceedsCapacity
    val isOdometerInputValid = odometer.isBlank() || odometerValue != null
    val isRemainingFuelInputValid = remainingFuel.isBlank() || (
            remainingFuelValue != null &&
                    (tankCapacity <= 0.0 || remainingFuelValue!! <= tankCapacity + 1e-6) &&
                    isRemainingPercentInRange
            )

    val isMissedValid = !hasMissedRecord || (missedOdometerValue != null && missedVolumeValue != null)
    val isMissedOdometerRangeValid = !hasMissedRecord || odometerValue != null && missedOdometerValue != null && missedOdometerValue!! < odometerValue!!

    val isTotalAmountValid = effectiveTotalAmount != null && effectiveTotalAmount > 0.0
    val isDiscountValid = discount.isBlank() || (discountValue != null && effectiveTotalAmount != null && discountValue!! <= effectiveTotalAmount)

    // 金额与乘积一致性
    val isAmountConsistent = amountValue != null && effectiveTotalAmount != null && discountValue != null && abs((effectiveTotalAmount - discountValue!!) - amountValue!!) <= 0.02
    val isProductConsistent = volumeValue != null && unitPriceValue != null && effectiveTotalAmount != null && abs((volumeValue!! * unitPriceValue!!) - effectiveTotalAmount) <= 0.05
    // 加满状态与剩余油量必须一致
    val isFullConsistent = !isFull || remainingFuel.isBlank() || abs((remainingFuelValue ?: 0.0) - tankCapacity) < 0.01

    // 统一错误信息
    val errorMessages = buildList {
        if (!isVehicleMatched) add("⛔ 记录所属车辆与当前车辆不一致，禁止保存")
        if (totalAmount.isBlank() && amount.isBlank()) add("请输入原价或实付") else if (!isTotalAmountValid) add("原价必须大于 0")
        if (volume.isBlank()) add("请输入加油量") else if (!isVolumeValid) add(if (isVolumeExceedsCapacity) "加油量超出油箱容量(${tankCapacity}L)" else "加油量无效")
        if (unitPrice.isBlank()) add("请输入单价") else if (unitPriceValue == null) add("单价无效")
        if (amount.isBlank()) add("请输入实付金额") else if (amountValue == null) add("实付金额无效")

        if (discount.isNotBlank() && discountValue == null) add("优惠金额无效")
        else if (!isDiscountValid) add("优惠金额不能超过原价")

        if (isTotalAmountValid && isDiscountValid && isAmountConsistent.not()) add("原价减去优惠金额应等于实付金额")
        if (isTotalAmountValid && volumeValue != null && unitPriceValue != null && isProductConsistent.not()) add("加油量乘以单价应等于原价")

        if (!isRemainingFuelInputValid) add(if (remainingFuelUsePercent) "剩余油量百分比需在 0~100 之间" else "剩余油量无效或超出容量")
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
            amount = formatMoney(total - disc)
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
        totalAmountValue?.let { amount = formatMoney(it - disc) }
    }

    fun switchRemainingUnit(toPercent: Boolean) {
        if (toPercent == remainingFuelUsePercent) return
        val currentRaw = remainingFuel.toValidDouble(allowZero = true)
        if (currentRaw != null && tankCapacity > 0.0) {
            remainingFuel = if (toPercent) formatPercent(currentRaw / tankCapacity * 100.0) else formatVolume(currentRaw / 100.0 * tankCapacity)
        }
        remainingFuelUsePercent = toPercent
    }

    // 根布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (currentVehicle == null) {
            Box(
                Modifier.fillMaxWidth().height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 1. 标题与返回键
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                MiuixBackButton(onClick = onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isEditMode) "编辑加油记录" else "记加油",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = adaptiveBackgroundTitleColor(
                        MiuixTheme.colorScheme.onBackground
                    ),
                    style = adaptiveBackgroundTitleStyle()
                )
            }

            // 车辆信息卡片
            MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("${currentVehicle.brand} ${currentVehicle.series}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(currentVehicle.model, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text("油箱容量 ${currentVehicle.tankCapacity} L", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
            }

            // 油品标号卡片
            MiuixCard(
                onClick = { showFuelGradePicker = true },
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixFieldLabel(
                        text = "油品标号",
                        isRequired = true
                    )
                    Text(fuelGrade, fontSize = 15.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            // 输入区域卡片
            MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp) // 增加垂直间距，看起来不拥挤
                ) {
                    // 1. 时间与公里数
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            MiuixTextField(
                                value = dateFormatter.format(Date(timestamp)),
                                onValueChange = {},
                                label = "加油时间",
                                isRequired = true,
                                readOnly = true,
                                textColor = MiuixTheme.colorScheme.primary, //把时间文本设为蓝色
                                modifier = Modifier.fillMaxWidth()
                            )
                            // 透明点击层，用于呼出时间滚轮
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showDatePicker = true }
                            )
                        }

                        MiuixTextField(
                            value = odometer,
                            onValueChange = { odometer = it },
                            label = "当前公里数",
                            modifier = Modifier.weight(1f),
                            suffix = "km",
                            keyboardType = KeyboardType.Decimal
                        )
                    }

                    // 2. 加油量与单价
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiuixTextField(
                            value = volume,
                            onValueChange = { onVolumeChange(it) },
                            label = "本次加油量",
                            modifier = Modifier.weight(1f),
                            isRequired = true,
                            suffix = "L",
                            isError = isVolumeExceedsCapacity,
                            errorMessage = "超出容量",
                            keyboardType = KeyboardType.Decimal
                        )
                        MiuixTextField(
                            value = unitPrice,
                            onValueChange = { onUnitPriceChange(it) },
                            label = "单价",
                            modifier = Modifier.weight(1f),
                            isRequired = true,
                            suffix = "元/L",
                            keyboardType = KeyboardType.Decimal
                        )
                    }

                    // 3. 原价与优惠
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiuixTextField(
                            value = totalAmount,
                            onValueChange = { onTotalAmountChange(it) },
                            label = "原价",
                            modifier = Modifier.weight(1f),
                            isRequired = true,
                            suffix = "元",
                            keyboardType = KeyboardType.Decimal
                        )
                        MiuixTextField(
                            value = discount,
                            onValueChange = { onDiscountChange(it) },
                            label = "优惠金额",
                            modifier = Modifier.weight(1f),
                            suffix = "元",
                            keyboardType = KeyboardType.Decimal
                        )
                    }

                    // 4. 实付与剩余
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiuixTextField(
                            value = amount,
                            onValueChange = { onAmountChange(it) },
                            label = "实际支付",
                            modifier = Modifier.weight(1f),
                            isRequired = true,
                            suffix = "元",
                            keyboardType = KeyboardType.Decimal
                        )

                        val remainingSupportingText = if (remainingFuelUsePercent && remainingFuelValue != null) {
                            "≈ ${formatVolume(remainingFuelValue!!)} L"
                        } else null

                        MiuixTextField(
                            value = remainingFuel,
                            onValueChange = { remainingFuel = it },
                            label = "加油后剩余",
                            modifier = Modifier.weight(1f),
                            enabled = !isFull,
                            // 截图里输入框右侧显示的是单位，根据你之前的逻辑，百分比时这里显示 L
                            suffix = if (remainingFuelUsePercent) "%" else "L",
                            keyboardType = KeyboardType.Decimal,
                            isError = remainingFuel.isNotBlank() && !isRemainingFuelInputValid,
                            errorMessage = "格式有误",
                            labelTrailing = {
                                if (tankCapacity > 0.0) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MiuixTheme.colorScheme.surfaceVariant)
                                            .clickable { switchRemainingUnit(!remainingFuelUsePercent) }
                                            .padding(horizontal = 8.dp, vertical = 2.dp), // 紧凑的药丸
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.SwapHoriz,
                                            "切换单位",
                                            modifier = Modifier.size(13.dp),
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (remainingFuelUsePercent) "L" else "%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            },
                            supportingText = remainingSupportingText
                        )

                    }
                    // ✅ 核心修改：将错误提示放在输入卡片的底部
                    if (errorMessages.isNotEmpty()) {
                        // 加一条极细的分割线，和输入框隔开
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        )

                        // 使用 FlowRow 自动换行排列标签
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            errorMessages.forEach { msg ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = msg,
                                        color = MiuixTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
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
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
                if (!isProductConsistent) {
                    Text(
                        "提示：加油量 × 单价 与 原价不一致，请检查输入",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            // 勾选项（将 Checkbox 替换为 Miuix Switch）
            MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    MiuixSwitchRow(isFull, "本次已加满") { checked ->
                        isFull = checked
                        if (checked) {
                            val fullText = if (remainingFuelUsePercent) "100" else formatVolume(tankCapacity)
                            if (tankCapacity > 0) remainingFuel = fullText
                        } else {
                            val fullText = if (remainingFuelUsePercent) "100" else formatVolume(tankCapacity)
                            if (remainingFuel == fullText) remainingFuel = ""
                        }
                    }
                    MiuixSwitchRow(hasMissedRecord, "上次加油忘记记录 (漏记)") { checked ->
                        hasMissedRecord = checked
                        if (!checked) {
                            missedOdometer = ""; missedVolume = ""
                        }
                    }
                    if (hasMissedRecord) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiuixTextField(
                                value = missedOdometer,
                                onValueChange = { missedOdometer = it },
                                label = "漏记期间大概行驶里程",
                                isRequired = true,
                                suffix = "km",
                                keyboardType = KeyboardType.Decimal
                            )
                            MiuixTextField(
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
            MiuixCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                MiuixTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.padding(12.dp),
                    label = "备注 (如: 某某加油站/优惠活动)"
                )
            }

            Spacer(Modifier.height(16.dp))

            // ✅ 等宽按钮布局
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 400.dp) // ✅ 依然限制最大宽度防大屏拉伸
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    // ✅ 自动在两个按钮之间留出 16dp 的空白
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 删除按钮（编辑模式显示）
                    if (isEditMode && onDelete != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f) // ✅ 和保存按钮平分剩余宽度
                                .height(50.dp) // ✅ 固定高度
                                .clip(RoundedCornerShape(25.dp))
                                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                                .clickable { showDeleteDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "删除",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }

                    // 保存按钮
                    Box(
                        modifier = Modifier
                            .weight(1f) // ✅ 平分宽度（若没有删除按钮，则自动占满全宽）
                            .height(50.dp) // ✅ 固定高度
                            .clip(RoundedCornerShape(25.dp))
                            .background(if (isFormValid && !isSaving) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary.copy(alpha = 0.4f))
                            .clickable(enabled = isFormValid && !isSaving) {
                                if (!isFormValid) {
                                    Toast.makeText(context, "请检查表单错误", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }

                                val vol = volumeValue!!
                                val amt = amountValue!!
                                val price = unitPriceValue!!
                                val disc = if (discount.isBlank()) null else discountValue
                                val remain = remainingFuelValue

                                isSaving = true
                                val recordToSave = record?.copy(
                                    timestamp = timestamp,
                                    fuelGrade = fuelGrade,
                                    odometer = odometerValue,
                                    volume = vol,
                                    totalAmount = effectiveTotalAmount,
                                    discount = disc,
                                    actualPaidAmount = amt,
                                    unitPrice = price,
                                    remainingFuel = remain,
                                    isFull = isFull,
                                    hasMissedRecord = hasMissedRecord,
                                    missedOdometer = missedOdometerValue,
                                    missedVolume = missedVolumeValue,
                                    note = note.trim()
                                ) ?: FuelRecord(
                                    vehicleId = currentVehicle.id,
                                    timestamp = timestamp,
                                    fuelGrade = fuelGrade,
                                    odometer = odometerValue,
                                    volume = vol,
                                    totalAmount = effectiveTotalAmount,
                                    discount = disc,
                                    actualPaidAmount = amt,
                                    unitPrice = price,
                                    remainingFuel = remain,
                                    isFull = isFull,
                                    hasMissedRecord = hasMissedRecord,
                                    missedOdometer = missedOdometerValue,
                                    missedVolume = missedVolumeValue,
                                    note = note.trim()
                                )

                                val onResult: (Result<Unit>) -> Unit = { result ->
                                    isSaving = false
                                    if (result.isSuccess) onSaved() else Toast.makeText(context, "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                                }

                                try {
                                    if (isEditMode) viewModel.updateFuelRecord(recordToSave, onResult)
                                    else viewModel.addFuelRecord(recordToSave, onResult)
                                } catch (e: Exception) {
                                    isSaving = false
                                    Toast.makeText(context, "系统异常：${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEditMode) "保存修改" else "保存加油记录",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ✅ 删除确认弹窗
    if (showDeleteDialog && onDelete != null) {
        MiuixConfirmDialog(
            title = "删除记录",
            message = "确定删除该条加油记录吗？删除后相关油耗计算将重新推导。",
            confirmText = "删除",
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showFuelGradePicker) {
        HyperFuelGradePickerDialog(
            fuelGrade,
            fuelGradeOptions,
            { fuelGrade = it; showFuelGradePicker = false },
            { showFuelGradePicker = false })
    }
    if (showDatePicker) {
        MiuixDatePickerDialog(
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

// ✅ 勾选框替换为 Miuix Switch
@Composable
private fun MiuixSwitchRow(checked: Boolean, text: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
        MiuixSwitch(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) }
        )
    }
}

@Composable
private fun HyperFuelGradePickerDialog(
    currentValue: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGrade by remember { mutableStateOf(currentValue) }
    Dialog(onDismissRequest = onDismiss) {
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("取消", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
                    Text("选择油品标号", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
                    Text("确定", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary, modifier = Modifier.clickable { onSelected(selectedGrade) }.padding(8.dp))
                }
                WheelList(
                    selectedValue = selectedGrade,
                    items = options,
                    modifier = Modifier.fillMaxWidth(),
                    visibleCount = 5,
                    style = WheelStyle.Plain,
                    itemLabel = { it },
                    onChange = { selectedGrade = it }
                )
            }
        }
    }
}
