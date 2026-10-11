package zx.offical.dn.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Loads small preview frames for the timeline on a background thread
 * and keeps the most recent ones in memory.
 */
class ThumbnailLoader(context: Context, private val uri: Uri) {

    val key: String = uri.toString()

    /** Bumped whenever a new thumbnail arrives, so the timeline can redraw. */
    var version by mutableIntStateOf(0)

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val cache = LruCache<Long, ImageBitmap>(160)

    private var retriever: MediaMetadataRetriever? = null
    private var thumbW = 160
    private var thumbH = 90

    @Volatile
    private var released = false

    private fun bucket(timeMs: Long): Long = (timeMs / BUCKET_MS) * BUCKET_MS

    fun cached(timeMs: Long): ImageBitmap? = cache.get(bucket(timeMs))

    private fun ensureRetriever(): MediaMetadataRetriever? {
        retriever?.let { return it }
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(appContext, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val dw = if (rotation == 90 || rotation == 270) h else w
            val dh = if (rotation == 90 || rotation == 270) w else h
            if (dw > 0 && dh > 0) {
                val scale = MAX_SIDE.toFloat() / max(dw, dh)
                thumbW = max(1, (dw * scale).roundToInt())
                thumbH = max(1, (dh * scale).roundToInt())
            }
            retriever = r
            r
        } catch (e: Exception) {
            null
        }
    }

    suspend fun readDurationMs(): Long = withContext(dispatcher) {
        try {
            ensureRetriever()
                ?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun load(timeMs: Long): ImageBitmap? {
        val image = withContext(dispatcher) {
            val b = bucket(timeMs)
            val existing = cache.get(b)
            if (existing != null) return@withContext existing
            if (released) return@withContext null
            val r = ensureRetriever() ?: return@withContext null
            val bitmap: Bitmap? = try {
                extractFrame(r, b * 1000L)
            } catch (e: Exception) {
                null
            }
            if (bitmap == null) return@withContext null
            val img = bitmap.asImageBitmap()
            cache.put(b, img)
            img
        }
        if (image != null) version++
        return image
    }

    private fun extractFrame(r: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 27) {
            r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, thumbW, thumbH)
        } else {
            val full = r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val scaled = Bitmap.createScaledBitmap(full, thumbW, thumbH, true)
            if (scaled !== full) full.recycle()
            scaled
        }
    }

    fun release() {
        released = true
        executor.execute {
            try {
                retriever?.release()
            } catch (e: Exception) {
                // ignore
            }
            retriever = null
        }
        executor.shutdown()
    }

    private companion object {
        const val BUCKET_MS = 500L
        const val MAX_SIDE = 160
    }
}
