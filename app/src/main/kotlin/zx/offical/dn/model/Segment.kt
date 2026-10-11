package zx.offical.dn.model

/** Shortest piece a clip can be trimmed or split down to. */
const val MIN_SEGMENT_MS = 300L

/** A piece of the original video: [startMs, endMs) of the source file. */
data class Segment(val id: Long, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

enum class TrimSide { START, END }
