package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MasterRouterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: MasterRouterViewModel) {
  val hasAdmin by viewModel.hasAdmin.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val errorMessage by viewModel.errorMessage.collectAsState()
  val setupWarning by viewModel.setupWarning.collectAsState()
  val currentEndpoint by viewModel.endpoint.collectAsState()
  val currentApiKey by viewModel.apiKey.collectAsState()

  var nameInput by remember { mutableStateOf("") }
  var emailInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var confirmPasswordInput by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmPasswordVisible by remember { mutableStateOf(false) }

  var showEndpointConfig by remember { mutableStateOf(false) }
  var endpointInput by remember(currentEndpoint) { mutableStateOf(currentEndpoint) }
  var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Universal Master Router", fontWeight = FontWeight.Bold, fontSize = 18.sp)
          }
        },
        actions = {
          IconButton(onClick = { showEndpointConfig = !showEndpointConfig }) {
            Icon(Icons.Default.Settings, contentDescription = "Endpoint Configuration")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {

      // Endpoint Config Card (Collapsible)
      if (showEndpointConfig) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cloudflare Worker Endpoint", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
              value = endpointInput,
              onValueChange = { endpointInput = it },
              label = { Text("Worker URL") },
              modifier = Modifier.fillMaxWidth().testTag("auth_endpoint_input")
            )
            OutlinedTextField(
              value = apiKeyInput,
              onValueChange = { apiKeyInput = it },
              label = { Text("Master API Key (Optional)") },
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("auth_apikey_input")
            )
            Button(
              onClick = {
                viewModel.saveConfig(endpointInput, apiKeyInput)
                showEndpointConfig = false
              },
              modifier = Modifier.fillMaxWidth().testTag("save_auth_endpoint_btn")
            ) {
              Text("Save & Reconnect")
            }
          }
        }
      }

      // Warning or Error Box
      if (setupWarning != null || errorMessage != null) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (setupWarning != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.errorContainer
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Text("Backend Connection Notice", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(
              setupWarning ?: (errorMessage ?: ""),
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
              onClick = { viewModel.checkAuthAndSetupStatus() },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.align(Alignment.End).testTag("retry_connection_btn")
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Retry Connection")
            }
          }
        }
      }

      if (hasAdmin == null && isLoading) {
        // Initial Checking State
        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text("Checking Master Router status...", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else if (hasAdmin == false) {
        // First-Time Administrator Setup Screen
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
              }
              Column {
                Text("Initialize Platform Owner", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("First-time setup for Master Router", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }

            Text(
              "No administrator account exists yet. Create your platform owner account to secure and initialize the Universal Master Router.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 18.sp
            )

            OutlinedTextField(
              value = nameInput,
              onValueChange = { nameInput = it },
              label = { Text("Full Name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("setup_name_input")
            )

            OutlinedTextField(
              value = emailInput,
              onValueChange = { emailInput = it },
              label = { Text("Admin Email Address") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("setup_email_input")
            )

            OutlinedTextField(
              value = passwordInput,
              onValueChange = { passwordInput = it },
              label = { Text("Password (min 12 characters)") },
              singleLine = true,
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                  Icon(
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility"
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("setup_password_input")
            )

            OutlinedTextField(
              value = confirmPasswordInput,
              onValueChange = { confirmPasswordInput = it },
              label = { Text("Confirm Password") },
              singleLine = true,
              visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                  Icon(
                    if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle confirm password visibility"
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("setup_confirm_password_input")
            )

            Button(
              onClick = {
                viewModel.setupOwner(nameInput, emailInput, passwordInput, confirmPasswordInput) {
                  passwordInput = ""
                  confirmPasswordInput = ""
                }
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setup_submit_btn")
            ) {
              if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Initializing Platform Owner...")
              } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Platform Owner")
              }
            }
          }
        }
      } else {
        // Normal Administrator Login Screen
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
              }
              Column {
                Text("Administrator Login", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Sign in to control plane", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }

            Text(
              "Enter your administrator credentials to manage connected Appwrite accounts, projects, routing rules, and API keys.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 18.sp
            )

            OutlinedTextField(
              value = emailInput,
              onValueChange = { emailInput = it },
              label = { Text("Admin Email Address") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("login_email_input")
            )

            OutlinedTextField(
              value = passwordInput,
              onValueChange = { passwordInput = it },
              label = { Text("Password") },
              singleLine = true,
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                  Icon(
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility"
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("login_password_input")
            )

            Button(
              onClick = {
                viewModel.login(emailInput, passwordInput) {
                  passwordInput = ""
                }
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("login_submit_btn")
            ) {
              if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Signing in...")
              } else {
                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign In Administrator")
              }
            }
          }
        }
      }

      // Security Notice Footer
      Text(
        "Universal Master Router • Enterprise Multi-Tenant Gateway",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
