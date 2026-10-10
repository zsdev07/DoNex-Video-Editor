package zx.offical.dn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF00121C),
    primaryContainer = SkyBlueSoft,
    onPrimaryContainer = SkyBlueLight,
    secondary = SkyBlueLight,
    onSecondary = Color(0xFF00121C),
    background = DnBlack,
    onBackground = DnTextOnDark,
    surface = DnInk,
    onSurface = DnTextOnDark,
    surfaceVariant = DnInkRaised,
    onSurfaceVariant = DnTextMutedOnDark,
    surfaceContainerLow = DnInk,
    surfaceContainer = DnInkRaised,
    surfaceContainerHigh = DnInkHigh,
    outline = DnInkOutline,
    outlineVariant = DnInkOutline
)

private val LightColors = lightColorScheme(
    primary = DnCharcoal,
    onPrimary = Color.White,
    primaryContainer = DnCharcoalSoft,
    onPrimaryContainer = DnCharcoal,
    secondary = DnCharcoal,
    onSecondary = Color.White,
    background = DnPaper,
    onBackground = DnTextOnLight,
    surface = DnPaperRaised,
    onSurface = DnTextOnLight,
    surfaceVariant = DnPaperSunken,
    onSurfaceVariant = DnTextMutedOnLight,
    surfaceContainerLow = DnPaperRaised,
    surfaceContainer = DnPaperRaised,
    surfaceContainerHigh = DnPaperSunken,
    outline = DnPaperOutline,
    outlineVariant = DnPaperOutline
)

@Composable
fun DoNexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = DoNexTypography,
        shapes = DoNexShapes,
        content = content
    )
}
