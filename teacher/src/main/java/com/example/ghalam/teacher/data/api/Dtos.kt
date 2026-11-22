package com.example.ghalam.teacher.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shapes of the teacher API (/api/teacher/v1), taken from the recorded test responses.
// Every field has a default so a missing / null value never breaks parsing.

@Serializable
data class LoginBody(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class IdTitleDto(val id: Long = 0, val title: String = "")

@Serializable
data class LevelDto(val level: Int = 0, val label: String = "", val emoji: String = "")

@Serializable
data class MetaDto(
    @SerialName("default_deadline") val defaultDeadline: String? = null,
    val levels: List<LevelDto> = emptyList(),
    @SerialName("submission_statuses") val submissionStatuses: Map<String, String> = emptyMap(),
)

@Serializable
data class MetaResponse(val data: MetaDto = MetaDto())

// ---------------------------------------------------------------- classrooms

@Serializable
data class ParamDto(val key: String = "", val title: String = "")

@Serializable
data class LessonDto(val id: Long = 0, val title: String = "", val parameters: List<ParamDto> = emptyList())

@Serializable
data class ClassroomDto(
    val id: Long = 0,
    val title: String = "",
    val degree: Int? = null,
    @SerialName("students_count") val studentsCount: Int = 0,
    val lessons: List<LessonDto> = emptyList(),
)

@Serializable
data class ClassroomsResponse(val data: List<ClassroomDto> = emptyList())

// ---------------------------------------------------------------- tasks

@Serializable
data class StatsDto(val total: Int = 0, val answered: Int = 0, val done: Int = 0)

@Serializable
data class PageMetaDto(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
    val total: Int = 0,
)

@Serializable
data class TaskDto(
    val id: Long = 0,
    val uuid: String = "",
    val classroom: IdTitleDto = IdTitleDto(),
    val lesson: IdTitleDto = IdTitleDto(),
    val description: String = "",
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("deadline_jalali") val deadlineJalali: String? = null,
    @SerialName("is_overdue") val isOverdue: Boolean = false,
    val stats: StatsDto = StatsDto(),
)

@Serializable
data class TaskListResponse(val data: List<TaskDto> = emptyList(), val meta: PageMetaDto = PageMetaDto())

@Serializable
data class TaskFileDto(
    val id: Long = 0,
    val uuid: String = "",
    val name: String = "",
    val mime: String? = null,
    val size: Long = 0,
    val url: String = "",
)

@Serializable
data class TaskLinkDto(
    val linkPage: String? = null,
    val linkDownload: String? = null,
    val status: String? = null,
)

@Serializable
data class DependencyDto(val id: Long = 0, val description: String = "")

@Serializable
data class TaskDetailDto(
    val id: Long = 0,
    val uuid: String = "",
    val classroom: IdTitleDto = IdTitleDto(),
    val lesson: IdTitleDto = IdTitleDto(),
    val description: String = "",
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("deadline_jalali") val deadlineJalali: String? = null,
    @SerialName("is_overdue") val isOverdue: Boolean = false,
    val stats: StatsDto = StatsDto(),
    @SerialName("expected_answer") val expectedAnswer: String = "",
    val files: List<TaskFileDto> = emptyList(),
    val links: List<TaskLinkDto> = emptyList(),
    val dependencies: List<DependencyDto> = emptyList(),
)

@Serializable
data class TaskDetailResponse(val data: TaskDetailDto = TaskDetailDto())

// ---------------------------------------------------------------- submissions / answers

@Serializable
data class SubmissionRowDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val photo: String? = null,
    /** done | no_file | none */
    val status: String = "none",
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("files_count") val filesCount: Int = 0,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("answered_at_jalali") val answeredAtJalali: String? = null,
    @SerialName("has_feedback") val hasFeedback: Boolean = false,
)

@Serializable
data class SubmissionsResponse(
    val data: List<SubmissionRowDto> = emptyList(),
    val meta: PageMetaDto = PageMetaDto(),
)

@Serializable
data class StudentDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val photo: String? = null,
)

@Serializable
data class AnswerFileDto(
    val id: Long = 0,
    val uuid: String = "",
    val name: String = "",
    val mime: String? = null,
    val size: Long = 0,
    val url: String = "",
    @SerialName("link_download") val linkDownload: String? = null,
    @SerialName("link_page") val linkPage: String? = null,
)

@Serializable
data class AnswerItemDto(
    val id: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("created_at_jalali") val createdAtJalali: String? = null,
    val text: String = "",
    val files: List<AnswerFileDto> = emptyList(),
)

@Serializable
data class AnswerDto(
    val student: StudentDto = StudentDto(),
    val status: String = "none",
    @SerialName("status_label") val statusLabel: String = "",
    val text: String = "",
    val answers: List<AnswerItemDto> = emptyList(),
)

@Serializable
data class AnswerResponse(val data: AnswerDto = AnswerDto())

// ---------------------------------------------------------------- feedback

@Serializable
data class FeedbackRowDto(
    val key: String = "",
    val title: String = "",
    val extra: Boolean = false,
    val level: Int? = null,
)

@Serializable
data class GeneralFeedbackDto(
    val positives: String = "",
    val negatives: String = "",
    val suggestions: String = "",
)

@Serializable
data class FeedbackDto(
    @SerialName("task_id") val taskId: Long = 0,
    val student: StudentDto = StudentDto(),
    val exists: Boolean = false,
    @SerialName("overall_level") val overallLevel: Int? = null,
    val rows: List<FeedbackRowDto> = emptyList(),
    val positives: String = "",
    val negatives: String = "",
    val suggestions: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    val levels: List<LevelDto> = emptyList(),
    val general: GeneralFeedbackDto = GeneralFeedbackDto(),
)

@Serializable
data class FeedbackResponse(val data: FeedbackDto = FeedbackDto())
