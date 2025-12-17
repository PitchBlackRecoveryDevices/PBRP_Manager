package com.pbrp.manager.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pbrp.manager.R
import com.pbrp.manager.ui.screens.HomeScreen
import com.pbrp.manager.ui.screens.SearchScreen
import com.pbrp.manager.ui.screens.ToolsScreen
import com.pbrp.manager.ui.theme.PBRP_Red

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var selectedDevice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                val items = listOf(
                    Triple("home", Icons.Default.Home, R.string.nav_home),
                    Triple("search", Icons.Default.Search, R.string.nav_search),
                    Triple("tools", Icons.Default.Build, R.string.nav_tools)
                )
                items.forEach { (route, icon, labelRes) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                        label = { Text(stringResource(labelRes)) },
                        selected = false,
                        onClick = { 
                            if (route == "home") selectedDevice = null 
                            navController.navigate(route) { launchSingleTop = true }
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = PBRP_Red.copy(alpha = 0.3f))
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(selectedDevice) }
            composable("search") { 
                SearchScreen(onDeviceSelected = { codename ->
                    selectedDevice = codename
                    navController.navigate("home")
                }) 
            }
            composable("tools") { ToolsScreen() }
        }
    }
}
