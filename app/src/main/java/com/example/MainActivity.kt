package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DatabaseBrowserScreen
import com.example.ui.screens.RoutingRulesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MasterRouterTheme
import com.example.ui.viewmodel.MasterRouterViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
  object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
  object Accounts : Screen("accounts", "Accounts", Icons.Default.Cloud)
  object Database : Screen("database", "Database", Icons.Default.Storage)
  object Rules : Screen("rules", "Routing", Icons.Default.Route)
  object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
  private val viewModel: MasterRouterViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MasterRouterTheme {
        val navController = rememberNavController()
        val items = listOf(
          Screen.Dashboard,
          Screen.Accounts,
          Screen.Database,
          Screen.Rules,
          Screen.Settings
        )

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            NavigationBar {
              val navBackStackEntry by navController.currentBackStackEntryAsState()
              val currentRoute = navBackStackEntry?.destination?.route
              items.forEach { screen ->
                NavigationBarItem(
                  icon = { Icon(screen.icon, contentDescription = screen.title) },
                  label = { Text(screen.title) },
                  selected = currentRoute == screen.route,
                  onClick = {
                    if (currentRoute != screen.route) {
                      navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                          saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                      }
                    }
                  }
                )
              }
            }
          }
        ) { innerPadding ->
          NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
          ) {
            composable(Screen.Dashboard.route) {
              DashboardScreen(viewModel) {
                navController.navigate(Screen.Accounts.route)
              }
            }
            composable(Screen.Accounts.route) {
              AccountsScreen(viewModel)
            }
            composable(Screen.Database.route) {
              DatabaseBrowserScreen(viewModel)
            }
            composable(Screen.Rules.route) {
              RoutingRulesScreen(viewModel)
            }
            composable(Screen.Settings.route) {
              SettingsScreen(viewModel)
            }
          }
        }
      }
    }
  }
}
