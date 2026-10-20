
package com.fueltracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    // =========================
    // 查询
    // =========================

    @Query(
        """
        SELECT * FROM vehicles
        WHERE isCurrent = 1
        ORDER BY id DESC
        LIMIT 1
        """
    )
    fun observeCurrentVehicle(): Flow<Vehicle?>

    @Query("SELECT * FROM vehicles ORDER BY id DESC")
    fun observeAllVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehicles(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId LIMIT 1")
    suspend fun getVehicleById(vehicleId: Long): Vehicle?

    @Query(
        """
        SELECT * FROM vehicles
        WHERE id != :excludedId
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getAnotherVehicle(excludedId: Long): Vehicle?

    // =========================
    // 插入与更新
    // =========================

    /**
     * 普通插入使用 ABORT，避免主键冲突时覆盖已有车辆。
     * 完整备份恢复应在清空车辆表后再插入。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vehicle: Vehicle): Long

    @Update
    suspend fun update(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()

    // =========================
    // 当前车辆状态
    // =========================

    @Query("UPDATE vehicles SET isCurrent = 0")
    suspend fun clearCurrent()

    @Query(
        """
        UPDATE vehicles
        SET isCurrent = 1
        WHERE id = :vehicleId
        """
    )
    suspend fun markCurrent(vehicleId: Long): Int

    @Query(
        """
        UPDATE vehicles
        SET isCurrent = 0
        WHERE id != :vehicleId
        """
    )
    suspend fun clearCurrentExcept(vehicleId: Long)

    /**
     * 切换当前车辆。
     *
     * 先确认目标存在，再清除其他车辆的当前状态。
     * 如果目标不存在，抛出异常，事务回滚。
     */
    @Transaction
    suspend fun setCurrentVehicle(vehicleId: Long) {
        val target = getVehicleById(vehicleId)
            ?: throw IllegalArgumentException("目标车辆不存在：$vehicleId")

        val changedRows = markCurrent(target.id)
        if (changedRows != 1) {
            throw IllegalStateException("设置当前车辆失败：${target.id}")
        }

        clearCurrentExcept(target.id)
    }

    /**
     * 新增车辆并设为当前车辆。
     * 插入失败时，清除旧当前状态的操作也会回滚。
     */
    @Transaction
    suspend fun insertAsCurrent(vehicle: Vehicle): Long {
        clearCurrent()

        val newId = insert(vehicle.copy(isCurrent = true))

        val changedRows = markCurrent(newId)
        if (changedRows != 1) {
            throw IllegalStateException("新增车辆后设置当前车辆失败：$newId")
        }

        clearCurrentExcept(newId)
        return newId
    }

    /**
     * 仅删除车辆行并在必要时选择下一辆车。
     *
     * 注意：此方法不会删除该车的加油和费用记录。
     * 完整删除车辆应由 MainViewModel 在同一个事务中删除关联记录。
     */
    @Transaction
    suspend fun deleteVehicle(vehicle: Vehicle) {
        val actualVehicle = getVehicleById(vehicle.id) ?: return
        val wasCurrent = actualVehicle.isCurrent

        delete(actualVehicle)

        if (wasCurrent) {
            val nextVehicle = getAnotherVehicle(actualVehicle.id)

            if (nextVehicle != null) {
                setCurrentVehicle(nextVehicle.id)
            }
        }
    }
}
