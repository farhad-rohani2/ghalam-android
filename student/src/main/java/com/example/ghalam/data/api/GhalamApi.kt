package com.example.ghalam.data.api

import com.example.ghalam.data.model.FiltersResponse
import com.example.ghalam.data.model.TaskDetailResponse
import com.example.ghalam.data.model.TaskResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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
import retrofit2.http.QueryMap

// pageId values used by the site for the different screens
const val PAGE_LIST = 61
const val PAGE_LOGIN = 62
const val PAGE_TASK = 66

@Serializable
data class PageBody(val pageId: Int = PAGE_LIST)

@Serializable
data class BlockBody(val blockIndex: Int = 0, val pageId: Int = PAGE_LIST)

@Serializable
data class LoginBody(val codeMelli: String, val pageId: Int = PAGE_LOGIN)

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
     * Task list. [filters] keys are already in the site's format, e.g.
     *   filter[title]=...           filter[has_media]=complete
     *   filter[lesson_id][0]=3      filter[lesson_id][1]=5
     */
    @POST("api/blueberry/sections/task")
    suspend fun tasks(
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "-id",
        @Query("view") view: String = "grid",
        @QueryMap filters: Map<String, String> = emptyMap(),
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
