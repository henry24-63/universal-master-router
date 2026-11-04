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
fun RoutingRulesScreen(viewModel: MasterRouterViewModel) {
  val rules by viewModel.routingRules.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Smart Routing Rules", fontWeight = FontWeight.Bold) },
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
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Text("Active Tenant Routing Map", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Configures how write and read traffic is intelligently distributed across connected project nodes.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
      }

      items(rules) { rule ->
        Card(
          modifier = Modifier.fillMaxWidth().testTag("rule_card_${rule.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("Tenant: ${rule.tenantId}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                Text(
                  text = rule.strategy,
                  color = MaterialTheme.colorScheme.tertiary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Target Project ID: ${rule.targetProjectId}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}
