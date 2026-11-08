package com.dot.gallery.editor.ui

import kotlin.jvm.JvmInline

/**
 * Stable identifier for a registered editor tool, namespaced by editor
 * (`photo.light`, `video.trim`). Saveable view state stores the raw string
 * so a workspace survives configuration and process restore even when a
 * tool id is renamed internally.
 */
@JvmInline
value class EditorToolId(val value: String)
