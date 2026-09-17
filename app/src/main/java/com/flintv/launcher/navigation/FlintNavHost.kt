package com.flintv.launcher.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flintv.launcher.ui.home.HomeScreen
import com.flintv.launcher.ui.home.LauncherActivity
import com.flintv.launcher.ui.apps.AllAppsScreen
import com.flintv.launcher.ui.apps.BindAppScreen
import com.flintv.launcher.ui.apps.FavoritesScreen
import com.flintv.launcher.ui.settings.SettingsScreen

/**
 * 畫面路由表。
 *
 * 輕量版沒有 PhoneRemote：原版的「手機遙控」頁面連動內網 HTTP 伺服器與雲端中繼，
 * 整條鏈路已切除，首頁對應的卡片也一併移除，因此不再需要這條路由。
 */
enum class Screen(val route: String) {
    Home("home"),
    AllApps("all_apps"),
    Favorites("favorites"),
    Settings("settings"),
    BindApp("bind_app/{slot}"),
}

@Composable
fun FlintNavHost() {
    val navController = rememberNavController()

    // 遙控器首頁鍵：不論目前停在哪一層子頁面，一律退回首頁。
    LaunchedEffect(Unit) {
        LauncherActivity.homeEvent.collect {
            navController.popBackStack(Screen.Home.route, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = { screen -> navController.navigate(screen.route) },
                onBindApp = { slot -> navController.navigate("bind_app/$slot") }
            )
        }
        composable(Screen.AllApps.route) {
            AllAppsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onBindApp = { slot -> navController.navigate("bind_app/$slot") }
            )
        }

        composable("bind_app/{slot}") { backStackEntry ->
            val slot = backStackEntry.arguments?.getString("slot") ?: "tv"
            BindAppScreen(
                slot = slot,
                onDone = { navController.popBackStack() }
            )
        }
    }
}
