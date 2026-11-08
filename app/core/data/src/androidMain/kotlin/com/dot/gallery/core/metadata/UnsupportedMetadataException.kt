package com.dot.gallery.core.metadata

/** The file is well formed but cannot be processed (too large, unsupported variant). */
internal class UnsupportedMetadataException(message: String) : IllegalStateException(message)
