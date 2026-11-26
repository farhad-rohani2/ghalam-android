package com.example.ghalam.teacher.data.api

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

private const val V1 = "api/teacher/v1"

/** The teacher API. The base URL (https://ghalam.razzar.ir/) comes from :core:network. */
interface TeacherApi {

    /** username + password -> {"data":{"token":..., "user":{...}}}. Sent without Authorization. */
    @POST("$V1/auth/login")
    suspend fun login(@Body body: LoginBody): JsonObject

    @POST("$V1/auth/logout")
    suspend fun logout(): ResponseBody

    @GET("$V1/meta")
    suspend fun meta(): MetaResponse

    @GET("$V1/classrooms")
    suspend fun classrooms(): ClassroomsResponse

    /** [from] = yyyy-MM-dd, only tasks whose deadline is on / after that day. Newest deadline first. */
    @GET("$V1/tasks")
    suspend fun tasks(
        @Query("page") page: Int = 1,
        @Query("from") from: String? = null,
    ): TaskListResponse

    @GET("$V1/tasks/{task}")
    suspend fun task(@Path("task") taskId: Long): TaskDetailResponse

    @GET("$V1/tasks/{task}/submissions")
    suspend fun submissions(
        @Path("task") taskId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): SubmissionsResponse

    @GET("$V1/tasks/{task}/students/{student}/answer")
    suspend fun answer(
        @Path("task") taskId: Long,
        @Path("student") studentId: Long,
    ): AnswerResponse

    @GET("$V1/tasks/{task}/students/{student}/feedback")
    suspend fun feedback(
        @Path("task") taskId: Long,
        @Path("student") studentId: Long,
    ): FeedbackResponse

    /** Whole-state upsert: sending the same body twice leaves one identical record. */
    @PUT("$V1/tasks/{task}/students/{student}/feedback")
    suspend fun saveFeedback(
        @Path("task") taskId: Long,
        @Path("student") studentId: Long,
        @Body body: JsonObject,
    ): FeedbackResponse

    /**
     * Parts: collection ("voice" | "files"), optional duration (seconds) and files[].
     * Returns the whole feedback of the student (with its media list).
     */
    @Multipart
    @POST("$V1/tasks/{task}/students/{student}/feedback/media")
    suspend fun addFeedbackMedia(
        @Path("task") taskId: Long,
        @Path("student") studentId: Long,
        @Part parts: List<MultipartBody.Part>,
    ): FeedbackResponse

    @DELETE("$V1/tasks/{task}/students/{student}/feedback/media/{media}")
    suspend fun removeFeedbackMedia(
        @Path("task") taskId: Long,
        @Path("student") studentId: Long,
        @Path("media") mediaId: Long,
    ): FeedbackResponse

    /** Same as [addFeedbackMedia] for the class-wide feedback of the task. */
    @Multipart
    @POST("$V1/tasks/{task}/feedback/general/media")
    suspend fun addGeneralFeedbackMedia(
        @Path("task") taskId: Long,
        @Part parts: List<MultipartBody.Part>,
    ): GeneralFeedbackResponse

    @DELETE("$V1/tasks/{task}/feedback/general/media/{media}")
    suspend fun removeGeneralFeedbackMedia(
        @Path("task") taskId: Long,
        @Path("media") mediaId: Long,
    ): GeneralFeedbackResponse

    // ---------------------------------------------------------------- task management (online only)

    /** lesson_id, description (<= 500), optional classroom_id / deadline_at / expected_answer / dependencies / links. */
    @POST("$V1/tasks")
    suspend fun createTask(@Body body: JsonObject): TaskStoreResponse

    /** Only the sent fields change; remove_media_ids deletes attached files. */
    @PUT("$V1/tasks/{task}")
    suspend fun updateTask(@Path("task") taskId: Long, @Body body: JsonObject): TaskStoreResponse

    @DELETE("$V1/tasks/{task}")
    suspend fun deleteTask(@Path("task") taskId: Long): OkResponse

    /** Parts are named files[]. */
    @Multipart
    @POST("$V1/tasks/{task}/files")
    suspend fun addTaskFiles(
        @Path("task") taskId: Long,
        @Part files: List<MultipartBody.Part>,
    ): TaskDetailResponse

    @DELETE("$V1/tasks/{task}/files/{media}")
    suspend fun removeTaskFile(
        @Path("task") taskId: Long,
        @Path("media") mediaId: Long,
    ): TaskDetailResponse

    // ---------------------------------------------------------------- general feedback of a task

    @GET("$V1/tasks/{task}/feedback/general")
    suspend fun generalFeedback(@Path("task") taskId: Long): GeneralFeedbackResponse

    /** Whole-state upsert; all three texts empty deletes it. */
    @PUT("$V1/tasks/{task}/feedback/general")
    suspend fun saveGeneralFeedback(
        @Path("task") taskId: Long,
        @Body body: JsonObject,
    ): GeneralFeedbackResponse

    // ---------------------------------------------------------------- reports (text composed by the server)

    @GET("$V1/reports/today")
    suspend fun reportToday(
        @Query("mode") mode: String,
        @Query("date") date: String? = null,
        @Query("classroom_id") classroomId: Long? = null,
    ): ReportResponse

    @POST("$V1/reports/selected")
    suspend fun reportSelected(@Body body: JsonObject): ReportResponse

    @GET("$V1/tasks/{task}/report")
    suspend fun reportTask(
        @Path("task") taskId: Long,
        @Query("mode") mode: String,
    ): ReportResponse

    // ---------------------------------------------------------------- messages to students

    @GET("$V1/message-templates")
    suspend fun templates(): TemplatesResponse

    @POST("$V1/message-templates")
    suspend fun createTemplate(@Body body: JsonObject): TemplatesResponse

    @PUT("$V1/message-templates/{key}")
    suspend fun updateTemplate(@Path("key") key: String, @Body body: JsonObject): TemplatesResponse

    /** A custom template is deleted; an edited default one goes back to its default text. */
    @DELETE("$V1/message-templates/{key}")
    suspend fun deleteTemplate(@Path("key") key: String): TemplatesResponse

    @GET("$V1/tasks/{task}/students/{student}/message-context")
    suspend fun messageContext(
        @Path("task") taskId: Long,
        @Path("student") studentId: Long,
    ): MessageContextResponse

    /** template (key), optional body (override) and task_ids (<= 15). */
    @POST("$V1/tasks/{task}/students/{student}/message")
    suspend fun composeMessage(
        @Path("task") taskId: Long,
        @Path("student") studentId: Long,
        @Body body: JsonObject,
    ): ComposeResponse
}
