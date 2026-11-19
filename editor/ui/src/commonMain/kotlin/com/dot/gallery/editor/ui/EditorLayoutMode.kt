package com.dot.gallery.editor.ui

/**
 * The window class of the workspace, chosen purely from the available
 * content window (never the OS):
 *
 * - [Compact]: portrait phones, narrow or square windows.
 * - [Sidebar]: wide landscape that is short or narrow — at least 700 dp
 *   wide, wider than tall, with 280 dp left for the canvas beside a
 *   360 dp side column.
 * - [MasterDetail]: large, near-square windows (>=1000x600 and narrower
 *   than 1.4:1).
 * - [Desktop]: wide desktop windows (>=1200x600 and at least 1.4:1).
 *
 * The photo editor only asks whether the window is [Compact]: Simple and
 * Studio each have one large composition for everything else. The video
 * editor still gives [Desktop] its own layout.
 */
internal enum class EditorLayoutMode {

    Compact,
    Sidebar,
    MasterDetail,
    Desktop;

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
                // Desktop needs a genuinely wide window: its side panes
                // take a large fixed share before the canvas gets one. A
                // 4:3 window of the same width is MasterDetail instead.
                widthDp >= DESKTOP_MIN_WIDTH && heightDp >= 600f &&
                    widthDp >= heightDp * DESKTOP_MIN_ASPECT -> Desktop
                widthDp >= 1000f && heightDp >= 600f -> MasterDetail
                else -> Sidebar
            }
        }

        /** The side column a wide window reserves — the canvas keeps 280 dp. */
        private const val SIDEBAR_MIN_WIDTH = 360f
        private const val CANVAS_MIN_WIDTH = 280f

        /** The desktop class's minimum window and aspect. */
        private const val DESKTOP_MIN_WIDTH = 1200f
        private const val DESKTOP_MIN_ASPECT = 1.4f
    }
}
