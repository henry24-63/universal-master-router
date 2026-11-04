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
  val adminEmail by viewModel.adminEmail.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()

  var endpointInput by remember(currentEndpoint) { mutableStateOf(currentEndpoint) }
  var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
  var savedMessage by remember { mutableStateOf(false) }

  var loginEmailInput by remember { mutableStateOf("") }
  var loginPasswordInput by remember { mutableStateOf("") }

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
      // Admin Account Section
      val currentUser by viewModel.currentUser.collectAsState()
      Text("Administrator Authentication", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      if (currentUser != null || adminEmail.isNotEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(
                if (currentUser?.isOwner == true) "Platform Owner Account" else "Administrator Account",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
            if (currentUser != null) {
              Text("Name: ${currentUser?.name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("Email: ${currentUser?.email ?: adminEmail}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = { viewModel.logout() },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
              Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Log Out Administrator")
            }
          }
        }
      } else {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sign in with your Administrator account to manage API keys, accounts, and routing.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
              value = loginEmailInput,
              onValueChange = { loginEmailInput = it },
              label = { Text("Admin Email") },
              modifier = Modifier.fillMaxWidth().testTag("admin_email_input")
            )
            OutlinedTextField(
              value = loginPasswordInput,
              onValueChange = { loginPasswordInput = it },
              label = { Text("Admin Password") },
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("admin_password_input")
            )
            if (errorMessage != null) {
              Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Button(
              onClick = {
                viewModel.login(loginEmailInput, loginPasswordInput) {
                  loginPasswordInput = ""
                }
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().testTag("admin_login_btn")
            ) {
              Text(if (isLoading) "Signing in..." else "Sign In Administrator")
            }
          }
        }
      }

      Divider()

      // Endpoint & API Key Configuration
      Text("Worker API Endpoint", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      OutlinedTextField(
        value = endpointInput,
        onValueChange = { endpointInput = it },
        label = { Text("Cloudflare Worker URL") },
        modifier = Modifier.fillMaxWidth().testTag("endpoint_input")
      )

      OutlinedTextField(
        value = apiKeyInput,
        onValueChange = { apiKeyInput = it },
        label = { Text("Client API Key (Optional if logged in)") },
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
        Text("Save Endpoint Configuration")
      }

      if (savedMessage) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "Endpoint saved and synchronized with Cloudflare Worker.",
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
          )
        }
      }
    }
  }
}
