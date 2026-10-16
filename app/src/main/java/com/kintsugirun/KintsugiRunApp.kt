package com.kintsugirun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun KintsugiRunApp(
    pendingActiveRoute: ActiveWorkoutRoute? = null,
    onRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(pendingActiveRoute) {
        pendingActiveRoute?.let { route ->
            navController.navigate(route) {
                popUpTo<HomeRoute> { inclusive = false }
            }
            onRouteConsumed()
        }
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val homeViewModel: HomeViewModel = koinViewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToWorkout = { workoutId ->
                    navController.navigate(WorkoutReviewRoute(workoutId = workoutId))
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
                workoutId = route.workoutId,
                viewModel = workoutReviewViewModel,
                onStartWorkout = { announceRepetitions ->
                    navController.navigate(
                        ActiveWorkoutRoute(
                            workoutId = route.workoutId,
                            announceRepetitions = announceRepetitions
                        )
                    ) {
                        popUpTo<WorkoutReviewRoute> { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onEditWorkout = { workoutId ->
                    navController.navigate(WorkoutEditorRoute(workoutId = workoutId))
                }
            )
        }

        composable<WorkoutEditorRoute> { backStackEntry ->
            val route: WorkoutEditorRoute = backStackEntry.toRoute()
            val workoutEditorViewModel: WorkoutEditorViewModel = koinViewModel { parametersOf(route.workoutId) }

            WorkoutEditorScreen(
                viewModel = workoutEditorViewModel,
                navController = navController
            )
        }

        composable<ActiveWorkoutRoute> { backStackEntry ->
            val route: ActiveWorkoutRoute = backStackEntry.toRoute()
            val activeWorkoutViewModel: ActiveWorkoutViewModel = koinViewModel()

            ActiveWorkoutScreen(
                workoutId = route.workoutId,
                announceRepetitions = route.announceRepetitions,
                viewModel = activeWorkoutViewModel,
                onNavigateBack = {
                    navController.navigate(WorkoutReviewRoute(workoutId = route.workoutId)) {
                        popUpTo<HomeRoute> { inclusive = false }
                    }
                }
            )
        }
    }
}
