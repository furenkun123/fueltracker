
package com.fueltracker.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

object ExpenseCategoryStore {

    private const val TAG = "ExpenseCategoryStore"

    private const val PREF_NAME = "expense_category_prefs"
    private const val KEY_MIGRATED = "migrated_to_room"
    private const val KEY_OLD_CATEGORIES = "custom_categories"

    /**
     * 默认费用分类。
     * 不允许作为自定义分类重复添加。
     */
    val DEFAULT_EXPENSE_CATEGORIES = listOf(
        "加油",
        "停车",
        "高速",
        "保养",
        "保险",
        "维修",
        "洗车",
        "其他"
    )

    private val appScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var db: AppDatabase? = null

    /**
     * null：尚未完成初始化。
     * emptyList()：初始化成功，但没有自定义分类。
     */
    private val _customCategories =
        MutableStateFlow<List<String>?>(null)

    val customCategories: StateFlow<List<String>?> =
        _customCategories.asStateFlow()

    /**
     * 长期数据库监听任务。
     */
    private var observerJob: Job? = null

    /**
     * 初始化。
     *
     * 1. 获取 Room 数据库。
     * 2. 迁移旧 SharedPreferences 数据。
     * 3. 等待 Room 首次发射数据。
     * 4. 启动长期监听。
     *
     * 初始化失败时不会设置初始化成功状态，
     * 后续可以再次调用 init()。
     */
    suspend fun init(context: Context) {
        initMutex.withLock {

            if (_customCategories.value != null &&
                observerJob?.isActive == true
            ) {
                return
            }

            val appContext = context.applicationContext

            val database = db ?: AppDatabase
                .getInstance(appContext)
                .also { db = it }

            val dao = database.customCategoryDao()

            try {
                // 1. 迁移旧数据
                migrateLegacyCategories(
                    context = appContext,
                    dao = dao
                )

                // 2. 等待 Room 首次发射
                val initialData = dao.observeAll().first()

                _customCategories.value = initialData

                // 3. 启动唯一的长期监听任务
                startObserver(dao)

                Log.i(
                    TAG,
                    "分类初始化成功，数量=${initialData.size}"
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "分类初始化失败", e)

                // 初始化失败时不能把空列表当作成功状态。
                _customCategories.value = null

                throw e
            }
        }
    }

    /**
     * 迁移旧 SharedPreferences 数据。
     *
     * 先写 Room，成功后再写迁移标记。
     * Room 插入使用 IGNORE，因此重复迁移安全。
     */
    private suspend fun migrateLegacyCategories(
        context: Context,
        dao: CustomCategoryDao
    ) {
        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        if (prefs.getBoolean(KEY_MIGRATED, false)) {
            return
        }

        val oldSet = prefs
            .getStringSet(KEY_OLD_CATEGORIES, emptySet())
            .orEmpty()

        val migratedCategories = oldSet
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { name ->
                DEFAULT_EXPENSE_CATEGORIES.any {
                    it.equals(name, ignoreCase = true)
                }
            }
            .distinctBy {
                it.lowercase(Locale.ROOT)
            }

        if (migratedCategories.isNotEmpty()) {
            dao.insertAll(
                migratedCategories.map {
                    CustomCategoryEntity(it)
                }
            )
        }

        // Room 写入成功后，才标记迁移完成。
        prefs.edit {
            putBoolean(KEY_MIGRATED, true)
            remove(KEY_OLD_CATEGORIES)
        }

        Log.i(
            TAG,
            "旧分类迁移完成，迁移数量=${migratedCategories.size}"
        )
    }

    /**
     * 启动数据库监听。
     *
     * 同一时间只允许一个监听任务。
     */
    private fun startObserver(dao: CustomCategoryDao) {
        if (observerJob?.isActive == true) {
            return
        }

        observerJob = appScope.launch {
            try {
                dao.observeAll().collect { categories ->
                    _customCategories.value = categories
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "分类数据库监听异常退出", e)
            }
        }
    }

    /**
     * 获取当前自定义分类。
     *
     * 未初始化时直接抛出异常，避免将 null 伪装为空列表。
     */
    fun getCustom(): List<String> {
        return checkNotNull(_customCategories.value) {
            "ExpenseCategoryStore 尚未初始化完成"
        }
    }

    /**
     * 获取数据库实例。
     */
    private fun requireDatabase(): AppDatabase {
        checkInitialized()
        return checkNotNull(db) {
            "ExpenseCategoryStore 数据库尚未初始化"
        }
    }

    /**
     * 检查初始化状态。
     */
    private fun checkInitialized() {
        check(_customCategories.value != null) {
            "ExpenseCategoryStore 尚未初始化完成"
        }
    }

    /**
     * 名称规范化。
     */
    private fun normalizeName(name: String): String {
        return name.trim()
    }

    /**
     * 判断是否与默认分类重名。
     */
    private fun isDefaultCategory(name: String): Boolean {
        return DEFAULT_EXPENSE_CATEGORIES.any {
            it.equals(name, ignoreCase = true)
        }
    }

    /**
     * 新增自定义分类。
     *
     * true：插入成功。
     * false：名称无效、默认分类重名或已经存在。
     */
    suspend fun add(name: String): Boolean {
        checkInitialized()

        val trimmed = normalizeName(name)

        if (trimmed.isEmpty()) return false
        if (isDefaultCategory(trimmed)) return false

        return writeMutex.withLock {
            val database = requireDatabase()
            val dao = database.customCategoryDao()

            val result = dao.insert(
                CustomCategoryEntity(trimmed)
            )

            result != -1L
        }
    }

    /**
     * 删除自定义分类。
     *
     * true：成功删除。
     * false：分类不存在。
     */
    suspend fun remove(name: String): Boolean {
        checkInitialized()

        val trimmed = normalizeName(name)

        return trimmed.isNotEmpty() && writeMutex.withLock {
            val database = requireDatabase()
            val deleted = database.customCategoryDao()
                .delete(trimmed)

            deleted == 1
        }
    }

    /**
     * 更新自定义分类。
     *
     * 仅负责分类表更新。
     * 如果还需要同步更新历史费用，
     * 必须使用 MainViewModel 中的跨表事务，
     * 不要调用本方法代替跨表重命名。
     *
     * true：更新成功。
     * false：名称无效、分类不存在、重名或名称未变化。
     */
    suspend fun update(
        oldName: String,
        newName: String
    ): Boolean {
        checkInitialized()

        val trimmed = normalizeName(newName)
        val oldTrimmed = normalizeName(oldName)

        if (oldTrimmed.isEmpty()) return false
        if (trimmed.isEmpty()) return false
        if (isDefaultCategory(trimmed)) return false

        return writeMutex.withLock {
            val database = requireDatabase()
            val dao = database.customCategoryDao()

            val current = dao.getAll()

            val actualOld = current.firstOrNull {
                it.equals(oldTrimmed, ignoreCase = true)
            } ?: return@withLock false

            // 与主业务保持相同规则：仅大小写变化也不允许。
            if (trimmed.equals(actualOld, ignoreCase = true)) {
                return@withLock false
            }

            if (current.any {
                    !it.equals(actualOld, ignoreCase = true) &&
                            it.equals(trimmed, ignoreCase = true)
                }
            ) {
                return@withLock false
            }

            database.withTransaction {
                val updated = dao.rename(actualOld, trimmed)
                updated == 1
            }
        }
    }

}
