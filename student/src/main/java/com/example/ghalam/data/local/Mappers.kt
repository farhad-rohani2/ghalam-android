package com.example.ghalam.data.local

import com.example.ghalam.data.model.Media
import com.example.ghalam.data.model.Rating
import com.example.ghalam.data.model.Task

fun Task.toEntity() = TaskEntity(
    id = id,
    description = description,
    deadlineAt = deadlineAt,
    deadlineFinished = deadlineFinished,
    classroom = classroom,
    lesson = lesson,
    exam = exam,
    updatedAt = updatedAt,
    mediaMain = media?.main,
    mediaHover = media?.hover,
    url = url,
    ratingFill = rating?.fill,
    ratingEmpty = rating?.empty,
)

fun TaskEntity.toTask() = Task(
    id = id,
    description = description,
    deadlineAt = deadlineAt,
    deadlineFinished = deadlineFinished,
    classroom = classroom,
    lesson = lesson,
    exam = exam,
    updatedAt = updatedAt,
    media = if (mediaMain != null || mediaHover != null) Media(mediaMain, mediaHover) else null,
    url = url,
    rating = if (ratingFill != null && ratingEmpty != null) Rating(ratingFill, ratingEmpty) else null,
)
