package com.fueltracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.miui.MuiXGroupCard
import com.fueltracker.ui.miui.MuiXInputItem
import com.fueltracker.ui.miui.MuiXSectionHeader
import com.fueltracker.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialRecordScreen(
    viewModel: MainViewModel,
    onCompleted: () -> Unit,
    onBack: () -> Unit
) {
    val vehicle by viewModel.currentVehicle.collectAsState()
    var odometer by remember { mutableStateOf("") }
    var currentFuel by remember { mutableStateOf("") }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("首次记录", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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

            MuiXSectionHeader(title = "当前车辆信息")
            MuiXGroupCard {
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

            MuiXSectionHeader(title = "录入初始状态")
            MuiXGroupCard {
                MuiXInputItem(
                    label = "当前公里数 (km)",
                    value = odometer,
                    onValueChange = { odometer = it },
                    placeholder = "必填，例如：20",
                    keyboardType = KeyboardType.Decimal
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.8.dp)
                MuiXInputItem(
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

            Button(
                onClick = {
                    viewModel.addInitialRecord(odometerValue, fuelValue)
                    onCompleted()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                enabled = canSave // Material3 默认的 disabled 状态已经足够好看，去掉了多余的颜色覆盖
            ) {
                Text("开始记录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}