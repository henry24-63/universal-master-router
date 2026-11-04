package com.example.data.api

import com.example.data.model.AccountHealth
import com.example.data.model.ApiResponse
import com.example.data.model.AppwriteAccount
import com.example.data.model.AuthMeResponse
import com.example.data.model.ConnectRequest
import com.example.data.model.CreateKeyRequest
import com.example.data.model.CreateKeyResponse
import com.example.data.model.LoginRequest
import com.example.data.model.LoginResponse
import com.example.data.model.MasterApiKey
import com.example.data.model.ProjectNode
import com.example.data.model.RoutingRule
import com.example.data.model.UsageMetric
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

import com.example.data.model.SetupRequest
import com.example.data.model.SetupResponse
import com.example.data.model.SetupStatusResponse

interface MasterRouterApi {

  // Authentication & Setup
  @GET("v1/auth/setup-status")
  suspend fun getSetupStatus(): ApiResponse<SetupStatusResponse>

  @POST("v1/auth/setup")
  suspend fun setupOwner(@Body request: SetupRequest): Response<ApiResponse<SetupResponse>>

  @POST("v1/auth/login")
  suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

  @POST("v1/auth/logout")
  suspend fun logout(
    @Header("Cookie") sessionCookie: String?,
    @Header("X-CSRF-Token") csrfToken: String?
  ): ApiResponse<Map<String, Boolean>>

  @GET("v1/auth/me")
  suspend fun getMe(@Header("Cookie") sessionCookie: String?): ApiResponse<AuthMeResponse>

  // Accounts
  @GET("v1/accounts")
  suspend fun getAccounts(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?
  ): ApiResponse<List<AppwriteAccount>>

  @POST("v1/accounts/manual")
  suspend fun connectAccount(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?,
    @Header("X-CSRF-Token") csrfToken: String?,
    @Body request: ConnectRequest
  ): ApiResponse<AppwriteAccount>

  @POST("v1/accounts/{id}/test")
  suspend fun testConnection(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?,
    @Header("X-CSRF-Token") csrfToken: String?,
    @Path("id") accountId: String
  ): ApiResponse<Map<String, Any>>

  @DELETE("v1/accounts/{id}")
  suspend fun deleteAccount(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?,
    @Header("X-CSRF-Token") csrfToken: String?,
    @Path("id") accountId: String
  ): ApiResponse<Map<String, Boolean>>

  // Projects
  @GET("v1/projects")
  suspend fun getProjects(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?
  ): ApiResponse<List<ProjectNode>>

  // Health & Usage
  @GET("v1/health")
  suspend fun getHealth(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?
  ): ApiResponse<List<AccountHealth>>

  @GET("v1/usage")
  suspend fun getUsage(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?
  ): ApiResponse<UsageMetric>

  // Routing Rules
  @GET("v1/routing/rules")
  suspend fun getRoutingRules(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?
  ): ApiResponse<List<RoutingRule>>

  // Master Router API Key Management
  @GET("v1/keys")
  suspend fun getKeys(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?
  ): ApiResponse<List<MasterApiKey>>

  @POST("v1/keys")
  suspend fun createKey(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?,
    @Header("X-CSRF-Token") csrfToken: String?,
    @Body request: CreateKeyRequest
  ): ApiResponse<CreateKeyResponse>

  @POST("v1/keys/{id}/revoke")
  suspend fun revokeKey(
    @Header("X-Master-API-Key") apiKey: String?,
    @Header("Cookie") sessionCookie: String?,
    @Header("X-CSRF-Token") csrfToken: String?,
    @Path("id") keyId: String
  ): ApiResponse<Map<String, Any>>
}
