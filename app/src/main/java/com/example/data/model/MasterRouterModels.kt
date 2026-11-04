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

// Admin Authentication & Setup Models
@JsonClass(generateAdapter = true)
data class SetupStatusResponse(
  @Json(name = "has_admin") val hasAdmin: Boolean,
  @Json(name = "db_initialized") val dbInitialized: Boolean? = true,
  @Json(name = "warning") val warning: String? = null
)

@JsonClass(generateAdapter = true)
data class SetupRequest(
  @Json(name = "name") val name: String,
  @Json(name = "email") val email: String,
  @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class SetupResponse(
  @Json(name = "user") val user: AdminUser,
  @Json(name = "csrf_token") val csrfToken: String? = null,
  @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
  @Json(name = "email") val email: String,
  @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AdminUser(
  @Json(name = "id") val id: String,
  @Json(name = "email") val email: String,
  @Json(name = "name") val name: String,
  @Json(name = "is_owner") val isOwner: Boolean
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
  @Json(name = "user") val user: AdminUser,
  @Json(name = "csrf_token") val csrfToken: String
)

@JsonClass(generateAdapter = true)
data class AuthMeResponse(
  @Json(name = "user") val user: AdminUser,
  @Json(name = "csrf_token") val csrfToken: String
)

// Master Router API Key Management Models
@JsonClass(generateAdapter = true)
data class MasterApiKey(
  @Json(name = "id") val id: String,
  @Json(name = "name") val name: String,
  @Json(name = "prefix") val prefix: String,
  @Json(name = "scopes") val scopes: String?,
  @Json(name = "tenant_id") val tenantId: String?,
  @Json(name = "rate_limit_rpm") val rateLimitRpm: Int?,
  @Json(name = "is_admin") val isAdmin: Int?,
  @Json(name = "created_at") val createdAt: String,
  @Json(name = "revoked_at") val revokedAt: String?
)

@JsonClass(generateAdapter = true)
data class CreateKeyRequest(
  @Json(name = "name") val name: String,
  @Json(name = "scopes") val scopes: List<String>?,
  @Json(name = "tenant_id") val tenantId: String?,
  @Json(name = "rate_limit_rpm") val rateLimitRpm: Int?,
  @Json(name = "is_admin") val isAdmin: Boolean?
)

@JsonClass(generateAdapter = true)
data class CreateKeyResponse(
  @Json(name = "id") val id: String,
  @Json(name = "name") val name: String,
  @Json(name = "api_key") val apiKey: String, // Plaintext secret returned only once
  @Json(name = "prefix") val prefix: String,
  @Json(name = "is_admin") val isAdmin: Boolean
)
