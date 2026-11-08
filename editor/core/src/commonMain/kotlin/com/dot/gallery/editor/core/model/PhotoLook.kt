package com.dot.gallery.editor.core.model

/**
 * One look selection — [id] picks the colour grade, [amount] the blend
 * toward it in model units (the UI speaks 0..100). [None] is the
 * neutral recipe: no look is applied whatever [amount] reads.
 */
data class PhotoLook(
    val id: PhotoLookId = PhotoLookId.None,
    val amount: Float = 1f
) {
    init {
        require(amount.isFinite() && amount in 0f..1f) {
            "look amount must be finite inside 0..1: $amount"
        }
    }

    val isNeutral: Boolean get() = id == PhotoLookId.None || amount == 0f
}
