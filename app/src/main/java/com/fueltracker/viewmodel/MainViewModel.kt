package com.fueltracker.viewmodel

import android.app.Application
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
import com.fueltracker.data.ExpenseDao
import com.fueltracker.data.ExpenseRecord
import com.fueltracker.data.FuelRecord
import com.fueltracker.data.Vehicle
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.StatisticsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import android.content.Context
import com.fueltracker.util.BackupManager
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val vehicleDao = database.vehicleDao()
    private val fuelDao = database.fuelDao()
    private val expenseDao: ExpenseDao = database.expenseDao()

    // 内存即时临时状态（用于消除切车/加车时的 UI 延迟）
    private val _currentVehicleOverride = MutableStateFlow<Vehicle?>(null)

    // StateFlow 统一订阅超时策略（切后台 5 秒后切断 DB 监听，节省系统资源）
    private val stopTimeout = 5_000L

    // =========================
    // 导航状态（配置变更时保留）
    // =========================

    /** 当前显示的页面 */
    var currentScreen by mutableStateOf(AppScreen.HOME)
        private set

    /** 费用页选中的分类（null = 不筛选） */
    var expenseSelectedCategory by mutableStateOf<String?>(null)
        private set

    /** 编辑加油的"来源"页面，返回时回到这里 */
    var editFuelOrigin by mutableStateOf(AppScreen.RECORDS)
        private set

    /** 编辑费用的"来源"页面（EDIT_EXPENSE 回 EXPENSES，EDIT_PARKING 回 HOME） */
    var editExpenseOrigin by mutableStateOf(AppScreen.EXPENSES)
        private set

    /** 添加车辆的来源页面（首页 or 设置页） */
    var addVehicleOrigin by mutableStateOf(AppScreen.HOME)
        private set

    /** 当前正在编辑的加油记录 */
    var editingRecord by mutableStateOf<FuelRecord?>(null)
        private set

    /** 当前正在编辑的车辆 */
    var editingVehicle by mutableStateOf<Vehicle?>(null)
        private set

    /** 当前正在编辑的费用记录（含停车） */
    var editingExpense by mutableStateOf<ExpenseRecord?>(null)
        private set

    /** 备份/恢复正在运行 */
    var isBackupOperationRunning by mutableStateOf(false)
        private set

    // 控制底栏和悬浮按钮是否显示
    var isBottomBarVisible by mutableStateOf(true)
        private set

    fun updateBottomBarVisibility(visible: Boolean) {
        if (isBottomBarVisible != visible) {
            isBottomBarVisible = visible
        }
    }

    // ---------- 导航方法 ----------

    fun navigateTo(screen: AppScreen) {
        currentScreen = screen
        // 切换页面时，强制显示底栏和 FAB
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


    // =========================
    // 数据流
    // =========================

    // 所有已添加的车辆列表
    val vehicles: StateFlow<List<Vehicle>> = vehicleDao
        .observeAllVehicles()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout),
            emptyList()
        )

    // 当前选中的车辆
    val currentVehicle: StateFlow<Vehicle?> = combine(
        vehicleDao.observeCurrentVehicle(),
        _currentVehicleOverride,
        vehicles
    ) { dbVehicle: Vehicle?, overrideVehicle: Vehicle?, allVehicles: List<Vehicle> ->
        val resolved = overrideVehicle ?: dbVehicle
        ?: allVehicles.find { it.isCurrent }
        ?: allVehicles.firstOrNull()

        // 当数据库返回的新状态与内存 Override 的 ID 相符时，清除 Override，恢复单源真理 (SSOT)
        if (overrideVehicle != null && dbVehicle?.id == overrideVehicle.id) {
            _currentVehicleOverride.value = null
        }

        resolved
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeout),
        null
    )

    // 加油记录（依据当前选中的车辆动态响应）
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
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout),
            emptyList()
        )

    // 费用记录
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
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout),
            emptyList()
        )

    /**
     * 当前激活的停车记录（最多一条），首页"当前车位"卡片用。
     */
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
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout),
            null
        )

    // =========================
    // 油耗计算 StateFlow
    // =========================

    /**
     * 完整统计指标汇总对象（提供给 UI 统计大卡片使用）
     */
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
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout),
            null
        )

    /**
     * 最新油耗（优先返回非离群、置信度不低于 LOW 的真实油耗）
     */
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
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout),
            null
        )

    /**
     * 平均油耗
     */
    val averageConsumption: StateFlow<Double?> = statisticsSummary
        .map { it?.averageConsumption }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout),
            null
        )

    // =========================
    // 车辆操作
    // =========================

    /**
     * 添加新车辆并将新车辆设为当前车辆
     */
    fun addVehicle(vehicle: Vehicle, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val newId = vehicleDao.insertAsCurrent(vehicle)
            val newVehicle = vehicle.copy(id = newId, isCurrent = true)
            _currentVehicleOverride.value = newVehicle
            onComplete?.invoke()
        }
    }

    /**
     * 切换当前激活的车辆
     */
    fun setCurrentVehicle(targetVehicle: Vehicle) {
        if (targetVehicle.isCurrent || currentVehicle.value?.id == targetVehicle.id) return
        viewModelScope.launch {
            _currentVehicleOverride.value = targetVehicle.copy(isCurrent = true)
            vehicleDao.setCurrentVehicle(targetVehicle.id)
        }
    }

    /**
     * 修改/更新车辆基本信息
     */
    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            if (currentVehicle.value?.id == vehicle.id || _currentVehicleOverride.value?.id == vehicle.id) {
                _currentVehicleOverride.value = vehicle
            }
            vehicleDao.update(vehicle)
        }
    }

    /**
     * 删除车辆及其关联的所有加油记录
     */
    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            val isDeletingCurrent = (currentVehicle.value?.id == vehicle.id) ||
                    (_currentVehicleOverride.value?.id == vehicle.id) ||
                    vehicle.isCurrent

            // 先把下一辆候选拿出来（事务外查询，减少锁窗口）
            val nextVehicle = if (isDeletingCurrent) {
                vehicleDao.getAnotherVehicle(vehicle.id)
            } else {
                null
            }

            // 删除 + 切换 current，放在一个事务里
            database.withTransaction {
                fuelDao.deleteByVehicleId(vehicle.id)
                expenseDao.deleteByVehicleId(vehicle.id)
                vehicleDao.delete(vehicle)

                if (isDeletingCurrent && nextVehicle != null) {
                    vehicleDao.setCurrentVehicle(nextVehicle.id)
                }
            }

            // 事务提交后再同步内存 override
            if (isDeletingCurrent) {
                _currentVehicleOverride.value = nextVehicle?.copy(isCurrent = true)
            }
        }
    }

    // =========================
    // 加油记录操作
    // =========================

    fun addFuelRecord(
        record: FuelRecord,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val vehicle = currentVehicle.value
                    ?: throw IllegalStateException("没有当前车辆")

                val defaultFuelGrade = vehicle.fuelGrade
                fuelDao.insert(
                    record.copy(
                        vehicleId = vehicle.id,
                        fuelGrade = record.fuelGrade.ifBlank { defaultFuelGrade }
                    )
                )
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("MainViewModel", "addFuelRecord failed", e)
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
                fuelDao.update(record)
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("MainViewModel", "updateFuelRecord failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    fun deleteRecord(record: FuelRecord) {
        viewModelScope.launch {
            fuelDao.delete(record)
        }
    }

    // =========================
    // 费用操作
    // =========================

    /**
     * 添加费用记录。
     * 停车且 isActive=true 时，会先把该车之前的激活停车记录清掉。
     */
    fun addExpense(
        record: ExpenseRecord,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val vehicle = currentVehicle.value
                    ?: throw IllegalStateException("没有当前车辆")

                if (record.category == CATEGORY_PARKING && record.isActive) {
                    expenseDao.deactivateAllParking(vehicle.id)
                }

                expenseDao.insert(record.copy(vehicleId = vehicle.id))
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("MainViewModel", "addExpense failed", e)
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
                expenseDao.update(record)
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("MainViewModel", "updateExpense failed", e)
                onComplete(Result.failure(e))
            }
        }
    }

    fun deleteExpense(record: ExpenseRecord) {
        viewModelScope.launch {
            expenseDao.delete(record)
        }
    }

    /**
     * 用户点"已取车"时调用。
     */
    fun clearActiveParking() {
        viewModelScope.launch {
            val vehicle = currentVehicle.value ?: return@launch
            expenseDao.deactivateAllParking(vehicle.id)
        }
    }

    /**
     * 用户点"忽略"时调用。
     * 保留停车卡片，只标记通知已忽略（下次不再发通知）。
     */
    fun ignoreActiveParkingNotification() {
        viewModelScope.launch {
            val vehicle = currentVehicle.value ?: return@launch
            val parking = expenseDao.getActiveParking(vehicle.id) ?: return@launch
            expenseDao.markNotificationIgnored(parking.id)
        }
    }

    fun addInitialRecord(
        odometer: Double?,
        currentFuel: Double?
    ) {
        viewModelScope.launch {
            val vehicle = currentVehicle.value ?: return@launch
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
        }
    }
    // =========================
    // 备份 / 恢复
    // =========================

    /**
     * 导出备份到指定 Uri。
     * 完成后通过 onResult 回调结果（UI 层负责弹 Toast）。
     */
    fun createBackup(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        if (isBackupOperationRunning) return
        isBackupOperationRunning = true

        viewModelScope.launch {
            try {
                BackupManager.writeBackupToUri(getApplication(), uri)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("MainViewModel", "createBackup failed", e)
                onResult(Result.failure(e))
            } finally {
                isBackupOperationRunning = false
            }
        }
    }

    /**
     * 从指定 Uri 恢复备份。
     */
    fun restoreBackup(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        if (isBackupOperationRunning) return
        isBackupOperationRunning = true

        viewModelScope.launch {
            try {
                BackupManager.restoreFromUri(getApplication(), uri)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("MainViewModel", "restoreBackup failed", e)
                onResult(Result.failure(e))
            } finally {
                isBackupOperationRunning = false
            }
        }
    }

    /**
     * 检查 Uri 是否是 JSON 文件（在 IO 线程）。
     */
    fun checkIsJsonFile(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val isJson = withContext(Dispatchers.IO) {
                isJsonFileUri(getApplication(), uri)
            }
            onResult(isJson)
        }
    }

    fun importFuelRecords(
        recordsToImport: List<FuelRecord>, // 为了避免重名，把参数名改一下
        onResult: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 直接拿当前车辆已有的记录进行对比
                val existingRecords = records.value
                var importCount = 0

                recordsToImport.forEach { newRecord ->
                    // 去重规则：时间戳相同，且里程相同，且实际支付金额相同
                    val isDuplicate = existingRecords.any {
                        it.timestamp == newRecord.timestamp &&
                                it.odometer == newRecord.odometer &&
                                it.actualPaidAmount == newRecord.actualPaidAmount
                    }

                    if (!isDuplicate) {
                        fuelDao.insert(newRecord)
                        importCount++
                    }
                }
                // 返回实际插入的条数
                onResult(Result.success(importCount))
            } catch (e: Exception) {
                Log.e("MainViewModel", "导入加油记录失败", e)
                onResult(Result.failure(e))
            }
        }
    }

    fun importExpenseRecords(
        recordsToImport: List<ExpenseRecord>,
        onResult: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 直接拿当前车辆已有的费用记录进行对比
                val existingRecords = expenses.value
                var importCount = 0

                recordsToImport.forEach { newRecord ->
                    // 去重规则：时间戳相同，且金额相同，且分类相同
                    val isDuplicate = existingRecords.any {
                        it.timestamp == newRecord.timestamp &&
                                it.amount == newRecord.amount &&
                                it.category == newRecord.category
                    }

                    if (!isDuplicate) {
                        expenseDao.insert(newRecord)
                        importCount++
                    }
                }
                onResult(Result.success(importCount))
            } catch (e: Exception) {
                Log.e("MainViewModel", "导入费用记录失败", e)
                onResult(Result.failure(e))
            }
        }
    }

    private fun isJsonFileUri(context: Context, uri: Uri): Boolean {
        val displayName = try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "query displayName failed", e)
            null
        }
        return displayName?.endsWith(".json", ignoreCase = true) == true
    }
}
