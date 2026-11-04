package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.RouterDatabase
import com.example.data.model.AccountHealth
import com.example.data.model.AppwriteAccount
import com.example.data.model.ConnectRequest
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
    viewModelScope, SharingStarted.WhileSubscribed(5000), "https://router.api.universal.internal"
  )
  val apiKey: StateFlow<String> = repository.apiKeyFlow.stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5000), ""
  )

  val cachedAccounts = repository.cachedAccounts

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

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  init {
    refreshData()
  }

  fun saveConfig(newEndpoint: String, newApiKey: String) {
    viewModelScope.launch {
      repository.saveConfig(newEndpoint, newApiKey)
      refreshData()
    }
  }

  fun refreshData() {
    viewModelScope.launch {
      _isLoading.value = true
      _errorMessage.value = null
      try {
        val ep = endpoint.value
        val key = apiKey.value
        _accounts.value = repository.fetchAccounts(ep, key)
        _healthList.value = repository.getHealth(ep, key)
        _usage.value = repository.getUsage(ep, key)
        _projects.value = repository.getProjects(ep, key)
        _routingRules.value = repository.getRoutingRules(ep, key)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to synchronize with Master Router"
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
        repository.connectAccount(endpoint.value, apiKey.value, req)
        refreshData()
        onSuccess()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Connection failed"
      } finally {
        _isLoading.value = false
      }
    }
  }
}
