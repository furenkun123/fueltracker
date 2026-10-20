package com.fueltracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

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

    fun getCustom(): List<String> =
        prefs.getStringSet(KEY_CUSTOM, emptySet())
            ?.toList()
            ?.sorted()
            .orEmpty()

    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (trimmed in DEFAULT_EXPENSE_CATEGORIES) return
        val set = prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty().toMutableSet()
        set.add(trimmed)
        prefs.edit { putStringSet(KEY_CUSTOM, set) }
    }

    fun remove(name: String) {
        val set = prefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty().toMutableSet()
        set.remove(name)
        prefs.edit { putStringSet(KEY_CUSTOM, set) }
    }

    @Suppress("UseKtx")
    fun update(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()

        if (trimmed.isEmpty()) return false
        if (trimmed == oldName) return false

        if (DEFAULT_EXPENSE_CATEGORIES.any {
                it.equals(trimmed, ignoreCase = true)
            }
        ) {
            return false
        }

        val set = prefs.getStringSet(KEY_CUSTOM, emptySet())
            .orEmpty()
            .toMutableSet()

        if (oldName !in set) return false

        if (set.any {
                it != oldName && it.equals(trimmed, ignoreCase = true)
            }
        ) {
            return false
        }

        set.remove(oldName)
        set.add(trimmed)

        return prefs.edit()
            .putStringSet(KEY_CUSTOM, set)
            .commit()
    }
    /**
     * 用给定列表替换所有自定义分类（备份恢复用）。
     */
    fun replaceAll(categories: List<String>) {
        val cleaned = categories
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in DEFAULT_EXPENSE_CATEGORIES }
            .toSet()
        prefs.edit { putStringSet(KEY_CUSTOM, cleaned) }
    }

}