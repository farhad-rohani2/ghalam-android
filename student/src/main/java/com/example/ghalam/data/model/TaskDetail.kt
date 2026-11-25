package com.example.ghalam.data.model

import com.example.ghalam.data.api.PAGE_TASK
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
//
// Only what the TEACHER enters in TeacherTaskResource is mapped for the student:
//   lesson, classroom, deadline, description (title), dependencies,
//   expected_answer, attached files, fileInInternet links.
// Everything else in the payload (ids, rating, updated_at, exam title, images) is ignored.

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
    val dependencies: JsonElement? = null,
    val url: String = "",
)

// ---------- UI models ----------

private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic")

data class RemoteFile(val url: String, val name: String, val uuid: String?) {
    /** Files are served from /storage and the site appends ?pageId. */
    val fullUrl: String get() = if ('?' in url) url else "$url?pageId=$PAGE_TASK"
    val isImage: Boolean
        get() = name.lowercase().let { n -> IMAGE_EXT.any { n.endsWith(it) } } ||
            url.substringBefore('?').lowercase().let { n -> IMAGE_EXT.any { n.endsWith(it) } }
}

/** A "file from the internet" link the teacher added (page the file came from). */
data class LinkItem(val title: String, val url: String)

/** A prerequisite task. */
data class DepItem(val title: String, val uuid: String?)

/** What is needed to upload into a question's answer. */
data class AnswerSlot(val answerId: Int, val fieldUuid: String)

data class QuestionUi(
    val id: Int,
    val slot: AnswerSlot?,
    /** student's own uploaded photos */
    val files: List<RemoteFile>,
    /** teacher: "توضیح / پاسخ مورد انتظار" (HTML from the rich editor) */
    val expectedHtml: String,
    /** teacher: attached files */
    val teacherFiles: List<RemoteFile>,
    /** teacher: links */
    val links: List<LinkItem>,
    /** the student's own text answer (empty when none) */
    val answerText: String = "",
)

data class DetailUi(
    val title: String,
    val lesson: String,
    val classroom: String,
    val deadline: String?,
    val dependencies: List<DepItem>,
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
        title = description.trim().ifEmpty { examObj?.get("title").str()?.trim().orEmpty() },
        lesson = (lesson as? JsonObject)?.get("title").str().orEmpty(),
        classroom = (classroom as? JsonObject)?.get("title").str().orEmpty(),
        deadline = deadlineAt?.takeIf { it.isNotBlank() },
        dependencies = (dependencies as? JsonArray).orEmpty().mapNotNull { parseDependency(it) },
        questions = questions,
    )
}

private fun parseDependency(el: JsonElement): DepItem? = when (el) {
    is JsonObject -> {
        val title = listOf("description", "title", "exam").firstNotNullOfOrNull { el[it].str()?.trim()?.ifEmpty { null } }
        val uuid = el["uuid"].str() ?: el["url"].str()?.substringBefore('?')?.trimEnd('/')?.substringAfterLast('/')
        title?.let { DepItem(it, uuid) }
    }
    is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }?.let { DepItem(it, null) }
    else -> null
}

private fun JsonObject.toQuestionUi(): QuestionUi {
    // ----- teacher part: question[] -> first block with data -----
    var expected = ""
    val teacherFiles = mutableListOf<RemoteFile>()
    val links = mutableListOf<LinkItem>()
    for (block in (get("question") as? JsonArray).orEmpty()) {
        val data = (block as? JsonObject)?.get("data") as? JsonObject ?: continue
        if (expected.isEmpty()) expected = data["expected_answer"].str().orEmpty()

        val file = data["file"] as? JsonObject
        val media = ((file?.get("extraData") as? JsonObject)?.get("mediaResource") as? JsonArray).orEmpty()
        media.forEach { parseMedia(it)?.let(teacherFiles::add) }

        (data["fileInInternet"] as? JsonArray).orEmpty().forEach { l ->
            val o = l as? JsonObject ?: return@forEach
            val page = o["linkPage"].str()?.trim().orEmpty()
            if (page.isNotEmpty()) links += LinkItem(page.removePrefix("https://").removePrefix("http://").trimEnd('/'), page)
        }
    }

    // ----- student part: answer -----
    val answer = get("answer") as? JsonObject // may be [] / null when there is no answer record
    val answerId = answer?.get("id").int()

    var slot: AnswerSlot? = null
    var answerText = ""
    val files = mutableListOf<RemoteFile>()
    for (block in (answer?.get("answer") as? JsonArray).orEmpty()) {
        val blockData = (block as? JsonObject)?.get("data") as? JsonObject
        if (answerText.isEmpty()) answerText = blockData?.get("answer").str().orEmpty()
        val file = blockData?.get("file") as? JsonObject ?: continue
        val fieldUuid = file["fieldUuid"].str() ?: continue
        if (slot == null && answerId != null) slot = AnswerSlot(answerId, fieldUuid)
        val media = ((file["extraData"] as? JsonObject)?.get("mediaResource") as? JsonArray).orEmpty()
        media.forEach { parseMedia(it)?.let(files::add) }
    }
    return QuestionUi(get("id").int() ?: 0, slot, files, expected, teacherFiles, links, answerText)
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
