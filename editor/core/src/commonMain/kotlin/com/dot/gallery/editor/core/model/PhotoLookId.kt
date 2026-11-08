package com.dot.gallery.editor.core.model

/**
 * The fixed catalogue of photo looks — one-tap stylized colour grades
 * applied inside the finishing stage after tone curves. [None] is the
 * identity: a recipe carrying it is neutral regardless of amount.
 */
enum class PhotoLookId {
    None,

    // Natural colour
    Clean,
    Vivid,
    Pastel,
    Chrome,

    // Warm and cool grades
    WarmFade,
    Cool,
    Golden,
    Desert,
    Forest,
    Rose,
    BlueHour,

    // Cinema
    Cinematic,
    TealOrange,
    CinemaGreen,

    // Film
    Vintage,
    Fade,
    Matte,
    Bleach,
    Sepia,

    // Monochrome
    Mono,
    Noir
}
