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
fun DatabaseBrowserScreen(viewModel: MasterRouterViewModel) {
  val projects by viewModel.projects.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Smart Database Browser & Projects", fontWeight = FontWeight.Bold) },
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
        Text("Connected Project Nodes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Smart routing resolves write operations to the appropriate project node.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
      }

      items(projects) { project ->
        Card(
          modifier = Modifier.fillMaxWidth().testTag("project_card_${project.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(project.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("Project ID: ${project.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("Region: ${project.region.uppercase()} • Mode: ${project.mode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
              color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
              shape = MaterialTheme.shapes.small
            ) {
              Text(
                text = project.mode,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }
      }
    }
  }
}
