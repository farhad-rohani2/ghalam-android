package com.example.ghalam.teacher.data.local

import com.example.ghalam.teacher.data.api.SubmissionRowDto
import com.example.ghalam.teacher.data.api.TaskDto
import java.time.OffsetDateTime

/** "2026-11-25T01:15:04+03:30" -> epoch millis (null when missing or not parsable). */
fun parseEpoch(value: String?): Long? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

fun TaskDto.toEntity(submissionsSyncedAt: Long) = TaskEntity(
    id = id,
    uuid = uuid,
    classroomId = classroom.id,
    classroomTitle = classroom.title,
    lessonId = lesson.id,
    lessonTitle = lesson.title,
    description = description,
    deadlineEpoch = parseEpoch(deadlineAt),
    deadlineJalali = deadlineJalali,
    statsTotal = stats.total,
    statsAnswered = stats.answered,
    statsDone = stats.done,
    submissionsSyncedAt = submissionsSyncedAt,
)

fun SubmissionRowDto.toEntity(taskId: Long) = SubmissionEntity(
    taskId = taskId,
    studentId = id,
    name = name,
    firstName = firstName,
    lastName = lastName,
    photo = photo,
    status = status,
    statusLabel = statusLabel,
    filesCount = filesCount,
    answeredAt = answeredAt,
    answeredAtJalali = answeredAtJalali,
    serverHasFeedback = hasFeedback,
)
