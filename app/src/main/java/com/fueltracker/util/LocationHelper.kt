package com.fueltracker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

object LocationHelper {

    private const val TAG = "LocationHelper"

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 获取当前位置的文字描述（区 · 街道 · POI）。
     * 失败返回 null。
     */
    suspend fun getCurrentLocationName(context: Context): String? = withContext(Dispatchers.IO) {
        try {

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null

            // 检查定位权限
            if (!hasPermission(context)) {
                Log.w(TAG, "没有定位权限")
                return@withContext null
            }
// 只使用 GPS 卫星定位，不使用 NETWORK_PROVIDER
            val gpsProvider = LocationManager.GPS_PROVIDER

            val gpsEnabled = try {
                lm.isProviderEnabled(gpsProvider)
            } catch (_: Exception) {
                false
            }

// 优先主动请求 GPS 定位
            val currentLocation = if (gpsEnabled) {
                requestSingleLocation(context, lm, gpsProvider)
            } else {
                null
            }

// 主动定位失败时，尝试读取 GPS 缓存
            val cachedLocation = try {
                lm.getLastKnownLocation(gpsProvider)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

// 只接受 10 分钟以内的缓存位置
            val usableCachedLocation = cachedLocation?.takeIf {
                val age = System.currentTimeMillis() - it.time
                age in 0..(10 * 60 * 1000L)
            }

            val location = currentLocation
                ?: usableCachedLocation
                ?: return@withContext null

            Log.d(
                TAG,
                "GPS定位成功：主动定位=${currentLocation != null}，精度=${location.accuracy}米"
            )

            // 尝试将 GPS 坐标转换为具体地址
            val address = try {
                if (!Geocoder.isPresent()) {
                    Log.w(TAG, "设备没有可用的 Geocoder 服务")
                    null
                } else {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.CHINA)

                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                    )?.firstOrNull()
                }
            } catch (e: IOException) {
                Log.w(TAG, "地址解析失败，可能无法连接地址服务", e)
                null
            }

// 地址解析失败时，退回 GPS 经纬度
            if (address == null) {
                val coordinateName = "GPS坐标（离线）：%.5f, %.5f".format(
                    Locale.US,
                    location.latitude,
                    location.longitude
                )

                Log.w(TAG, "GPS定位成功，返回坐标：$coordinateName")

                return@withContext coordinateName
            }

            // 区县：subAdminArea → subLocality
            val district = sequenceOf(address.subAdminArea, address.subLocality)
                .mapNotNull { it?.trim() }
                .firstOrNull { it.isNotBlank() }

            // 街道
            val street = address.thoroughfare
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "Unnamed Road" }

            // POI 名，去掉门牌号
            val poi = address.featureName
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "Unnamed Road" }
                ?.let { removeStreetNumber(it) }

            val name = listOfNotNull(district, street, poi)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }

            if (name != null) {
                Log.d(TAG, "地址解析成功：$name")
                name
            } else {
                val coordinateName = "GPS坐标（离线）：%.5f, %.5f".format(
                    Locale.US,
                    location.latitude,
                    location.longitude
                )

                Log.w(TAG, "GPS定位成功，但地址字段为空，返回坐标：$coordinateName")
                coordinateName
            }
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocationName failed", e)
            null
        }
    }

    /**
     * 主动请求指定定位服务的当前位置。
     * 最多等待 10 秒，超时或失败返回 null。
     */
    private suspend fun requestSingleLocation(
        context: Context,
        locationManager: LocationManager,
        provider: String
    ): Location? = withTimeoutOrNull(15.seconds) {
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()

            // 协程取消时，同时取消系统定位请求
            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
            }

            try {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    context.mainExecutor
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            } catch (_: IllegalArgumentException) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * 去掉 "XX路123号" 这类门牌号。
     */
    private fun removeStreetNumber(text: String): String {
        val match = Regex("""^.{2,8}(路|街|道|巷|大街)\d+号""").find(text)
        if (match != null) {
            val rest = text.substring(match.value.length)
                .trimStart('，', ',', '·', ' ')
            if (rest.isNotBlank()) return rest
        }
        return text
    }
}