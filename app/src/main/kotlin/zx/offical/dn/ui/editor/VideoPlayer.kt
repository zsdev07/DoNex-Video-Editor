package zx.offical.dn.ui.editor

import android.net.Uri
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale
import zx.offical.dn.model.Segment

/**
 * Wraps an ExoPlayer that plays the edit list: each segment is a clipped
 * piece of the source, played back to back. "Timeline time" is the position
 * in the edited result, not in the original file.
 */
@Stable
class VideoPlayerController(val player: ExoPlayer, private val uri: Uri) {

    var isPlaying by mutableStateOf(false)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set
    var pixelRatio by mutableFloatStateOf(1f)
        private set
    var hasError by mutableStateOf(false)
        private set

    val isPortrait: Boolean
        get() = videoWidth > 0 && videoHeight > videoWidth

    val displayAspectRatio: Float
        get() = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth * pixelRatio / videoHeight
        } else {
            16f / 9f
        }

    private var segments: List<Segment> = emptyList()
    private var starts = LongArray(0)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@VideoPlayerController.isPlaying = isPlaying
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoWidth = videoSize.width
            videoHeight = videoSize.height
            pixelRatio = videoSize.pixelWidthHeightRatio
        }

        override fun onPlayerError(error: PlaybackException) {
            hasError = true
        }
    }

    init {
        player.addListener(listener)
    }

    /** Replaces the playlist with [newSegments] and jumps to [timelineMs]. */
    fun setSegments(newSegments: List<Segment>, timelineMs: Long) {
        segments = newSegments
        starts = LongArray(newSegments.size)
        var acc = 0L
        newSegments.forEachIndexed { i, s ->
            starts[i] = acc
            acc += s.durationMs
        }
        totalMs = acc

        if (newSegments.isEmpty()) {
            player.clearMediaItems()
            return
        }

        val items = newSegments.map { s ->
            MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(s.startMs)
                        .setEndPositionMs(s.endMs)
                        .build()
                )
                .build()
        }
        val (index, offset) = locate(timelineMs)
        player.setMediaItems(items, index, offset)
        player.prepare()
    }

    private fun locate(timelineMs: Long): Pair<Int, Long> {
        if (segments.isEmpty()) return 0 to 0L
        val t = timelineMs.coerceIn(0L, totalMs)
        for (i in segments.indices.reversed()) {
            if (t >= starts[i]) {
                return i to (t - starts[i]).coerceAtMost(segments[i].durationMs)
            }
        }
        return 0 to 0L
    }

    fun currentTimelineMs(): Long {
        if (segments.isEmpty()) return 0L
        val i = player.currentMediaItemIndex.coerceIn(0, segments.lastIndex)
        val local = player.currentPosition.coerceIn(0L, segments[i].durationMs)
        return starts[i] + local
    }

    fun seekTo(timelineMs: Long) {
        if (segments.isEmpty()) return
        val (index, offset) = locate(timelineMs)
        player.seekTo(index, offset)
    }

    fun pause() {
        player.pause()
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) seekTo(0L)
            player.play()
        }
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}

/** Creates a player for [uri] and releases it when the screen leaves composition. */
@Composable
fun rememberVideoPlayerController(uri: Uri?): VideoPlayerController? {
    val context = LocalContext.current
    val controller = remember(uri) {
        uri?.let { VideoPlayerController(ExoPlayer.Builder(context).build(), it) }
    }

    DisposableEffect(controller) {
        onDispose { controller?.release() }
    }

    // Pause when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return controller
}

/** Draws the video. Size it with Modifier.aspectRatio(controller.displayAspectRatio). */
@Composable
fun VideoSurface(controller: VideoPlayerController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { controller.player.setVideoTextureView(it) }
        }
    )
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
