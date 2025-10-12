package com.alexander.vibevault

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alexander.vibevault.di.AppModule
import com.alexander.vibevault.ui.theme.VibeVaultTheme

class MainActivity : ComponentActivity() {
    @SuppressLint("ViewModelConstructorInComposable")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppModule.initialize(applicationContext)
        setContent {
            VibeVaultTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen(navController) }
                    composable("addEntry") {
                        AddEntryScreen(AddEntryViewModel(AppModule.provideDatabase()), navController)
                    }
                    composable("stats") {
                        StatsScreen(StatsViewModel(AppModule.provideRepository()))
                    }
                }
            }
        }
    }
}