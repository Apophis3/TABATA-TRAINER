package com.tuapp.tabatatrainer.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tuapp.tabatatrainer.ui.home.HomeScreen
import com.tuapp.tabatatrainer.ui.config.ConfigScreen
import com.tuapp.tabatatrainer.ui.workout.WorkoutScreen
import com.tuapp.tabatatrainer.ui.freeride.FreeRideScreen
import com.tuapp.tabatatrainer.ui.session.SessionDetailScreen
import com.tuapp.tabatatrainer.ui.history.HistoryScreen

sealed class Screen(val route: String) {
    // Nueva pantalla principal
    object Home : Screen("home")
    
    // Configuración Tabata
    object TabataConfig : Screen("tabata_config")
    
    // Workout Tabata (existente)
    object Workout : Screen("workout/{warmup}/{work}/{rest}/{rounds}/{gps}") {
        fun createRoute(warmup: Int, work: Int, rest: Int, rounds: Int, gpsEnabled: Boolean) = 
            "workout/$warmup/$work/$rest/$rounds/$gpsEnabled"
    }
    
    // Nueva pantalla de Ruta Libre
    object FreeRide : Screen("freeride")
    
    // Historial
    object History : Screen("history")
    
    // Detalle de sesión
    object SessionDetail : Screen("session/{sessionId}") {
        fun createRoute(sessionId: String) = "session/$sessionId"
    }
    
    // Ajustes (futuro)
    object Settings : Screen("settings")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    
    // Guardar la última configuración de workout para poder volver
    var lastWorkoutConfig by remember { 
        mutableStateOf<WorkoutNavigationConfig?>(null) 
    }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route  // ← Ahora empieza en Home
    ) {
        // ============================================================
        // HOME - Pantalla principal
        // ============================================================
        composable(Screen.Home.route) {
            HomeScreen(
                onStartFreeRide = {
                    navController.navigate(Screen.FreeRide.route)
                },
                onStartTabata = {
                    navController.navigate(Screen.TabataConfig.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    // TODO: Settings screen
                    // navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        // ============================================================
        // RUTA LIBRE
        // ============================================================
        composable(Screen.FreeRide.route) {
            FreeRideScreen(
                onFinish = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onViewSession = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }
        
        // ============================================================
        // CONFIGURACIÓN TABATA
        // ============================================================
        composable(Screen.TabataConfig.route) {
            ConfigScreen(
                onStartWorkout = { warmup, work, rest, rounds, gpsEnabled ->
                    // Guardar configuración
                    lastWorkoutConfig = WorkoutNavigationConfig(warmup, work, rest, rounds, gpsEnabled)
                    navController.navigate(Screen.Workout.createRoute(warmup, work, rest, rounds, gpsEnabled))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onResumeWorkout = { warmup, work, rest, rounds, gpsEnabled ->
                    // Navegar directamente al workout con la configuración del servicio activo
                    val route = Screen.Workout.createRoute(warmup, work, rest, rounds, gpsEnabled)
                    // Actualizar la config guardada
                    lastWorkoutConfig = WorkoutNavigationConfig(warmup, work, rest, rounds, gpsEnabled)
                    // Intentar volver a la pantalla si ya existe en el back stack
                    val popped = navController.popBackStack(route, inclusive = false)
                    if (!popped) {
                        // Si no existe, navegar a ella
                        navController.navigate(route) {
                            launchSingleTop = true
                            // Limpiar el back stack hasta Home para evitar loops
                            popUpTo(Screen.Home.route) {
                                inclusive = false
                            }
                        }
                    }
                }
            )
        }
        
        // ============================================================
        // HISTORIAL
        // ============================================================
        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }
        
        // ============================================================
        // WORKOUT TABATA (existente)
        // ============================================================
        composable(
            route = Screen.Workout.route,
            arguments = listOf(
                navArgument("warmup") { type = NavType.IntType },
                navArgument("work") { type = NavType.IntType },
                navArgument("rest") { type = NavType.IntType },
                navArgument("rounds") { type = NavType.IntType },
                navArgument("gps") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val warmup = backStackEntry.arguments?.getInt("warmup") ?: 10
            val work = backStackEntry.arguments?.getInt("work") ?: 20
            val rest = backStackEntry.arguments?.getInt("rest") ?: 10
            val rounds = backStackEntry.arguments?.getInt("rounds") ?: 8
            val gpsEnabled = backStackEntry.arguments?.getBoolean("gps") ?: false
            
            // Actualizar la config guardada
            LaunchedEffect(warmup, work, rest, rounds, gpsEnabled) {
                lastWorkoutConfig = WorkoutNavigationConfig(warmup, work, rest, rounds, gpsEnabled)
            }
            
            WorkoutScreen(
                warmupSeconds = warmup,
                workSeconds = work,
                restSeconds = rest,
                rounds = rounds,
                gpsEnabled = gpsEnabled,
                onFinish = {
                    // Limpiar config guardada al terminar
                    lastWorkoutConfig = null
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onViewSession = { sessionId ->
                    navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                }
            )
        }
        
        // ============================================================
        // DETALLE DE SESIÓN
        // ============================================================
        composable(
            route = Screen.SessionDetail.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            
            SessionDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Datos de configuración del workout para poder volver a él
 */
data class WorkoutNavigationConfig(
    val warmup: Int,
    val work: Int,
    val rest: Int,
    val rounds: Int,
    val gpsEnabled: Boolean
)
