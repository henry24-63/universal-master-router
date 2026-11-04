package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MasterRouterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: MasterRouterViewModel) {
  val accounts by viewModel.accounts.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }

  var label by remember { mutableStateOf("") }
  var endpoint by remember { mutableStateOf("https://cloud.appwrite.io/v1") }
  var projectId by remember { mutableStateOf("") }
  var apiKey by remember { mutableStateOf("") }
  var region by remember { mutableStateOf("sgp") }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Connected Appwrite Accounts", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("add_account_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Account", tint = MaterialTheme.colorScheme.onPrimary)
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
      items(accounts) { account ->
        Card(
          modifier = Modifier.fillMaxWidth().testTag("account_card_${account.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text(account.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                Text(
                  text = account.status,
                  color = MaterialTheme.colorScheme.primary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Endpoint: ${account.endpoint}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Region: ${account.region.uppercase()} • Projects: ${account.projectsCount}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
              OutlinedButton(onClick = { viewModel.refreshData() }) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Connection")
              }
            }
          }
        }
      }
    }

    if (showAddDialog) {
      AlertDialog(
        onDismissRequest = { showAddDialog = false },
        title = { Text("Connect Appwrite Account") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Account Label") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Appwrite Endpoint") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = projectId, onValueChange = { projectId = it }, label = { Text("Default Project ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("Appwrite API Key") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text("Region (e.g. sgp, fra)") }, modifier = Modifier.fillMaxWidth())
          }
        },
        confirmButton = {
          Button(
            onClick = {
              viewModel.connectNewAccount(label, endpoint, projectId, apiKey, region) {
                showAddDialog = false
                label = ""
                projectId = ""
                apiKey = ""
              }
            },
            modifier = Modifier.testTag("submit_connect_btn")
          ) {
            Text("Connect")
          }
        },
        dismissButton = {
          TextButton(onClick = { showAddDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}
