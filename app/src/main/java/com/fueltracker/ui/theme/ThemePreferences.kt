package com.fueltracker.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * 主题模式持久化 + 可观察状态
 * 用 SharedPreferences 存, 用 mutableStateOf 让 Compose 直接观察
 */
object ThemePreferences {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_MODE = "theme_mode"

    /** 当前模式, Compose 里读它会自动重组 */
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** App 启动时调用一次 */
    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = sp.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        mode = runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    /** 设置模式并落盘 */
    fun setMode(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_MODE, newMode.name)
            }
    }
}