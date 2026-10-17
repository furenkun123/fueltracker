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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.CATEGORY_PARKING
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.notif.ParkingNotification
import com.fueltracker.ui.miui.MiuiCard
import com.fueltracker.util.LocationHelper
import com.fueltracker.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(
    viewModel: MainViewModel,
    record: ExpenseRecord? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isEditMode = record != null
    val context = LocalContext.current


    val vehicle by viewModel.currentVehicle.collectAsState()

    val expenses by viewModel.expenses.collectAsState()

    // 从历史停车记录里提取常用位置
    val recentLocations = remember(expenses, record) {
        expenses
            .asSequence()
            .filter { it.category == CATEGORY_PARKING }
            // 编辑当前记录时，排除自己，避免把当前值也列进 chips
            .filter { record == null || it.id != record.id }
            .mapNotNull { it.locationName?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .distinct()
            .take(5)
            .toList()
    }

    val floorOptions = remember {
        listOf("B1", "B2", "B3", "B4", "LG1", "LG2", "P1", "P2")
    }
    val areaOptions = remember {
        listOf("A区", "B区", "C区", "D区")
    }

    var locationName by remember(record) {
        mutableStateOf(record?.locationName ?: "")
    }
    var floor by remember(record) {
        mutableStateOf(record?.floor ?: "")
    }
    var area by remember(record) {
        mutableStateOf(record?.area ?: "")
    }
    var spotNumber by remember(record) {
        mutableStateOf(record?.spotNumber ?: "")
    }
    var amountText by remember(record) {
        mutableStateOf(
            record?.amount?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: ""
        )
    }
    var note by remember(record) {
        mutableStateOf(record?.note ?: "")
    }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }
    var locationCandidate by remember { mutableStateOf<String?>(null) }
    // 定位的实际逻辑（不涉及权限，被下面两处调用）
    fun performLocate(showToast: Boolean) {
        scope.launch {
            isLocating = true
            val name = LocationHelper.getCurrentLocationName(context)
            isLocating = false
            if (name != null) {
                // 不自动填入，放到候选，让用户点
                locationCandidate = name
            } else if (showToast) {
                Toast.makeText(
                    context,
                    "定位失败，请手动输入",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) {
            performLocate(showToast = false)
        } else {
            Toast.makeText(
                context,
                "未授权定位权限",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun startLocate(showToast: Boolean = true) {
        if (isLocating) return
        if (LocationHelper.hasPermission(context)) {
            performLocate(showToast)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }


    LaunchedEffect(Unit) {
        // 新建模式下，若位置为空且已有权限，自动定位一次
        if (!isEditMode &&
            locationName.isBlank() &&
            LocationHelper.hasPermission(context)
        ) {
            startLocate(showToast = false)
        }
    }


    val amount = amountText.toDoubleOrNull() ?: 0.0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "编辑停车" else "记停车",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ParkingFormCard {
                ParkingInputField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = "位置",
                    placeholder = "如：万达广场",
                    keyboardType = KeyboardType.Text,
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !isLocating) {
                                    startLocate()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLocating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "定位",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                )

                // 定位结果：显示在输入框下方，用户点击才填入
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
                    LocationChip(
                        text = candidate,
                        selected = false,
                        onClick = {
                            locationName = candidate
                            locationCandidate = null
                        }
                    )
                }

                // 最近使用：在定位结果下方
                if (recentLocations.isNotEmpty()) {
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
                        recentLocations.forEach { loc ->
                            LocationChip(
                                text = loc,
                                selected = locationName == loc,
                                onClick = { locationName = loc }
                            )
                        }
                    }
                }
            }


            ParkingFormCard {
                Text(
                    text = "楼层",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    floorOptions.forEach { option ->
                        ParkingChip(
                            text = option,
                            selected = floor == option,
                            onClick = {
                                floor = if (floor == option) "" else option
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                ParkingInputField(
                    value = floor,
                    onValueChange = { floor = it },
                    label = "",
                    placeholder = "或手动输入楼层（如 B2）",
                    keyboardType = KeyboardType.Text
                )
            }

            ParkingFormCard {
                Text(
                    text = "区域",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    areaOptions.forEach { option ->
                        ParkingChip(
                            text = option,
                            selected = area == option,
                            onClick = {
                                area = if (area == option) "" else option
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                ParkingInputField(
                    value = area,
                    onValueChange = { area = it },
                    label = "",
                    placeholder = "或手动输入区域（如 东区）",
                    keyboardType = KeyboardType.Text
                )
            }

            ParkingFormCard {
                ParkingInputField(
                    value = spotNumber,
                    onValueChange = { spotNumber = it },
                    label = "车位号",
                    placeholder = "如：135",
                    keyboardType = KeyboardType.Text
                )
            }

            ParkingFormCard {
                ParkingInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "停车费（可选）",
                    placeholder = "如：5.00",
                    suffix = "元",
                    keyboardType = KeyboardType.Decimal
                )
            }

            ParkingFormCard {
                ParkingInputField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注（可选）",
                    placeholder = "如：电梯口旁边",
                    keyboardType = KeyboardType.Text
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isSaving) return@Button

                    isSaving = true

                    val newRecord = if (isEditMode && record != null) {
                        record.copy(
                            category = CATEGORY_PARKING,
                            amount = amount,
                            locationName = locationName.trim().ifEmpty { null },
                            floor = floor.trim().ifEmpty { null },
                            area = area.trim().ifEmpty { null },
                            spotNumber = spotNumber.trim().ifEmpty { null },
                            note = note.trim()
                        )
                    } else {
                        ExpenseRecord(
                            vehicleId = 0,
                            timestamp = System.currentTimeMillis(),
                            category = CATEGORY_PARKING,
                            amount = amount,
                            locationName = locationName.trim().ifEmpty { null },
                            floor = floor.trim().ifEmpty { null },
                            area = area.trim().ifEmpty { null },
                            spotNumber = spotNumber.trim().ifEmpty { null },
                            isActive = true,
                            notificationIgnored = false,
                            note = note.trim()
                        )
                    }

                    val onResult: (Result<Unit>) -> Unit = { result ->
                        isSaving = false
                        if (result.isSuccess) {
                            val isActiveParking = !isEditMode || record.isActive
                            // 通知：仅新建且保存成功时发
                            val vId = vehicle?.id
                            if (isActiveParking && vId != null && vId > 0L) {
                                ParkingNotification.show(
                                    context = context,
                                    vehicleId = vId,
                                    locationName = locationName.trim().ifEmpty { null },
                                    floor = floor.trim().ifEmpty { null },
                                    area = area.trim().ifEmpty { null },
                                    spotNumber = spotNumber.trim().ifEmpty { null }
                                )
                            }
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
                        viewModel.updateExpense(newRecord, onResult)
                    } else {
                        viewModel.addExpense(newRecord, onResult)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isEditMode) "保存修改" else "保存停车",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}



@Composable
private fun ParkingFormCard(
    content: @Composable () -> Unit
) {
    MiuiCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ParkingChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
private fun LocationChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun ParkingInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Column {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                if (placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
            textStyle = TextStyle(
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
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}