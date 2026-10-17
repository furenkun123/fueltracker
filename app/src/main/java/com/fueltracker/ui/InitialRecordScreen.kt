package com.fueltracker.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

    val odometerValue = odometer.toDoubleOrNull()
    val fuelValue = currentFuel.toDoubleOrNull()

    val canSave = currentVehicle != null &&
            odometerValue != null && odometerValue >= 0 &&
            (fuelValue == null ||
                    (fuelValue >= 0 && fuelValue <= currentVehicle.tankCapacity))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "首次记录",
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
    ) { paddingValues ->
        if (currentVehicle == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
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
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp)
                    ) {
                        Text(
                            text = "${currentVehicle.brand} ${currentVehicle.series} ${currentVehicle.model}".trim(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "油箱容量：${currentVehicle.tankCapacity} L",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "燃油标号：${currentVehicle.fuelGrade}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 0.8.dp
                    )

                    MuiXInputItem(
                        label = "当前剩余油量 (L)",
                        value = currentFuel,
                        onValueChange = { currentFuel = it },
                        placeholder = "选填，最多 ${currentVehicle.tankCapacity} L",
                        keyboardType = KeyboardType.Decimal
                    )
                }

                if (fuelValue != null && fuelValue > currentVehicle.tankCapacity) {
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
                        if (!canSave) return@Button
                        viewModel.addInitialRecord(
                            odometer = odometerValue,
                            currentFuel = fuelValue
                        )
                        onCompleted()
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
                        text = "开始记录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}