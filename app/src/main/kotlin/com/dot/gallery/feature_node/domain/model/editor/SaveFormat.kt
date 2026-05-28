package com.dot.gallery.feature_node.domain.model.editor

import android.annotation.SuppressLint
import android.graphics.Bitmap.CompressFormat
import androidx.annotation.Keep

@SuppressLint("NewApi")
@Keep
sealed interface SaveFormat {
    
    val format: CompressFormat
    val mimeType: String
    
    data object PNG : SaveFormat {
        override val format = CompressFormat.PNG
        override val mimeType = "image/png"
    }
    
    data object JPEG : SaveFormat {
        override val format = CompressFormat.JPEG
        override val mimeType = "image/jpeg"
    }
    
    data object WEBP_LOSSLESS : SaveFormat {
        override val format = CompressFormat.WEBP_LOSSLESS
        override val mimeType = "image/webp"
    }
    
    data object WEBP_LOSSY : SaveFormat {
        override val format = CompressFormat.WEBP_LOSSY
        override val mimeType = "image/webp"
    }
    
}
