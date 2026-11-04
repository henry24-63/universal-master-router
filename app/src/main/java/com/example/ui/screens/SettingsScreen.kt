package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MasterRouterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MasterRouterViewModel) {
  val currentEndpoint by viewModel.endpoint.collectAsState()
  val currentApiKey by viewModel.apiKey.collectAsState()

  var endpointInput by remember(currentEndpoint) { mutableStateOf(currentEndpoint) }
  var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
  var savedMessage by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Master Router Settings", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(MaterialTheme.colorScheme.background)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text("API Connection Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      Text("Connect the native Android app to your deployed Master Router API endpoint.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

      OutlinedTextField(
        value = endpointInput,
        onValueChange = { endpointInput = it },
        label = { Text("Master Router API Endpoint") },
        modifier = Modifier.fillMaxWidth().testTag("endpoint_input")
      )

      OutlinedTextField(
        value = apiKeyInput,
        onValueChange = { apiKeyInput = it },
        label = { Text("Master Router API Key") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().testTag("apikey_input")
      )

      Button(
        onClick = {
          viewModel.saveConfig(endpointInput, apiKeyInput)
          savedMessage = true
        },
        modifier = Modifier.fillMaxWidth().testTag("save_config_btn")
      ) {
        Icon(Icons.Default.Save, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save & Synchronize")
      }

      if (savedMessage) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "Configuration saved successfully! Synchronizing data...",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      Divider()
      Spacer(modifier = Modifier.height(8.dp))

      Text("Security & Credential Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      Text("Credentials are securely stored using Android Keystore encryption.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
