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
interface ExpenseDao {

    // =========================
    // 查询
    // =========================

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun observeByVehicle(vehicleId: Long): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId
          AND category = '停车'
          AND isActive = 1
        ORDER BY timestamp DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getActiveParking(vehicleId: Long): ExpenseRecord?

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId
          AND category = '停车'
          AND isActive = 1
        ORDER BY timestamp DESC, id DESC
        LIMIT 1
        """
    )
    fun observeActiveParking(vehicleId: Long): Flow<ExpenseRecord?>

    @Query("SELECT * FROM expenses ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<ExpenseRecord>

    @Query("SELECT * FROM expenses WHERE id = :recordId LIMIT 1")
    suspend fun getById(recordId: Long): ExpenseRecord?

    // =========================
    // 插入与更新
    // =========================

    /**
     * 普通新增遇到主键冲突时失败，不覆盖已有费用。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ExpenseRecord): Long

    /**
     * 返回实际更新行数。
     * 调用方可以据此判断记录是否存在。
     */
    @Update
    suspend fun update(record: ExpenseRecord): Int

    @Delete
    suspend fun delete(record: ExpenseRecord)

    // =========================
    // 停车状态
    // =========================

    @Query(
        """
        UPDATE expenses
        SET isActive = 0
        WHERE vehicleId = :vehicleId
          AND category = '停车'
          AND isActive = 1
        """
    )
    suspend fun deactivateAllParking(vehicleId: Long)

    @Query(
        """
        UPDATE expenses
        SET isActive = 0
        WHERE vehicleId = :vehicleId
          AND category = '停车'
          AND isActive = 1
          AND id != :exceptId
        """
    )
    suspend fun deactivateOtherActiveParking(
        vehicleId: Long,
        exceptId: Long
    )

    /**
     * 新增一条有效停车记录时，先停用旧记录，再插入新记录。
     * 两步处于同一个事务中；插入失败会回滚旧记录的停用操作。
     */
    @Transaction
    suspend fun insertParkingAsActive(record: ExpenseRecord): Long {
        require(record.category == "停车") {
            "insertParkingAsActive 只能用于停车记录"
        }
        require(record.isActive) {
            "新停车记录必须为激活状态"
        }

        deactivateAllParking(record.vehicleId)
        return insert(record)
    }

    /**
     * 编辑停车记录并将其设为有效时，先停用其他有效停车记录。
     * 如果目标记录不存在，抛出异常并回滚本次事务。
     */
    @Transaction
    suspend fun updateParkingAsActive(record: ExpenseRecord) {
        require(record.category == "停车") {
            "updateParkingAsActive 只能用于停车记录"
        }
        require(record.isActive) {
            "目标停车记录必须为激活状态"
        }

        deactivateOtherActiveParking(
            vehicleId = record.vehicleId,
            exceptId = record.id
        )

        val updatedRows = update(record)
        if (updatedRows != 1) {
            throw IllegalStateException("停车记录不存在或更新失败：${record.id}")
        }
    }

    @Query(
        """
        UPDATE expenses
        SET notificationIgnored = 1
        WHERE id = :recordId
        """
    )
    suspend fun markNotificationIgnored(recordId: Long): Int

    // =========================
    // 删除
    // =========================

    @Query("DELETE FROM expenses WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicleId(vehicleId: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query(
        """
    UPDATE expenses
    SET category = :newName
    WHERE category = :oldName
    """
    )
    suspend fun renameCategory(
        oldName: String,
        newName: String
    ): Int
}