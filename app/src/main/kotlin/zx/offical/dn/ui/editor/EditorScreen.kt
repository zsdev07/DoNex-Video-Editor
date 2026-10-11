@file:OptIn(ExperimentalMaterial3Api::class)

package zx.offical.dn.ui.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zx.offical.dn.model.EditorMode

@Composable
fun EditorScreen(
    mode: EditorMode,
    videoUri: Uri?,
    onBack: () -> Unit,
    onSwitchToShorts: (Uri) -> Unit
) {
    val controller = rememberVideoPlayerController(videoUri)

    // Vertical video opened in Video mode: offer to switch to Shorts.
    var promptDismissed by rememberSaveable(videoUri?.toString()) { mutableStateOf(false) }
    if (
        mode == EditorMode.VIDEO &&
        videoUri != null &&
        controller != null &&
        controller.isPortrait &&
        !promptDismissed
    ) {
        AlertDialog(
            onDismissRequest = { promptDismissed = true },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("This looks like a short") },
            text = { Text("Your video is vertical. Open it in Shorts mode for the best fit?") },
            confirmButton = {
                TextButton(onClick = {
                    promptDismissed = true
                    onSwitchToShorts(videoUri)
                }) { Text("Switch to Shorts") }
            },
            dismissButton = {
                TextButton(onClick = { promptDismissed = true }) { Text("Keep editing") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "${mode.label} project",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ) {
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }

        // Preview canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val canvasModifier = if (mode == EditorMode.SHORTS) {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(mode.aspectRatio, matchHeightConstraintsFirst = true)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(mode.aspectRatio)
            }
            Surface(
                modifier = canvasModifier,
                shape = RoundedCornerShape(24.dp),
                color = if (controller != null) Color.Black else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        controller == null -> Text(
                            text = "Preview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        controller.hasError -> Text(
                            text = "Couldn't play this video",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        else -> VideoSurface(
                            controller = controller,
                            modifier = Modifier.aspectRatio(controller.displayAspectRatio)
                        )
                    }
                }
            }
        }

        // Player controls
        if (controller != null && !controller.hasError) {
            PlayerControls(controller)
            Spacer(Modifier.height(12.dp))
        }

        // Timeline placeholder
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Timeline coming soon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(controller: VideoPlayerController) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val duration = controller.durationMs.coerceAtLeast(1L).toFloat()
    val sliderValue = if (dragging) {
        dragValue
    } else {
        controller.positionMs.toFloat().coerceIn(0f, duration)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlayPauseButton(
                isPlaying = controller.isPlaying,
                onClick = { controller.togglePlay() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        dragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        controller.seekTo(dragValue.toLong())
                        dragging = false
                    },
                    valueRange = 0f..duration,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(sliderValue.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(controller.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isPlaying) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 5.dp, height = 18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(tint)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
