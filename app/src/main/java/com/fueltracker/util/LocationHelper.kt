package com.fueltracker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

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
            if (!Geocoder.isPresent()) {
                Log.w(TAG, "Geocoder is not present on this device")
                return@withContext null
            }
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext null

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )

            var bestLocation: android.location.Location? = null
            providers.forEach { provider ->
                if (lm.isProviderEnabled(provider)) {
                    val loc = try {
                        lm.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            }

            val location = bestLocation ?: return@withContext null

            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.CHINA)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )

            val address = addresses?.firstOrNull() ?: return@withContext null

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

            Log.d(
                TAG,
                "resolved: $name " +
                        "(district=$district, street=$street, poi=$poi, " +
                        "lat=${location.latitude}, lng=${location.longitude})"
            )
            name
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocationName failed", e)
            null
        }
    }

    /**
     * 去掉 "XX路123号" 这类门牌号。
     * "新城十九路47号幸福苑二期" → "幸福苑二期"
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