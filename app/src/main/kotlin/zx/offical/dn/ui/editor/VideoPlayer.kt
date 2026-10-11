package zx.offical.dn.ui.editor

import android.net.Uri
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

/** Holds an ExoPlayer plus the bits of its state the UI cares about. */
@Stable
class VideoPlayerController(val player: ExoPlayer) {

    var isPlaying by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
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

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@VideoPlayerController.isPlaying = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                durationMs = player.duration.coerceAtLeast(0L)
            }
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

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        positionMs = ms
    }

    fun refreshPosition() {
        positionMs = player.currentPosition
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
        uri?.let {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(it))
                prepare()
            }
            VideoPlayerController(player)
        }
    }

    DisposableEffect(controller) {
        onDispose { controller?.release() }
    }

    // Pause when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller?.player?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep the seek bar moving.
    LaunchedEffect(controller) {
        if (controller != null) {
            while (true) {
                controller.refreshPosition()
                delay(250L)
            }
        }
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
