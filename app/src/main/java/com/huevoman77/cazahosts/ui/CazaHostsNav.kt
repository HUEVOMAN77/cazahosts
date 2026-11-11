package com.huevoman77.cazahosts.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.huevoman77.cazahosts.data.AppSettings
import com.huevoman77.cazahosts.ui.screens.CheckerScreen
import com.huevoman77.cazahosts.ui.screens.CredentialsScreen
import com.huevoman77.cazahosts.ui.screens.FindingsScreen
import com.huevoman77.cazahosts.ui.screens.GeneratorsScreen
import com.huevoman77.cazahosts.ui.screens.PanelScreen
import com.huevoman77.cazahosts.ui.screens.ScannerScreen
import com.huevoman77.cazahosts.ui.screens.SettingsScreen

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("panel", "Panel", Icons.Outlined.Dashboard),
    Destination("scanner", "Escanear", Icons.Outlined.TravelExplore),
    Destination("hallazgos", "Riesgos", Icons.Outlined.Security),
    Destination("checker", "Checker", Icons.Outlined.Timeline),
    Destination("generadores", "Generar", Icons.Outlined.AutoAwesome),
    Destination("credenciales", "Claves", Icons.Outlined.Key)
)

@Composable
fun CazaHostsNav(settings: AppSettings) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "panel"
    val showBar = destinations.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "panel",
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable("panel") {
                PanelScreen(
                    settings = settings,
                    onOpenScanner = { navController.navigate("scanner") },
                    onOpenFindings = { navController.navigate("hallazgos") }
                )
            }
            composable("scanner") {
                ScannerScreen(settings = settings)
            }
            composable("hallazgos") {
                FindingsScreen()
            }
            composable("checker") {
                CheckerScreen()
            }
            composable("generadores") {
                GeneratorsScreen()
            }
            composable("credenciales") {
                CredentialsScreen(settings = settings)
            }
            composable("ajustes") {
                SettingsScreen(settings = settings, onBack = { navController.popBackStack() })
            }
        }
    }
}
