package com.spcollector.gacha.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppNav() } } }
    }
}

@Composable
fun AppNav(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(nav, "splash") {
        composable("splash") { SplashScreen(nav) }
        composable("dashboard") { DashboardScreen(vm, nav) }
        composable("scanner") { ScannerScreen(vm, nav) }
        composable("verify") { VerifyScreen(vm, nav) }
        composable("history") { HistoryScreen(vm, nav) }
        composable("calculator") { CalcScreen(vm, nav) }
        composable("montecarlo") { MonteCarloScreen(vm, nav) }
        composable("rng") { RngScreen(vm, nav) }
        composable("tracker") { TrackerScreen(vm, nav) }
        composable("recommendation") { RecommendationScreen(vm, nav) }
        composable("resources") { ResourcesScreen(vm, nav) }
        composable("settings") { SettingsScreen(vm, nav) }
    }
}
