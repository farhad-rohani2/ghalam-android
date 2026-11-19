package com.example.ghalam.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// The detail payload is irregular (PHP turns empty objects into []), so everything
// below the top level is kept as raw JSON and mapped by hand, defensively.

@Serializable
data class TaskDetailResponse(val data: TaskDetail)

@Serializable
data class TaskDetail(
    val id: Int = 0,
    val uuid: String = "",
    val description: String = "",
    @SerialName("deadline_at") val deadlineAt: String? = null,
    val classroom: JsonElement? = null,
    val lesson: JsonElement? = null,
    val exam: JsonElement? = null,
    val url: String = "",
)

// ---------- UI models ----------

data class RemoteFile(val url: String, val name: String, val uuid: String?) {
    /** Images are served from /storage and the site appends ?pageId. */
    val imageUrl: String get() = if ('?' in url) url else "$url?pageId=$PAGE_TASK"
}

/** What is needed to upload into a question's answer. */
data class AnswerSlot(val answerId: Int, val fieldUuid: String)

data class QuestionUi(
    val id: Int,
    val prompt: String,
    val slot: AnswerSlot?,
    val files: List<RemoteFile>,
)

data class DetailUi(
    val title: String,
    val description: String,
    val lesson: String,
    val classroom: String,
    val deadline: String?,
    val questions: List<QuestionUi>,
)

// ---------- mapping ----------

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

fun TaskDetail.toUi(): DetailUi {
    val examObj = exam as? JsonObject
    val questions = (examObj?.get("questions") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.toQuestionUi() }
    return DetailUi(
        title = examObj?.get("title").str()?.trim().orEmpty().ifEmpty { description.trim() },
        description = description.trim(),
        lesson = (lesson as? JsonObject)?.get("title").str().orEmpty(),
        classroom = (classroom as? JsonObject)?.get("title").str().orEmpty(),
        deadline = deadlineAt,
        questions = questions,
    )
}

private fun JsonObject.toQuestionUi(): QuestionUi {
    val prompt = (get("question") as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("data") as? JsonObject }
        .flatMap { d -> listOfNotNull(d["title"].str(), d["text"].str(), d["body"].str()) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("\n")

    val answer = get("answer") as? JsonObject // may be [] / null when there is no answer record
    val answerId = answer?.get("id").int()

    var slot: AnswerSlot? = null
    val files = mutableListOf<RemoteFile>()
    for (block in (answer?.get("answer") as? JsonArray).orEmpty()) {
        val file = ((block as? JsonObject)?.get("data") as? JsonObject)?.get("file") as? JsonObject
            ?: continue
        val fieldUuid = file["fieldUuid"].str() ?: continue
        if (slot == null && answerId != null) slot = AnswerSlot(answerId, fieldUuid)
        val media = ((file["extraData"] as? JsonObject)?.get("mediaResource") as? JsonArray).orEmpty()
        media.forEach { parseMedia(it)?.let(files::add) }
    }
    return QuestionUi(get("id").int() ?: 0, prompt, slot, files)
}

/** Works for both mediaResource[] items and the upload response's "data". */
fun parseMedia(el: JsonElement?): RemoteFile? {
    val o = el as? JsonObject ?: return null
    val src = o["source"].str() ?: return null
    val opts = o["options"] as? JsonObject
    val uuid = (opts?.get("metadata") as? JsonObject)?.get("uuid").str()
    val name = ((opts?.get("fileInfo") ?: opts?.get("file")) as? JsonObject)?.get("name").str()
        ?: src.substringAfterLast('/')
    return RemoteFile(src, name, uuid)
}
