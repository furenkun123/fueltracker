
package com.fueltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {

    @Query("SELECT name FROM custom_categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT name FROM custom_categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CustomCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CustomCategoryEntity>)

    @Query("DELETE FROM custom_categories WHERE name = :name")
    suspend fun delete(name: String): Int

    @Query("UPDATE custom_categories SET name = :newName WHERE name = :oldName")
    suspend fun rename(oldName: String, newName: String): Int

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()
}
