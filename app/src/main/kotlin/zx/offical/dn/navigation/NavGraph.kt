package zx.offical.dn.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    const val EDITOR = "editor/{mode}?uri={uri}"

    fun editor(mode: EditorMode, videoUri: Uri? = null): String =
        if (videoUri == null) {
            "editor/${mode.route}"
        } else {
            "editor/${mode.route}?uri=${Uri.encode(videoUri.toString())}"
        }
}

@Composable
fun DoNexNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val pickVideo = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    navController.navigate(Routes.editor(EditorMode.VIDEO, uri))
                }
            }

            HomeScreen(
                onModeSelected = { mode ->
                    if (mode == EditorMode.VIDEO) {
                        pickVideo.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    } else {
                        navController.navigate(Routes.editor(mode))
                    }
                }
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("uri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val mode = EditorMode.fromRoute(entry.arguments?.getString("mode"))
            val videoUri = entry.arguments?.getString("uri")?.let { Uri.parse(it) }

            EditorScreen(
                mode = mode,
                videoUri = videoUri,
                onBack = { navController.popBackStack() },
                onSwitchToShorts = { uri ->
                    navController.navigate(Routes.editor(EditorMode.SHORTS, uri)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }
    }
}
