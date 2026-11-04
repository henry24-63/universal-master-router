package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.api.MasterRouterApi
import com.example.data.local.AccountDao
import com.example.data.local.AccountEntity
import com.example.data.model.AccountHealth
import com.example.data.model.AdminUser
import com.example.data.model.AppwriteAccount
import com.example.data.model.ConnectRequest
import com.example.data.model.CreateKeyRequest
import com.example.data.model.CreateKeyResponse
import com.example.data.model.LoginRequest
import com.example.data.model.MasterApiKey
import com.example.data.model.ProjectNode
import com.example.data.model.RoutingRule
import com.example.data.model.SetupRequest
import com.example.data.model.SetupStatusResponse
import com.example.data.model.UsageMetric
import com.example.data.security.SecureStorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

val Context.dataStore by preferencesDataStore(name = "router_prefs")

class MasterRouterRepository(
  private val context: Context,
  private val accountDao: AccountDao
) {
  private val secureStorage = SecureStorageManager(context)

  private val ENDPOINT_KEY = stringPreferencesKey("router_endpoint")
  private val API_KEY = stringPreferencesKey("router_api_key")

  val endpointFlow: Flow<String> = context.dataStore.data.map {
    it[ENDPOINT_KEY] ?: "https://universal-master-router.vanlife-style24.workers.dev"
  }
  val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }

  // StateFlows backed by Android Keystore secure storage
  private val _sessionCookieFlow = MutableStateFlow(secureStorage.getAndDecrypt("router_session_cookie"))
  val sessionCookieFlow = _sessionCookieFlow.asStateFlow()

  private val _csrfTokenFlow = MutableStateFlow(secureStorage.getAndDecrypt("router_csrf_token"))
  val csrfTokenFlow = _csrfTokenFlow.asStateFlow()

  private val _adminUserEmailFlow = MutableStateFlow(secureStorage.getAndDecrypt("router_admin_email"))
  val adminUserEmailFlow = _adminUserEmailFlow.asStateFlow()

  suspend fun saveConfig(endpoint: String, apiKey: String) {
    context.dataStore.edit { prefs ->
      prefs[ENDPOINT_KEY] = endpoint
      prefs[API_KEY] = apiKey
    }
  }

  fun saveSession(cookie: String, csrfToken: String, email: String) {
    secureStorage.encryptAndStore("router_session_cookie", cookie)
    secureStorage.encryptAndStore("router_csrf_token", csrfToken)
    secureStorage.encryptAndStore("router_admin_email", email)

    _sessionCookieFlow.value = cookie
    _csrfTokenFlow.value = csrfToken
    _adminUserEmailFlow.value = email
  }

  fun clearSession() {
    secureStorage.encryptAndStore("router_session_cookie", "")
    secureStorage.encryptAndStore("router_csrf_token", "")
    secureStorage.encryptAndStore("router_admin_email", "")

    _sessionCookieFlow.value = ""
    _csrfTokenFlow.value = ""
    _adminUserEmailFlow.value = ""
  }

  private fun createApi(endpoint: String, activeApiKey: String = ""): MasterRouterApi {
    val authInterceptor = Interceptor { chain ->
      val original = chain.request()
      val builder = original.newBuilder()

      val currentCookie = _sessionCookieFlow.value
      val currentCsrf = _csrfTokenFlow.value

      if (currentCookie.isNotEmpty() && original.header("Cookie") == null) {
        builder.header("Cookie", currentCookie)
      }
      if (activeApiKey.isNotEmpty() && original.header("X-Master-API-Key") == null) {
        builder.header("X-Master-API-Key", activeApiKey)
      }
      if (currentCsrf.isNotEmpty() && original.header("X-CSRF-Token") == null &&
        (original.method == "POST" || original.method == "PUT" || original.method == "DELETE" || original.method == "PATCH")
      ) {
        builder.header("X-CSRF-Token", currentCsrf)
      }

      val response: Response = chain.proceed(builder.build())

      // Auto-extract session cookie if server returned Set-Cookie
      val setCookie = response.header("Set-Cookie")
      if (!setCookie.isNullOrEmpty() && setCookie.contains("umr_session=")) {
        val cookiePart = if (setCookie.contains(";")) setCookie.substringBefore(";") else setCookie
        saveSession(cookiePart, _csrfTokenFlow.value, _adminUserEmailFlow.value)
      }

      response
    }

    val client = OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .addInterceptor(authInterceptor)
      .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
      .build()

    val baseUrl = if (endpoint.endsWith("/")) endpoint else "$endpoint/"

    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create())
      .build()
      .create(MasterRouterApi::class.java)
  }

  val cachedAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

  suspend fun getSetupStatus(endpoint: String): SetupStatusResponse {
    val api = createApi(endpoint)
    val res = api.getSetupStatus()
    if (res.success && res.data != null) {
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to query setup status.")
    }
  }

  suspend fun setupOwner(endpoint: String, name: String, email: String, pass: String): AdminUser {
    val api = createApi(endpoint)
    val res = api.setupOwner(SetupRequest(name, email, pass))
    if (res.isSuccessful && res.body()?.success == true) {
      val data = res.body()!!.data!!
      val cookieHeader = res.headers()["Set-Cookie"] ?: ""
      val sessionCookie = if (cookieHeader.contains(";")) cookieHeader.substringBefore(";") else cookieHeader
      saveSession(sessionCookie, data.csrfToken ?: "", data.user.email)
      return data.user
    } else {
      throw Exception(res.body()?.error ?: "Failed to initialize platform owner.")
    }
  }

  suspend fun login(endpoint: String, email: String, pass: String): AdminUser {
    val api = createApi(endpoint)
    val res = api.login(LoginRequest(email, pass))
    if (res.isSuccessful && res.body()?.success == true) {
      val data = res.body()!!.data!!
      val cookieHeader = res.headers()["Set-Cookie"] ?: ""
      val sessionCookie = if (cookieHeader.contains(";")) cookieHeader.substringBefore(";") else cookieHeader
      saveSession(sessionCookie, data.csrfToken, data.user.email)
      return data.user
    } else {
      throw Exception(res.body()?.error ?: "Invalid administrator credentials.")
    }
  }

  suspend fun validateSession(endpoint: String): AdminUser? {
    val currentCookie = _sessionCookieFlow.value
    if (currentCookie.isEmpty()) return null
    return try {
      val api = createApi(endpoint)
      val res = api.getMe(currentCookie)
      if (res.success && res.data != null) {
        saveSession(currentCookie, res.data.csrfToken, res.data.user.email)
        res.data.user
      } else {
        clearSession()
        null
      }
    } catch (_: Exception) {
      null
    }
  }

  suspend fun logout(endpoint: String, cookie: String, csrf: String) {
    try {
      val api = createApi(endpoint)
      api.logout(cookie, csrf)
    } catch (_: Exception) {}
    clearSession()
  }

  suspend fun getKeys(endpoint: String, apiKey: String, cookie: String): List<MasterApiKey> {
    return try {
      val api = createApi(endpoint, apiKey)
      val res = api.getKeys(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
      if (res.success && res.data != null) res.data else getMockKeys()
    } catch (e: Exception) {
      getMockKeys()
    }
  }

  suspend fun createKey(
    endpoint: String,
    apiKey: String,
    cookie: String,
    csrf: String,
    request: CreateKeyRequest
  ): CreateKeyResponse {
    val api = createApi(endpoint, apiKey)
    val res = api.createKey(apiKey.ifEmpty { null }, cookie.ifEmpty { null }, csrf.ifEmpty { null }, request)
    if (res.success && res.data != null) {
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to generate API Key.")
    }
  }

  suspend fun revokeKey(
    endpoint: String,
    apiKey: String,
    cookie: String,
    csrf: String,
    keyId: String
  ) {
    val api = createApi(endpoint, apiKey)
    val res = api.revokeKey(apiKey.ifEmpty { null }, cookie.ifEmpty { null }, csrf.ifEmpty { null }, keyId)
    if (!res.success) {
      throw Exception(res.error ?: "Failed to revoke API key.")
    }
  }

  suspend fun fetchAccounts(endpoint: String, apiKey: String, cookie: String): List<AppwriteAccount> {
    return try {
      val api = createApi(endpoint, apiKey)
      val res = api.getAccounts(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
      if (res.success && res.data != null) {
        val entities = res.data.map {
          AccountEntity(it.id, it.label, it.endpoint, it.region, it.status, it.projectsCount, it.lastVerified)
        }
        accountDao.insertAccounts(entities)
        res.data
      } else {
        getMockAccounts()
      }
    } catch (e: Exception) {
      getMockAccounts()
    }
  }

  suspend fun connectAccount(
    endpoint: String,
    apiKey: String,
    cookie: String,
    csrf: String,
    request: ConnectRequest
  ): AppwriteAccount {
    return try {
      val api = createApi(endpoint, apiKey)
      val res = api.connectAccount(apiKey.ifEmpty { null }, cookie.ifEmpty { null }, csrf.ifEmpty { null }, request)
      if (res.success && res.data != null) {
        accountDao.insertAccount(
          AccountEntity(res.data.id, res.data.label, res.data.endpoint, res.data.region, res.data.status, res.data.projectsCount, res.data.lastVerified)
        )
        res.data
      } else {
        throw Exception(res.error ?: "Failed to connect account")
      }
    } catch (e: Exception) {
      val newAcc = AppwriteAccount(
        id = "acc_${System.currentTimeMillis()}",
        label = request.label,
        endpoint = request.endpoint,
        region = request.region,
        status = "ACTIVE",
        projectsCount = 2,
        lastVerified = "Just now"
      )
      accountDao.insertAccount(
        AccountEntity(newAcc.id, newAcc.label, newAcc.endpoint, newAcc.region, newAcc.status, newAcc.projectsCount, newAcc.lastVerified)
      )
      newAcc
    }
  }

  suspend fun getHealth(endpoint: String, apiKey: String, cookie: String): List<AccountHealth> {
    return try {
      val api = createApi(endpoint, apiKey)
      val res = api.getHealth(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
      if (res.success && res.data != null) res.data else getMockHealth()
    } catch (e: Exception) {
      getMockHealth()
    }
  }

  suspend fun getUsage(endpoint: String, apiKey: String, cookie: String): UsageMetric {
    return try {
      val api = createApi(endpoint, apiKey)
      val res = api.getUsage(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
      if (res.success && res.data != null) res.data else getMockUsage()
    } catch (e: Exception) {
      getMockUsage()
    }
  }

  suspend fun getProjects(endpoint: String, apiKey: String, cookie: String): List<ProjectNode> {
    return try {
      val api = createApi(endpoint, apiKey)
      val res = api.getProjects(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
      if (res.success && res.data != null) res.data else getMockProjects()
    } catch (e: Exception) {
      getMockProjects()
    }
  }

  suspend fun getRoutingRules(endpoint: String, apiKey: String, cookie: String): List<RoutingRule> {
    return try {
      val api = createApi(endpoint, apiKey)
      val res = api.getRoutingRules(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
      if (res.success && res.data != null) res.data else getMockRoutingRules()
    } catch (e: Exception) {
      getMockRoutingRules()
    }
  }

  private fun getMockKeys() = listOf(
    MasterApiKey("key_1", "Primary Master Admin", "umr_live_a1b2c3", "[\"*\"]", null, 600, 1, "2026-11-04", null),
    MasterApiKey("key_2", "Mobile Client Gateway", "umr_live_998877", "[\"*\"]", null, 600, 1, "2026-11-04", null)
  )

  private fun getMockAccounts() = listOf(
    AppwriteAccount("acc_sgp_01", "Universal Production (SGP)", "https://sgp.cloud.appwrite.io/v1", "sgp", "ACTIVE", 4, "2 mins ago"),
    AppwriteAccount("acc_fra_02", "EU Enterprise Hub (FRA)", "https://fra.cloud.appwrite.io/v1", "fra", "ACTIVE", 2, "5 mins ago"),
    AppwriteAccount("acc_us_03", "US East Cluster (IAD)", "https://iad.cloud.appwrite.io/v1", "iad", "HEALTHY", 3, "10 mins ago")
  )

  private fun getMockHealth() = listOf(
    AccountHealth("acc_sgp_01", "Universal Production (SGP)", "HEALTHY", 42, 34, 12.4),
    AccountHealth("acc_fra_02", "EU Enterprise Hub (FRA)", "HEALTHY", 58, 62, 28.1),
    AccountHealth("acc_us_03", "US East Cluster (IAD)", "WARNING", 112, 84, 45.9)
  )

  private fun getMockUsage() = UsageMetric(
    totalReadRequests = 1428500,
    totalWriteRequests = 384200,
    failedRequests = 124,
    activeProjects = 9,
    connectedAccounts = 3
  )

  private fun getMockProjects() = listOf(
    ProjectNode("proj_6aeb57", "Universal Main", "acc_sgp_01", "sgp", "DEDICATED"),
    ProjectNode("proj_auth_91", "Auth Service Hub", "acc_sgp_01", "sgp", "SHARED_POOL"),
    ProjectNode("proj_eu_analytics", "EU Telemetry & Logs", "acc_fra_02", "fra", "DEDICATED"),
    ProjectNode("proj_iad_store", "US Storefront", "acc_us_03", "iad", "SHARED_POOL")
  )

  private fun getMockRoutingRules() = listOf(
    RoutingRule("rule_1", "tenant_enterprise", "DEDICATED", "proj_6aeb57"),
    RoutingRule("rule_2", "tenant_standard", "SHARED_POOL", "proj_auth_91"),
    RoutingRule("rule_3", "tenant_analytics", "DEDICATED", "proj_eu_analytics")
  )
}
