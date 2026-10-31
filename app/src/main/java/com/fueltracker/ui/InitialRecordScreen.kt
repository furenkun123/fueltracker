package com.fueltracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.components.MiuiConfirmDialog
import com.fueltracker.ui.miui.MiuiButton
import com.fueltracker.ui.miui.MiuiGroupCard
import com.fueltracker.ui.miui.MiuiInputItem
import com.fueltracker.ui.miui.MiuiSectionHeader
import com.fueltracker.ui.miui.MiuiTopBar
import com.fueltracker.viewmodel.MainViewModel
import com.kyant.backdrop.Backdrop

@Composable
fun InitialRecordScreen(
    viewModel: MainViewModel,
    backdrop: Backdrop,
    isGlassEnabled: Boolean = false,
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val vehicle by viewModel.currentVehicle.collectAsState()
    var odometer by remember { mutableStateOf("") }
    var currentFuel by remember { mutableStateOf("") }

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

    BackHandler(enabled = true) {
        showSkipConfirm = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 🚨 修复：右上角「跳过」作为 action 传入，
            // 不传 navigationIcon（取消左上角返回按钮）
            MiuiTopBar(
                backdrop = backdrop,
                isGlassEnabled = isGlassEnabled,
                action = {
                    TextButton(onClick = { showSkipConfirm = true }) {
                        Text(
                            text = "跳过",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
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

    if (showSkipConfirm) {
        MiuiConfirmDialog(
            title = "确定要跳过吗？",
            message = "没有初始里程，系统将无法计算这辆车前几次的油耗。您可以在后续添加加油记录时补充里程信息。",
            confirmText = "仍要跳过",
            dismissText = "去填写",
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = {
                showSkipConfirm = false
                onBack()
            },
            onDismiss = {
                showSkipConfirm = false
            }
        )
    }
}