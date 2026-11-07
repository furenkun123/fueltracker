package com.fueltracker.ui.components.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.miuix.MiuixCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixVehicleCard(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
    onSwitchClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)

    if (onClick != null) {
        MiuixCard(
            onClick = onClick,
            modifier = cardModifier
        ) {
            MiuixVehicleCardContent(vehicle, onSwitchClick)
        }
    } else {
        MiuixCard(modifier = cardModifier) {
            MiuixVehicleCardContent(vehicle, onSwitchClick)
        }
    }
}

@Composable
private fun ColumnScope.MiuixVehicleCardContent(
    vehicle: Vehicle,
    onSwitchClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${vehicle.brand} ${vehicle.series}",
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }

            if (onSwitchClick != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSwitchClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "切换",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "切换车辆",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Text(
            text = "${vehicle.year}款 ${vehicle.model}",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}
