package com.example.ghalam.teacher.data.repository

import com.example.ghalam.teacher.data.api.FeedbackRowDto
import com.example.ghalam.teacher.data.api.LevelDto

/** What the teacher edits on the answer screen. */
data class FeedbackForm(
    val overallLevel: Int? = null,
    val rows: List<FeedbackRowDto> = emptyList(),
    val positives: String = "",
    val negatives: String = "",
    val suggestions: String = "",
) {
    /** Nothing to say: such a form is never stored (and it deletes an existing feedback). */
    val isEmpty: Boolean
        get() = overallLevel == null && rows.all { it.level == null } &&
            positives.isBlank() && negatives.isBlank() && suggestions.isBlank()
}

/** The saved form plus where it stands with the server (state = null: nothing saved yet). */
data class FeedbackScreenState(
    val form: FeedbackForm,
    val state: String?,
    val lastError: String?,
    val levels: List<LevelDto>,
)

/** Used until /meta has been downloaded once. */
val DEFAULT_LEVELS = listOf(
    LevelDto(4, "خیلی خوب", "🌟"),
    LevelDto(3, "خوب", "😊"),
    LevelDto(2, "قابل قبول", "🙂"),
    LevelDto(1, "نیازمند تلاش", "💪"),
)
