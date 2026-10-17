package com.fueltracker.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fueltracker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

/**
 * 处理通知栏按钮点击。
 *
 * 不依赖 App 是否在运行：
 * - 直接操作数据库
 * - App 运行时 ViewModel 的 Room Flow 会自动感知变化
 * - App 未运行时下次打开也会看到最新状态
 */
class ParkingActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLEAR = "com.fueltracker.action.PARKING_CLEAR"
        const val ACTION_IGNORE = "com.fueltracker.action.PARKING_IGNORE"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val vehicleId = intent.getLongExtra(EXTRA_VEHICLE_ID, -1L)
        if (vehicleId <= 0L) {
            ParkingNotification.cancel(context)
            return
        }

        val action = intent.action
        val appContext = context.applicationContext

        // 关键：onReceive 不能做耗时操作，用 goAsync() 延后 finish
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val dao = db.expenseDao()

                when (action) {
                    ACTION_CLEAR -> {
                        dao.deactivateAllParking(vehicleId)
                    }

                    ACTION_IGNORE -> {
                        val parking = dao.getActiveParking(vehicleId)
                        if (parking != null) {
                            dao.markNotificationIgnored(parking.id)
                        }
                    }
                }

                // DB 操作成功，才清通知
                ParkingNotification.cancel(appContext)
            } catch (e: Exception) {
                // 失败时不清通知，让用户重试
                Log.e("ParkingAction", "处理通知动作失败: action=$action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}