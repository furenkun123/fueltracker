package com.fueltracker.util

import android.content.Context
import androidx.core.content.edit

/**
 * 首启/更新提示管理。
 *
 * 逻辑：
 * - lastShown == 0（首次安装）→ 弹隐私声明
 * - 0 < lastShown < 当前版本 → 弹更新内容
 * - lastShown == 当前版本 → 不弹
 */
object UpdateLogManager {

    private const val PREFS = "update_log_prefs"
    private const val KEY_LAST_SHOWN_VERSION = "last_shown_version"

    /**
     * 各版本的更新内容。
     * key = versionCode（对应 build.gradle.kts 里的 appVersionCode）
     */
    private val UPDATE_LOGS: Map<Int, List<String>> = mapOf(
        29 to listOf(
            "优化记加油页面交互",
            "修复若干已知问题"
        ),
        33 to listOf(
            "新增停车记录功能，保存楼层、区域、车位号",
            "首页新增当前车位卡片，一键取车",
            "新增费用记录：停车、高速、保养、保险等",
            "新增费用统计页，支持自定义分类",
            "新增费用趋势图和结构分析",
            "油耗统计卡片搬迁至首页，支持按年月筛选"
        ),
        37 to listOf(
            "记停车支持自动定位，快速填入位置",
            "停车位置支持历史记忆，一键复用"
        ),
        40 to listOf(
            "增加小熊数据导入,数据是原有数据追加导入的,建议备份原数据再导入",
            "小熊网页版导出CSV,油耗，A:日期,B:公里数,C:单价,D:原价,E:实付价,F:燃油标号,G:是否加满,H:有无亮灯，" +
                    "然后费用，A:日期,B:价格,C,费用类别，都是无表头CSV",
            "优化细节"
        )
        // 后续版本继续追加
    )

    /**
     * versionCode → versionName 映射表。
     * 用于展示用户能看懂的版本号（如 "1.6" 而不是 "37"）。
     * 每次加新版本时，记得同步加一条。
     */
    private val VERSION_NAMES: Map<Int, String> = mapOf(
        29 to "1.33",
        33 to "1.50",
        37 to "1.60",
        40 to "1.61"
    )

    /**
     * 更新历史条目（带版本名和内容）。
     */
    data class UpdateLogEntry(
        val versionCode: Int,
        val versionName: String,
        val items: List<String>
    )

    /**
     * 首启状态。
     */
    sealed class StartState {
        /** 不需要弹任何东西 */
        object None : StartState()

        /** 首次安装，弹隐私声明 */
        object Privacy : StartState()

        /** 版本更新，弹更新内容（按版本分组） */
        data class UpdateLog(val entries: List<UpdateLogEntry>) : StartState()
    }

    /**
     * 获取所有历史版本更新内容（按版本号倒序）。
     * 供"更新历史"页面展示。
     */
    fun getAllUpdateLogs(): List<UpdateLogEntry> {
        return UPDATE_LOGS
            .toSortedMap(compareByDescending { it })
            .map { (code, items) ->
                UpdateLogEntry(
                    versionCode = code,
                    versionName = VERSION_NAMES[code] ?: "未知",
                    items = items
                )
            }
    }

    /**
     * 计算本次启动应该弹什么。
     */
    fun getStartState(context: Context, currentVersion: Int): StartState {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastShown = prefs.getInt(KEY_LAST_SHOWN_VERSION, 0)

        // 首次安装
        if (lastShown == 0) {
            return StartState.Privacy
        }

        // 无变化
        if (lastShown >= currentVersion) {
            return StartState.None
        }

        // 版本升级：返回 lastShown+1 到 currentVersion 之间的所有更新（按版本分组）
        val entries = UPDATE_LOGS
            .filterKeys { it in (lastShown + 1)..currentVersion }
            .toSortedMap()
            .map { (code, items) ->
                UpdateLogEntry(
                    versionCode = code,
                    versionName = VERSION_NAMES[code] ?: "未知",
                    items = items
                )
            }

        return if (entries.isEmpty()) {
            StartState.None
        } else {
            StartState.UpdateLog(entries)
        }
    }

    /**
     * 标记已展示，避免下次再弹。
     */
    fun markShown(context: Context, versionCode: Int) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_LAST_SHOWN_VERSION, versionCode) }
    }
}