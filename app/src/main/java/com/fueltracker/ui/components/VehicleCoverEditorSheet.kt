package com.fueltracker.ui.components

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.data.Vehicle
import com.fueltracker.ui.miuix.MiuixSwitch
import com.fueltracker.ui.miuix.dialogs.MiuixBottomSheetWrapper
import com.fueltracker.ui.theme.VehicleCoverCropTransform
import com.fueltracker.ui.theme.VehicleCoverPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun VehicleCoverEditorSheet(
    vehicle: Vehicle,
    coverUri: Uri?,
    showInfoOnCover: Boolean,
    pendingCoverUri: Uri?,
    onCancelPendingCover: () -> Unit,
    onSavePendingCover: (Uri, VehicleCoverCropTransform) -> Boolean,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onShowInfoChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var showCropEditor by remember(vehicle.id) {
        mutableStateOf(false)
    }

    // 选择图片后自动进入裁剪界面
    LaunchedEffect(pendingCoverUri) {
        if (pendingCoverUri != null) {
            showCropEditor = true
        }
    }

    // 新图片优先于当前封面
    val cropUri = pendingCoverUri ?: if (showCropEditor) {
        coverUri
    } else {
        null
    }

    if (showCropEditor && cropUri != null) {

        VehicleCoverCropSheet(
            uri = cropUri,

            // 新图片默认居中，旧图片读取原来的裁剪位置
            initialTransform = if (pendingCoverUri != null) {
                VehicleCoverCropTransform()
            } else {
                VehicleCoverPreferences.getCropTransform(vehicle.id)
            },

            onDismiss = {
                showCropEditor = false

                // 取消新图片裁剪，不替换旧封面
                if (pendingCoverUri != null) {
                    onCancelPendingCover()
                }
            },

            onConfirm = { transform ->

                if (pendingCoverUri != null) {

                    // 保存新图片及其裁剪位置
                    val saved = onSavePendingCover(
                        pendingCoverUri,
                        transform
                    )

                    if (saved) {
                        showCropEditor = false
                    }

                } else {

                    // 只是重新调整已有图片
                    VehicleCoverPreferences.setCropTransform(
                        context = context,
                        vehicleId = vehicle.id,
                        transform = transform
                    )

                    showCropEditor = false
                }
            }
        )

    } else {

        val previewBitmap = rememberCoverPreview(coverUri)
        val cropTransform =
            VehicleCoverPreferences.getCropTransform(vehicle.id)

        MiuixBottomSheetWrapper(
            title = "车辆封面",
            onDismiss = onDismiss,
            onConfirm = onDismiss,
            confirmText = "完成"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "${vehicle.brand} ${vehicle.series}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                if (coverUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                MiuixTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "车辆封面预览",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .graphicsLayer {
                                        scaleX = cropTransform.scale
                                        scaleY = cropTransform.scale

                                        translationX =
                                            cropTransform.panX *
                                                    size.width *
                                                    (cropTransform.scale - 1f) / 2f

                                        translationY =
                                            cropTransform.panY *
                                                    size.height *
                                                    (cropTransform.scale - 1f) / 2f
                                    }
                            )
                        }

                        if (showInfoOnCover) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.82f)
                                            )
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 10.dp
                                    ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${vehicle.brand} ${vehicle.series}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.92f)
                                )

                                Text(
                                    text = "${vehicle.year}款 ${vehicle.model}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                MiuixTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "尚未添加车辆封面",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }

                if (coverUri == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CoverActionButton(
                            text = "选择图片",
                            color = MiuixTheme.colorScheme.primary,
                            onClick = onPickImage
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverActionButton(
                            text = "删除封面",
                            color = MiuixTheme.colorScheme.error,
                            onClick = onClearImage
                        )

                        CoverActionButton(
                            text = "更换图片",
                            color = MiuixTheme.colorScheme.primary,
                            onClick = onPickImage
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "显示车型信息",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "关闭后，封面只显示汽车图片",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }

                    MiuixSwitch(
                        checked = showInfoOnCover,
                        onCheckedChange = onShowInfoChange
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberCoverPreview(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current

    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = uri
    ) {
        value = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(
                        context.contentResolver,
                        uri
                    )

                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val width = info.size.width.coerceAtLeast(1)
                        val height = info.size.height.coerceAtLeast(1)

                        val scale = minOf(
                            1f,
                            1200f / maxOf(width, height)
                        )

                        decoder.setTargetSize(
                            (width * scale).roundToInt().coerceAtLeast(1),
                            (height * scale).roundToInt().coerceAtLeast(1)
                        )

                        decoder.isMutableRequired = false
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    return bitmap
}
@Composable
private fun CoverActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(
                horizontal = 22.dp,
                vertical = 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}