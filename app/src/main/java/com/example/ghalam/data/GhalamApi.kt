package com.example.ghalam.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// pageId values used by the site for the different screens
const val PAGE_LIST = 61
const val PAGE_LOGIN = 62
const val PAGE_TASK = 66

// ---------- Request bodies ----------

@Serializable
data class PageBody(val pageId: Int = PAGE_LIST)

@Serializable
data class BlockBody(val blockIndex: Int = 0, val pageId: Int = PAGE_LIST)

@Serializable
data class LoginBody(val codeMelli: String, val pageId: Int = PAGE_LOGIN)

// ---------- Task list response ----------

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

// ---------- Filters meta response ----------

@Serializable
data class FiltersResponse(val filters: List<Filter>)

@Serializable
data class Filter(
    val key: String,
    val name: String,
    val type: String,
    // options can be a JSON object ({"0":{...}}) or an array, so keep it raw
    val options: JsonElement? = null,
) {
    fun parsedOptions(): List<FilterOption> {
        val el = options ?: return emptyList()
        val items = when (el) {
            is JsonObject -> el.values
            else -> el.jsonArray
        }
        return items.map {
            val o = it.jsonObject
            FilterOption(
                id = o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                label = o["label"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }
}

data class FilterOption(val id: String, val label: String)

// ---------- API ----------

interface GhalamApi {

    /** National code -> user object containing "token". Sent without Authorization. */
    @POST("api/blueberry/sections/task/login")
    suspend fun login(@Body body: LoginBody): JsonObject

    @POST("api/blueberry/auth/logout")
    suspend fun logout(@Body body: PageBody = PageBody(PAGE_TASK)): ResponseBody

    /** Filter definitions (lessons, deadline, media state). */
    @POST("api/blueberry/sections/task/filters-meta")
    suspend fun filtersMeta(@Body body: PageBody = PageBody()): FiltersResponse

    /**
     * Task list.
     * deadlineUnfinished: "ok" (has deadline) / "no"
     * hasMedia: "complete" / "withoutAnswer" / "atLeastAnWithoutAnswer"
     */
    @POST("api/blueberry/sections/task")
    suspend fun tasks(
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "-id",
        @Query("view") view: String = "grid",
        @Query("filter[lesson_id][]") lessonIds: List<Int>? = null,
        @Query("filter[title]") title: String? = null,
        @Query("filter[deadline_unfinished]") deadlineUnfinished: String? = null,
        @Query("filter[has_media]") hasMedia: String? = null,
        @Body body: BlockBody = BlockBody(),
    ): TaskResponse

    @GET("api/blueberry/sections/task/{uuid}")
    suspend fun taskDetail(
        @Path("uuid") uuid: String,
        @Query("pageId") pageId: Int = PAGE_TASK,
    ): TaskDetailResponse

    /**
     * Uploads one photo as an answer.
     * fieldUuid = answer.answer[].data.file.fieldUuid
     * json      = {"extraData":{"morphedModel":"answer","modelId":<answer.id>},"metadata":null}
     * Response: {"data":{"source":..., "options":{"metadata":{"uuid":...}, ...}}}
     */
    @Multipart
    @POST("api/blueberry/sections/task/upload-media")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("fileName") fileName: RequestBody,
        @Part("fieldUuid") fieldUuid: RequestBody,
        @Part("json") json: RequestBody,
    ): JsonObject

    /** uuid = options.metadata.uuid of the uploaded file. */
    @HTTP(method = "DELETE", path = "api/blueberry/sections/task/remove-media/{uuid}", hasBody = true)
    suspend fun removeMedia(
        @Path("uuid") uuid: String,
        @Body body: PageBody = PageBody(PAGE_TASK),
    ): ResponseBody
}
