package com.fueltracker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.fueltracker.ui.miuix.dialogs.MiuixConfirmDialog
import com.fueltracker.ui.miuix.MiuixButton
import com.fueltracker.ui.miuix.MiuixGroupCard
import com.fueltracker.ui.miuix.MiuixSectionHeader
import com.fueltracker.ui.miuix.MiuixInputItem
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

    var showSkipConfirm by remember { mutableStateOf(false) }

    val currentVehicle = vehicle

    if (currentVehicle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val odometerValue = odometer.toDoubleOrNull()
    val fuelValue = currentFuel.toDoubleOrNull()

    // 👇 新增：先确保车辆的油箱容量本身是合法的有限数值
    val isCapacitySane = currentVehicle.tankCapacity.isFinite() && currentVehicle.tankCapacity > 0.0

    // 👇 修改：在正常容量的前提下，再判断输入值
    val isFuelValid = fuelValue == null || (
            fuelValue.isFinite() && fuelValue >= 0 && isCapacitySane && fuelValue <= currentVehicle.tankCapacity
            )

    val canSave = odometerValue != null && odometerValue.isFinite() && odometerValue >= 0 && isFuelValid

    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        showSkipConfirm = true
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,

        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "车辆初始信息",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground
                )

                // 跳过按钮包围背景
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .clickable { showSkipConfirm = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "跳过",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MiuixSectionHeader(title = "当前车辆信息")
            MiuixGroupCard {
                Column(modifier = Modifier.padding(vertical = 14.dp)) {
                    Text(
                        text = "${currentVehicle.brand} ${currentVehicle.series} ${currentVehicle.model}".trim(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("油箱容量：${currentVehicle.tankCapacity} L", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        Text("燃油标号：${currentVehicle.fuelGrade}", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MiuixSectionHeader(title = "录入初始状态")
            MiuixGroupCard {
                MiuixInputItem(
                    label = "当前公里数 (km)",
                    value = odometer,
                    onValueChange = { odometer = it },
                    placeholder = "必填，例如：20",
                    keyboardType = KeyboardType.Decimal
                )
                HorizontalDivider(color = MiuixTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)
                MiuixInputItem(
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
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            MiuixButton(
                text = "开始记录",
                enabled = canSave && !isSaving,
                onClick = {
                    isSaving = true
                    viewModel.addInitialRecord(odometerValue, fuelValue) { result ->
                        isSaving = false
                        if (result.isSuccess) {
                            onCompleted()
                        } else {
                            Toast.makeText(
                                context,
                                "保存失败：${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSkipConfirm) {
        MiuixConfirmDialog(
            title = "确定要跳过吗？",
            message = "没有初始里程，系统将无法计算这辆车前几次的油耗。您可以在后续添加加油记录时补充里程信息。",
            confirmText = "仍要跳过",
            dismissText = "去填写",
            confirmColor = MiuixTheme.colorScheme.error,
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
