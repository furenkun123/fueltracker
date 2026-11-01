package com.fueltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT name FROM custom_categories ORDER BY name ASC")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT name FROM custom_categories ORDER BY name ASC")
    suspend fun getAll(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE name = :name")
    suspend fun delete(name: String)

    @Query("UPDATE custom_categories SET name = :newName WHERE name = :oldName")
    suspend fun rename(oldName: String, newName: String)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CustomCategoryEntity>)
}