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
                    composable(Routes.HOME) { HomeScreen(navController) }
                    composable(Routes.ADD_ENTRY) {
                        AddEntryScreen(viewModel<AddEntryViewModel>(factory = factory), navController)
                    }
                    composable(Routes.STATS) {
                        StatsScreen(viewModel<StatsViewModel>(factory = factory))
                    }
                }
            }
        }
    }
}
