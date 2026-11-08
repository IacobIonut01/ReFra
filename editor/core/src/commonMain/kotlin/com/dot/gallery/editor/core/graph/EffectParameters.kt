package com.dot.gallery.editor.core.graph

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoBlendMode
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoRetouch
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PixelSize
import kotlinx.collections.immutable.PersistentMap

/**
 * Parameters carried by an [EffectNode]. Registered custom effects use
 * [Extension], whose opaque key/value payload is preserved verbatim so a
 * backend without the effect can reject it explicitly instead of dropping the
 * node.
 */
sealed interface EffectParameters {

    /** Source read parameters: [sourceId] names the media to decode. */
    data class Source(val sourceId: String) : EffectParameters {
        init {
            require(sourceId.isNotBlank()) { "sourceId must be non-blank" }
        }
    }

    /** Parameters for the `color.basic` effect. */
    data class Color(val adjustments: ColorAdjustments) : EffectParameters

    /** Parameters for the `geometry.photo` crop/rotate/flip effect. */
    data class Geometry(val geometry: PhotoGeometry) : EffectParameters

    /**
     * Parameters for `photo.layer.place`: [layer] carries the transform and
     * mask; [outputSize] is the base canvas size this render targets.
     * [referenceOutputSize] is the full-resolution base output the layer's
     * translation units are expressed in — the transform's translationX/Y
     * scale by `outputSize / referenceOutputSize` before placement.
     */
    data class LayerPlacement(
        val layer: PhotoImageLayer,
        val outputSize: PixelSize,
        val referenceOutputSize: PixelSize
    ) : EffectParameters

    /** Parameters for `photo.composite`. */
    data class Composite(
        val blendMode: PhotoBlendMode,
        val opacity: Float,
        val clipToBelow: Boolean
    ) : EffectParameters

    /**
     * Parameters for `photo.spatial`: [originY] is the frame's top row
     * in absolute output coordinates (0 for a full frame, the expanded
     * band's top for a strip) and [fullSize] the full output size —
     * both keep the lens-blur focus mask identical regardless of
     * banding.
     */
    data class Spatial(
        val spatial: PhotoSpatialEffects,
        val originY: Int = 0,
        val fullSize: PixelSize
    ) : EffectParameters {
        init {
            require(originY >= 0) {
                "spatial originY must be non-negative: $originY"
            }
        }
    }

    /**
     * Parameters for `photo.finishing`: [originY] is the frame's top row
     * in absolute output coordinates (0 for a full frame, the band top
     * for a strip) and [fullSize] the full output size — both keep the
     * vignette mask and grain field identical regardless of banding.
     */
    data class Finishing(
        val finishing: PhotoFinishing,
        val originY: Int = 0,
        val fullSize: PixelSize
    ) : EffectParameters {
        init {
            require(originY >= 0) {
                "finishing originY must be non-negative: $originY"
            }
        }
    }

    /**
     * Parameters for `photo.retouch`: [originY] is the frame's top row in
     * absolute output coordinates and [fullSize] the full output size, so
     * markup, mosaic blocks and the border stay put across banded renders.
     */
    data class Retouch(
        val retouch: PhotoRetouch,
        val originY: Int = 0,
        val fullSize: PixelSize
    ) : EffectParameters {
        init {
            require(originY >= 0) {
                "retouch originY must be non-negative: $originY"
            }
        }
    }

    /** Opaque parameters of a registered extension effect. */
    data class Extension(val values: PersistentMap<String, String>) : EffectParameters
}
