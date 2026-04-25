package com.kintsugirun

import android.app.Application
import androidx.compose.runtime.Composable
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
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = WorkoutRepository(context.applicationContext)
            return when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                    HomeViewModel(repository) as T
                }
                modelClass.isAssignableFrom(ActiveWorkoutViewModel::class.java) -> {
                    ActiveWorkoutViewModel(repository, context.applicationContext as Application) as T
                }
                modelClass.isAssignableFrom(WorkoutReviewViewModel::class.java) -> {
                    WorkoutReviewViewModel(repository) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToWorkout = { fileName ->
                    navController.navigate("workout_review/$fileName")
                }
            )
        }
        composable("workout_review/{fileName}") { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
            val workoutReviewViewModel: WorkoutReviewViewModel = viewModel(factory = factory)

            WorkoutReviewScreen(
                fileName = fileName,
                viewModel = workoutReviewViewModel,
                onStartWorkout = {
                    navController.navigate("active_workout/$fileName") {
                        popUpTo("workout_review/$fileName") { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
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
