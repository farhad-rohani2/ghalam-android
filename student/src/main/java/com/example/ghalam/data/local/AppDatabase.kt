package com.example.ghalam.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, TaskEntryEntity::class, RemoteKeyEntity::class, CachedJsonEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun cacheDao(): CacheDao
}
