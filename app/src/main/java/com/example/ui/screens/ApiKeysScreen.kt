package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MasterApiKey
import com.example.ui.viewmodel.MasterRouterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(viewModel: MasterRouterViewModel) {
  val context = LocalContext.current
  val apiKeys by viewModel.apiKeys.collectAsState()
  val newlyGeneratedKey by viewModel.newlyGeneratedKey.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()
  val successMessage by viewModel.successMessage.collectAsState()
  val sessionCookie by viewModel.sessionCookie.collectAsState()
  val adminEmail by viewModel.adminEmail.collectAsState()

  var showCreateDialog by remember { mutableStateOf(false) }
  var keyNameToCreate by remember { mutableStateOf("") }
  var tenantIdToCreate by remember { mutableStateOf("") }
  var rateLimitInput by remember { mutableStateOf("600") }
  var isAdminChecked by remember { mutableStateOf(false) }

  var keyToRevoke by remember { mutableStateOf<MasterApiKey?>(null) }
  var copyToastVisible by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Master API Keys", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
              if (adminEmail.isNotEmpty()) "Logged in as $adminEmail" else "Admin Authentication Required",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          IconButton(onClick = { viewModel.refreshData() }, modifier = Modifier.testTag("refresh_keys_btn")) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Keys")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showCreateDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("generate_key_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Generate Key", tint = MaterialTheme.colorScheme.onPrimary)
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(MaterialTheme.colorScheme.background),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (isLoading) {
        item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
      }

      if (errorMessage != null) {
        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(errorMessage ?: "", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
          }
        }
      }

      if (successMessage != null) {
        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(successMessage ?: "", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
          }
        }
      }

      // Security Notice Card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              "Master Router API Keys use SHA-256 hash verification. Plaintext keys are shown only once at generation.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      if (apiKeys.isEmpty() && !isLoading) {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No API keys found. Tap '+' to generate your first key.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      items(apiKeys) { key ->
        Card(
          modifier = Modifier.fillMaxWidth().testTag("key_card_${key.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text(key.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
              val isRevoked = key.revokedAt != null
              Surface(
                color = if (isRevoked) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
              ) {
                Text(
                  text = if (isRevoked) "REVOKED" else "ACTIVE",
                  color = if (isRevoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Prefix: ${key.prefix}...", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Role: ${if (key.isAdmin == 1) "Master Admin" else "Tenant Key"} • Rate Limit: ${key.rateLimitRpm ?: 600} rpm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (key.tenantId != null) {
              Text("Tenant: ${key.tenantId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Created: ${key.createdAt}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (key.revokedAt == null) {
              Spacer(modifier = Modifier.height(10.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                  onClick = { keyToRevoke = key },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                  Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Revoke Key")
                }
              }
            }
          }
        }
      }
    }

    // Modal: One-Time Plaintext Display & Clipboard Copy
    if (newlyGeneratedKey != null) {
      val keyData = newlyGeneratedKey!!
      AlertDialog(
        onDismissRequest = { viewModel.dismissGeneratedKey() },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("API Key Generated")
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              "Copy and save this key now. For security, it will NEVER be displayed again.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.error
            )
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = keyData.apiKey,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
              )
            }
            Button(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Master Router API Key", keyData.apiKey)
                clipboard.setPrimaryClip(clip)
                copyToastVisible = true
              },
              modifier = Modifier.fillMaxWidth().testTag("copy_key_btn")
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Copy to Clipboard")
            }
            if (copyToastVisible) {
              Text("Copied to clipboard!", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { viewModel.dismissGeneratedKey() }) {
            Text("I Have Saved My Secret")
          }
        }
      )
    }

    // Modal: Create API Key
    if (showCreateDialog) {
      AlertDialog(
        onDismissRequest = { showCreateDialog = false },
        title = { Text("Generate Master API Key") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = keyNameToCreate,
              onValueChange = { keyNameToCreate = it },
              label = { Text("Key Label / Description") },
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = tenantIdToCreate,
              onValueChange = { tenantIdToCreate = it },
              label = { Text("Tenant ID (Optional)") },
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = rateLimitInput,
              onValueChange = { rateLimitInput = it },
              label = { Text("Rate Limit (RPM)") },
              modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = isAdminChecked, onCheckedChange = { isAdminChecked = it })
              Spacer(modifier = Modifier.width(6.dp))
              Text("Grant Administrator Privileges", fontSize = 13.sp)
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (keyNameToCreate.isNotBlank()) {
                val rpm = rateLimitInput.toIntOrNull() ?: 600
                val tenant = if (tenantIdToCreate.isBlank()) null else tenantIdToCreate
                viewModel.generateKey(keyNameToCreate, listOf("*"), tenant, rpm, isAdminChecked)
                showCreateDialog = false
                keyNameToCreate = ""
                tenantIdToCreate = ""
              }
            },
            modifier = Modifier.testTag("submit_create_key_btn")
          ) {
            Text("Generate")
          }
        },
        dismissButton = {
          TextButton(onClick = { showCreateDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // Modal: Confirmation for Revoke
    if (keyToRevoke != null) {
      val key = keyToRevoke!!
      AlertDialog(
        onDismissRequest = { keyToRevoke = null },
        title = { Text("Revoke API Key?") },
        text = {
          Text("Are you sure you want to revoke '${key.name}' (${key.prefix}...)? Any client using this key will immediately be denied access.")
        },
        confirmButton = {
          Button(
            onClick = {
              viewModel.revokeKey(key.id)
              keyToRevoke = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("Revoke Now")
          }
        },
        dismissButton = {
          TextButton(onClick = { keyToRevoke = null }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}
