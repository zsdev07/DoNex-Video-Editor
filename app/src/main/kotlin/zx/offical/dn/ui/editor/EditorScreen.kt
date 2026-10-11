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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import zx.offical.dn.model.EditorMode

@Composable
fun EditorScreen(
    mode: EditorMode,
    videoUri: Uri?,
    onBack: () -> Unit,
    onSwitchToShorts: (Uri) -> Unit
) {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel()
    val controller = rememberVideoPlayerController(videoUri)
    val timelineState = rememberTimelineState()

    val thumbs = remember(videoUri) { videoUri?.let { ThumbnailLoader(context, it) } }
    DisposableEffect(thumbs) {
        onDispose { thumbs?.release() }
    }

    // Read the video length once, then start with one piece covering all of it.
    var loadFailed by remember(videoUri) { mutableStateOf(false) }
    LaunchedEffect(thumbs) {
        if (thumbs != null && vm.segments.isEmpty()) {
            val duration = thumbs.readDurationMs()
            if (duration > 0L) vm.start(thumbs.key, duration) else loadFailed = true
        }
    }

    // Give the player its playlist as soon as both exist.
    val hasSegments = vm.segments.isNotEmpty()
    LaunchedEffect(controller, hasSegments) {
        if (controller != null && hasSegments) {
            controller.setSegments(
                vm.segments,
                timelineState.positionMs().coerceIn(0L, vm.totalMs)
            )
        }
    }

    // While playing, keep the timeline moving with the video.
    val playing = controller?.isPlaying == true
    LaunchedEffect(controller, playing) {
        if (controller != null && playing) {
            while (true) {
                withFrameNanos { }
                timelineState.setPositionMs(controller.currentTimelineMs())
            }
        }
    }

    val applyEdit: (Long?) -> Unit = { position ->
        if (position != null) {
            controller?.setSegments(vm.segments, position)
            timelineState.setPositionMs(position)
        }
    }

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
                        loadFailed || controller.hasError -> Text(
                            text = "Couldn't open this video",
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

        if (controller != null && hasSegments && !controller.hasError) {
            // Play button + time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlayPauseButton(
                    isPlaying = controller.isPlaying,
                    onClick = { controller.togglePlay() }
                )
                TimeText(timelineState, controller.totalMs)
            }
            Spacer(Modifier.height(10.dp))

            // Split / Delete / Undo
            ToolRow(
                state = timelineState,
                vm = vm,
                onUndo = {
                    if (vm.undo()) {
                        applyEdit(timelineState.positionMs().coerceIn(0L, vm.totalMs))
                    }
                },
                onSplit = { applyEdit(vm.split(timelineState.positionMs())) },
                onDelete = { applyEdit(vm.deleteSelected()) }
            )
            Spacer(Modifier.height(10.dp))

            // Timeline
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Timeline(
                    segments = vm.segments,
                    selectedId = vm.selectedId,
                    state = timelineState,
                    thumbnails = thumbs,
                    trimBounds = { vm.trimBounds(it) },
                    onScrub = { ms ->
                        controller.pause()
                        controller.seekTo(ms)
                    },
                    onSelect = { vm.select(it) },
                    onTrimCommit = { id, newStart, newEnd, side ->
                        applyEdit(vm.trim(id, newStart, newEnd, side))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                )
            }
        } else if (controller == null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Shorts editing coming soon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeText(state: TimelineState, totalMs: Long) {
    Text(
        text = "${formatTime(state.positionMs())} / ${formatTime(totalMs)}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ToolRow(
    state: TimelineState,
    vm: EditorViewModel,
    onUndo: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit
) {
    val canSplit = vm.canSplitAt(state.positionMs())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ToolPill("Undo", vm.canUndo, onUndo, Modifier.weight(1f))
        ToolPill("Split", canSplit, onSplit, Modifier.weight(1f))
        ToolPill("Delete", vm.canDelete, onDelete, Modifier.weight(1f))
    }
}

@Composable
private fun ToolPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
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
