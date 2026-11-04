package com.example.data.api

import com.example.data.model.AccountHealth
import com.example.data.model.ApiResponse
import com.example.data.model.AppwriteAccount
import com.example.data.model.ConnectRequest
import com.example.data.model.ProjectNode
import com.example.data.model.RoutingRule
import com.example.data.model.UsageMetric
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface MasterRouterApi {

  @GET("v1/accounts")
  suspend fun getAccounts(@Header("X-Master-API-Key") apiKey: String): ApiResponse<List<AppwriteAccount>>

  @POST("v1/accounts/manual")
  suspend fun connectAccount(
    @Header("X-Master-API-Key") apiKey: String,
    @Body request: ConnectRequest
  ): ApiResponse<AppwriteAccount>

  @POST("v1/accounts/{id}/test")
  suspend fun testConnection(
    @Header("X-Master-API-Key") apiKey: String,
    @Path("id") accountId: String
  ): ApiResponse<Map<String, Any>>

  @DELETE("v1/accounts/{id}")
  suspend fun deleteAccount(
    @Header("X-Master-API-Key") apiKey: String,
    @Path("id") accountId: String
  ): ApiResponse<Map<String, Boolean>>

  @GET("v1/projects")
  suspend fun getProjects(@Header("X-Master-API-Key") apiKey: String): ApiResponse<List<ProjectNode>>

  @GET("v1/health")
  suspend fun getHealth(@Header("X-Master-API-Key") apiKey: String): ApiResponse<List<AccountHealth>>

  @GET("v1/usage")
  suspend fun getUsage(@Header("X-Master-API-Key") apiKey: String): ApiResponse<UsageMetric>

  @GET("v1/routing/rules")
  suspend fun getRoutingRules(@Header("X-Master-API-Key") apiKey: String): ApiResponse<List<RoutingRule>>
}
