package com.fueltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


@Database(
    entities = [
        Vehicle::class,
        FuelRecord::class,
        ExpenseRecord::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    abstract fun fuelDao(): FuelDao

    abstract fun expenseDao(): ExpenseDao


    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `vehicleId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `locationName` TEXT,
                        `floor` TEXT,
                        `area` TEXT,
                        `spotNumber` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `fromLocation` TEXT,
                        `toLocation` TEXT,
                        `maintenanceItem` TEXT,
                        `note` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 → v4：
         * 给 expenses 表加 notificationIgnored 列。
         *
         * 先检查列是否已存在，防止某些用户数据库里已经有这一列
         * （比如开发期间手动改过），导致 ALTER TABLE 报重复列错误。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                var hasColumn = false
                db.query("PRAGMA table_info(`expenses`)").use { cursor ->
                    while (cursor.moveToNext()) {
                        val nameIndex = cursor.getColumnIndex("name")
                        if (nameIndex >= 0) {
                            val columnName = cursor.getString(nameIndex)
                            if (columnName == "notificationIgnored") {
                                hasColumn = true
                                break
                            }
                        }
                    }
                }

                if (!hasColumn) {
                    db.execSQL(
                        "ALTER TABLE `expenses` ADD COLUMN `notificationIgnored` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fuel_tracker.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}