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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountHealth
import com.example.data.model.UsageMetric
import com.example.ui.viewmodel.MasterRouterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MasterRouterViewModel, onNavigateToAccounts: () -> Unit) {
  val usage by viewModel.usage.collectAsState()
  val healthList by viewModel.healthList.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Universal Master Router", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Enterprise Control Plane", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          IconButton(onClick = { viewModel.refreshData() }, modifier = Modifier.testTag("refresh_button")) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(MaterialTheme.colorScheme.background),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (isLoading) {
        item {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("loading_indicator"))
        }
      }

      // Overview Metrics Grid
      item {
        Text("Traffic & Usage Overview", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          MetricCard(
            title = "Read Requests",
            value = usage?.totalReadRequests?.toString() ?: "1,428,500",
            icon = Icons.Default.Visibility,
            modifier = Modifier.weight(1f)
          )
          MetricCard(
            title = "Write Requests",
            value = usage?.totalWriteRequests?.toString() ?: "384,200",
            icon = Icons.Default.Edit,
            modifier = Modifier.weight(1f)
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          MetricCard(
            title = "Active Projects",
            value = usage?.activeProjects?.toString() ?: "9",
            icon = Icons.Default.Folder,
            modifier = Modifier.weight(1f)
          )
          MetricCard(
            title = "Connected Accounts",
            value = usage?.connectedAccounts?.toString() ?: "3",
            icon = Icons.Default.Cloud,
            modifier = Modifier.weight(1f)
          )
        }
      }

      // Account Health Section Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Account Health & Quotas", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
          TextButton(onClick = onNavigateToAccounts, modifier = Modifier.testTag("view_all_accounts_btn")) {
            Text("Manage")
          }
        }
      }

      // Health List
      items(healthList) { health ->
        HealthCard(health)
      }
    }
  }
}

@Composable
fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
  }
}

@Composable
fun HealthCard(health: AccountHealth) {
  val isHealthy = health.status == "HEALTHY"
  val badgeColor = if (isHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(health.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Surface(color = badgeColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
          Text(
            text = health.status,
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Latency: ${health.latencyMs}ms", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Quota: ${health.quotaUsagePercent}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(modifier = Modifier.height(6.dp))
      LinearProgressIndicator(
        progress = { health.quotaUsagePercent / 100f },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = badgeColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )
    }
  }
}
