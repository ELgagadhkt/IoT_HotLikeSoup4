package com.example.iot_hotlikesoup4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.iot_hotlikesoup4.ui.theme.IoT_HotLikeSoup4Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IoT_HotLikeSoup4Theme {
                // Set up the NavHost with NavController
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "screen1") {
                    composable("screen1") { LoginScreen(navController) }
                    composable("screen2") { Menu(navController) }
                    composable(
                        route = "screen3/{selectedChauffage}",
                        arguments = listOf(navArgument("selectedChauffage") {
                            type = NavType.StringType
                        })
                    ) { backStackEntry ->
                        val selectedOutil = backStackEntry.arguments?.getString("selectedChauffage")
                        SuiviTemp(navController, selectedOutil)
                    }
                    composable(
                        route = "screen4/{selectedChauffage}",
                        arguments = listOf(navArgument("selectedChauffage") {
                            type = NavType.StringType
                        })
                    ) { backStackEntry ->
                        val selectedOutil = backStackEntry.arguments?.getString("selectedChauffage")
                        Programs(navController, selectedOutil)
                    }
                }
            }
        }
    }
}