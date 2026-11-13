package com.sdumsd11.easytracker.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Retrofit surface for backend/README.md (`/api` prefix is in the base URL). */
interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @GET("me")
    suspend fun me(): Response<UserDto>

    @GET("groups")
    suspend fun getGroups(): Response<List<GroupDto>>

    @POST("groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): Response<GroupDto>

    @GET("groups/{id}")
    suspend fun getGroup(@Path("id") id: String): Response<GroupDto>

    @PATCH("groups/{id}")
    suspend fun patchGroup(@Path("id") id: String, @Body body: PatchGroupRequest): Response<GroupDto>

    @DELETE("groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String): Response<Unit>

    @POST("groups/{id}/members")
    suspend fun addMember(@Path("id") id: String, @Body body: AddMemberRequest): Response<GroupDto>

    @DELETE("groups/{id}/members/{userId}")
    suspend fun removeMember(@Path("id") id: String, @Path("userId") userId: String): Response<Unit>

    @POST("groups/{id}/invites/respond")
    suspend fun respondToInvite(@Path("id") id: String, @Body body: InviteRespondRequest): Response<InviteDto>

    @GET("groups/{id}/expenses")
    suspend fun getExpenses(@Path("id") id: String): Response<List<ExpenseDto>>

    @POST("groups/{id}/expenses")
    suspend fun createExpense(@Path("id") id: String, @Body body: CreateExpenseRequest): Response<ExpenseDto>

    @GET("expenses/{id}")
    suspend fun getExpense(@Path("id") id: String): Response<ExpenseDto>

    @PATCH("expenses/{id}")
    suspend fun patchExpense(@Path("id") id: String, @Body body: PatchExpenseRequest): Response<ExpenseDto>

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String): Response<Unit>

    @GET("groups/{id}/summary")
    suspend fun getSummary(@Path("id") id: String): Response<SummaryDto>

    @POST("groups/{id}/payments")
    suspend fun recordPayment(@Path("id") id: String, @Body body: PaymentRequest): Response<PaymentResponse>
}
