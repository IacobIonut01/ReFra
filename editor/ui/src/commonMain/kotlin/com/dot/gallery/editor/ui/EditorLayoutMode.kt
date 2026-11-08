package com.dot.gallery.editor.ui

/**
 * Responsive composition of the workspace, chosen purely from the available
 * content window (never the OS):
 *
 * - [Compact]: tools in a contextual bottom panel — portrait phones, narrow
 *   or square windows.
 * - [Sidebar]: an 80 dp tool rail + 280 dp inspector beside the canvas —
 *   wide landscape (248 dp inspector when the window is shorter than
 *   400 dp).
 * - [MasterDetail]: a floating document header over the canvas and a
 *   380 dp inspector — large, near-square windows (>=1000x600 and
 *   narrower than 1.4:1).
 * - [Desktop]: the four-pane composition — rail, canvas, layers list and
 *   layer properties — on wide desktop windows (>=1200x600 and at least
 *   1.4:1).
 *
 * The sidebar only exists when the window is at least 700 dp wide, wider
 * than tall, and leaves the canvas at least 280 dp after reserving it.
 */
internal enum class EditorLayoutMode {

    Compact,
    Sidebar,
    MasterDetail,
    Desktop;

    /**
     * Inspector width for this mode, 0 when the composition owns its own
     * chrome. A short landscape window (<400 dp tall) shrinks the sidebar
     * to 248 dp and lets it scroll so the preview keeps breathing room.
     */
    fun inspectorWidthDp(windowHeightDp: Float): Float = when (this) {
        Compact, Desktop -> 0f
        Sidebar -> if (windowHeightDp < 400f) 248f else 280f
        MasterDetail -> MASTER_DETAIL_INSPECTOR_WIDTH_DP
    }

    companion object {

        fun resolve(widthDp: Float, heightDp: Float): EditorLayoutMode {
            require(widthDp.isFinite() && heightDp.isFinite()) {
                "window dims must be finite: ${widthDp}x$heightDp"
            }
            require(widthDp > 0f && heightDp > 0f) {
                "window dims must be positive: ${widthDp}x$heightDp"
            }
            val fits = widthDp >= 700f && widthDp > heightDp &&
                widthDp - SIDEBAR_MIN_WIDTH >= CANVAS_MIN_WIDTH
            if (!fits) return Compact
            return when {
                // The four-pane needs a genuinely wide window: the 80 dp
                // rail plus a 280 dp layers list and a 304 dp properties
                // column eat 664 dp before the canvas gets a share. A 4:3
                // window of the same width keeps the two-pane
                // master-detail instead.
                widthDp >= DESKTOP_MIN_WIDTH && heightDp >= 600f &&
                    widthDp >= heightDp * DESKTOP_MIN_ASPECT -> Desktop
                widthDp >= 1000f && heightDp >= 600f -> MasterDetail
                else -> Sidebar
            }
        }

        /** 80 dp tool rail + 280 dp inspector — the canvas keeps 280 dp. */
        private const val SIDEBAR_MIN_WIDTH = 360f
        private const val CANVAS_MIN_WIDTH = 280f

        /** The four-pane composition's minimum window and aspect. */
        private const val DESKTOP_MIN_WIDTH = 1200f
        private const val DESKTOP_MIN_ASPECT = 1.4f

        /**
         * The tablet master-detail inspector width — the canvas keeps the
         * remaining 620 dp at the 1000 dp minimum.
         */
        const val MASTER_DETAIL_INSPECTOR_WIDTH_DP = 380f
    }
}
