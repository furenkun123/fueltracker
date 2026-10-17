package com.fueltracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun observeByVehicle(vehicleId: Long): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId AND category = '停车' AND isActive = 1
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    suspend fun getActiveParking(vehicleId: Long): ExpenseRecord?

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId AND category = '停车' AND isActive = 1
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    fun observeActiveParking(vehicleId: Long): Flow<ExpenseRecord?>

    @Query(
        """
        UPDATE expenses SET isActive = 0
        WHERE vehicleId = :vehicleId AND category = '停车' AND isActive = 1
        """
    )
    suspend fun deactivateAllParking(vehicleId: Long)
    @Query("UPDATE expenses SET notificationIgnored = 1 WHERE id = :id")
    suspend fun markNotificationIgnored(id: Long)

    @Insert
    suspend fun insert(record: ExpenseRecord): Long

    @Update
    suspend fun update(record: ExpenseRecord)

    @Delete
    suspend fun delete(record: ExpenseRecord)

    @Query("DELETE FROM expenses WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicleId(vehicleId: Long)

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseRecord>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}