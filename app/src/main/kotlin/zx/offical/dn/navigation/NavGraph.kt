package zx.offical.dn.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import zx.offical.dn.model.EditorMode
import zx.offical.dn.ui.editor.EditorScreen
import zx.offical.dn.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{mode}"

    fun editor(mode: EditorMode) = "editor/${mode.route}"
}

@Composable
fun DoNexNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onModeSelected = { mode -> navController.navigate(Routes.editor(mode)) }
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { entry ->
            EditorScreen(
                mode = EditorMode.fromRoute(entry.arguments?.getString("mode")),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
