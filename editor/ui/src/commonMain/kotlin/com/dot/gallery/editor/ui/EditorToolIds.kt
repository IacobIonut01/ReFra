package com.dot.gallery.editor.ui

/**
 * The registered tool identifiers — one stable string per real tool,
 * namespaced by editor so the shared [EditorWorkspaceState] format can
 * later serve both.
 */
object EditorToolIds {
    val PhotoLight = EditorToolId("photo.light")
    val PhotoColour = EditorToolId("photo.colour")
    val PhotoCurves = EditorToolId("photo.curves")
    val PhotoWhiteBalance = EditorToolId("photo.whiteBalance")
    val PhotoVignette = EditorToolId("photo.vignette")
    val PhotoLensBlur = EditorToolId("photo.lensBlur")
    val PhotoGrain = EditorToolId("photo.grain")
    val PhotoLooks = EditorToolId("photo.looks")
    val PhotoCrop = EditorToolId("photo.crop")
    val PhotoPerspective = EditorToolId("photo.perspective")
    val PhotoDetail = EditorToolId("photo.detail")
    val PhotoDoubleExposure = EditorToolId("photo.doubleExposure")
    val PhotoTransform = EditorToolId("photo.transform")
    val PhotoBlend = EditorToolId("photo.blend")
    val PhotoLayerMask = EditorToolId("photo.layerMask")
    val PhotoMarkup = EditorToolId("photo.markup")
    val PhotoCutout = EditorToolId("photo.cutout")
    val PhotoRemoveBackground = EditorToolId("photo.removeBackground")
    val PhotoColourTone = EditorToolId("photo.colourTone")
    val PhotoPosterize = EditorToolId("photo.posterize")
    val PhotoEdges = EditorToolId("photo.edges")
    val PhotoBorders = EditorToolId("photo.borders")
    val PhotoDevelop = EditorToolId("photo.develop")
    val VideoAddMedia = EditorToolId("video.addMedia")
    val VideoArrange = EditorToolId("video.arrange")
    val VideoTrim = EditorToolId("video.trim")
    val VideoTransform = EditorToolId("video.transform")
    val VideoColour = EditorToolId("video.colour")
    val VideoLayers = EditorToolId("video.layers")
    val VideoAddAudio = EditorToolId("video.addAudio")
    val VideoClipAudio = EditorToolId("video.clipAudio")
    val VideoMixer = EditorToolId("video.mixer")
    val VideoRouting = EditorToolId("video.routing")
    val VideoDucking = EditorToolId("video.ducking")
    val VideoEq = EditorToolId("video.eq")
    val VideoVoice = EditorToolId("video.voice")
    val VideoExport = EditorToolId("video.export")
}
