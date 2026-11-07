package com.sdumsd11.easytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Offline-first store (provided by `di.AppModule`, owned by architecture —
 * do not rename). Previously retrieved data stays readable with no network.
 */
@Database(
    entities = [UserEntity::class, GroupEntity::class, ExpenseEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EasyTrackerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    abstract fun groupDao(): GroupDao

    abstract fun expenseDao(): ExpenseDao
}
