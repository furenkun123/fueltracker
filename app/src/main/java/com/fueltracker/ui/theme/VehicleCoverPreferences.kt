package com.fueltracker.ui.theme

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.edit


/**
 * 车辆封面裁剪参数。
 *
 * scale：图片缩放比例
 * panX：水平方向偏移比例，范围 -1～1
 * panY：垂直方向偏移比例，范围 -1～1
 *
 * 偏移比例不直接使用像素，避免屏幕尺寸变化后位置失准。
 */
data class VehicleCoverCropTransform(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
)

/**
 * 车辆封面图片及显示设置。
 *
 * 每辆车独立保存：
 * 1. 封面图片持久化 URI
 * 2. 是否在封面上显示车型信息
 */
object VehicleCoverPreferences {

    private const val PREFS_NAME = "vehicle_cover_prefs"

    private const val KEY_COVER_PREFIX = "cover_uri_"
    private const val KEY_SHOW_INFO_PREFIX = "show_info_"
    private const val KEY_CROP_SCALE_PREFIX = "crop_scale_"
    private const val KEY_CROP_X_PREFIX = "crop_x_"
    private const val KEY_CROP_Y_PREFIX = "crop_y_"

    private val coverUris = mutableStateMapOf<Long, String>()
    private val showInfoValues = mutableStateMapOf<Long, Boolean>()
    private val cropTransforms =
        mutableStateMapOf<Long, VehicleCoverCropTransform>()

    private var preferences: SharedPreferences? = null

    /**
     * 应用启动时加载所有车辆的封面设置。
     */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        preferences = prefs

        coverUris.clear()
        showInfoValues.clear()
        cropTransforms.clear()

        val cropVehicleIds = mutableSetOf<Long>()

        prefs.all.forEach { (key, value) ->
            when {
                key.startsWith(KEY_COVER_PREFIX) && value is String -> {
                    val vehicleId = key
                        .removePrefix(KEY_COVER_PREFIX)
                        .toLongOrNull()

                    if (vehicleId != null && vehicleId > 0L) {
                        coverUris[vehicleId] = value
                    }
                }

                key.startsWith(KEY_SHOW_INFO_PREFIX) && value is Boolean -> {
                    val vehicleId = key
                        .removePrefix(KEY_SHOW_INFO_PREFIX)
                        .toLongOrNull()

                    if (vehicleId != null && vehicleId > 0L) {
                        showInfoValues[vehicleId] = value
                    }
                }
                key.startsWith(KEY_CROP_SCALE_PREFIX) -> {
                    val vehicleId = key
                        .removePrefix(KEY_CROP_SCALE_PREFIX)
                        .toLongOrNull()

                    if (vehicleId != null && vehicleId > 0L) {
                        cropVehicleIds.add(vehicleId)
                    }
                }
            }
        }
        cropVehicleIds.forEach { vehicleId ->
            cropTransforms[vehicleId] = VehicleCoverCropTransform(
                scale = prefs.getFloat(
                    cropScaleKey(vehicleId),
                    1f
                ).coerceIn(1f, 4f),

                panX = prefs.getFloat(
                    cropXKey(vehicleId),
                    0f
                ).coerceIn(-1f, 1f),

                panY = prefs.getFloat(
                    cropYKey(vehicleId),
                    0f
                ).coerceIn(-1f, 1f)
            )
        }
    }

    /**
     * 获取指定车辆的封面图片 URI。
     */
    fun getCoverUri(vehicleId: Long): Uri? {
        if (vehicleId <= 0L) return null

        return coverUris[vehicleId]?.let(Uri::parse)
    }

    /**
     * 获取是否显示车型信息。
     * 默认开启。
     */
    fun shouldShowInfo(vehicleId: Long): Boolean {
        return vehicleId <= 0L || showInfoValues[vehicleId] ?: true
    }
    /**
     * 获取车辆封面裁剪参数。
     */
    fun getCropTransform(vehicleId: Long): VehicleCoverCropTransform {
        if (vehicleId <= 0L) {
            return VehicleCoverCropTransform()
        }

        return cropTransforms[vehicleId]
            ?: VehicleCoverCropTransform()
    }

    /**
     * 保存车辆封面裁剪参数。
     */
    fun setCropTransform(
        context: Context,
        vehicleId: Long,
        transform: VehicleCoverCropTransform
    ) {
        if (vehicleId <= 0L) return

        val safeTransform = VehicleCoverCropTransform(
            scale = transform.scale
                .takeIf { it.isFinite() }
                ?.coerceIn(1f, 4f)
                ?: 1f,

            panX = transform.panX
                .takeIf { it.isFinite() }
                ?.coerceIn(-1f, 1f)
                ?: 0f,

            panY = transform.panY
                .takeIf { it.isFinite() }
                ?.coerceIn(-1f, 1f)
                ?: 0f
        )

        prefs(context).edit {
            putFloat(
                cropScaleKey(vehicleId),
                safeTransform.scale
            )
            putFloat(
                cropXKey(vehicleId),
                safeTransform.panX
            )
            putFloat(
                cropYKey(vehicleId),
                safeTransform.panY
            )
        }

        cropTransforms[vehicleId] = safeTransform
    }

    /**
     * 设置车辆封面。
     *
     * 保存 URI 的持久化读取权限，
     * 更换图片后释放不再使用的旧权限。
     */
    fun setCover(
        context: Context,
        vehicleId: Long,
        uri: Uri
    ) {
        require(vehicleId > 0L) {
            "车辆 ID 无效"
        }

        val oldUri = getCoverUri(vehicleId)

        if (oldUri == uri) return

        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        prefs(context).edit {
            putString(
                coverKey(vehicleId),
                uri.toString()
            )

            // 新图片使用默认裁剪位置
            remove(cropScaleKey(vehicleId))
            remove(cropXKey(vehicleId))
            remove(cropYKey(vehicleId))
        }

        cropTransforms.remove(vehicleId)

        coverUris[vehicleId] = uri.toString()

        releasePermissionIfUnused(
            context,
            oldUri,
            vehicleId
        )
    }

    /**
     * 删除指定车辆的封面图片。
     */
    fun clearCover(
        context: Context,
        vehicleId: Long
    ) {
        if (vehicleId <= 0L) return

        val oldUri = getCoverUri(vehicleId)

        prefs(context).edit {
            remove(coverKey(vehicleId))
            remove(cropScaleKey(vehicleId))
            remove(cropXKey(vehicleId))
            remove(cropYKey(vehicleId))
        }


        coverUris.remove(vehicleId)
        cropTransforms.remove(vehicleId)

        releasePermissionIfUnused(
            context,
            oldUri,
            vehicleId
        )
    }

    /**
     * 设置是否在封面上显示车型信息。
     */
    fun setShowInfo(
        context: Context,
        vehicleId: Long,
        show: Boolean
    ) {
        if (vehicleId <= 0L) return

        prefs(context).edit {
            putBoolean(
                showInfoKey(vehicleId),
                show
            )
        }

        showInfoValues[vehicleId] = show
    }

    /**
     * 删除车辆时清理其封面设置。
     */
    fun clearVehicleSettings(
        context: Context,
        vehicleId: Long
    ) {
        if (vehicleId <= 0L) return

        clearCover(context, vehicleId)

        prefs(context).edit {
            remove(showInfoKey(vehicleId))
        }

        showInfoValues.remove(vehicleId)
    }

    private fun releasePermissionIfUnused(
        context: Context,
        uri: Uri?,
        exceptVehicleId: Long
    ) {
        if (uri == null) return

        // 其他车辆仍在使用同一张图片时，不释放权限。
        val usedByAnotherVehicle = coverUris.any { (id, value) ->
            id != exceptVehicleId && value == uri.toString()
        }

        // 全局背景仍在使用时，不释放权限。
        if (
            usedByAnotherVehicle ||
            BackgroundPreferences.backgroundUri == uri
        ) {
            return
        }

        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun prefs(context: Context): SharedPreferences {
        return preferences ?: context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .also {
                preferences = it
            }
    }

    private fun coverKey(vehicleId: Long) =
        "$KEY_COVER_PREFIX$vehicleId"

    private fun showInfoKey(vehicleId: Long) =
        "$KEY_SHOW_INFO_PREFIX$vehicleId"

    private fun cropScaleKey(vehicleId: Long) =
        "$KEY_CROP_SCALE_PREFIX$vehicleId"

    private fun cropXKey(vehicleId: Long) =
        "$KEY_CROP_X_PREFIX$vehicleId"

    private fun cropYKey(vehicleId: Long) =
        "$KEY_CROP_Y_PREFIX$vehicleId"
}