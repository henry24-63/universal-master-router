package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.RouterDatabase
import com.example.data.model.AccountHealth
import com.example.data.model.AdminUser
import com.example.data.model.AppwriteAccount
import com.example.data.model.ConnectRequest
import com.example.data.model.CreateKeyRequest
import com.example.data.model.CreateKeyResponse
import com.example.data.model.MasterApiKey
import com.example.data.model.ProjectNode
import com.example.data.model.RoutingRule
import com.example.data.model.UsageMetric
import com.example.data.repository.MasterRouterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MasterRouterViewModel(application: Application) : AndroidViewModel(application) {
  private val database = RouterDatabase.getDatabase(application)
  private val repository = MasterRouterRepository(application, database.accountDao())

  val endpoint: StateFlow<String> = repository.endpointFlow.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), "https://universal-master-router.vanlife-style24.workers.dev"
  )
  val apiKey: StateFlow<String> = repository.apiKeyFlow.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), ""
  )
  val sessionCookie: StateFlow<String> = repository.sessionCookieFlow.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), ""
  )
  val csrfToken: StateFlow<String> = repository.csrfTokenFlow.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), ""
  )
  val adminEmail: StateFlow<String> = repository.adminUserEmailFlow.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), ""
  )

  val cachedAccounts = repository.cachedAccounts

  private val _hasAdmin = MutableStateFlow<Boolean?>(null)
  val hasAdmin: StateFlow<Boolean?> = _hasAdmin.asStateFlow()

  private val _currentUser = MutableStateFlow<AdminUser?>(null)
  val currentUser: StateFlow<AdminUser?> = _currentUser.asStateFlow()

  private val _isAuthInitialized = MutableStateFlow(false)
  val isAuthInitialized: StateFlow<Boolean> = _isAuthInitialized.asStateFlow()

  private val _setupWarning = MutableStateFlow<String?>(null)
  val setupWarning: StateFlow<String?> = _setupWarning.asStateFlow()

  private val _accounts = MutableStateFlow<List<AppwriteAccount>>(emptyList())
  val accounts: StateFlow<List<AppwriteAccount>> = _accounts.asStateFlow()

  private val _healthList = MutableStateFlow<List<AccountHealth>>(emptyList())
  val healthList: StateFlow<List<AccountHealth>> = _healthList.asStateFlow()

  private val _usage = MutableStateFlow<UsageMetric?>(null)
  val usage: StateFlow<UsageMetric?> = _usage.asStateFlow()

  private val _projects = MutableStateFlow<List<ProjectNode>>(emptyList())
  val projects: StateFlow<List<ProjectNode>> = _projects.asStateFlow()

  private val _routingRules = MutableStateFlow<List<RoutingRule>>(emptyList())
  val routingRules: StateFlow<List<RoutingRule>> = _routingRules.asStateFlow()

  private val _apiKeys = MutableStateFlow<List<MasterApiKey>>(emptyList())
  val apiKeys: StateFlow<List<MasterApiKey>> = _apiKeys.asStateFlow()

  private val _newlyGeneratedKey = MutableStateFlow<CreateKeyResponse?>(null)
  val newlyGeneratedKey: StateFlow<CreateKeyResponse?> = _newlyGeneratedKey.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _successMessage = MutableStateFlow<String?>(null)
  val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

  init {
    checkAuthAndSetupStatus()
  }

  fun checkAuthAndSetupStatus() {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null
      _setupWarning.value = null
      try {
        val ep = endpoint.value
        val setupStatus = repository.getSetupStatus(ep)
        _hasAdmin.value = setupStatus.hasAdmin
        _setupWarning.value = setupStatus.warning

        if (setupStatus.hasAdmin) {
          val validatedUser = repository.validateSession(ep)
          _currentUser.value = validatedUser
          if (validatedUser != null) {
            refreshData()
          }
        }
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to query Master Router status."
      } finally {
        _isAuthInitialized.value = true
        _isLoading.value = false
      }
    }
  }

  fun saveConfig(newEndpoint: String, newApiKey: String) {
    viewModelScope.launch {
      repository.saveConfig(newEndpoint, newApiKey)
      checkAuthAndSetupStatus()
    }
  }

  fun setupOwner(name: String, email: String, pass: String, confirmPass: String, onSuccess: () -> Unit) {
    val trimmedName = name.trim()
    val trimmedEmail = email.trim()

    if (trimmedName.isEmpty()) {
      _errorMessage.value = "Full name is required."
      return
    }
    val emailRegex = Regex("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}\$")
    if (!emailRegex.matches(trimmedEmail)) {
      _errorMessage.value = "Please enter a valid email address."
      return
    }
    if (pass.length < 12) {
      _errorMessage.value = "Password must be at least 12 characters long."
      return
    }
    if (pass != confirmPass) {
      _errorMessage.value = "Passwords do not match."
      return
    }

    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null
      try {
        val user = repository.setupOwner(endpoint.value, trimmedName, trimmedEmail, pass)
        _currentUser.value = user
        _hasAdmin.value = true
        _successMessage.value = "Platform owner account initialized successfully!"
        refreshData()
        onSuccess()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to initialize platform owner."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun login(email: String, pass: String, onSuccess: () -> Unit) {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null
      try {
        val user = repository.login(endpoint.value, email.trim(), pass)
        _currentUser.value = user
        _hasAdmin.value = true
        refreshData()
        onSuccess()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Login failed."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun logout() {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        repository.logout(endpoint.value, sessionCookie.value, csrfToken.value)
        _currentUser.value = null
        checkAuthAndSetupStatus()
      } catch (_: Exception) {
        _currentUser.value = null
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun refreshData() {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null
      try {
        val ep = endpoint.value
        val key = apiKey.value
        val cookie = sessionCookie.value
        _accounts.value = repository.fetchAccounts(ep, key, cookie)
        _healthList.value = repository.getHealth(ep, key, cookie)
        _usage.value = repository.getUsage(ep, key, cookie)
        _projects.value = repository.getProjects(ep, key, cookie)
        _routingRules.value = repository.getRoutingRules(ep, key, cookie)
        _apiKeys.value = repository.getKeys(ep, key, cookie)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to synchronize with Master Router"
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun generateKey(name: String, scopes: List<String>, tenantId: String?, rateLimitRpm: Int?, isAdmin: Boolean) {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null
      try {
        val req = CreateKeyRequest(name, scopes, tenantId, rateLimitRpm, isAdmin)
        val created = repository.createKey(endpoint.value, apiKey.value, sessionCookie.value, csrfToken.value, req)
        _newlyGeneratedKey.value = created
        _successMessage.value = "API key created successfully!"
        refreshData()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to generate API Key."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun dismissGeneratedKey() {
    _newlyGeneratedKey.value = null
  }

  fun revokeKey(keyId: String) {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null
      try {
        repository.revokeKey(endpoint.value, apiKey.value, sessionCookie.value, csrfToken.value, keyId)
        _successMessage.value = "API key revoked."
        refreshData()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to revoke key."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun connectNewAccount(label: String, endpointUrl: String, projId: String, secretKey: String, region: String, onSuccess: () -> Unit) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val req = ConnectRequest(label, endpointUrl, projId, secretKey, region)
        repository.connectAccount(endpoint.value, apiKey.value, sessionCookie.value, csrfToken.value, req)
        refreshData()
        onSuccess()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Connection failed"
      } finally {
        _isLoading.value = false
      }
    }
  }

  // Database Browser ViewModel State & Actions
  private val _databases = MutableStateFlow<List<com.example.data.model.AppwriteDatabase>>(emptyList())
  val databases: StateFlow<List<com.example.data.model.AppwriteDatabase>> = _databases.asStateFlow()

  private val _collections = MutableStateFlow<List<com.example.data.model.AppwriteCollection>>(emptyList())
  val collections: StateFlow<List<com.example.data.model.AppwriteCollection>> = _collections.asStateFlow()

  private val _documents = MutableStateFlow<List<Map<String, Any?>>>(emptyList())
  val documents: StateFlow<List<Map<String, Any?>>> = _documents.asStateFlow()

  fun fetchDatabases(accountId: String) {
    viewModelScope.launch {
      _isLoading.value = true
      _databases.value = emptyList()
      _collections.value = emptyList()
      _documents.value = emptyList()
      try {
        _databases.value = repository.getDatabases(endpoint.value, accountId, apiKey.value, sessionCookie.value)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to fetch databases."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun fetchCollections(accountId: String, dbId: String) {
    viewModelScope.launch {
      _isLoading.value = true
      _collections.value = emptyList()
      _documents.value = emptyList()
      try {
        _collections.value = repository.getCollections(endpoint.value, accountId, dbId, apiKey.value, sessionCookie.value)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to fetch collections."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun fetchDocuments(accountId: String, dbId: String, colId: String) {
    viewModelScope.launch {
      _isLoading.value = true
      _documents.value = emptyList()
      try {
        _documents.value = repository.getDocuments(endpoint.value, accountId, dbId, colId, apiKey.value, sessionCookie.value)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to fetch documents."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun createDocumentInDb(accountId: String, dbId: String, colId: String, docId: String?, dataMap: Map<String, Any?>, onSuccess: () -> Unit) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        repository.createDocument(endpoint.value, accountId, dbId, colId, docId, dataMap, apiKey.value, sessionCookie.value, csrfToken.value)
        _successMessage.value = "Document created successfully!"
        fetchDocuments(accountId, dbId, colId)
        onSuccess()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to create document."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun updateDocumentInDb(accountId: String, dbId: String, colId: String, docId: String, dataMap: Map<String, Any?>, onSuccess: () -> Unit) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        repository.updateDocument(endpoint.value, accountId, dbId, colId, docId, dataMap, apiKey.value, sessionCookie.value, csrfToken.value)
        _successMessage.value = "Document updated successfully!"
        fetchDocuments(accountId, dbId, colId)
        onSuccess()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to update document."
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun deleteDocumentInDb(accountId: String, dbId: String, colId: String, docId: String) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        repository.deleteDocument(endpoint.value, accountId, dbId, colId, docId, apiKey.value, sessionCookie.value, csrfToken.value)
        _successMessage.value = "Document deleted."
        fetchDocuments(accountId, dbId, colId)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to delete document."
      } finally {
        _isLoading.value = false
      }
    }
  }
}

