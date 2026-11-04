package com.fueltracker.ui.theme

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private const val KEY_BACKGROUND_IMAGE = "background_image_uri"

    /** 当前模式, Compose 里读它会自动重组 */
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** 自定义应用背景图片 URI；为空时使用默认渐变背景。 */
    var backgroundImageUri by mutableStateOf<String?>(null)
        private set

    /** App 启动时调用一次 */
    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = sp.getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        mode = runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
        backgroundImageUri = sp.getString(KEY_BACKGROUND_IMAGE, null)
    }

    /** 设置模式并落盘 */
    fun setMode(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_MODE, newMode.name)
            }
    }

    /** 保存图片访问权限和 URI，确保应用重启后仍可读取。 */
    fun setBackgroundImage(context: Context, uri: Uri?) {
        val resolver = context.contentResolver
        val previousUri = backgroundImageUri?.let(Uri::parse)

        if (uri != null) {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                if (uri == null) remove(KEY_BACKGROUND_IMAGE)
                else putString(KEY_BACKGROUND_IMAGE, uri.toString())
            }

        backgroundImageUri = uri?.toString()

        if (previousUri != null && previousUri != uri) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    previousUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}