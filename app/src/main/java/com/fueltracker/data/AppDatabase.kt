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
        ExpenseRecord::class,
        CustomCategoryEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    abstract fun fuelDao(): FuelDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun customCategoryDao(): CustomCategoryDao


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
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_categories` " +
                            "(`name` TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY(`name`))"
                )
            }
        }

        /**
         * v5 → v6：
         * 1. 将 Vehicle 中的旧初始里程/油量迁移为 FuelRecord 初始化记录；
         * 2. 移除车辆表中不再使用的官方油耗和初始状态字段；
         * 3. 将自定义分类主键统一为 NOCASE，修复旧迁移与实体定义不一致的问题。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 已存在的初始化记录优先保留，只补齐旧 Vehicle 中仍有价值的字段。
                db.execSQL(
                    """
                    UPDATE fuel_records
                    SET
                        odometer = COALESCE(
                            odometer,
                            (SELECT initialOdometer FROM vehicles WHERE vehicles.id = fuel_records.vehicleId)
                        ),
                        initialFuel = COALESCE(
                            initialFuel,
                            remainingFuel,
                            (SELECT CASE WHEN fuelType = '纯电动' THEN NULL ELSE initialFuel END
                             FROM vehicles WHERE vehicles.id = fuel_records.vehicleId)
                        )
                    WHERE isInitialRecord = 1
                    """.trimIndent()
                )

                // 没有初始化记录的旧车辆，按旧字段补建一条；时间尽量放在交车日或首条加油记录之前。
                db.execSQL(
                    """
                    INSERT INTO fuel_records (
                        vehicleId, timestamp, fuelGrade, odometer, volume,
                        totalAmount, discount, actualPaidAmount, unitPrice,
                        remainingFuel, isFull, isInitialRecord, initialFuel,
                        hasMissedRecord, missedOdometer, missedVolume, note, consumption
                    )
                    SELECT
                        v.id,
                        CASE
                            WHEN (
                                SELECT MIN(timestamp)
                                FROM fuel_records
                                WHERE vehicleId = v.id
                                  AND (v.deliveryDate IS NULL OR timestamp >= v.deliveryDate)
                            ) IS NOT NULL
                            THEN MAX(
                                COALESCE(NULLIF(v.deliveryDate, 0), 1),
                                (SELECT MIN(timestamp) - 1
                                 FROM fuel_records
                                 WHERE vehicleId = v.id
                                   AND (v.deliveryDate IS NULL OR timestamp >= v.deliveryDate))
                            )
                            ELSE MAX(
                                COALESCE(NULLIF(v.deliveryDate, 0), 1),
                                CAST(strftime('%s', 'now') AS INTEGER) * 1000
                            )
                        END,
                        v.fuelGrade,
                        v.initialOdometer,
                        0.0,
                        NULL,
                        NULL,
                        0.0,
                        0.0,
                        NULL,
                        0,
                        1,
                        CASE WHEN v.fuelType = '纯电动' THEN NULL ELSE v.initialFuel END,
                        0,
                        NULL,
                        NULL,
                        '车辆初始化数据迁移',
                        NULL
                    FROM vehicles AS v
                    WHERE NOT EXISTS (
                        SELECT 1 FROM fuel_records AS f
                        WHERE f.vehicleId = v.id AND f.isInitialRecord = 1
                    )
                    """.trimIndent()
                )

                // 旧版本创建的分类表可能没有 NOCASE；重建时顺便合并大小写不同的重复项。
                db.execSQL("DROP TABLE IF EXISTS custom_categories_new")
                db.execSQL(
                    """
                    CREATE TABLE custom_categories_new (
                        name TEXT NOT NULL COLLATE NOCASE,
                        PRIMARY KEY(name)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO custom_categories_new(name)
                    SELECT TRIM(name) FROM custom_categories
                    ORDER BY name COLLATE NOCASE
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE custom_categories")
                db.execSQL("ALTER TABLE custom_categories_new RENAME TO custom_categories")

                // Room 旧 SQLite 版本兼容：重建 vehicles，避免依赖 DROP COLUMN。
                db.execSQL("DROP TABLE IF EXISTS vehicles_new")
                db.execSQL(
                    """
                    CREATE TABLE vehicles_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        brand TEXT NOT NULL,
                        series TEXT NOT NULL,
                        model TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        tankCapacity REAL NOT NULL,
                        fuelType TEXT NOT NULL,
                        fuelGrade TEXT NOT NULL,
                        deliveryDate INTEGER,
                        isCurrent INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO vehicles_new (
                        id, brand, series, model, year, tankCapacity,
                        fuelType, fuelGrade, deliveryDate, isCurrent
                    )
                    SELECT
                        id, brand, series, model, year, tankCapacity,
                        fuelType, fuelGrade, deliveryDate, isCurrent
                    FROM vehicles
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE vehicles")
                db.execSQL("ALTER TABLE vehicles_new RENAME TO vehicles")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fuel_tracker.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}