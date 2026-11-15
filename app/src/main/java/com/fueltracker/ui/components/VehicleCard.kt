package com.fueltracker.ui.components

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fueltracker.ui.miuix.MiuixCard
import com.fueltracker.ui.theme.VehicleCoverPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 首页车辆信息卡片。
 *
 * 有封面图片时使用固定高度图片背景；
 * 没有封面图片时保持原来的文字卡片。
 */
@Composable
fun MiuixVehicleCard(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val coverUri = VehicleCoverPreferences.getCoverUri(vehicle.id)
    val showInfo = VehicleCoverPreferences.shouldShowInfo(vehicle.id)
    val cropTransform = VehicleCoverPreferences.getCropTransform(vehicle.id)

    val coverBitmap = rememberVehicleCoverBitmap(coverUri)

    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)

    MiuixCard(
        modifier = if (onLongClick != null) {
            cardModifier.combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
        } else {
            cardModifier
        }
    ) {
        if (coverUri != null) {
            // 有封面：固定尺寸图片卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MiuixTheme.colorScheme.surfaceVariant)
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = "车辆封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
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

                if (showInfo) {
                    // 底部渐变遮罩
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(84.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.82f)
                                    )
                                )
                            )
                    )

                    // 底部车型信息
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${vehicle.brand} ${vehicle.series}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.92f)
                        )

                        Text(
                            text = "${vehicle.year}款 ${vehicle.model}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        } else {
            // 无封面：保持原来的卡片样式
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "${vehicle.brand} ${vehicle.series}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Text(
                    text = "${vehicle.year}款 ${vehicle.model}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

/**
 * 在后台解码车辆封面，避免大图片解码阻塞主线程。
 */
@Composable
private fun rememberVehicleCoverBitmap(uri: Uri?): ImageBitmap? {
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
                        val maxDimension = maxOf(width, height)

                        val scale = minOf(
                            1f,
                            1200f / maxDimension
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