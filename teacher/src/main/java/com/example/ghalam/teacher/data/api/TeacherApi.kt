package com.example.ghalam.teacher.data.api

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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
}
