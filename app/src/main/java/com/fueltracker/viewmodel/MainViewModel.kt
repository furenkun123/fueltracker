package com.fueltracker.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.fueltracker.AppScreen
import com.fueltracker.data.AppDatabase
import com.fueltracker.data.CATEGORY_PARKING
import com.fueltracker.data.ExpenseCategoryStore
import com.fueltracker.data.ExpenseDao
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.util.BackupManager
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.StatisticsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val vehicleDao = database.vehicleDao()
    private val fuelDao = database.fuelDao()
    private val expenseDao: ExpenseDao = database.expenseDao()

    companion object {
        private const val TAG = "MainViewModel"
    }

    /**
     * 临时车辆状态，用于降低切换车辆时的 UI 延迟。
     *
     * 注意：数据库提交成功后，最终状态仍以数据库为准。
     */
    private val _currentVehicleOverride = MutableStateFlow<Vehicle?>(null)

    private val stopTimeout = 5_000L

    // =========================================================
    // 导航状态
    // =========================================================

    var currentScreen by mutableStateOf(AppScreen.HOME)
        private set

    var expenseSelectedCategory by mutableStateOf<String?>(null)
        private set

    var editFuelOrigin by mutableStateOf(AppScreen.RECORDS)
        private set

    var editExpenseOrigin by mutableStateOf(AppScreen.EXPENSES)
        private set

    var addVehicleOrigin by mutableStateOf(AppScreen.HOME)
        private set

    var editingRecord by mutableStateOf<FuelRecord?>(null)
        private set

    var editingVehicle by mutableStateOf<Vehicle?>(null)
        private set

    var editingExpense by mutableStateOf<ExpenseRecord?>(null)
        private set

    var isBackupOperationRunning by mutableStateOf(false)
        private set

    var isBottomBarVisible by mutableStateOf(true)
        private set

    fun updateBottomBarVisibility(visible: Boolean) {
        if (isBottomBarVisible != visible) {
            isBottomBarVisible = visible
        }
    }

    fun navigateTo(screen: AppScreen) {
        currentScreen = screen
        isBottomBarVisible = true
    }

    fun updateExpenseSelectedCategory(category: String?) {
        expenseSelectedCategory = category
    }

    fun editFuel(record: FuelRecord, origin: AppScreen) {
        editingRecord = record
        editFuelOrigin = origin
        currentScreen = AppScreen.EDIT_FUEL
    }

    fun editVehicle(vehicle: Vehicle) {
        editingVehicle = vehicle
        currentScreen = AppScreen.EDIT_VEHICLE
    }

    fun editExpenseRecord(
        record: ExpenseRecord,
        targetScreen: AppScreen,
        origin: AppScreen
    ) {
        editingExpense = record
        editExpenseOrigin = origin
        currentScreen = targetScreen
    }

    fun navigateToAddVehicle(origin: AppScreen) {
        addVehicleOrigin = origin
        currentScreen = AppScreen.ADD_VEHICLE
    }

    fun clearEditingStates() {
        editingRecord = null
        editingVehicle = null
        editingExpense = null
    }

    // =========================================================
    // 数据流
    // =========================================================

    val vehicles: StateFlow<List<Vehicle>> = vehicleDao
        .observeAllVehicles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeout),
            initialValue = emptyList()
        )

    val currentVehicle: StateFlow<Vehicle?> = combine(
        vehicleDao.observeCurrentVehicle(),
        _currentVehicleOverride,
        vehicles
    ) { dbVehicle, overrideVehicle, allVehicles ->

        val resolved = overrideVehicle
            ?: dbVehicle
            ?: allVehicles.firstOrNull { it.isCurrent }
            ?: allVehicles.firstOrNull()

        /*
         * 不在 combine 转换函数中修改 MutableStateFlow。
         * 这里仅返回最终展示值，避免转换过程产生额外状态写入。
         */
        resolved
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeout),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<FuelRecord>> = currentVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(emptyList())
            } else {
                fuelDao.observeRecords(vehicle.id)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeout),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseRecord>> = currentVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(emptyList())
            } else {
                expenseDao.observeByVehicle(vehicle.id)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeout),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeParking: StateFlow<ExpenseRecord?> = currentVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(null)
            } else {
                expenseDao.observeActiveParking(vehicle.id)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeout),
            initialValue = null
        )

    // =========================================================
    // 油耗统计
    // =========================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    val statisticsSummary: StateFlow<StatisticsSummary?> = currentVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(null)
            } else {
                fuelDao.observeRecords(vehicle.id).map { list ->
                    if (list.isEmpty()) {
                        null
                    } else {
                        FuelCalculator.calculateSummary(
                            allRecords = list,
                            vehicle = vehicle
                        )
                    }
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeout),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestConsumption: StateFlow<Double?> = currentVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(null)
            } else {
                fuelDao.observeRecords(vehicle.id).map { list ->
                    FuelCalculator.latestReliableConsumption(
                        records = list,
                        vehicle = vehicle
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeout),
            initialValue = null
        )

    val averageConsumption: StateFlow<Double?> = statisticsSummary
        .map { it?.averageConsumption }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeout),
            initialValue = null
        )

    // =========================================================
    // 通用校验辅助方法
    // =========================================================

    private fun requireFiniteNonNegative(
        value: Double,
        fieldName: String
    ) {
        require(value.isFinite() && value >= 0.0) {
            "${fieldName}必须是有效的非负数"
        }
    }

    private fun requireNullableFiniteNonNegative(
        value: Double?,
        fieldName: String
    ) {
        if (value != null) {
            requireFiniteNonNegative(value, fieldName)
        }
    }

    /**
     * 校验加油记录的数值。
     *
     * 初始记录允许油量、金额和单价为 0；
     * 普通加油记录要求加油量大于 0。
     */
    private fun validateFuelRecord(record: FuelRecord) {
        require(record.timestamp > 0L) {
            "加油时间无效"
        }

        requireFiniteNonNegative(record.volume, "加油量")
        requireFiniteNonNegative(record.actualPaidAmount, "实付金额")
        requireFiniteNonNegative(record.unitPrice, "单价")

        requireNullableFiniteNonNegative(record.odometer, "里程")
        requireNullableFiniteNonNegative(record.totalAmount, "原价")
        requireNullableFiniteNonNegative(record.discount, "优惠金额")
        requireNullableFiniteNonNegative(record.remainingFuel, "剩余油量")
        requireNullableFiniteNonNegative(record.initialFuel, "初始油量")
        requireNullableFiniteNonNegative(record.missedOdometer, "漏记里程")
        requireNullableFiniteNonNegative(record.missedVolume, "漏记加油量")
        requireNullableFiniteNonNegative(record.consumption, "油耗")

        if (!record.isInitialRecord) {
            require(record.volume > 0.0) {
                "加油量必须大于 0"
            }
        }

        if (record.hasMissedRecord) {
            require(
                record.missedOdometer == null ||
                        record.missedOdometer.isFinite()
            ) {
                "漏记里程无效"
            }

            require(
                record.missedVolume == null ||
                        record.missedVolume.isFinite()
            ) {
                "漏记加油量无效"
            }
        }
    }

    /**
     * 直接查询数据库，而不是依赖可能尚未订阅或尚未刷新的 StateFlow。
     */
    private suspend fun getCurrentVehicleFromDatabase(): Vehicle? {
        val allVehicles = vehicleDao.getAllVehicles()

        return allVehicles.firstOrNull { it.isCurrent }
            ?: currentVehicle.value?.let { current ->
                allVehicles.firstOrNull { it.id == current.id }
            }
            ?: allVehicles.firstOrNull()
    }

    private suspend fun requireVehicleExists(vehicleId: Long): Vehicle {
        require(vehicleId > 0L) {
            "车辆 ID 无效"
        }

        return vehicleDao.getVehicleById(vehicleId)
            ?: throw IllegalArgumentException("车辆不存在：$vehicleId")
    }

    // =========================================================
    // 车辆操作
    // =========================================================

    /**
     * 添加车辆并将其设为当前车辆。
     *
     * 保留原有回调签名。失败时记录日志，不会让协程异常直接崩溃。
     */
    fun addVehicle(
        vehicle: Vehicle,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val newId = vehicleDao.insertAsCurrent(
                    vehicle.copy(isCurrent = true)
                )

                val newVehicle = vehicle.copy(
                    id = newId,
                    isCurrent = true
                )

                _currentVehicleOverride.value = newVehicle
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "addVehicle failed", e)
            }
        }
    }

    /**
     * 切换当前车辆。
     */
    fun setCurrentVehicle(targetVehicle: Vehicle) {
        viewModelScope.launch {
            val previousOverride = _currentVehicleOverride.value

            try {
                val actualVehicle = vehicleDao.getVehicleById(targetVehicle.id)
                    ?: throw IllegalArgumentException(
                        "目标车辆不存在：${targetVehicle.id}"
                    )

                if (
                    actualVehicle.isCurrent &&
                    currentVehicle.value?.id == actualVehicle.id
                ) {
                    _currentVehicleOverride.value = null
                    return@launch
                }

                // 先更新 UI，减少切车延迟。
                _currentVehicleOverride.value = actualVehicle.copy(
                    isCurrent = true
                )

                vehicleDao.setCurrentVehicle(actualVehicle.id)

                /*
                 * 不在这里立即清空 override。
                 * 等数据库车辆流返回一致状态后，再由下方的同步逻辑清理。
                 */
                clearOverrideWhenDatabaseMatches(actualVehicle.id)
            } catch (e: Exception) {
                Log.e(TAG, "setCurrentVehicle failed", e)
                _currentVehicleOverride.value = previousOverride
            }
        }
    }

    /**
     * 当数据库确认目标车辆已成为当前车辆时，清理临时覆盖状态。
     */
    private suspend fun clearOverrideWhenDatabaseMatches(vehicleId: Long) {
        val actualVehicle = vehicleDao.getVehicleById(vehicleId) ?: return

        if (actualVehicle.isCurrent) {
            _currentVehicleOverride.value = null
        }
    }

    /**
     * 修改车辆信息。
     */
    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            val previousOverride = _currentVehicleOverride.value

            try {
                val oldVehicle = vehicleDao.getVehicleById(vehicle.id)
                    ?: throw IllegalArgumentException(
                        "车辆不存在：${vehicle.id}"
                    )

                val updatedVehicle = vehicle.copy(
                    isCurrent = oldVehicle.isCurrent
                )

                if (
                    currentVehicle.value?.id == vehicle.id ||
                    previousOverride?.id == vehicle.id
                ) {
                    _currentVehicleOverride.value = updatedVehicle
                }

                vehicleDao.update(updatedVehicle)
            } catch (e: Exception) {
                Log.e(TAG, "updateVehicle failed", e)
                _currentVehicleOverride.value = previousOverride
            }
        }
    }

    /**
     * 删除车辆及其加油、费用记录。
     *
     * 查询待接替车辆、删除关联数据和更新当前车辆均在同一个事务中完成。
     */
    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            val oldOverride = _currentVehicleOverride.value

            try {
                val result = database.withTransaction {
                    val actualVehicle = vehicleDao.getVehicleById(vehicle.id)
                        ?: return@withTransaction Pair(false, null)

                    val wasCurrent =
                        actualVehicle.isCurrent ||
                                currentVehicle.value?.id == actualVehicle.id ||
                                oldOverride?.id == actualVehicle.id

                    // 必须在事务内部查询候选车辆，避免使用事务外的过期数据。
                    val nextVehicle = if (wasCurrent) {
                        vehicleDao.getAnotherVehicle(actualVehicle.id)
                    } else {
                        null
                    }

                    fuelDao.deleteByVehicleId(actualVehicle.id)
                    expenseDao.deleteByVehicleId(actualVehicle.id)
                    vehicleDao.delete(actualVehicle)

                    if (wasCurrent && nextVehicle != null) {
                        vehicleDao.setCurrentVehicle(nextVehicle.id)
                    }

                    Pair(wasCurrent, nextVehicle)
                }

                val wasCurrent = result.first
                val nextVehicle = result.second

                if (wasCurrent) {
                    _currentVehicleOverride.value = nextVehicle?.copy(
                        isCurrent = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteVehicle failed", e)
                _currentVehicleOverride.value = oldOverride
            }
        }
    }

    // =========================================================
    // 加油记录操作
    // =========================================================

    fun addFuelRecord(
        record: FuelRecord,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                validateFuelRecord(record)

                val vehicle = getCurrentVehicleFromDatabase()
                    ?: throw IllegalStateException("没有当前车辆")

                val defaultFuelGrade = vehicle.fuelGrade
                val recordToInsert = record.copy(
                    id = 0L,
                    vehicleId = vehicle.id,
                    fuelGrade = record.fuelGrade.ifBlank {
                        defaultFuelGrade
                    }
                )

                fuelDao.insert(recordToInsert)

                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "addFuelRecord failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    fun updateFuelRecord(
        record: FuelRecord,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                require(record.id > 0L) {
                    "加油记录 ID 无效"
                }

                validateFuelRecord(record)

                val existing = fuelDao.getRecordById(record.id)
                    ?: throw IllegalArgumentException(
                        "加油记录不存在：${record.id}"
                    )

                // 防止编辑时意外把记录移动到另一辆车。
                require(record.vehicleId == existing.vehicleId) {
                    "不能通过编辑加油记录更改所属车辆"
                }

                requireVehicleExists(existing.vehicleId)

                fuelDao.update(record)

                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "updateFuelRecord failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    fun deleteRecord(record: FuelRecord) {
        viewModelScope.launch {
            try {
                val existing = fuelDao.getRecordById(record.id) ?: return@launch
                fuelDao.delete(existing)
            } catch (e: Exception) {
                Log.e(TAG, "deleteRecord failed", e)
            }
        }
    }

    // =========================================================
    // 费用操作
    // =========================================================

    /**
     * 新增费用。
     *
     * 激活停车记录通过 DAO 事务方法写入，避免先停用旧停车记录、
     * 后续插入失败导致当前停车状态丢失。
     */
    fun addExpense(
        record: ExpenseRecord,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val vehicle = getCurrentVehicleFromDatabase()
                    ?: throw IllegalStateException("没有当前车辆")

                require(record.timestamp > 0L) {
                    "费用时间无效"
                }

                requireFiniteNonNegative(record.amount, "费用金额")

                val recordToInsert = record.copy(
                    id = 0L,
                    vehicleId = vehicle.id
                )

                if (
                    recordToInsert.category == CATEGORY_PARKING &&
                    recordToInsert.isActive
                ) {
                    expenseDao.insertParkingAsActive(recordToInsert)
                } else {
                    expenseDao.insert(recordToInsert)
                }

                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "addExpense failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    fun updateExpense(
        record: ExpenseRecord,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                require(record.id > 0L) {
                    "费用记录 ID 无效"
                }

                require(record.timestamp > 0L) {
                    "费用时间无效"
                }

                requireFiniteNonNegative(record.amount, "费用金额")

                val existing = expenseDao.getById(record.id)
                    ?: throw IllegalArgumentException(
                        "费用记录不存在：${record.id}"
                    )

                // 编辑记录不允许改变所属车辆。
                require(record.vehicleId == existing.vehicleId) {
                    "不能通过编辑费用记录更改所属车辆"
                }

                requireVehicleExists(existing.vehicleId)

                if (
                    record.category == CATEGORY_PARKING &&
                    record.isActive
                ) {
                    expenseDao.updateParkingAsActive(record)
                } else {
                    val updatedRows = expenseDao.update(record)

                    if (updatedRows != 1) {
                        throw IllegalStateException(
                            "费用记录不存在或更新失败：${record.id}"
                        )
                    }
                }

                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "updateExpense failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    fun deleteExpense(record: ExpenseRecord) {
        viewModelScope.launch {
            try {
                val existing = expenseDao.getById(record.id) ?: return@launch
                expenseDao.delete(existing)
            } catch (e: Exception) {
                Log.e(TAG, "deleteExpense failed", e)
            }
        }
    }
    // =========================================================
    // 费用分类操作
    // =========================================================

    // 1. 改成小驼峰命名
    private val defaultExpenseCategories = listOf(
        "加油", "停车", "高速", "保养", "保险", "维修", "洗车", "其他"
    )

    /**
     * 重命名自定义分类。
     * 重点保障：前置校验必须与 ExpenseCategoryStore 内部规则绝对一致，
     * 避免 database.withTransaction 成功，但 Store 静默失败导致数据不一致。
     */
    fun renameExpenseCategory(
        oldName: String,
        newName: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            // 切换到 IO 线程，防止同步 commit 阻塞 UI
            val result = withContext(Dispatchers.IO) {
                try {
                    val trimmed = newName.trim()
                    val currentCustomList = ExpenseCategoryStore.getCustom()

                    // 1. 前置校验
                    // 修复 3：统一使用 ignoreCase 匹配原分类，消除规则差异
                    val existingOldName = currentCustomList.find { it.equals(oldName, ignoreCase = true) }
                    requireNotNull(existingOldName) { "原分类不存在" }

                    require(trimmed.isNotEmpty()) { "分类名称不能为空" }
                    require(!existingOldName.equals(trimmed, ignoreCase = true)) {
                        "分类名称没有变化或仅大小写不同"
                    }
                    require(defaultExpenseCategories.none { it.equals(trimmed, ignoreCase = true) }) {
                        "不能使用系统默认分类名称"
                    }
                    require(currentCustomList.none {
                        !it.equals(existingOldName, ignoreCase = true) && it.equals(trimmed, ignoreCase = true)
                    }) { "该分类名称已存在" }

                    // 2. 更新本地 SP
                    val updateSuccess = ExpenseCategoryStore.update(existingOldName, trimmed)
                    if (!updateSuccess) {
                        throw IllegalStateException("本地分类配置更新失败，可能重名或写入磁盘失败")
                    }

                    // 3. 更新数据库
                    try {
                        database.withTransaction {
                            expenseDao.renameCategory(existingOldName, trimmed)
                        }
                    } catch (e: CancellationException) {
                        // 修复 2：遇到协程取消，说明正在执行的 DB 事务被强制中断
                        // 必须尽力尝试把 SP 改回去，然后再重新抛出取消信号
                        Log.w(TAG, "协程取消：尝试恢复本地分类配置", e)
                        try {
                            ExpenseCategoryStore.update(trimmed, existingOldName)
                        } catch (rollbackError: Exception) {
                            rollbackError.addSuppressed(e)
                            Log.e(TAG, "严重错误：协程取消且本地分类恢复失败", rollbackError)
                        }
                        throw e as Throwable // 必须重新抛出，不能吞掉取消信号
                    } catch (e: Exception) {
                        Log.w(TAG, "数据库更新失败，尝试恢复本地分类配置", e)

                        // 4. 补偿回滚 SP
                        try {
                            val rollbackSuccess = ExpenseCategoryStore.update(trimmed, existingOldName)
                            if (!rollbackSuccess) {
                                throw IllegalStateException("补偿本地分类配置失败")
                            }
                        } catch (rollbackError: Exception) {
                            if (rollbackError is CancellationException) {
                                throw rollbackError
                            }
                            // 修复 1：将补偿异常作为 Suppressed 附加到原始数据库异常上，保留完整链路
                            e.addSuppressed(rollbackError)
                            Log.e(TAG, "严重错误：数据库更新失败，且本地分类配置恢复失败", e)
                        }
                        throw e
                    }

                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e as Throwable
                } catch (e: Exception) {
                    Log.e(TAG, "renameExpenseCategory failed", e)
                    Result.failure(e)
                }
            }

            // 回到主线程回调
            onComplete(result)
        }
    }

    /**
     * 用户点击“已取车”时调用。
     */
    fun clearActiveParking() {
        viewModelScope.launch {
            try {
                val vehicle = getCurrentVehicleFromDatabase()
                    ?: return@launch

                expenseDao.deactivateAllParking(vehicle.id)
            } catch (e: Exception) {
                Log.e(TAG, "clearActiveParking failed", e)
            }
        }
    }

    /**
     * 用户点击“忽略”时调用。
     * 保留停车卡片，仅标记通知已忽略。
     */
    fun ignoreActiveParkingNotification() {
        viewModelScope.launch {
            try {
                val vehicle = getCurrentVehicleFromDatabase()
                    ?: return@launch

                val parking = expenseDao.getActiveParking(vehicle.id)
                    ?: return@launch

                expenseDao.markNotificationIgnored(parking.id)
            } catch (e: Exception) {
                Log.e(TAG, "ignoreActiveParkingNotification failed", e)
            }
        }
    }

    /**
     * 新增初始记录。
     *
     * 初始记录允许加油量、金额、单价为 0，
     * 用于建立后续油耗计算的起点。
     */
    fun addInitialRecord(
        odometer: Double?,
        currentFuel: Double?
    ) {
        viewModelScope.launch {
            try {
                val vehicle = getCurrentVehicleFromDatabase()
                    ?: return@launch

                requireNullableFiniteNonNegative(odometer, "初始里程")
                requireNullableFiniteNonNegative(currentFuel, "当前油量")

                fuelDao.insert(
                    FuelRecord(
                        vehicleId = vehicle.id,
                        fuelGrade = vehicle.fuelGrade,
                        timestamp = System.currentTimeMillis(),
                        odometer = odometer,
                        volume = 0.0,
                        actualPaidAmount = 0.0,
                        unitPrice = 0.0,
                        remainingFuel = currentFuel,
                        isInitialRecord = true,
                        initialFuel = currentFuel
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "addInitialRecord failed", e)
            }
        }
    }

    // =========================================================
    // 备份 / 恢复
    // =========================================================

    /**
     * 导出备份到指定 Uri。
     */
    fun createBackup(
        uri: Uri,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (isBackupOperationRunning) {
            onResult(
                Result.failure(
                    IllegalStateException("备份或恢复操作正在进行")
                )
            )
            return
        }

        isBackupOperationRunning = true

        viewModelScope.launch {
            try {
                BackupManager.writeBackupToUri(
                    getApplication(),
                    uri
                )

                onResult(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "createBackup failed", e)
                onResult(Result.failure(e))
            } finally {
                isBackupOperationRunning = false
            }
        }
    }

    /**
     * 从指定 Uri 恢复备份。
     */
    fun restoreBackup(
        uri: Uri,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (isBackupOperationRunning) {
            onResult(
                Result.failure(
                    IllegalStateException("备份或恢复操作正在进行")
                )
            )
            return
        }

        isBackupOperationRunning = true

        viewModelScope.launch {
            try {
                BackupManager.restoreFromUri(
                    getApplication(),
                    uri
                )

                /*
                 * 恢复操作可能替换了全部车辆。
                 * 清除临时状态，避免旧车辆覆盖恢复后的数据库状态。
                 */
                _currentVehicleOverride.value = null

                onResult(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "restoreBackup failed", e)
                onResult(Result.failure(e))
            } finally {
                isBackupOperationRunning = false
            }
        }
    }

    /**
     * 检查 Uri 是否是 JSON 文件。
     */
    fun checkIsJsonFile(
        uri: Uri,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val isJson = try {
                withContext(Dispatchers.IO) {
                    isJsonFileUri(getApplication(), uri)
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkIsJsonFile failed", e)
                false
            }

            onResult(isJson)
        }
    }

    // =========================================================
    // 加油记录导入
    // =========================================================

    /**
     * 导入到当前车辆。
     *
     * 去重条件沿用原逻辑：
     * 时间戳、里程、实际支付金额均相同则视为重复。
     *
     * 使用数据库现有记录，而不是 records.value；
     * 本次导入中的新记录也会加入去重集合。
     */
    fun importFuelRecords(
        recordsToImport: List<FuelRecord>,
        onResult: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val importedCount = database.withTransaction {
                    val vehicle = getCurrentVehicleFromDatabase()
                        ?: throw IllegalStateException("没有当前车辆")

                    val existingRecords =
                        fuelDao.getRecordsByVehicleId(vehicle.id)

                    val seen = existingRecords
                        .map {
                            Triple(
                                it.timestamp,
                                it.odometer,
                                it.actualPaidAmount
                            )
                        }
                        .toMutableSet()

                    var count = 0

                    recordsToImport.forEach { source ->
                        validateFuelRecord(source)

                        val key = Triple(
                            source.timestamp,
                            source.odometer,
                            source.actualPaidAmount
                        )

                        if (seen.add(key)) {
                            val recordToInsert = source.copy(
                                id = 0L,
                                vehicleId = vehicle.id,
                                fuelGrade = source.fuelGrade.ifBlank {
                                    vehicle.fuelGrade
                                }
                            )

                            fuelDao.insert(recordToInsert)
                            count++
                        }
                    }

                    count
                }

                onResult(Result.success(importedCount))
            } catch (e: Exception) {
                Log.e(TAG, "导入加油记录失败", e)
                onResult(Result.failure(e))
            }
        }
    }

    // =========================================================
    // 费用记录导入
    // =========================================================

    /**
     * 导入到当前车辆。
     *
     * 去重条件沿用原逻辑：
     * 时间戳、金额、分类均相同则视为重复。
     */
    fun importExpenseRecords(
        recordsToImport: List<ExpenseRecord>,
        onResult: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val importedCount = database.withTransaction {
                    val vehicle = getCurrentVehicleFromDatabase()
                        ?: throw IllegalStateException("没有当前车辆")

                    val existingRecords = expenseDao.getAll()
                        .filter { it.vehicleId == vehicle.id }

                    val seen = existingRecords
                        .map {
                            Triple(
                                it.timestamp,
                                it.amount,
                                it.category
                            )
                        }
                        .toMutableSet()

                    var count = 0

                    recordsToImport.forEach { source ->
                        require(source.timestamp > 0L) {
                            "费用时间无效"
                        }
                        requireFiniteNonNegative(source.amount, "费用金额")

                        val key = Triple(
                            source.timestamp,
                            source.amount,
                            source.category
                        )

                        if (seen.add(key)) {
                            val recordToInsert = source.copy(
                                id = 0L,
                                vehicleId = vehicle.id
                            )

                            if (
                                recordToInsert.category == CATEGORY_PARKING &&
                                recordToInsert.isActive
                            ) {
                                expenseDao.insertParkingAsActive(
                                    recordToInsert
                                )
                            } else {
                                expenseDao.insert(recordToInsert)
                            }

                            count++
                        }
                    }

                    count
                }

                onResult(Result.success(importedCount))
            } catch (e: Exception) {
                Log.e(TAG, "导入费用记录失败", e)
                onResult(Result.failure(e))
            }
        }
    }

    // =========================================================
    // Uri 辅助方法
    // =========================================================

    private fun isJsonFileUri(
        context: Context,
        uri: Uri
    ): Boolean {
        val displayName = try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(
                    android.provider.OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "query displayName failed", e)
            null
        }

        return displayName
            ?.endsWith(".json", ignoreCase = true) == true
    }
}