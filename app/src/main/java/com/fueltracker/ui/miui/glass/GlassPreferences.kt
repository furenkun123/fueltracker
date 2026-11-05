package com.fueltracker.ui.miui.glass

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * 全局液态玻璃参数。
 *
 * 参数只影响现有卡片玻璃效果，不创建额外 Backdrop 或采样层。
 */
data class GlassParams(
    val blur: Float = 12f,
    val refractionHeight: Float = 14f,
    val refractionAmount: Float = 24f,
    val concentration: Float = 65f
)

object GlassPreferences {

    private const val PREFS_NAME = "glass_prefs"
    private const val KEY_BLUR = "blur"
    private const val KEY_REFRACTION_HEIGHT = "refraction_height"
    private const val KEY_REFRACTION_AMOUNT = "refraction_amount"
    private const val KEY_CONCENTRATION = "concentration"
    private const val KEY_ENABLED = "enabled"

    var params by mutableStateOf(GlassParams())
        private set

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, true)
        params = GlassParams(
            blur = prefs.getFloat(KEY_BLUR, 12f).coerceIn(0f, 30f),
            refractionHeight = prefs.getFloat(KEY_REFRACTION_HEIGHT, 14f).coerceIn(0f, 64f),
            refractionAmount = prefs.getFloat(KEY_REFRACTION_AMOUNT, 24f).coerceIn(0f, 128f),
            concentration = prefs.getFloat(KEY_CONCENTRATION, 65f).coerceIn(0f, 100f)
        )
    }

    fun setEnabled(context: Context, isEnabled: Boolean) {
        enabled = isEnabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, isEnabled)
        }
    }

    fun update(context: Context, newParams: GlassParams) {
        val safeParams = newParams.copy(
            blur = newParams.blur.coerceIn(0f, 30f),
            refractionHeight = newParams.refractionHeight.coerceIn(0f, 64f),
            refractionAmount = newParams.refractionAmount.coerceIn(0f, 128f),
            concentration = newParams.concentration.coerceIn(0f, 100f)
        )
        params = safeParams
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putFloat(KEY_BLUR, safeParams.blur)
            putFloat(KEY_REFRACTION_HEIGHT, safeParams.refractionHeight)
            putFloat(KEY_REFRACTION_AMOUNT, safeParams.refractionAmount)
            putFloat(KEY_CONCENTRATION, safeParams.concentration)
        }
    }

    fun reset(context: Context) {
        update(context, GlassParams())
    }
}
