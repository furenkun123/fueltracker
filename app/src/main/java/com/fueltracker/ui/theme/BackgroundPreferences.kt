
package com.fueltracker.ui.theme

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * 个性化背景图片设置。
 *
 * 保存 SAF 返回的持久化 URI，不复制图片文件。
 */
object BackgroundPreferences {

    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_BACKGROUND_URI = "background_uri"
    private const val KEY_SCRIM_ALPHA = "background_scrim_alpha"

    private var backgroundUriString by mutableStateOf<String?>(null)

    private var scrimAlphaState by mutableFloatStateOf(0.32f)

    val backgroundUri: Uri?
        get() = backgroundUriString?.let(Uri::parse)

    /**
     * 背景图片上方黑色遮罩的透明度。
     * 默认 0.32，限制在 0f～0.8f。
     */
    val scrimAlpha: Float
        get() = scrimAlphaState

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        backgroundUriString = prefs.getString(KEY_BACKGROUND_URI, null)
        scrimAlphaState = prefs.getFloat(KEY_SCRIM_ALPHA, 0.32f)
            .coerceIn(0f, 0.8f)
    }

    fun setBackground(context: Context, uri: Uri) {
        val oldUri = backgroundUri

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_BACKGROUND_URI, uri.toString())
            }

        backgroundUriString = uri.toString()

        if (oldUri != null && oldUri != uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun setScrimAlpha(context: Context, alpha: Float) {
        val safeAlpha = alpha.coerceIn(0f, 0.8f)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putFloat(KEY_SCRIM_ALPHA, safeAlpha)
            }

        scrimAlphaState = safeAlpha
    }

    fun clear(context: Context) {
        val oldUri = backgroundUri

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_BACKGROUND_URI)
            }

        backgroundUriString = null

        if (oldUri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}
