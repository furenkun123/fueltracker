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
import com.fueltracker.data.*
import com.fueltracker.util.BackupManager
import com.fueltracker.util.FuelCalculator
import com.fueltracker.util.StatisticsSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val vehicleDao = database.vehicleDao()
    private val fuelDao = database.fuelDao()
    private val expenseDao: ExpenseDao = database.expenseDao()

    private data class ParkingSource(
        val vehicle: Vehicle?,
        val ready: Boolean
    )

    companion object {
        private const val TAG = "MainViewModel"
        private const val STOP_TIMEOUT = 5_000L
    }

    private val _currentVehicleOverride = MutableStateFlow<Vehicle?>(null)

    // ========== 首次真实数据到达标记 ==========
// false: 数据库首次查询还没 emit，禁止任何通知操作（防重启误取消）
// true : 至少拿到过一次真实列表，后续 vehicle==null 才代表"真无车"
    private val dataReady = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                vehicleDao.observeAllVehicles().first()
                dataReady.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "observeAllVehicles first failed", e)
                // 降级：标记就绪，避免永远 NotReady 导致通知永不更新
                dataReady.value = true
            }
        }
    }

    // =========================================================
    // 导航与页面状态
    // =========================================================
    var currentScreen by mutableStateOf(AppScreen.HOME); private set
    var expenseSelectedCategory by mutableStateOf<String?>(null); private set
    var editFuelOrigin by mutableStateOf(AppScreen.RECORDS); private set
    var editExpenseOrigin by mutableStateOf(AppScreen.EXPENSES); private set
    var addVehicleOrigin by mutableStateOf(AppScreen.HOME); private set
    var editingRecord by mutableStateOf<FuelRecord?>(null); private set
    var editingVehicle by mutableStateOf<Vehicle?>(null); private set
    var editingExpense by mutableStateOf<ExpenseRecord?>(null); private set
    var isBackupOperationRunning by mutableStateOf(false); private set
    var isBottomBarVisible by mutableStateOf(true); private set

    // 控制底栏样式的状态（默认 true 悬浮）
    var isFloatingBottomBar by mutableStateOf(true)
        private set

    fun toggleFloatingBottomBar() {
        isFloatingBottomBar = !isFloatingBottomBar
    }

    //液态玻璃效果
    var isGlassEffectEnabled by mutableStateOf(true) // 默认开启
        private set

    fun toggleGlassEffect() {
        isGlassEffectEnabled = !isGlassEffectEnabled
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

    fun editExpenseRecord(record: ExpenseRecord, targetScreen: AppScreen, origin: AppScreen) {
        editingExpense = record
        editExpenseOrigin = origin
        currentScreen = targetScreen
    }

    fun navigateToAddVehicle(origin: AppScreen) {
        addVehicleOrigin = origin
        currentScreen = AppScreen.ADD_VEHICLE
    }

    // 👇 控制右下角悬浮加号（FAB）的显隐
    var isFabVisible by mutableStateOf(true)
        private set

    fun updateFabVisibility(visible: Boolean) {
        if (isFabVisible != visible) isFabVisible = visible
    }

    fun clearEditingStates() {
        editingRecord = null
        editingVehicle = null
        editingExpense = null
    }

    // =========================================================
    // 数据流状态
    // =========================================================
    val vehicles: StateFlow<List<Vehicle>> = vehicleDao.observeAllVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val currentVehicle: StateFlow<Vehicle?> = combine(
        vehicleDao.observeCurrentVehicle(),
        _currentVehicleOverride,
        vehicles
    ) { dbVehicle, overrideVehicle, allVehicles ->
        overrideVehicle ?: dbVehicle ?: allVehicles.firstOrNull { it.isCurrent } ?: allVehicles.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<FuelRecord>> = currentVehicle
        .flatMapLatest { vehicle -> if (vehicle == null) flowOf(emptyList()) else fuelDao.observeRecords(vehicle.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseRecord>> = currentVehicle
        .flatMapLatest { vehicle -> if (vehicle == null) flowOf(emptyList()) else expenseDao.observeByVehicle(vehicle.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeParking: StateFlow<ExpenseRecord?> = currentVehicle
        .flatMapLatest { vehicle -> if (vehicle == null) flowOf(null) else expenseDao.observeActiveParking(vehicle.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    // =========================================================
// 停车通知同步（唯一入口）
//
// 关键点：
// - 用底层 DAO Flow 组合，避免 currentVehicle/activeParking
//   的 stateIn 初始值 null 造成"误判为无停车"
// - stateIn 初始值为 NotReady，首次真实数据到达前
//   UI 层不会执行任何 cancel/show
// - 切换车辆、重启、DB 增删改，都通过此流自动同步
// =========================================================
    @OptIn(ExperimentalCoroutinesApi::class)
    val parkingNotificationState: StateFlow<ParkingNotificationState> =
        combine(
            vehicleDao.observeCurrentVehicle(),
            _currentVehicleOverride,
            vehicleDao.observeAllVehicles(),
            dataReady
        ) { dbVehicle, overrideVehicle, allVehicles, ready ->
            ParkingSource(
                vehicle = overrideVehicle ?: dbVehicle
                ?: allVehicles.firstOrNull { it.isCurrent }
                ?: allVehicles.firstOrNull(),
                ready = ready
            )
        }
            .distinctUntilChanged()
            .flatMapLatest { source ->
                when {
                    // ① 首次真实数据未到达：绝不操作通知
                    !source.ready ->
                        flowOf<ParkingNotificationState>(ParkingNotificationState.NotReady)

                    // ② 已就绪但确实无车（首次安装 / 删除所有车辆）
                    source.vehicle == null ->
                        flowOf<ParkingNotificationState>(ParkingNotificationState.Hidden)

                    // ③ 有车：观察其停车记录
                    else ->
                        expenseDao.observeActiveParking(source.vehicle.id).map { parking ->
                            if (parking != null && parking.isActive && !parking.notificationIgnored) {
                                ParkingNotificationState.Visible(
                                    vehicleId = source.vehicle.id,
                                    locationName = parking.locationName,
                                    floor = parking.floor,
                                    area = parking.area,
                                    spotNumber = parking.spotNumber
                                )
                            } else {
                                ParkingNotificationState.Hidden
                            }
                        }
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT),
                ParkingNotificationState.NotReady
            )
    // =========================================================
    // 油耗计算状态
    // =========================================================
    @OptIn(ExperimentalCoroutinesApi::class)
    val statisticsSummary: StateFlow<StatisticsSummary?> = currentVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(null)
            else fuelDao.observeRecords(vehicle.id).map { list ->
                if (list.isEmpty()) null else FuelCalculator.calculateSummary(allRecords = list, vehicle = vehicle)
            }
        }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestConsumption: StateFlow<Double?> = currentVehicle
        .flatMapLatest { vehicle ->
            if (vehicle == null) flowOf(null)
            else fuelDao.observeRecords(vehicle.id).map { list ->
                FuelCalculator.latestReliableConsumption(records = list, vehicle = vehicle)
            }
        }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val averageConsumption: StateFlow<Double?> = statisticsSummary
        .map { it?.averageConsumption }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    // =========================================================
    // 核心数据校验 (业务规则闭环)
    // =========================================================
    private fun requireNonNegative(value: Double, fieldName: String) {
        require(value.isFinite() && value >= 0.0) { "${fieldName}必须是有效的非负数" }
    }

    private fun requireNullableNonNegative(value: Double?, fieldName: String) {
        if (value != null) requireNonNegative(value, fieldName)
    }

    private fun validateFuelRecord(record: FuelRecord) {
        require(record.timestamp > 0L) { "加油时间无效" }

        requireNullableNonNegative(record.odometer, "里程")
        requireNullableNonNegative(record.remainingFuel, "剩余油量")
        requireNullableNonNegative(record.initialFuel, "初始油量")
        requireNullableNonNegative(record.consumption, "油耗")

        if (record.isInitialRecord) {
            require(record.volume == 0.0) { "初始化记录的加油量必须为 0" }
            require(record.unitPrice == 0.0) { "初始化记录的单价必须为 0" }
            require(record.actualPaidAmount == 0.0) { "初始化记录的金额必须为 0" }
        } else {
            require(record.volume > 0.0) { "加油量必须大于 0" }
            require(record.unitPrice > 0.0) { "单价必须大于 0" }
            requireNonNegative(record.actualPaidAmount, "实付金额")
        }

        if (record.hasMissedRecord) {
            require(record.missedOdometer != null && record.missedOdometer.isFinite() && record.missedOdometer > 0.0) { "漏记里程必须大于 0" }
            require(record.missedVolume != null && record.missedVolume.isFinite() && record.missedVolume > 0.0) { "漏记加油量必须大于 0" }
            if (record.odometer != null) {
                require(record.missedOdometer < record.odometer) { "漏记里程必须小于当前里程" }
            }
        } else {
            require(record.missedOdometer == null && record.missedVolume == null) { "未勾选漏记时，漏记字段必须为空" }
        }

        requireNullableNonNegative(record.totalAmount, "原价")
        requireNullableNonNegative(record.discount, "优惠金额")

        val total = record.totalAmount
        val disc = record.discount
        val paid = record.actualPaidAmount

        if (total != null) {
            if (disc != null) {
                require(disc <= total) { "优惠金额不能超过原价" }
                require(abs((total - disc) - paid) <= 0.05) { "原价减去优惠金额应等于实付金额" }
            } else {
                require(abs(total - paid) <= 0.05) { "原价应等于实付金额" }
            }

            if (!record.isInitialRecord) {
                val expectedTotal = record.volume * record.unitPrice
                require(abs(expectedTotal - total) <= 0.1) { "加油量乘以单价应近似等于原价" }
            }
        }
    }

    private fun validateExpenseRecord(record: ExpenseRecord) {
        require(record.timestamp > 0L) { "费用时间无效" }
        require(record.category.isNotBlank()) { "费用分类不能为空" }
        require(record.amount.isFinite() && record.amount >= 0.0) { "费用金额必须是有效的非负数" }

        if (record.category != CATEGORY_PARKING) {
            require(!record.isActive) { "非停车分类不能设置为激活状态" }
        }
    }

    private suspend fun getCurrentVehicleFromDatabase(): Vehicle? {
        val allVehicles = vehicleDao.getAllVehicles()
        return allVehicles.firstOrNull { it.isCurrent } ?: allVehicles.firstOrNull()
    }

    private suspend fun requireVehicleExists(vehicleId: Long): Vehicle {
        require(vehicleId > 0L) { "车辆 ID 无效" }
        val vehicle = vehicleDao.getVehicleById(vehicleId) ?: throw IllegalArgumentException("车辆不存在：$vehicleId")

        // 👇 兜底：如果数据库里查出这辆车的油箱容量已经是 Infinity，直接拦截
        require(vehicle.fuelType != "纯电动") { "当前版本不支持纯电动车辆" }
        require(vehicle.tankCapacity.isFinite() && vehicle.tankCapacity > 0.0) {
            "该车辆的油箱容量数据异常，请先前往设置编辑车辆修正"
        }
        return vehicle
    }

    // =========================================================
    // 车辆操作
    // =========================================================
    fun addVehicle(
        vehicle: Vehicle,
        currentOdometer: Double,
        currentFuel: Double?,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = try {
                require(vehicle.brand.isNotBlank() && vehicle.model.isNotBlank()) { "车辆品牌和车型不能为空" }
                require(vehicle.fuelType.isNotBlank()) { "能源类型不能为空" }
                require(currentOdometer.isFinite() && currentOdometer >= 0.0) { "当前里程必须是有效的非负数" }

                require(vehicle.fuelType != "纯电动") { "当前版本不支持纯电动车辆" }
                require(vehicle.fuelGrade.isNotBlank()) { "燃油标号不能为空" }
                require(vehicle.tankCapacity.isFinite() && vehicle.tankCapacity > 0.0) {
                    "油箱容量必须大于 0 的有限数值"
                }
                require(currentFuel != null && currentFuel.isFinite() &&
                        currentFuel >= 0.0 && currentFuel <= vehicle.tankCapacity) {
                    "当前剩余油量必须填写，且不能超过油箱容量"
                }

                val insertedVehicle = vehicle.copy(isCurrent = true)
                val newId = database.withTransaction {
                    val id = vehicleDao.insertAsCurrent(insertedVehicle)
                    val initialTimestamp = maxOf(
                        System.currentTimeMillis(),
                        insertedVehicle.deliveryDate ?: 0L
                    )
                    fuelDao.insert(
                        FuelRecord(
                            vehicleId = id,
                            timestamp = initialTimestamp,
                            fuelGrade = insertedVehicle.fuelGrade,
                            odometer = currentOdometer,
                            volume = 0.0,
                            actualPaidAmount = 0.0,
                            unitPrice = 0.0,
                            isInitialRecord = true,
                            initialFuel = currentFuel,
                            note = "车辆初始记录"
                        )
                    )
                    id
                }

                _currentVehicleOverride.value = insertedVehicle.copy(id = newId)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "addVehicle failed", e)
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun setCurrentVehicle(targetVehicle: Vehicle) {
        viewModelScope.launch {
            val prev = _currentVehicleOverride.value
            try {
                val actual = vehicleDao.getVehicleById(targetVehicle.id) ?: throw IllegalArgumentException("目标车辆不存在")
                if (actual.isCurrent && currentVehicle.value?.id == actual.id) {
                    _currentVehicleOverride.value = null
                    return@launch
                }
                _currentVehicleOverride.value = actual.copy(isCurrent = true)
                vehicleDao.setCurrentVehicle(actual.id)
                if (vehicleDao.getVehicleById(actual.id)?.isCurrent == true) _currentVehicleOverride.value = null
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "setCurrentVehicle failed", e)
                _currentVehicleOverride.value = prev
            }
        }
    }

    fun updateVehicle(vehicle: Vehicle, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val prev = _currentVehicleOverride.value
            val result = try {
                require(vehicle.brand.isNotBlank() && vehicle.model.isNotBlank()) { "车辆品牌和车型不能为空" }
                require(vehicle.fuelType.isNotBlank()) { "能源类型不能为空" }

                require(vehicle.fuelType != "纯电动") { "当前版本不支持纯电动车辆" }
                require(vehicle.fuelGrade.isNotBlank()) { "燃油标号不能为空" }
                require(vehicle.tankCapacity.isFinite() && vehicle.tankCapacity > 0.0) { "油箱容量必须大于 0 的有限数值" }

                val old = vehicleDao.getVehicleById(vehicle.id) ?: throw IllegalArgumentException("车辆不存在")
                val updated = vehicle.copy(isCurrent = old.isCurrent)
                if (currentVehicle.value?.id == vehicle.id || prev?.id == vehicle.id) {
                    _currentVehicleOverride.value = updated
                }
                if (vehicleDao.update(updated) != 1) {
                    throw IllegalStateException("更新失败，车辆可能已被删除")
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "updateVehicle failed", e)
                _currentVehicleOverride.value = prev
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            val oldOverride = _currentVehicleOverride.value
            try {
                val (wasCurrent, next) = database.withTransaction {
                    val actual = vehicleDao.getVehicleById(vehicle.id) ?: return@withTransaction Pair(false, null)
                    val isCur = actual.isCurrent || currentVehicle.value?.id == actual.id || oldOverride?.id == actual.id
                    val nextV = if (isCur) vehicleDao.getAnotherVehicle(actual.id) else null
                    fuelDao.deleteByVehicleId(actual.id)
                    expenseDao.deleteByVehicleId(actual.id)
                    vehicleDao.delete(actual)
                    if (isCur && nextV != null) vehicleDao.setCurrentVehicle(nextV.id)
                    Pair(isCur, nextV)
                }
                if (wasCurrent) _currentVehicleOverride.value = next?.copy(isCurrent = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteVehicle failed", e)
                _currentVehicleOverride.value = oldOverride
            }
        }
    }

    // =========================================================
    // 加油记录操作
    // =========================================================
    fun addFuelRecord(record: FuelRecord, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                validateFuelRecord(record)
                val vehicle = getCurrentVehicleFromDatabase() ?: throw IllegalStateException("没有当前车辆")
                val targetVehicleId = vehicle.id
                requireVehicleExists(targetVehicleId)

                fuelDao.insert(
                    record.copy(id = 0L, vehicleId = targetVehicleId, fuelGrade = record.fuelGrade.ifBlank { vehicle.fuelGrade })
                )
                Result.success(Unit)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "addFuelRecord failed", e)
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun updateFuelRecord(record: FuelRecord, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                require(record.id > 0L) { "加油记录 ID 无效" }
                validateFuelRecord(record)

                val existing = fuelDao.getRecordById(record.id) ?: throw IllegalArgumentException("记录不存在")
                require(record.vehicleId == existing.vehicleId) { "不能通过编辑更改所属车辆" }
                requireVehicleExists(existing.vehicleId)

                // 👇 DAO 必须返回 Int
                if (fuelDao.update(record) != 1) {
                    throw IllegalStateException("更新失败，记录可能已被删除")
                }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "updateFuelRecord failed", e)
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun deleteRecord(record: FuelRecord, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                val existing = fuelDao.getRecordById(record.id) ?: throw IllegalArgumentException("记录不存在")
                // 👇 DAO 必须返回 Int
                if (fuelDao.delete(existing) != 1) {
                    throw IllegalStateException("删除失败")
                }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteRecord failed", e)
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    // =========================================================
    // 费用操作
    // =========================================================
    fun addExpense(record: ExpenseRecord, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                val vehicle = getCurrentVehicleFromDatabase() ?: throw IllegalStateException("没有当前车辆")

                // 👇 修复：先归一化，再校验
                val normalized = if (record.category != CATEGORY_PARKING) {
                    record.copy(isActive = false)
                } else {
                    record
                }
                validateExpenseRecord(normalized)

                val toInsert = normalized.copy(id = 0L, vehicleId = vehicle.id)

                if (toInsert.category == CATEGORY_PARKING && toInsert.isActive) {
                    expenseDao.insertParkingAsActive(toInsert)
                } else {
                    expenseDao.insert(toInsert)
                }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "addExpense failed", e)
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun updateExpense(record: ExpenseRecord, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                require(record.id > 0L) { "费用 ID 无效" }

                // 👇 修复：先归一化，再校验
                val normalized = if (record.category != CATEGORY_PARKING) {
                    record.copy(isActive = false)
                } else {
                    record
                }
                validateExpenseRecord(normalized)

                val existing = expenseDao.getById(record.id) ?: throw IllegalArgumentException("记录不存在")
                require(record.vehicleId == existing.vehicleId) { "不能更改所属车辆" }
                requireVehicleExists(existing.vehicleId)

                if (normalized.category == CATEGORY_PARKING && normalized.isActive) {
                    expenseDao.updateParkingAsActive(normalized)
                } else {
                    if (expenseDao.update(normalized) != 1) {
                        throw IllegalStateException("更新失败，记录可能已被删除")
                    }
                }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "updateExpense failed", e)
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun deleteExpense(record: ExpenseRecord, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = try {
                val existing = expenseDao.getById(record.id) ?: throw IllegalArgumentException("记录不存在")
                // 👇 DAO 必须返回 Int
                if (expenseDao.delete(existing) != 1) {
                    throw IllegalStateException("删除失败")
                }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteExpense failed", e)
                Result.failure(e)
            }
            onComplete(result)
        }
    }

    fun clearActiveParking() {
        viewModelScope.launch {
            try {
                getCurrentVehicleFromDatabase()?.let { expenseDao.deactivateAllParking(it.id) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { Log.e(TAG, "clearActiveParking failed", e) }
        }
    }

    fun ignoreActiveParkingNotification() {
        viewModelScope.launch {
            try {
                val v = getCurrentVehicleFromDatabase() ?: return@launch
                expenseDao.getActiveParking(v.id)?.let { expenseDao.markNotificationIgnored(it.id) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { Log.e(TAG, "ignoreActiveParkingNotification failed", e) }
        }
    }

    // =========================================================
    // 费用分类重命名
    // =========================================================
    private val defaultExpenseCategories = listOf("加油", "停车", "高速", "保养", "保险", "维修", "洗车", "其他")


    fun renameExpenseCategory(
        oldName: String,
        newName: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val trimmed = newName.trim()
                    val currentCustomList = ExpenseCategoryStore.getCustom()

                    val existing = currentCustomList.find {
                        it.equals(oldName.trim(), ignoreCase = true)
                    }

                    requireNotNull(existing) {
                        "原分类不存在"
                    }

                    require(trimmed.isNotEmpty()) {
                        "名称不能为空"
                    }

                    require(!existing.equals(trimmed, ignoreCase = true)) {
                        "名称无变化或仅大小写不同"
                    }

                    require(
                        ExpenseCategoryStore.DEFAULT_EXPENSE_CATEGORIES.none {
                            it.equals(trimmed, ignoreCase = true)
                        }
                    ) {
                        "不能与默认分类重名"
                    }

                    require(currentCustomList.none {
                        !it.equals(existing, ignoreCase = true) &&
                                it.equals(trimmed, ignoreCase = true)
                    }) {
                        "分类已存在"
                    }

                    database.withTransaction {
                        val dao = database.customCategoryDao()

                        val deleted = dao.delete(existing)

                        check(deleted == 1) {
                            "原分类已不存在，请刷新后重试"
                        }

                        val inserted = dao.insert(
                            CustomCategoryEntity(trimmed)
                        )

                        check(inserted != -1L) {
                            "新分类名称已存在"
                        }

                        val updated = expenseDao.renameCategory(
                            existing,
                            trimmed
                        )

                        // 不强制 updated > 0：
                        // 该分类可能尚未被任何费用记录使用。
                        check(updated >= 0) {
                            "历史费用更新失败"
                        }
                    }

                    Result.success(Unit)

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "renameExpenseCategory failed", e)
                    Result.failure(e)
                }
            }

            onComplete(result)
        }
    }


    // =========================================================
    // 备份恢复
    // =========================================================

    fun createBackup(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        if (isBackupOperationRunning) return onResult(Result.failure(IllegalStateException("操作进行中")))
        isBackupOperationRunning = true

        viewModelScope.launch {
            // 👇 修复：用局部变量保存结果，防止回调异常触发二次回调
            val res = try {
                BackupManager.writeBackupToUri(getApplication(), uri)
                Result.success(Unit)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "createBackup failed", e)
                Result.failure(e)
            } finally {
                isBackupOperationRunning = false
            }
            onResult(res)
        }
    }

    fun restoreBackup(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        if (isBackupOperationRunning) return onResult(Result.failure(IllegalStateException("操作进行中")))
        isBackupOperationRunning = true

        viewModelScope.launch {
            val res = try {
                // 统一在 BackupManager 内完成 DB + SP 的原子化恢复
                BackupManager.restoreFromUri(getApplication(), uri)
                _currentVehicleOverride.value = null   // 让 Room Flow 重新驱动当前车辆
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "restoreBackup failed", e)
                Result.failure(e)
            } finally {
                isBackupOperationRunning = false
            }
            onResult(res)
        }
    }

    fun checkIsJsonFile(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val isJson = try {
                withContext(Dispatchers.IO) { isJsonFileUri(getApplication(), uri) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.w(TAG, "checkIsJsonFile failed", e)
                false
            }
            onResult(isJson)
        }
    }

    // =========================================================
    // CSV 导入（去重键增强 & 统一校验）
    // =========================================================
    fun importFuelRecords(recordsToImport: List<FuelRecord>, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val res = try {
                val count = database.withTransaction {
                    val vehicle = getCurrentVehicleFromDatabase() ?: throw IllegalStateException("没有当前车辆")
                    // 👇 增加单价、油量等字段，减少误判
                    val seen = fuelDao.getRecordsByVehicleId(vehicle.id)
                        .map { listOf(it.timestamp, it.odometer, it.actualPaidAmount, it.volume, it.unitPrice) }
                        .toMutableSet()
                    var importCount = 0
                    recordsToImport.forEach { source ->
                        validateFuelRecord(source)
                        val key = listOf(source.timestamp, source.odometer, source.actualPaidAmount, source.volume, source.unitPrice)
                        if (seen.add(key)) {
                            fuelDao.insert(source.copy(id = 0L, vehicleId = vehicle.id, fuelGrade = source.fuelGrade.ifBlank { vehicle.fuelGrade }))
                            importCount++
                        }
                    }
                    importCount
                }
                Result.success(count)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "导入加油失败", e)
                Result.failure(e)
            }
            onResult(res)
        }
    }

    fun importExpenseRecords(recordsToImport: List<ExpenseRecord>, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val res = try {
                val count = database.withTransaction {
                    val vehicle = getCurrentVehicleFromDatabase() ?: throw IllegalStateException("没有当前车辆")
                    // 👇 增加 note 等字段，减少误判
                    val seen = expenseDao.getAll().filter { it.vehicleId == vehicle.id }
                        .map { listOf(it.timestamp, it.amount, it.category, it.note) }
                        .toMutableSet()
                    var importCount = 0
                    recordsToImport.forEach { source ->
                        // 👇 修复：导入也走归一化和校验
                        val normalized = if (source.category != CATEGORY_PARKING) {
                            source.copy(isActive = false)
                        } else {
                            source
                        }
                        validateExpenseRecord(normalized)

                        val key = listOf(normalized.timestamp, normalized.amount, normalized.category, normalized.note)
                        if (seen.add(key)) {
                            val toInsert = normalized.copy(id = 0L, vehicleId = vehicle.id)
                            if (toInsert.category == CATEGORY_PARKING && toInsert.isActive) expenseDao.insertParkingAsActive(toInsert)
                            else expenseDao.insert(toInsert)
                            importCount++
                        }
                    }
                    importCount
                }
                Result.success(count)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "导入费用失败", e)
                Result.failure(e)
            }
            onResult(res)
        }
    }

    // =========================================================
    // 辅助工具
    // =========================================================
    private fun isJsonFileUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                index >= 0 && cursor.moveToFirst() && cursor.getString(index)?.endsWith(".json", ignoreCase = true) == true
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "query displayName failed", e)
            false
        }
    }
}
sealed interface ParkingNotificationState {
    /** 数据库首次加载完成前，禁止任何通知操作（防止重启误取消） */
    data object NotReady : ParkingNotificationState
    /** 当前车辆没有有效的激活停车记录，应取消通知 */
    data object Hidden : ParkingNotificationState
    /** 应显示的通知内容 */
    data class Visible(
        val vehicleId: Long,
        val locationName: String?,
        val floor: String?,
        val area: String?,
        val spotNumber: String?
    ) : ParkingNotificationState
}