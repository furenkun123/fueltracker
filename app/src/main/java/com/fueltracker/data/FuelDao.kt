
package com.fueltracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {

    // =========================
    // 查询
    // =========================

    @Query(
        """
        SELECT * FROM fuel_records
        WHERE vehicleId = :vehicleId
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun observeRecords(vehicleId: Long): Flow<List<FuelRecord>>

    @Query(
        """
        SELECT * FROM fuel_records
        WHERE vehicleId = :vehicleId
        ORDER BY timestamp DESC, id DESC
        """
    )
    suspend fun getRecordsByVehicleId(vehicleId: Long): List<FuelRecord>

    @Query("SELECT * FROM fuel_records ORDER BY timestamp ASC, id ASC")
    suspend fun getAllRecords(): List<FuelRecord>

    @Query("SELECT * FROM fuel_records WHERE id = :recordId LIMIT 1")
    suspend fun getRecordById(recordId: Long): FuelRecord?

    // =========================
    // 写入
    // =========================

    /**
     * 普通插入遇到重复主键时直接失败，禁止静默覆盖旧记录。
     * 完整备份恢复需要先清空旧表，再插入保留原始 ID 的记录。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: FuelRecord): Long

    @Update
    suspend fun update(record: FuelRecord): Int

    @Delete
    suspend fun delete(record: FuelRecord): Int

    // =========================
    // 删除
    // =========================

    @Query("DELETE FROM fuel_records WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicleId(vehicleId: Long)

    @Query("DELETE FROM fuel_records")
    suspend fun deleteAll()
}
