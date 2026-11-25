package com.example.ghalam.teacher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * NOTE: unlike the student app this is NOT only a cache: it also holds feedback the teacher wrote
 * offline and that has not reached the server yet. So the builder in Graph has no
 * fallbackToDestructiveMigration(); every schema change needs a real Migration (see below).
 */
@Database(
    entities = [
        ClassroomEntity::class,
        TaskEntity::class,
        SubmissionEntity::class,
        AnswerEntity::class,
        FeedbackEntity::class,
        GeneralFeedbackEntity::class,
        MediaEntity::class,
        JsonCacheEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class TeacherDatabase : RoomDatabase() {
    abstract fun classroomDao(): ClassroomDao
    abstract fun taskDao(): TaskDao
    abstract fun submissionDao(): SubmissionDao
    abstract fun answerDao(): AnswerDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun generalFeedbackDao(): GeneralFeedbackDao
    abstract fun mediaDao(): MediaDao
    abstract fun cacheDao(): CacheDao
}

/** v1 -> v2: the class-wide feedback of a task (offline editable, uploaded later). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `general_feedback` (" +
                "`taskId` INTEGER NOT NULL, " +
                "`positives` TEXT NOT NULL, " +
                "`negatives` TEXT NOT NULL, " +
                "`suggestions` TEXT NOT NULL, " +
                "`syncState` TEXT NOT NULL, " +
                "`lastError` TEXT, " +
                "`localUpdatedAt` INTEGER NOT NULL, " +
                "`syncedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`taskId`))",
        )
    }
}
