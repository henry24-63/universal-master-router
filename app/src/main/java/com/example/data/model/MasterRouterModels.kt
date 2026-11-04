package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
  @Json(name = "success") val success: Boolean,
  @Json(name = "data") val data: T?,
  @Json(name = "error") val error: String?,
  @Json(name = "request_id") val requestId: String?
)

@JsonClass(generateAdapter = true)
data class AppwriteAccount(
  @Json(name = "id") val id: String,
  @Json(name = "label") val label: String,
  @Json(name = "endpoint") val endpoint: String,
  @Json(name = "region") val region: String,
  @Json(name = "status") val status: String, // ACTIVE, FROZEN, ERROR
  @Json(name = "projects_count") val projectsCount: Int,
  @Json(name = "last_verified") val lastVerified: String
)

@JsonClass(generateAdapter = true)
data class ProjectNode(
  @Json(name = "id") val id: String,
  @Json(name = "name") val name: String,
  @Json(name = "account_id") val accountId: String,
  @Json(name = "region") val region: String,
  @Json(name = "mode") val mode: String // DEDICATED, SHARED_POOL
)

@JsonClass(generateAdapter = true)
data class AccountHealth(
  @Json(name = "account_id") val accountId: String,
  @Json(name = "label") val label: String,
  @Json(name = "status") val status: String, // HEALTHY, WARNING, CRITICAL
  @Json(name = "latency_ms") val latencyMs: Int,
  @Json(name = "quota_usage_percent") val quotaUsagePercent: Int,
  @Json(name = "storage_used_gb") val storageUsedGb: Double
)

@JsonClass(generateAdapter = true)
data class UsageMetric(
  @Json(name = "total_read_requests") val totalReadRequests: Long,
  @Json(name = "total_write_requests") val totalWriteRequests: Long,
  @Json(name = "failed_requests") val failedRequests: Long,
  @Json(name = "active_projects") val activeProjects: Int,
  @Json(name = "connected_accounts") val connectedAccounts: Int
)

@JsonClass(generateAdapter = true)
data class RoutingRule(
  @Json(name = "id") val id: String,
  @Json(name = "tenant_id") val tenantId: String,
  @Json(name = "strategy") val strategy: String, // DEDICATED, SHARED_POOL, HYBRID
  @Json(name = "target_project_id") val targetProjectId: String
)

@JsonClass(generateAdapter = true)
data class ConnectRequest(
  @Json(name = "label") val label: String,
  @Json(name = "endpoint") val endpoint: String,
  @Json(name = "project_id") val projectId: String,
  @Json(name = "api_key") val apiKey: String,
  @Json(name = "region") val region: String
)
