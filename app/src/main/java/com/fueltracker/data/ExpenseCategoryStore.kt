package com.fueltracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import android.util.Log

object ExpenseCategoryStore {

    private const val PREFS = "expense_category_prefs"
    private const val KEY_CUSTOM = "custom_categories"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }
    private fun normalize(categories: List<String>): Set<String> {
        val result = LinkedHashSet<String>()
        for (raw in categories) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            // 过滤默认分类（大小写不敏感）
            if (DEFAULT_EXPENSE_CATEGORIES.any { it.equals(name, ignoreCase = true) }) continue
            // 去重（大小写不敏感），保留第一个出现的写法
            if (result.any { it.equals(name, ignoreCase = true) }) continue
            result.add(name)
        }
        return result
    }

    fun getCustom(): List<String> =
        prefs.getStringSet(KEY_CUSTOM, emptySet())
            ?.toList()
            ?.sorted()
            .orEmpty()

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (DEFAULT_EXPENSE_CATEGORIES.any { it.equals(trimmed, ignoreCase = true) }) return

        val current = prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty()
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return   // 👈 核心

        val newSet = current.toMutableSet().apply { add(trimmed) }
        prefs.edit { putStringSet(KEY_CUSTOM, newSet) }
    }

    fun remove(name: String) {
        val current = prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty()
        val filtered = current.filterNot { it.equals(name, ignoreCase = true) }.toSet()
        prefs.edit { putStringSet(KEY_CUSTOM, filtered) }
    }

    @Suppress("UseKtx")
    fun update(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false

        if (DEFAULT_EXPENSE_CATEGORIES.any { it.equals(trimmed, ignoreCase = true) }) return false

        val current = prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty()

        // ① 用 ignoreCase 找到实际存储的原值，防止 UI 传的 oldName 和 SP 里的写法不一致
        val actualOld = current.firstOrNull { it.equals(oldName, ignoreCase = true) }
            ?: return false

        // ② 新名和实际存储值完全一致（含大小写）才视为「无变化」
        //    如果只有大小写差异，视为用户有意规范写法，允许改动
        if (trimmed == actualOld) return false

        // ③ 冲突检查要排除 actualOld 自身（而不是用户传入的 oldName）
        if (current.any { it != actualOld && it.equals(trimmed, ignoreCase = true) }) return false

        val newSet = current.toMutableSet()
        newSet.remove(actualOld)
        newSet.add(trimmed)
        return prefs.edit().putStringSet(KEY_CUSTOM, newSet).commit()
    }

    /**
     * 同步版本，供事务内需要确认成败的场景使用（如备份恢复）。
     *
     * 这里刻意不用 KTX 的 edit(commit = true) {} —— 它丢弃了 commit() 的返回值，
     * 无法得知写入是否成功，会破坏 BackupManager 的「DB + SP 原子恢复」逻辑。
     *
     * @return true 表示确实写入成功
     */
    @Suppress("UseKtx", "ApplySharedPref")
    fun replaceAllSync(categories: List<String>): Boolean {
        return ::prefs.isInitialized && try {
            prefs.edit()
                .putStringSet(KEY_CUSTOM, normalize(categories))
                .commit()            // ← 唯一能拿到 Boolean 结果的写法
        } catch (e: Exception) {
            Log.e("ExpenseCategoryStore", "replaceAllSync failed", e)
            false
        }
    }
}