package com.dot.gallery.feature_node.presentation.edit.adjustments.filters

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ColorMatrix
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.presentation.util.applyColorMatrix

private fun applyViaColorMatrix(bitmap: Bitmap, cm: ColorMatrix): Bitmap =
    applyColorMatrix(bitmap, cm.values)

// Group 1: Warm/Vibrant

data class LiteFilter(override val name: String = "Lite") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.05f, 0f, 0f, 0f, 10f,
        0f, 1.05f, 0f, 0f, 10f,
        0f, 0f, 1.05f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class PlayaFilter(override val name: String = "Playa") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.15f, 0.05f, 0f, 0f, 15f,
        0f, 1.08f, 0f, 0f, 10f,
        0f, 0f, 0.92f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class HoneyFilter(override val name: String = "Honey") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.2f, 0.1f, 0f, 0f, 10f,
        0f, 1.05f, 0f, 0f, 8f,
        0f, 0f, 0.8f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class IslaFilter(override val name: String = "Isla") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.05f, 0f, 0.05f, 0f, 5f,
        0f, 1.1f, 0f, 0f, 5f,
        0.05f, 0f, 1.1f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class DesertFilter(override val name: String = "Desert") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val cm = ColorMatrix(floatArrayOf(
            1.1f, 0.05f, 0f, 0f, 10f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        val s = 0.85f
        cm.timesAssign(ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class ClayFilter(override val name: String = "Clay") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.1f, 0.08f, 0.02f, 0f, 8f,
        0.02f, 1.0f, 0.02f, 0f, 5f,
        0f, 0f, 0.88f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class PalmaFilter(override val name: String = "Palma") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val s = 1.25f
        val cm = ColorMatrix(floatArrayOf(
            0.95f, 0f, 0f, 0f, 0f,
            0f, 1.12f, 0f, 0f, 5f,
            0f, 0f, 0.95f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class BlushFilter(override val name: String = "Blush") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.1f, 0.05f, 0.05f, 0f, 12f,
        0f, 0.98f, 0f, 0f, 5f,
        0.02f, 0f, 1.02f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class AlpacaFilter(override val name: String = "Alpaca") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val s = 0.9f
        val cm = ColorMatrix(floatArrayOf(
            1.1f, 0.05f, 0f, 0f, 10f,
            0f, 1.02f, 0f, 0f, 8f,
            0f, 0f, 0.9f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class ModenaFilter(override val name: String = "Modena") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.15f, 0.05f, 0f, 0f, 5f,
        0.02f, 1.08f, 0f, 0f, 5f,
        0f, 0f, 0.95f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

// Group 2: Cool/Urban

data class WestFilter(override val name: String = "West") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val s = 0.8f
        val cm = ColorMatrix(floatArrayOf(
            0.95f, 0f, 0.05f, 0f, 5f,
            0f, 0.98f, 0.02f, 0f, 5f,
            0f, 0.05f, 1.05f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class MetroFilter(override val name: String = "Metro") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val contrast = 1.3f
        return ColorMatrix(floatArrayOf(
            contrast * 0.95f, 0f, 0.05f, 0f, 128f * (1 - contrast),
            0f, contrast * 0.98f, 0.02f, 0f, 128f * (1 - contrast),
            0f, 0.02f, contrast * 1.05f, 0f, 128f * (1 - contrast),
            0f, 0f, 0f, 1f, 0f
        ))
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class ReelFilter(override val name: String = "Reel") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        1.1f, 0f, 0f, 0f, 5f,
        0f, 1.05f, 0.05f, 0f, 0f,
        0f, 0.05f, 1.15f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class BazaarFilter(override val name: String = "Bazaar") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val s = 1.2f
        val cm = ColorMatrix(floatArrayOf(
            1.15f, 0.05f, 0f, 0f, 5f,
            0f, 1.05f, 0f, 0f, 3f,
            0f, 0f, 0.92f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class OllieFilter(override val name: String = "Ollie") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val s = 0.75f
        val cm = ColorMatrix(floatArrayOf(
            0.95f, 0.05f, 0f, 0f, 15f,
            0.02f, 1.0f, 0.03f, 0f, 12f,
            0f, 0.05f, 0.92f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

// Group 3: Black & White / Desaturated

data class OnyxFilter(override val name: String = "Onyx") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val contrast = 1.3f
        val cm = ColorMatrix(floatArrayOf(
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0.33f, 0.33f, 0.33f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.timesAssign(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, 128f * (1 - contrast),
            0f, contrast, 0f, 0f, 128f * (1 - contrast),
            0f, 0f, contrast, 0f, 128f * (1 - contrast),
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class EiffelFilter(override val name: String = "Eiffel") : ImageFilter {
    override fun colorMatrix() = ColorMatrix(floatArrayOf(
        0.33f, 0.33f, 0.33f, 0f, 5f,
        0.33f, 0.33f, 0.33f, 0f, 5f,
        0.33f, 0.33f, 0.33f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f
    ))
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class VogueFilter(override val name: String = "Vogue") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val contrast = 1.25f
        return ColorMatrix(floatArrayOf(
            0.35f * contrast, 0.33f * contrast, 0.32f * contrast, 0f, 128f * (1 - contrast) + 3f,
            0.33f * contrast, 0.34f * contrast, 0.33f * contrast, 0f, 128f * (1 - contrast) + 2f,
            0.32f * contrast, 0.33f * contrast, 0.33f * contrast, 0f, 128f * (1 - contrast),
            0f, 0f, 0f, 1f, 0f
        ))
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class VistaFilter(override val name: String = "Vista") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val contrast = 0.85f
        return ColorMatrix(floatArrayOf(
            0.33f * contrast, 0.33f * contrast, 0.33f * contrast, 0f, 128f * (1 - contrast) + 15f,
            0.33f * contrast, 0.33f * contrast, 0.33f * contrast, 0f, 128f * (1 - contrast) + 15f,
            0.33f * contrast, 0.33f * contrast, 0.33f * contrast, 0f, 128f * (1 - contrast) + 15f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}

data class AstroFilter(override val name: String = "Astro") : ImageFilter {
    override fun colorMatrix(): ColorMatrix {
        val s = 0.3f
        val cm = ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        // Blue shift
        cm.timesAssign(ColorMatrix(floatArrayOf(
            0.95f, 0f, 0f, 0f, 0f,
            0f, 0.97f, 0f, 0f, 0f,
            0f, 0f, 1.1f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )))
        return cm
    }
    override fun apply(bitmap: Bitmap) = applyViaColorMatrix(bitmap, colorMatrix())
}
