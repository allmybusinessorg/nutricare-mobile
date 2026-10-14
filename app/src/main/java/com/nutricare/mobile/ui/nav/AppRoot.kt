package com.nutricare.mobile.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
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
import com.nutricare.mobile.features.auth.LoginScreen
import com.nutricare.mobile.features.chat.ChatScreen
import com.nutricare.mobile.features.home.HomeScreen
import com.nutricare.mobile.features.profile.ProfileScreen
import com.nutricare.mobile.features.reports.ReportsScreen
import com.nutricare.mobile.features.scan.ScanScreen
import com.nutricare.mobile.features.wearables.WearablesScreen
import com.nutricare.mobile.ui.theme.EmeraldDark
import io.github.jan.supabase.auth.auth

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "Home", Icons.Filled.Home)
    data object Scan : Dest("scan", "Scan", Icons.Filled.QrCodeScanner)
    data object Reports : Dest("reports", "Reports", Icons.Filled.MonitorHeart)
    data object Chat : Dest("chat", "AI Chat", Icons.Filled.Chat)
    data object Profile : Dest("profile", "Profile", Icons.Filled.Person)
}

private val bottomDests = listOf(Dest.Home, Dest.Scan, Dest.Reports, Dest.Chat, Dest.Profile)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Google OAuth lands back in the app via a deep link; when the SDK
    // imports the session, move to Home.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.nutricare.mobile.core.AuthEventBus.sessionArrived.collect {
            navController.navigate("home") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomDests.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.paperDp()) {
                    bottomDests.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldDark,
                                selectedTextColor = EmeraldDark,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
            }
        }
    ) { padding ->
        val startOnHome = runCatching {
            com.nutricare.mobile.core.ServiceLocator.supabase.auth.currentSessionOrNull() != null
        }.getOrDefault(false)

        NavHost(
            navController = navController,
            startDestination = if (startOnHome) "home" else "login",
            modifier = Modifier.padding(padding),
        ) {
            composable("login") { LoginScreen(onLoggedIn = { navController.navigate("home") { popUpTo("login") { inclusive = true } } }) }
            composable("home") { HomeScreen(onNavigate = { route -> navController.navigate(route) }) }
            composable("scan") { ScanScreen() }
            composable("reports") { ReportsScreen() }
            composable("chat") { ChatScreen() }
            composable("profile") { ProfileScreen(onSignedOut = { navController.navigate("login") { popUpTo(0) { inclusive = true } } }, onNavigate = { route -> navController.navigate(route) }) }
            composable("medications") { com.nutricare.mobile.features.medications.MedicationsScreen(onBack = { navController.popBackStack() }) }
            composable("wearables") { WearablesScreen(onBack = { navController.popBackStack() }) }
            composable("dietitians") { com.nutricare.mobile.features.dietitian.DietitianScreen(onBack = { navController.popBackStack() }) }
            composable("recipes") { com.nutricare.mobile.features.recipes.RecipesScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

// small helper so NavigationBar elevation reads cleanly
private fun Int.paperDp(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(this.toFloat())
