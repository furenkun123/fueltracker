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

    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun observeRecords(vehicleId: Long): Flow<List<FuelRecord>>

    @Query("SELECT * FROM fuel_records ORDER BY timestamp ASC")
    suspend fun getAllRecords(): List<FuelRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FuelRecord): Long

    @Update
    suspend fun update(record: FuelRecord)

    @Delete
    suspend fun delete(record: FuelRecord)

    // ✅ 新增：删除指定车辆下的所有加油记录
    @Query("DELETE FROM fuel_records WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicleId(vehicleId: Long)

    @Query("DELETE FROM fuel_records")
    suspend fun deleteAll()

}