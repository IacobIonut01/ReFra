package com.dot.gallery.editor.ui.icons

/**
 * Receiver for the editor's Material Symbols Rounded icon set. Every icon is
 * an extension property in its own file (generated from the official icon
 * font at opsz 24, wght 400, GRAD 0, ROND 50, FILL 1), so each accessor is
 * reachable only through direct references and the bytecode shrinker can
 * drop every icon no call site names. Never aggregate the set — no map or
 * `when` that touches all accessors.
 */
public object EditorSymbols
