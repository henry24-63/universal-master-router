package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppwriteAccount
import com.example.data.model.AppwriteCollection
import com.example.data.model.AppwriteDatabase
import com.example.ui.viewmodel.MasterRouterViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseBrowserScreen(viewModel: MasterRouterViewModel) {
  val accounts by viewModel.accounts.collectAsState()
  val databases by viewModel.databases.collectAsState()
  val collections by viewModel.collections.collectAsState()
  val documents by viewModel.documents.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()

  var selectedAccount by remember { mutableStateOf<AppwriteAccount?>(null) }
  var selectedDatabase by remember { mutableStateOf<AppwriteDatabase?>(null) }
  var selectedCollection by remember { mutableStateOf<AppwriteCollection?>(null) }

  var showDocDialog by remember { mutableStateOf(false) }
  var editingDocId by remember { mutableStateOf<String?>(null) }
  var inputDocId by remember { mutableStateOf("") }
  var inputJsonData by remember { mutableStateOf("{\n  \"title\": \"Sample Document\"\n}") }
  var jsonError by remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Appwrite Database Browser", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        actions = {
          if (selectedCollection != null) {
            IconButton(
              onClick = {
                editingDocId = null
                inputDocId = ""
                inputJsonData = "{\n  \"title\": \"Sample Document\"\n}"
                jsonError = null
                showDocDialog = true
              },
              modifier = Modifier.testTag("create_document_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = "Create Document")
            }
          }
        }
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
      // 1. Account Selector Dropdown
      var accountExpanded by remember { mutableStateOf(false) }
      ExposedDropdownMenuBox(
        expanded = accountExpanded,
        onExpandedChange = { accountExpanded = !accountExpanded }
      ) {
        OutlinedTextField(
          value = selectedAccount?.let { "${it.label} (${it.endpoint})" } ?: "Select Connected Account",
          onValueChange = {},
          readOnly = true,
          label = { Text("Appwrite Upstream Account") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
          modifier = Modifier.menuAnchor().fillMaxWidth().testTag("account_dropdown")
        )
        ExposedDropdownMenu(
          expanded = accountExpanded,
          onDismissRequest = { accountExpanded = false }
        ) {
          if (accounts.isEmpty()) {
            DropdownMenuItem(
              text = { Text("No accounts connected") },
              onClick = { accountExpanded = false }
            )
          } else {
            accounts.forEach { acc ->
              DropdownMenuItem(
                text = { Text("${acc.label} (${acc.endpoint})") },
                onClick = {
                  selectedAccount = acc
                  selectedDatabase = null
                  selectedCollection = null
                  accountExpanded = false
                  viewModel.fetchDatabases(acc.id)
                }
              )
            }
          }
        }
      }

      // 2. Database & Collection Pickers
      if (selectedAccount != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
          // Database Dropdown
          var dbExpanded by remember { mutableStateOf(false) }
          ExposedDropdownMenuBox(
            expanded = dbExpanded,
            onExpandedChange = { dbExpanded = !dbExpanded },
            modifier = Modifier.weight(1f)
          ) {
            OutlinedTextField(
              value = selectedDatabase?.name ?: "Select Database",
              onValueChange = {},
              readOnly = true,
              label = { Text("Database") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dbExpanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth().testTag("database_dropdown")
            )
            ExposedDropdownMenu(
              expanded = dbExpanded,
              onDismissRequest = { dbExpanded = false }
            ) {
              if (databases.isEmpty()) {
                DropdownMenuItem(
                  text = { Text("No databases found") },
                  onClick = { dbExpanded = false }
                )
              } else {
                databases.forEach { db ->
                  DropdownMenuItem(
                    text = { Text(db.name) },
                    onClick = {
                      selectedDatabase = db
                      selectedCollection = null
                      dbExpanded = false
                      selectedAccount?.let { viewModel.fetchCollections(it.id, db.id) }
                    }
                  )
                }
              }
            }
          }

          // Collection Dropdown
          var colExpanded by remember { mutableStateOf(false) }
          ExposedDropdownMenuBox(
            expanded = colExpanded,
            onExpandedChange = { if (selectedDatabase != null) colExpanded = !colExpanded },
            modifier = Modifier.weight(1f)
          ) {
            OutlinedTextField(
              value = selectedCollection?.name ?: "Select Collection",
              onValueChange = {},
              readOnly = true,
              enabled = selectedDatabase != null,
              label = { Text("Collection") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colExpanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth().testTag("collection_dropdown")
            )
            ExposedDropdownMenu(
              expanded = colExpanded,
              onDismissRequest = { colExpanded = false }
            ) {
              if (collections.isEmpty()) {
                DropdownMenuItem(
                  text = { Text("No collections found") },
                  onClick = { colExpanded = false }
                )
              } else {
                collections.forEach { col ->
                  DropdownMenuItem(
                    text = { Text(col.name) },
                    onClick = {
                      selectedCollection = col
                      colExpanded = false
                      selectedAccount?.let { acc ->
                        selectedDatabase?.let { db ->
                          viewModel.fetchDocuments(acc.id, db.id, col.id)
                        }
                      }
                    }
                  )
                }
              }
            }
          }
        }
      }

      // 3. Document List View
      if (selectedCollection == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = if (selectedAccount == null) "Select an upstream Appwrite account to begin browsing."
            else if (selectedDatabase == null) "Select a database."
            else "Select a collection to view and manage documents.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Documents (${documents.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Button(
            onClick = {
              editingDocId = null
              inputDocId = ""
              inputJsonData = "{\n  \"title\": \"Sample Document\"\n}"
              jsonError = null
              showDocDialog = true
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("add_doc_fab")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Document", fontSize = 13.sp)
          }
        }

        if (isLoading && documents.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        } else if (documents.isEmpty()) {
          Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
          ) {
            Text(
              "No documents exist in this collection. Click 'Add Document' to insert a record.",
              modifier = Modifier.padding(24.dp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 13.sp
            )
          }
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
          ) {
            items(documents) { docMap ->
              val docId = docMap["\$id"]?.toString() ?: "unknown"
              val filtered = docMap.filterKeys { !it.startsWith("\$") }
              val jsonString = JSONObject(filtered).toString(2)

              Card(
                modifier = Modifier.fillMaxWidth().testTag("document_card_$docId"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text("ID: $docId", fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Row {
                      IconButton(
                        onClick = {
                          editingDocId = docId
                          inputDocId = docId
                          inputJsonData = jsonString
                          jsonError = null
                          showDocDialog = true
                        },
                        modifier = Modifier.size(36.dp).testTag("edit_doc_$docId")
                      ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Document", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                      }
                      IconButton(
                        onClick = {
                          selectedAccount?.let { acc ->
                            selectedDatabase?.let { db ->
                              selectedCollection?.let { col ->
                                viewModel.deleteDocumentInDb(acc.id, db.id, col.id, docId)
                              }
                            }
                          }
                        },
                        modifier = Modifier.size(36.dp).testTag("delete_doc_$docId")
                      ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Document", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = jsonString,
                      fontSize = 12.sp,
                      fontFamily = FontFamily.Monospace,
                      color = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.padding(10.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // 4. Document Create/Edit Dialog
  if (showDocDialog) {
    AlertDialog(
      onDismissRequest = { showDocDialog = false },
      title = { Text(if (editingDocId == null) "Create Document" else "Edit Document ($editingDocId)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (editingDocId == null) {
            OutlinedTextField(
              value = inputDocId,
              onValueChange = { inputDocId = it },
              label = { Text("Document ID (optional)") },
              placeholder = { Text("unique()") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("doc_id_input")
            )
          }
          OutlinedTextField(
            value = inputJsonData,
            onValueChange = {
              inputJsonData = it
              jsonError = null
            },
            label = { Text("JSON Attributes") },
            isError = jsonError != null,
            modifier = Modifier.fillMaxWidth().height(180.dp).testTag("doc_data_input")
          )
          jsonError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val jsonMap = mutableMapOf<String, Any?>()
            try {
              val obj = JSONObject(inputJsonData)
              obj.keys().forEach { key ->
                jsonMap[key] = obj.get(key)
              }
            } catch (e: Exception) {
              jsonError = "Invalid JSON syntax: ${e.localizedMessage}"
              return@Button
            }

            selectedAccount?.let { acc ->
              selectedDatabase?.let { db ->
                selectedCollection?.let { col ->
                  if (editingDocId != null) {
                    viewModel.updateDocumentInDb(acc.id, db.id, col.id, editingDocId!!, jsonMap) {
                      showDocDialog = false
                    }
                  } else {
                    viewModel.createDocumentInDb(acc.id, db.id, col.id, inputDocId.ifBlank { null }, jsonMap) {
                      showDocDialog = false
                    }
                  }
                }
              }
            }
          },
          modifier = Modifier.testTag("save_doc_button")
        ) {
          Text("Save Document")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDocDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
