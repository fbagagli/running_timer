package com.kintsugirun

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun KintsugiRunApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val homeViewModel: HomeViewModel = koinViewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToWorkout = { fileName ->
                    navController.navigate(WorkoutReviewRoute(fileName = fileName))
                },
                onCreateWorkout = {
                    navController.navigate(WorkoutEditorRoute())
                }
            )
        }

        composable<WorkoutReviewRoute> { backStackEntry ->
            val route: WorkoutReviewRoute = backStackEntry.toRoute()
            val workoutReviewViewModel: WorkoutReviewViewModel = koinViewModel()

            WorkoutReviewScreen(
                fileName = route.fileName,
                viewModel = workoutReviewViewModel,
                onStartWorkout = { announceRepetitions ->
                    navController.navigate(
                        ActiveWorkoutRoute(
                            fileName = route.fileName,
                            announceRepetitions = announceRepetitions
                        )
                    ) {
                        popUpTo<WorkoutReviewRoute> { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onEditWorkout = {
                    navController.navigate(WorkoutEditorRoute(fileName = route.fileName))
                }
            )
        }

        composable<WorkoutEditorRoute> { backStackEntry ->
            val route: WorkoutEditorRoute = backStackEntry.toRoute()
            val workoutEditorViewModel: WorkoutEditorViewModel = koinViewModel { parametersOf(route.fileName) }

            WorkoutEditorScreen(
                viewModel = workoutEditorViewModel,
                navController = navController
            )
        }

        composable<ActiveWorkoutRoute> { backStackEntry ->
            val route: ActiveWorkoutRoute = backStackEntry.toRoute()
            val activeWorkoutViewModel: ActiveWorkoutViewModel = koinViewModel()

            ActiveWorkoutScreen(
                fileName = route.fileName,
                announceRepetitions = route.announceRepetitions,
                viewModel = activeWorkoutViewModel,
                onNavigateBack = {
                    navController.popBackStack<HomeRoute>(inclusive = false)
                }
            )
        }
    }
}
