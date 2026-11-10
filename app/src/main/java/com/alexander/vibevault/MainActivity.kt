package com.alexander.vibevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexander.vibevault.di.AppModule
import com.alexander.vibevault.ui.theme.VibeVaultTheme

object Routes {
    const val HOME = "home"
    const val ADD_ENTRY = "addEntry"
    const val STATS = "stats"
    const val ACHIEVEMENTS = "achievements"
    const val BODY = "body"
}

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // Falls inzwischen "Wecker & Erinnerungen" erlaubt wurde: laufenden Timer pünktlich einplanen
        AppModule.initialize(applicationContext)
        AppModule.providePouchTimer().rescheduleIfActive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppModule.initialize(applicationContext)
        val factory = AppModule.provideViewModelFactory()
        setContent {
            VibeVaultTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            viewModel = viewModel<HomeViewModel>(factory = factory),
                            onAddEntry = { navController.navigate(Routes.ADD_ENTRY) },
                            onOpenStats = { navController.navigate(Routes.STATS) },
                            onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                            onOpenBody = { navController.navigate(Routes.BODY) }
                        )
                    }
                    composable(Routes.BODY) {
                        BodyScreen(
                            viewModel = viewModel<BodyViewModel>(factory = factory),
                            onBack = { navController.navigateUp() }
                        )
                    }
                    composable(Routes.ADD_ENTRY) {
                        AddEntryScreen(
                            viewModel = viewModel<AddEntryViewModel>(factory = factory),
                            onDone = { navController.navigateUp() }
                        )
                    }
                    composable(Routes.STATS) {
                        StatsScreen(
                            viewModel = viewModel<StatsViewModel>(factory = factory),
                            onBack = { navController.navigateUp() }
                        )
                    }
                    composable(Routes.ACHIEVEMENTS) {
                        AchievementsScreen(
                            viewModel = viewModel<AchievementsViewModel>(factory = factory),
                            onBack = { navController.navigateUp() }
                        )
                    }
                }
            }
        }
    }
}
