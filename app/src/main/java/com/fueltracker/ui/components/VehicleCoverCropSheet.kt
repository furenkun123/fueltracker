package com.fueltracker.ui.components

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.ui.miuix.MiuixTextButton
import com.fueltracker.ui.miuix.dialogs.MiuixBottomSheetWrapper
import com.fueltracker.ui.theme.VehicleCoverCropTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt


@Composable
fun VehicleCoverCropSheet(
    uri: Uri,
    initialTransform: VehicleCoverCropTransform,
    onDismiss: () -> Unit,
    onConfirm: (VehicleCoverCropTransform) -> Unit
) {
    val bitmap = rememberCropBitmap(uri)

    var scale by remember(uri) {
        mutableFloatStateOf(initialTransform.scale.coerceIn(1f, 4f))
    }

    var panX by remember(uri) {
        mutableFloatStateOf(initialTransform.panX.coerceIn(-1f, 1f))
    }

    var panY by remember(uri) {
        mutableFloatStateOf(initialTransform.panY.coerceIn(-1f, 1f))
    }

    var previewSize by remember(uri) {
        mutableStateOf(IntSize.Zero)
    }

    // 手势状态：双指缩放、单指拖动
    val transformState = rememberTransformableState {
            _, zoomChange, panChange, _ ->

        val width = previewSize.width.toFloat()
        val height = previewSize.height.toFloat()

        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)

        // 先还原当前像素偏移，再根据新缩放比例计算合法位置
        val oldMaxX = width * (scale - 1f) / 2f
        val oldMaxY = height * (scale - 1f) / 2f

        val newMaxX = width * (nextScale - 1f) / 2f
        val newMaxY = height * (nextScale - 1f) / 2f

        val nextOffsetX = (
                panX * oldMaxX + panChange.x
                ).coerceIn(-newMaxX, newMaxX)

        val nextOffsetY = (
                panY * oldMaxY + panChange.y
                ).coerceIn(-newMaxY, newMaxY)

        scale = nextScale

        panX = if (newMaxX > 0f) {
            nextOffsetX / newMaxX
        } else {
            0f
        }

        panY = if (newMaxY > 0f) {
            nextOffsetY / newMaxY
        } else {
            0f
        }
    }

    MiuixBottomSheetWrapper(
        title = "调整封面",
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(
                VehicleCoverCropTransform(
                    scale = scale,
                    panX = panX,
                    panY = panY
                )
            )
        },
        confirmText = "保存"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "拖动图片调整位置，双指缩放图片",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            // 裁剪预览区域，保持和首页相同的固定高度
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .onSizeChanged {
                        previewSize = it
                    }
                    .transformable(transformState),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "车辆封面裁剪预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale

                                translationX =
                                    panX * size.width * (scale - 1f) / 2f

                                translationY =
                                    panY * size.height * (scale - 1f) / 2f
                            }
                    )
                } else {
                    Text(
                        text = "图片读取失败",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            MiuixTextButton(
                text = "恢复默认位置",
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 13.sp,
                onClick = {
                    scale = 1f
                    panX = 0f
                    panY = 0f
                }
            )

            Text(
                text = "最大缩放 4 倍，保存后仅影响当前车辆封面，不会修改原始图片。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun rememberCropBitmap(uri: Uri): ImageBitmap? {
    val context = LocalContext.current

    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = uri
    ) {
        value = withContext(Dispatchers.IO) {
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
                        1600f / maxOf(width, height)
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

    return bitmap
}