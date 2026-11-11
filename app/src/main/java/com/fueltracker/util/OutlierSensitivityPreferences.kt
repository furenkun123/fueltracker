
package com.fueltracker.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * 油耗偏离值识别灵敏度
 */
enum class OutlierSensitivity(
    val displayName: String,
    val description: String,
    val madMultiplier: Double,
    val relativeMadMinWidth: Double,
    val madThresholdFloor: Double,
    val plugInMadMinWidth: Double
) {
    STANDARD(
        displayName = "标准",
        description = "默认规则，不要随便调整",
        madMultiplier = 3.5,
        relativeMadMinWidth = 0.45,
        madThresholdFloor = 0.35,
        plugInMadMinWidth = 0.75
    ),
    LOOSE(
        displayName = "宽松",
        description = "适合凭记忆补录或者波动异常数据",
        madMultiplier = 4.0,
        relativeMadMinWidth = 0.55,
        madThresholdFloor = 0.50,
        plugInMadMinWidth = 0.90
    )
}

/**
 * 偏离值识别设置持久化
 */
object OutlierSensitivityPreferences {

    private const val PREFS_NAME = "outlier_sensitivity_prefs"
    private const val KEY_MODE = "outlier_sensitivity_mode"

    var mode by mutableStateOf(OutlierSensitivity.STANDARD)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val savedName = prefs.getString(
            KEY_MODE,
            OutlierSensitivity.STANDARD.name
        )

        mode = OutlierSensitivity.entries
            .firstOrNull { it.name == savedName }
            ?: OutlierSensitivity.STANDARD
    }

    fun setMode(
        context: Context,
        newMode: OutlierSensitivity
    ) {
        mode = newMode

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit {
            putString(KEY_MODE, newMode.name)
        }
    }
}
