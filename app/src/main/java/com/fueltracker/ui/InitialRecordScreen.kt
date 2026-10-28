package com.fueltracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.components.MiuiConfirmDialog // 👈 引入确认弹窗
import com.fueltracker.ui.miui.*
import com.fueltracker.viewmodel.MainViewModel

@Composable
fun InitialRecordScreen(
    viewModel: MainViewModel,
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val vehicle by viewModel.currentVehicle.collectAsState()
    var odometer by remember { mutableStateOf("") }
    var currentFuel by remember { mutableStateOf("") }

    // 👇 控制“跳过”确认弹窗的显示
    var showSkipConfirm by remember { mutableStateOf(false) }

    val currentVehicle = vehicle

    if (currentVehicle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val odometerValue = odometer.toDoubleOrNull()
    val fuelValue = currentFuel.toDoubleOrNull()
    val isFuelValid = fuelValue == null || (fuelValue >= 0 && fuelValue <= currentVehicle.tankCapacity)
    val canSave = odometerValue != null && odometerValue >= 0 && isFuelValid

    // 👇 拦截系统返回键，不直接退出，而是弹窗确认
    BackHandler(enabled = true) {
        showSkipConfirm = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MiuiTopBar(
                title = "首次记录",
                // 取消左上角的返回按钮
                navigationIcon = null,
                // 👇 右上角提供显式的“跳过”按钮
                action = {
                    TextButton(onClick = { showSkipConfirm = true }) {
                        Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            MiuiSectionHeader(title = "当前车辆信息")
            MiuiGroupCard {
                Column(modifier = Modifier.padding(vertical = 14.dp)) {
                    Text(
                        text = "${currentVehicle.brand} ${currentVehicle.series} ${currentVehicle.model}".trim(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("油箱容量：${currentVehicle.tankCapacity} L", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("燃油标号：${currentVehicle.fuelGrade}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MiuiSectionHeader(title = "录入初始状态")
            MiuiGroupCard {
                MiuiInputItem(
                    label = "当前公里数 (km)",
                    value = odometer,
                    onValueChange = { odometer = it },
                    placeholder = "必填，例如：20",
                    keyboardType = KeyboardType.Decimal
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)
                MiuiInputItem(
                    label = "当前剩余油量 (L)",
                    value = currentFuel,
                    onValueChange = { currentFuel = it },
                    placeholder = "选填，最多 ${currentVehicle.tankCapacity} L",
                    keyboardType = KeyboardType.Decimal
                )
            }

            if (!isFuelValid) {
                Text(
                    text = "剩余油量不能超过油箱容量 ${currentVehicle.tankCapacity} L",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            MiuiButton(
                text = "开始记录",
                enabled = canSave,
                onClick = {
                    viewModel.addInitialRecord(odometerValue, fuelValue)
                    onCompleted()
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 👇 跳过时的确认弹窗
    if (showSkipConfirm) {
        MiuiConfirmDialog(
            title = "确定要跳过吗？",
            message = "没有初始里程，系统将无法计算这辆车前几次的油耗。您可以在后续添加加油记录时补充里程信息。",
            confirmText = "仍要跳过",
            dismissText = "去填写",
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = {
                showSkipConfirm = false
                onBack() // 执行原本的返回逻辑
            },
            onDismiss = {
                showSkipConfirm = false
            }
        )
    }
}