package zx.offical.dn.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.max
import kotlin.math.min
import zx.offical.dn.model.MIN_SEGMENT_MS
import zx.offical.dn.model.Segment
import zx.offical.dn.model.TrimSide

/**
 * Non-destructive edit list: the original file is never changed,
 * we only keep which parts of it to show, in order.
 */
class EditorViewModel : ViewModel() {

    var segments by mutableStateOf<List<Segment>>(emptyList())
        private set
    var selectedId by mutableStateOf<Long?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set

    private var sourceKey: String? = null
    private var sourceDurationMs = 0L
    private var nextId = 1L
    private val undoStack = ArrayDeque<List<Segment>>()

    val totalMs: Long get() = segments.sumOf { it.durationMs }

    fun start(key: String, durationMs: Long) {
        if (sourceKey == key && segments.isNotEmpty()) return
        sourceKey = key
        sourceDurationMs = durationMs
        undoStack.clear()
        canUndo = false
        selectedId = null
        segments = listOf(Segment(nextId++, 0L, durationMs))
    }

    fun select(id: Long?) {
        selectedId = id
    }

    private fun commit(newSegments: List<Segment>) {
        undoStack.addLast(segments)
        if (undoStack.size > 50) undoStack.removeFirst()
        segments = newSegments
        canUndo = true
    }

    private fun findAt(timelineMs: Long): Pair<Segment, Long>? {
        var acc = 0L
        for (s in segments) {
            if (timelineMs < acc + s.durationMs) return s to (timelineMs - acc)
            acc += s.durationMs
        }
        return null
    }

    fun canSplitAt(timelineMs: Long): Boolean {
        val (seg, offset) = findAt(timelineMs) ?: return false
        return offset >= MIN_SEGMENT_MS && seg.durationMs - offset >= MIN_SEGMENT_MS
    }

    /** Returns the playhead position to use afterwards, or null if nothing happened. */
    fun split(timelineMs: Long): Long? {
        if (!canSplitAt(timelineMs)) return null
        val (seg, offset) = findAt(timelineMs) ?: return null
        val left = Segment(nextId++, seg.startMs, seg.startMs + offset)
        val right = Segment(nextId++, seg.startMs + offset, seg.endMs)
        commit(segments.flatMap { if (it.id == seg.id) listOf(left, right) else listOf(it) })
        selectedId = right.id
        return timelineMs
    }

    val canDelete: Boolean get() = selectedId != null && segments.size > 1

    fun deleteSelected(): Long? {
        val id = selectedId ?: return null
        if (segments.size <= 1) return null
        val index = segments.indexOfFirst { it.id == id }
        if (index < 0) return null
        val start = segments.take(index).sumOf { it.durationMs }
        commit(segments.filter { it.id != id })
        selectedId = null
        return min(start, totalMs)
    }

    /** How far a piece's edges may move without overlapping another piece of the source. */
    fun trimBounds(seg: Segment): Pair<Long, Long> {
        var lo = 0L
        var hi = sourceDurationMs
        for (other in segments) {
            if (other.id == seg.id) continue
            if (other.endMs <= seg.startMs) lo = max(lo, other.endMs)
            if (other.startMs >= seg.endMs) hi = min(hi, other.startMs)
        }
        return lo to hi
    }

    fun trim(id: Long, newStart: Long, newEnd: Long, side: TrimSide): Long? {
        val index = segments.indexOfFirst { it.id == id }
        if (index < 0) return null
        val seg = segments[index]
        if (newStart == seg.startMs && newEnd == seg.endMs) return null
        val timelineStart = segments.take(index).sumOf { it.durationMs }
        commit(segments.map { if (it.id == id) it.copy(startMs = newStart, endMs = newEnd) else it })
        val newDuration = newEnd - newStart
        return when (side) {
            TrimSide.START -> timelineStart
            TrimSide.END -> timelineStart + max(0L, newDuration - 1000L)
        }
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        segments = undoStack.removeLast()
        canUndo = undoStack.isNotEmpty()
        if (segments.none { it.id == selectedId }) selectedId = null
        return true
    }
}
