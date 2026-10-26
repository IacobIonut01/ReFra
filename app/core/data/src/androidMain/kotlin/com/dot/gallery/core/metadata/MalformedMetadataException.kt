package com.dot.gallery.core.metadata

internal class MalformedMetadataException(message: String) : IllegalArgumentException(message)

internal fun malformed(message: String): Nothing = throw MalformedMetadataException(message)
