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
    val api = createApi(endpoint, apiKey)
    val res = api.getKeys(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) {
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to fetch API keys.")
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
    val api = createApi(endpoint, apiKey)
    val res = api.getAccounts(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) {
      val entities = res.data.map {
        AccountEntity(it.id, it.label, it.endpoint, it.region, it.status, it.projectsCount, it.lastVerified)
      }
      accountDao.insertAccounts(entities)
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to fetch connected accounts.")
    }
  }

  suspend fun connectAccount(
    endpoint: String,
    apiKey: String,
    cookie: String,
    csrf: String,
    request: ConnectRequest
  ): AppwriteAccount {
    val api = createApi(endpoint, apiKey)
    val res = api.connectAccount(apiKey.ifEmpty { null }, cookie.ifEmpty { null }, csrf.ifEmpty { null }, request)
    if (res.success && res.data != null) {
      accountDao.insertAccount(
        AccountEntity(res.data.id, res.data.label, res.data.endpoint, res.data.region, res.data.status, res.data.projectsCount, res.data.lastVerified)
      )
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to connect account.")
    }
  }

  suspend fun getHealth(endpoint: String, apiKey: String, cookie: String): List<AccountHealth> {
    val api = createApi(endpoint, apiKey)
    val res = api.getHealth(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) {
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to fetch account health metrics.")
    }
  }

  suspend fun getUsage(endpoint: String, apiKey: String, cookie: String): UsageMetric {
    val api = createApi(endpoint, apiKey)
    val res = api.getUsage(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) {
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to fetch usage metrics.")
    }
  }

  suspend fun getProjects(endpoint: String, apiKey: String, cookie: String): List<ProjectNode> {
    val api = createApi(endpoint, apiKey)
    val res = api.getProjects(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) {
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to fetch project nodes.")
    }
  }

  suspend fun getRoutingRules(endpoint: String, apiKey: String, cookie: String): List<RoutingRule> {
    val api = createApi(endpoint, apiKey)
    val res = api.getRoutingRules(apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) {
      return res.data
    } else {
      throw Exception(res.error ?: "Failed to fetch routing rules.")
    }
  }

  // Database Browser Methods
  suspend fun getDatabases(endpoint: String, accountId: String, apiKey: String, cookie: String): List<com.example.data.model.AppwriteDatabase> {
    val api = createApi(endpoint, apiKey)
    val res = api.getDatabases(accountId, apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) return res.data else throw Exception(res.error ?: "Failed to fetch databases.")
  }

  suspend fun getCollections(endpoint: String, accountId: String, dbId: String, apiKey: String, cookie: String): List<com.example.data.model.AppwriteCollection> {
    val api = createApi(endpoint, apiKey)
    val res = api.getCollections(dbId, accountId, apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) return res.data else throw Exception(res.error ?: "Failed to fetch collections.")
  }

  suspend fun getDocuments(endpoint: String, accountId: String, dbId: String, colId: String, apiKey: String, cookie: String): List<Map<String, Any?>> {
    val api = createApi(endpoint, apiKey)
    val res = api.getDocuments(dbId, colId, accountId, apiKey.ifEmpty { null }, cookie.ifEmpty { null })
    if (res.success && res.data != null) return res.data else throw Exception(res.error ?: "Failed to fetch documents.")
  }

  suspend fun createDocument(
    endpoint: String, accountId: String, dbId: String, colId: String,
    docId: String?, data: Map<String, Any?>, apiKey: String, cookie: String, csrf: String
  ): Map<String, Any?> {
    val api = createApi(endpoint, apiKey)
    val req = com.example.data.model.CreateDocumentApiRequest(docId, data)
    val res = api.createDocument(dbId, colId, accountId, apiKey.ifEmpty { null }, cookie.ifEmpty { null }, csrf.ifEmpty { null }, req)
    if (res.success && res.data != null) return res.data else throw Exception(res.error ?: "Failed to create document.")
  }

  suspend fun updateDocument(
    endpoint: String, accountId: String, dbId: String, colId: String,
    docId: String, data: Map<String, Any?>, apiKey: String, cookie: String, csrf: String
  ): Map<String, Any?> {
    val api = createApi(endpoint, apiKey)
    val req = com.example.data.model.UpdateDocumentApiRequest(data)
    val res = api.updateDocument(dbId, colId, docId, accountId, apiKey.ifEmpty { null }, cookie.ifEmpty { null }, csrf.ifEmpty { null }, req)
    if (res.success && res.data != null) return res.data else throw Exception(res.error ?: "Failed to update document.")
  }

  suspend fun deleteDocument(
    endpoint: String, accountId: String, dbId: String, colId: String,
    docId: String, apiKey: String, cookie: String, csrf: String
  ) {
    val api = createApi(endpoint, apiKey)
    val res = api.deleteDocument(dbId, colId, docId, accountId, apiKey.ifEmpty { null }, cookie.ifEmpty { null }, csrf.ifEmpty { null })
    if (!res.success) throw Exception(res.error ?: "Failed to delete document.")
  }
}

