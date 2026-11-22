package com.example.ghalam.teacher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * NOTE: unlike the student app this is NOT only a cache: it also holds feedback the teacher wrote
 * offline and that has not reached the server yet. So the builder in Graph has no
 * fallbackToDestructiveMigration(); when the schema changes, write a real Migration.
 */
@Database(
    entities = [
        ClassroomEntity::class,
        TaskEntity::class,
        SubmissionEntity::class,
        AnswerEntity::class,
        FeedbackEntity::class,
        MediaEntity::class,
        JsonCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TeacherDatabase : RoomDatabase() {
    abstract fun classroomDao(): ClassroomDao
    abstract fun taskDao(): TaskDao
    abstract fun submissionDao(): SubmissionDao
    abstract fun answerDao(): AnswerDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun mediaDao(): MediaDao
    abstract fun cacheDao(): CacheDao
}
