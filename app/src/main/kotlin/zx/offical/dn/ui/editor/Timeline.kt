package zx.offical.dn.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import zx.offical.dn.model.MIN_SEGMENT_MS
import zx.offical.dn.model.Segment
import zx.offical.dn.model.TrimSide

private const val DP_PER_SECOND = 36f
private const val TILE_MS = 2000L
private const val STRIP_DP = 52

private data class TrimDrag(val id: Long, val side: TrimSide, val deltaMs: Long)

/** The playhead position, stored as pixels so scrolling is smooth. */
@Stable
class TimelineState(val pxPerMs: Float) {
    var offsetPx by mutableFloatStateOf(0f)

    fun positionMs(): Long = (offsetPx / pxPerMs).toLong()

    fun setPositionMs(ms: Long) {
        offsetPx = max(0L, ms) * pxPerMs
    }
}

@Composable
fun rememberTimelineState(): TimelineState {
    val density = LocalDensity.current
    return remember(density.density) {
        TimelineState(with(density) { DP_PER_SECOND.dp.toPx() } / 1000f)
    }
}

private fun clampSafe(value: Long, lo: Long, hi: Long): Long =
    if (hi < lo) lo else value.coerceIn(lo, hi)

/**
 * Scrolling thumbnail strip with a fixed playhead in the middle.
 * Scroll to seek, tap a piece to select it, drag its edge handles to trim.
 */
@Composable
fun Timeline(
    segments: List<Segment>,
    selectedId: Long?,
    state: TimelineState,
    thumbnails: ThumbnailLoader?,
    trimBounds: (Segment) -> Pair<Long, Long>,
    onScrub: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onTrimCommit: (Long, Long, Long, TrimSide) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerMs = state.pxPerMs
    val tilePx = TILE_MS * pxPerMs
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val onPrimary = colors.onPrimary
    val playheadColor = colors.onBackground
    val placeholder = colors.outline.copy(alpha = 0.5f)

    var widthPx by remember { mutableIntStateOf(0) }
    var trimDrag by remember { mutableStateOf<TrimDrag?>(null) }

    val segmentsRef = rememberUpdatedState(segments)
    val selectedRef = rememberUpdatedState(selectedId)
    val boundsRef = rememberUpdatedState(trimBounds)
    val onScrubRef = rememberUpdatedState(onScrub)
    val onSelectRef = rememberUpdatedState(onSelect)
    val onTrimRef = rememberUpdatedState(onTrimCommit)

    val totalMs = segments.sumOf { it.durationMs }
    val totalPxRef = rememberUpdatedState(totalMs * pxPerMs)

    val scrollState = rememberScrollableState { delta ->
        val old = state.offsetPx
        val new = (old - delta).coerceIn(0f, totalPxRef.value)
        state.offsetPx = new
        if (new != old) onScrubRef.value(state.positionMs())
        old - new
    }

    // Load thumbnails for whatever is on screen (plus a little either side).
    val tileIndex by remember(tilePx) { derivedStateOf { (state.offsetPx / tilePx).toInt() } }
    LaunchedEffect(segments, tileIndex, widthPx, thumbnails) {
        if (thumbnails == null || widthPx == 0) return@LaunchedEffect
        val half = widthPx / 2f
        val leftPx = state.offsetPx - half - tilePx
        val rightPx = state.offsetPx + half + tilePx
        val wanted = ArrayList<Long>()
        var startPx = 0f
        for (seg in segments) {
            val segW = seg.durationMs * pxPerMs
            if (startPx + segW >= leftPx && startPx <= rightPx) {
                val k0 = max(0, ((leftPx - startPx) / tilePx).toInt())
                val k1 = min((segW / tilePx).toInt(), ((rightPx - startPx) / tilePx).toInt())
                for (k in k0..k1) {
                    wanted.add(min(seg.startMs + k * TILE_MS, seg.endMs - 1))
                }
            }
            startPx += segW
        }
        for (t in wanted) {
            if (thumbnails.cached(t) == null) thumbnails.load(t)
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .scrollable(state = scrollState, orientation = Orientation.Horizontal)
            // Edge handles: if the touch starts on a handle, we take over from scrolling.
            .pointerInput(Unit) {
                val hitPx = 26.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val segs = segmentsRef.value
                    val sel = segs.firstOrNull { it.id == selectedRef.value }
                        ?: return@awaitEachGesture
                    val centerX = size.width / 2f
                    val startMs = segs.takeWhile { it.id != sel.id }.sumOf { it.durationMs }
                    val left = centerX + startMs * pxPerMs - state.offsetPx
                    val right = left + sel.durationMs * pxPerMs
                    val dl = abs(down.position.x - left)
                    val dr = abs(down.position.x - right)
                    val side = when {
                        dl <= hitPx && dl <= dr -> TrimSide.START
                        dr <= hitPx -> TrimSide.END
                        else -> return@awaitEachGesture
                    }
                    val (lo, hi) = boundsRef.value(sel)
                    down.consume()
                    var total = 0f
                    drag(down.id) { change ->
                        total += change.positionChange().x
                        val raw = (total / pxPerMs).toLong()
                        val delta = when (side) {
                            TrimSide.START ->
                                clampSafe(sel.startMs + raw, lo, max(lo, sel.endMs - MIN_SEGMENT_MS)) - sel.startMs
                            TrimSide.END ->
                                clampSafe(sel.endMs + raw, min(hi, sel.startMs + MIN_SEGMENT_MS), hi) - sel.endMs
                        }
                        trimDrag = TrimDrag(sel.id, side, delta)
                        change.consume()
                    }
                    val d = trimDrag
                    trimDrag = null
                    if (d != null && d.deltaMs != 0L) {
                        if (side == TrimSide.START) {
                            onTrimRef.value(sel.id, sel.startMs + d.deltaMs, sel.endMs, side)
                        } else {
                            onTrimRef.value(sel.id, sel.startMs, sel.endMs + d.deltaMs, side)
                        }
                    }
                }
            }
            // Tap a piece to select it.
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val px = state.offsetPx + (tap.x - size.width / 2f)
                    val tMs = px / pxPerMs
                    var acc = 0L
                    var hit: Long? = null
                    for (s in segmentsRef.value) {
                        if (tMs >= acc && tMs < acc + s.durationMs) {
                            hit = s.id
                            break
                        }
                        acc += s.durationMs
                    }
                    onSelectRef.value(hit)
                }
            }
    ) {
        thumbnails?.version // re-draw when a new thumbnail arrives

        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val stripH = STRIP_DP.dp.toPx()
        val top = (h - stripH) / 2f
        val corner = 10.dp.toPx()
        val handleW = 14.dp.toPx()
        val offset = state.offsetPx
        val drag = trimDrag

        var startMs = 0L
        for (seg in segments) {
            val left = centerX + startMs * pxPerMs - offset
            val right = left + seg.durationMs * pxPerMs
            startMs += seg.durationMs
            if (right < 0f || left > w) continue

            val path = Path().apply {
                addRoundRect(RoundRect(left, top, right, top + stripH, corner, corner))
            }
            clipPath(path) {
                val k0 = max(0, floorDiv(0f - left, tilePx))
                val k1 = min(
                    ceilDiv(right - left, tilePx) - 1,
                    floorDiv(w - left, tilePx)
                )
                for (k in k0..k1) {
                    val x = left + k * tilePx
                    val t = min(seg.startMs + k * TILE_MS, seg.endMs - 1)
                    val img = thumbnails?.cached(t)
                    if (img != null) {
                        drawCropped(img, x, top, tilePx, stripH)
                    } else {
                        drawRect(placeholder, Offset(x, top), Size(tilePx, stripH))
                    }
                }
            }

            if (seg.id == selectedId) {
                val dd = if (drag != null && drag.id == seg.id) drag else null
                var leftDraw = left
                var rightDraw = right
                if (dd != null) {
                    if (dd.side == TrimSide.START) leftDraw += dd.deltaMs * pxPerMs
                    else rightDraw += dd.deltaMs * pxPerMs
                }
                val dim = Color.Black.copy(alpha = 0.55f)
                val ghost = primary.copy(alpha = 0.25f)
                if (dd != null) {
                    if (dd.side == TrimSide.START) {
                        if (dd.deltaMs > 0) drawRect(dim, Offset(left, top), Size(leftDraw - left, stripH))
                        else drawRect(ghost, Offset(leftDraw, top), Size(left - leftDraw, stripH))
                    } else {
                        if (dd.deltaMs < 0) drawRect(dim, Offset(rightDraw, top), Size(right - rightDraw, stripH))
                        else drawRect(ghost, Offset(right, top), Size(rightDraw - right, stripH))
                    }
                }
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(leftDraw, top),
                    size = Size(rightDraw - leftDraw, stripH),
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(leftDraw, top),
                    size = Size(handleW, stripH),
                    cornerRadius = CornerRadius(corner)
                )
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(rightDraw - handleW, top),
                    size = Size(handleW, stripH),
                    cornerRadius = CornerRadius(corner)
                )
                val gripTop = top + stripH * 0.3f
                val gripBottom = top + stripH * 0.7f
                drawLine(
                    color = onPrimary,
                    start = Offset(leftDraw + handleW / 2f, gripTop),
                    end = Offset(leftDraw + handleW / 2f, gripBottom),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = onPrimary,
                    start = Offset(rightDraw - handleW / 2f, gripTop),
                    end = Offset(rightDraw - handleW / 2f, gripBottom),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Playhead
        val phW = 3.dp.toPx()
        drawRoundRect(
            color = playheadColor,
            topLeft = Offset(centerX - phW / 2f, 2.dp.toPx()),
            size = Size(phW, h - 4.dp.toPx()),
            cornerRadius = CornerRadius(phW / 2f)
        )
    }
}

private fun floorDiv(a: Float, b: Float): Int = kotlin.math.floor(a / b).toInt()

private fun ceilDiv(a: Float, b: Float): Int = kotlin.math.ceil(a / b).toInt()

/** Draws [img] filling the box, centre-cropped. */
private fun DrawScope.drawCropped(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float) {
    val dstAspect = w / h
    val srcAspect = img.width.toFloat() / img.height.toFloat()
    val srcW: Int
    val srcH: Int
    if (srcAspect > dstAspect) {
        srcH = img.height
        srcW = max(1, (img.height * dstAspect).toInt())
    } else {
        srcW = img.width
        srcH = max(1, (img.width / dstAspect).toInt())
    }
    drawImage(
        image = img,
        srcOffset = IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(max(1, w.roundToInt()), max(1, h.roundToInt()))
    )
}
