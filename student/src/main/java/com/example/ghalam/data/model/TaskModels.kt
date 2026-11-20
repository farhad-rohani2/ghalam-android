package com.example.ghalam.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskResponse(
    val data: List<Task>,
    val links: Links,
    val meta: Meta,
)

@Serializable
data class Task(
    val id: Int,
    val description: String = "",
    @SerialName("deadline_at") val deadlineAt: String? = null,
    @SerialName("deadline_finished") val deadlineFinished: Boolean = false,
    val classroom: String = "",
    val lesson: String = "",
    val exam: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    val media: Media? = null,
    val url: String = "",
    val rating: Rating? = null,
) {
    /** The detail endpoint is keyed by uuid, which is the last segment of [url]. */
    val uuid: String get() = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
}

@Serializable
data class Media(val main: String? = null, val hover: String? = null)

@Serializable
data class Rating(val fill: Int = 0, val empty: Int = 0)

@Serializable
data class Links(
    val first: String? = null,
    val last: String? = null,
    val prev: String? = null,
    val next: String? = null,
)

@Serializable
data class Meta(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
    val from: Int? = null,
    val to: Int? = null,
)
