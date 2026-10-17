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

    @Query("""
        SELECT * FROM vehicles 
        WHERE isCurrent = 1 
        ORDER BY id DESC 
        LIMIT 1
    """)
    fun observeCurrentVehicle(): Flow<Vehicle?>


    @Query("""
        SELECT * FROM vehicles 
        ORDER BY id DESC
    """)
    fun observeAllVehicles(): Flow<List<Vehicle>>


    @Query("""
        SELECT * FROM vehicles 
        WHERE id != :excludedId 
        ORDER BY id DESC 
        LIMIT 1
    """)
    suspend fun getAnotherVehicle(
        excludedId: Long
    ): Vehicle?


    @Query("UPDATE vehicles SET isCurrent = 0")
    suspend fun clearCurrent()


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: Vehicle): Long


    @Update
    suspend fun update(vehicle: Vehicle)


    @Delete
    suspend fun delete(vehicle: Vehicle)


    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()


    @Query("""
        UPDATE vehicles 
        SET isCurrent = 1 
        WHERE id = :vehicleId
    """)
    suspend fun markCurrent(
        vehicleId: Long
    )


    @Transaction
    suspend fun insertAsCurrent(vehicle: Vehicle): Long {

        clearCurrent()

        return insert(
            vehicle.copy(
                isCurrent = true
            )
        )
    }


    @Transaction
    suspend fun setCurrentVehicle(vehicleId: Long) {

        clearCurrent()

        markCurrent(vehicleId)
    }


    @Transaction
    suspend fun deleteVehicle(vehicle: Vehicle) {

        delete(vehicle)

        if (vehicle.isCurrent) {

            getAnotherVehicle(vehicle.id)
                ?.let {
                    setCurrentVehicle(it.id)
                }
        }
    }
    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehicles(): List<Vehicle>

}