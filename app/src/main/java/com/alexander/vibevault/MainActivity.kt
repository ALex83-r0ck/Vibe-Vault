package com.alexander.vibevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
                            onOpenStats = { navController.navigate(Routes.STATS) }
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
                }
            }
        }
    }
}
