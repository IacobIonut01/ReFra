package com.dot.gallery.editor.ui

/** The shader compiled fine but the frame's canvas cannot take it (software raster). */
internal class ShaderCanvasUnavailableException : RuntimeException(
    "runtime shader cannot draw on this canvas"
)
