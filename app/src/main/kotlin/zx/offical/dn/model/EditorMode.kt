package zx.offical.dn.model

/** The two kinds of projects a user can create. */
enum class EditorMode(
    val label: String,
    val description: String,
    val route: String,
    private val aspectW: Int,
    private val aspectH: Int
) {
    VIDEO("Video", "16:9 · Long edits", "video", 16, 9),
    SHORTS("Shorts", "9:16 · Reels, Shorts, TikTok", "shorts", 9, 16);

    val aspectRatio: Float get() = aspectW.toFloat() / aspectH.toFloat()

    companion object {
        fun fromRoute(route: String?): EditorMode =
            entries.firstOrNull { it.route == route } ?: VIDEO
    }
}
