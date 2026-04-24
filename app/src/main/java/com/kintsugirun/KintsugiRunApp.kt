package com.kintsugirun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun KintsugiRunApp() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // Create a factory for the ViewModels
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = WorkoutRepository(context.applicationContext)
            return when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                    HomeViewModel(repository) as T
                }
                modelClass.isAssignableFrom(ActiveWorkoutViewModel::class.java) -> {
                    ActiveWorkoutViewModel(repository, context.applicationContext) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToWorkout = { fileName ->
                    navController.navigate("active_workout/$fileName")
                }
            )
        }
        composable("active_workout/{fileName}") { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
            val activeWorkoutViewModel: ActiveWorkoutViewModel = viewModel(factory = factory)

            ActiveWorkoutScreen(
                fileName = fileName,
                viewModel = activeWorkoutViewModel,
                onNavigateBack = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}
