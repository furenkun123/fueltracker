package com.fueltracker.data

import android.content.Context
import androidx.core.content.edit // 👈 新增：KTX edit 扩展函数
import androidx.room.withTransaction // 👈 新增：Room withTransaction 扩展函数
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ExpenseCategoryStore {

    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 内存缓存：便于 UI 同步调用 getCustom()
    private val _customCategories = MutableStateFlow<List<String>>(emptyList())
    val customCategories: StateFlow<List<String>> = _customCategories.asStateFlow()

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = AppDatabase.getInstance(context)
        val dao = db.customCategoryDao()

        scope.launch {
            // 1. 先尝试迁移旧的 SP 数据
            val prefs = context.getSharedPreferences("expense_category_prefs", Context.MODE_PRIVATE)
            val hasMigrated = prefs.getBoolean("migrated_to_room", false)

            if (!hasMigrated) {
                val oldSet = prefs.getStringSet("custom_categories", emptySet()).orEmpty()
                if (oldSet.isNotEmpty()) {
                    val entities = oldSet.map { CustomCategoryEntity(it) }
                    dao.insertAll(entities)
                }
                // 👇 修复警告：改用 KTX 的 edit { } 写法，更加简洁
                prefs.edit {
                    putBoolean("migrated_to_room", true)
                    remove("custom_categories")
                }
            }

            // 2. 迁移完成后再开启监听，并同步给缓存
            dao.observeAll().collect {
                _customCategories.value = it
            }
        }
    }

    // 同步返回缓存数据
    fun getCustom(): List<String> = _customCategories.value

    // 以下方法改为 suspend，交由协程处理
    suspend fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (DEFAULT_EXPENSE_CATEGORIES.any { it.equals(trimmed, ignoreCase = true) }) return

        // Room 主键冲突会报错，利用 IGNORE 静默忽略
        db.customCategoryDao().insert(CustomCategoryEntity(trimmed))
    }

    suspend fun remove(name: String) {
        // 忽略大小写删除
        db.customCategoryDao().getAll()
            .filter { it.equals(name, ignoreCase = true) }
            .forEach { db.customCategoryDao().delete(it) }
    }

    suspend fun update(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        if (DEFAULT_EXPENSE_CATEGORIES.any { it.equals(trimmed, ignoreCase = true) }) return false

        val current = db.customCategoryDao().getAll()
        val actualOld = current.firstOrNull { it.equals(oldName, ignoreCase = true) } ?: return false

        if (trimmed == actualOld) return false
        if (current.any { it != actualOld && it.equals(trimmed, ignoreCase = true) }) return false

        // 由于主键是 name，改名相当于 删旧增新
        db.withTransaction { // 👈 import 加上后，这里就不会再报错了
            db.customCategoryDao().delete(actualOld)
            db.customCategoryDao().insert(CustomCategoryEntity(trimmed))
        }
        return true
    }
}