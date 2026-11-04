package com.fueltracker.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CATEGORY_PARKING
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.ui.miui.MiuiBackButton
import com.fueltracker.ui.miui.MiuiChip
import com.fueltracker.ui.miui.MiuiFormCard
import com.fueltracker.util.LocationHelper
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.launch

// 扩展函数：简化判空和去空格
private fun String?.toCleanString(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

@Composable
fun ParkingScreen(
    viewModel: MainViewModel,
    record: ExpenseRecord? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isEditMode = record != null
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()

    // 提取常用位置
    val recentLocations = remember(expenses, record) {
        expenses.asSequence()
            .filter { it.category == CATEGORY_PARKING && (record == null || it.id != record.id) }
            .mapNotNull { it.locationName?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .distinct().take(5).toList()
    }

    // 表单状态
    var locationName by remember(record) { mutableStateOf(record?.locationName ?: "") }
    var floor by remember(record) { mutableStateOf(record?.floor ?: "") }
    var area by remember(record) { mutableStateOf(record?.area ?: "") }
    var spotNumber by remember(record) { mutableStateOf(record?.spotNumber ?: "") }
    var amountText by remember(record) { mutableStateOf(record?.amount?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "") }
    var note by remember(record) { mutableStateOf(record?.note ?: "") }

    // 状态流转
    var isSaving by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    var locationCandidate by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun performLocate() {
        scope.launch {
            isLocating = true
            val name = LocationHelper.getCurrentLocationName(context)
            isLocating = false
            if (name != null) locationCandidate = name
            else Toast.makeText(context, "定位失败，请手动输入", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) performLocate()
        else Toast.makeText(context, "未授权定位权限", Toast.LENGTH_SHORT).show()
    }

    fun startLocate() {
        if (isLocating) return
        if (LocationHelper.hasPermission(context)) performLocate()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) {
        if (!isEditMode && locationName.isBlank() && LocationHelper.hasPermission(context)) startLocate()
    }

    val amount = amountText.toDoubleOrNull() ?: 0.0

    // 👇 移除 Scaffold 和 TopAppBar，改用 LazyColumn 支撑页面
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
            bottom = 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 👇 1. 标题与返回键作为第一个 item
        item(key = "parking_title") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // 👇 去掉背景圆底，只保留 IconButton 的点击波纹
                MiuiBackButton(onClick = onBack)

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isEditMode) "编辑停车" else "记停车",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // 👇 2. 位置选择卡片
        item {
            MiuiFormCard {
                ParkingInputField(
                    value = locationName, onValueChange = { locationName = it },
                    label = "位置", placeholder = "如：万达广场",
                    trailingIcon = {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = !isLocating) { startLocate() }, contentAlignment = Alignment.Center) {
                            if (isLocating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.LocationOn, "定位", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }
                )

                locationCandidate?.let { candidate ->
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "定位结果（点击填入）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "清除",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { locationCandidate = null }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.clickable {
                            locationName = candidate
                            locationCandidate = null
                        }
                    ) {
                        MiuiChip(
                            text = candidate,
                            cornerRadius = 20.dp,
                            paddingH = 14.dp,
                            paddingV = 8.dp,
                            fontSize = 13.sp
                        )
                    }
                }

                val displayRecentLocations = recentLocations.filter {
                    it != locationName && it != locationCandidate
                }

                if (displayRecentLocations.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "最近使用",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        displayRecentLocations.forEach { loc ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { locationName = loc }
                            ) {
                                MiuiChip(
                                    text = loc,
                                    cornerRadius = 20.dp,
                                    paddingH = 14.dp,
                                    paddingV = 8.dp,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 👇 3. 楼层与区域
        item {
            ParkingOptionSection(
                title = "楼层", options = listOf("B1", "B2", "B3", "B4", "LG1", "LG2", "P1", "P2"),
                currentValue = floor, onValueChange = { floor = it }, placeholder = "或手动输入楼层（如 B2）"
            )
        }

        item {
            ParkingOptionSection(
                title = "区域", options = listOf("A区", "B区", "C区", "D区"),
                currentValue = area, onValueChange = { area = it }, placeholder = "或手动输入区域（如 东区）"
            )
        }

        // 👇 4. 车位号与金额、备注
        item {
            MiuiFormCard {
                ParkingInputField(spotNumber, { spotNumber = it }, "车位号", "如：135")
            }
        }

        item {
            MiuiFormCard {
                ParkingInputField(amountText, { amountText = it }, "停车费（可选）", "如：5.00", "元", KeyboardType.Decimal)
            }
        }

        item {
            MiuiFormCard {
                ParkingInputField(note, { note = it }, "备注（可选）", "如：电梯口旁边")
            }
        }

        // 👇 5. 保存按钮
        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true

                    val cleanLoc = locationName.toCleanString()
                    val cleanFloor = floor.toCleanString()
                    val cleanArea = area.toCleanString()
                    val cleanSpot = spotNumber.toCleanString()

                    val newRecord = record?.copy(
                        category = CATEGORY_PARKING, amount = amount,
                        locationName = cleanLoc, floor = cleanFloor, area = cleanArea,
                        spotNumber = cleanSpot, note = note.trim(),
                                notificationIgnored = false
                    ) ?: ExpenseRecord(
                        vehicleId = 0, timestamp = System.currentTimeMillis(), category = CATEGORY_PARKING, amount = amount,
                        locationName = cleanLoc, floor = cleanFloor, area = cleanArea, spotNumber = cleanSpot,
                        isActive = true, notificationIgnored = false, note = note.trim()
                    )

                    val onResult: (Result<Unit>) -> Unit = { result ->
                        isSaving = false
                        if (result.isSuccess) {
                            // 通知由 MainViewModel.parkingNotificationState 统一驱动，
                            // 此处不手动 show/cancel
                            onSaved()
                        } else {
                            Toast.makeText(
                                context,
                                "保存失败：${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    if (isEditMode) viewModel.updateExpense(newRecord, onResult) else viewModel.addExpense(newRecord, onResult)
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(if (isEditMode) "保存修改" else "保存停车", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@Composable
private fun ParkingOptionSection(title: String, options: List<String>, currentValue: String, onValueChange: (String) -> Unit, placeholder: String) {
    MiuiFormCard {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                Box(
                    modifier = Modifier.clickable {
                        onValueChange(if (currentValue == option) "" else option)
                    }
                ) {
                    MiuiChip(
                        text = option,
                        selected = currentValue == option
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ParkingInputField(value = currentValue, onValueChange = onValueChange, label = "", placeholder = placeholder)
    }
}

@Composable
private fun ParkingInputField(
    value: String, onValueChange: (String) -> Unit, label: String,
    placeholder: String = "", suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text, trailingIcon: (@Composable () -> Unit)? = null
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val inputFill = if (isLight) Color.White.copy(alpha = 0.34f) else Color(0xFF0B1422).copy(alpha = 0.42f)
    val inputOutline = if (isLight) Color(0xFF475569).copy(alpha = 0.20f) else Color(0xFF070D16).copy(alpha = 0.44f)

    Column {
        if (label.isNotEmpty()) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = RoundedCornerShape(14.dp),
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            suffix = { suffix?.let { Text(it, fontSize = 14.sp) } },
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = inputFill.copy(alpha = if (isLight) 0.52f else 0.58f),
                unfocusedContainerColor = inputFill,
                disabledContainerColor = inputFill.copy(alpha = 0.18f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                unfocusedBorderColor = inputOutline,
                disabledBorderColor = inputOutline.copy(alpha = 0.45f),
                errorBorderColor = MaterialTheme.colorScheme.error,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}