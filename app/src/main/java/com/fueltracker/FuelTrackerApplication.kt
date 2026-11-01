
package com.fueltracker

import android.app.Application
import android.util.Log
import com.fueltracker.data.ExpenseCategoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FuelTrackerApplication : Application() {

    companion object {
        private const val TAG = "FuelTrackerApplication"
    }

    sealed interface InitState {
        data object Loading : InitState
        data object Success : InitState
        data class Error(val message: String) : InitState
    }

    private val appScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initMutex = Mutex()

    private val _initState =
        MutableStateFlow<InitState>(InitState.Loading)

    val initState: StateFlow<InitState> =
        _initState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        startInitialization()
    }

    /**
     * 启动初始化。
     *
     * Mutex 防止重复点击重试导致多个初始化任务同时运行。
     */
    fun startInitialization() {
        appScope.launch {
            initMutex.withLock {
                // 已经成功，不重复初始化。
                if (_initState.value is InitState.Success) {
                    return@withLock
                }

                _initState.value = InitState.Loading

                try {
                    ExpenseCategoryStore.init(this@FuelTrackerApplication)

                    _initState.value = InitState.Success

                    Log.i(TAG, "应用初始化成功")

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {
                    Log.e(TAG, "应用初始化失败", e)

                    _initState.value = InitState.Error(
                        e.message ?: "未知初始化错误"
                    )
                }
            }
        }
    }
}
