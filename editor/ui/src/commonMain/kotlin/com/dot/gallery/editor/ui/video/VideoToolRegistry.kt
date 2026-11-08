package com.dot.gallery.editor.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoInsertion
import com.dot.gallery.editor.ui.EditorToolDescriptor
import com.dot.gallery.editor.ui.EditorToolEntry
import com.dot.gallery.editor.ui.EditorToolGroup
import com.dot.gallery.editor.ui.EditorToolId
import com.dot.gallery.editor.ui.EditorToolIds
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_audio_add
import com.dot.gallery.editor.ui.generated.resources.editor_audio_clip
import com.dot.gallery.editor.ui.generated.resources.editor_audio_ducking
import com.dot.gallery.editor.ui.generated.resources.editor_audio_eq
import com.dot.gallery.editor.ui.generated.resources.editor_video_mixer
import com.dot.gallery.editor.ui.generated.resources.editor_video_routing
import com.dot.gallery.editor.ui.generated.resources.editor_layers
import com.dot.gallery.editor.ui.generated.resources.editor_voice
import com.dot.gallery.editor.ui.generated.resources.editor_video_export_video
import com.dot.gallery.editor.ui.generated.resources.editor_video_add_media
import com.dot.gallery.editor.ui.generated.resources.editor_video_arrange
import com.dot.gallery.editor.ui.generated.resources.editor_video_colour
import com.dot.gallery.editor.ui.generated.resources.editor_video_transform
import com.dot.gallery.editor.ui.generated.resources.editor_video_trim_title
import com.dot.gallery.editor.ui.icons.CallSplit
import com.dot.gallery.editor.ui.icons.Download
import com.dot.gallery.editor.ui.icons.ColorLens
import com.dot.gallery.editor.ui.icons.ContentCut
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.GraphicEq
import com.dot.gallery.editor.ui.icons.Layers
import com.dot.gallery.editor.ui.icons.Movie
import com.dot.gallery.editor.ui.icons.Mic
import com.dot.gallery.editor.ui.icons.MusicNote
import com.dot.gallery.editor.ui.icons.Reorder
import com.dot.gallery.editor.ui.icons.Transform
import com.dot.gallery.editor.ui.icons.Tune
import com.dot.gallery.editor.ui.icons.VolumeOff
import com.dot.gallery.editor.ui.icons.VolumeUp

internal const val TAG_VIDEO_TOOL_ADD_MEDIA = "videoEditor.tool.addMedia"
internal const val TAG_VIDEO_TOOL_TRANSFORM = "videoEditor.tool.transform"
internal const val TAG_VIDEO_TOOL_COLOUR = "videoEditor.tool.colour"
internal const val TAG_VIDEO_TOOL_LAYERS = "videoEditor.tool.layers"
internal const val TAG_VIDEO_TOOL_ADD_AUDIO = "videoEditor.tool.addAudio"
internal const val TAG_VIDEO_TOOL_CLIP_AUDIO = "videoEditor.tool.clipAudio"
internal const val TAG_VIDEO_TOOL_MIXER = "videoEditor.tool.mixer"
internal const val TAG_VIDEO_TOOL_ROUTING = "videoEditor.tool.routing"
internal const val TAG_VIDEO_TOOL_DUCKING = "videoEditor.tool.ducking"
internal const val TAG_VIDEO_TOOL_EQ = "videoEditor.tool.eq"
internal const val TAG_VIDEO_TOOL_VOICE = "videoEditor.tool.voice"
internal const val TAG_VIDEO_TOOL_EXPORT = "videoEditor.tool.export"

/**
 * The workspace-owned bits a video tool surface needs but the session
 * state does not carry: the chosen media-insertion mode and a way to
 * open the Media panel primed for an overlay pick. Provided by
 * [VideoEditorContent]; surfaces degrade to the default insertion and a
 * direct request when it is absent (tests, previews).
 */
internal class VideoWorkspaceContext(
    val insertion: VideoInsertion,
    val onSelectInsertion: (VideoInsertion) -> Unit,
    val openMedia: (VideoInsertion) -> Unit,
    /**
     * The shared timeline viewport — surfaces that carry zoom controls
     * (compact Arrange) drive it. Null where a command row already owns
     * the zoom trio (wide timeline header).
     */
    val viewport: VideoTimelineViewport? = null,
    /**
     * Whether the Arrange surface emits its own Split/Delete/Move
     * buttons — compact yes; wide no, where the timeline command row
     * already owns those tags and actions.
     */
    val arrangeCommands: Boolean = true,
    /**
     * Opens another registered tool's surface — the mixer's Channels
     * action switches to routing without touching panel state directly.
     * Null no-ops where a host embeds the target (desktop mixer pane).
     */
    val openAudioTask: (EditorToolId) -> Unit = {},
    /**
     * Dismisses the active tool surface back to the timeline — the
     * voice permission card's "Not now". Null where a surface cannot be
     * dismissed (desktop inspector) — the button hides rather than
     * painting a dead control.
     */
    val closeTool: (() -> Unit)? = null
)

internal val LocalVideoWorkspace =
    staticCompositionLocalOf<VideoWorkspaceContext?> { null }

/**
 * The video editor's registered tools — every entry is a real working
 * surface, shared as descriptors so the dock, the context header and the
 * inspector all read one source of truth. Entries the session cannot
 * honour (Text, Audio) are simply absent, never painted disabled.
 *
 * Add media and Video layers are also dock destinations — they register
 * here so the catalogue, the context header and the inspector resolve
 * them through the same dispatch.
 */
internal fun videoToolEntries(): List<EditorToolEntry<VideoEditorState, VideoEditorActions>> =
    listOf(
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoAddMedia,
                title = Res.string.editor_video_add_media,
                icon = EditorSymbols.Movie,
                group = EditorToolGroup.Edit
            ),
            tag = TAG_VIDEO_TOOL_ADD_MEDIA,
            available = { it.sequence != null }
        ) { state, actions ->
            val workspace = LocalVideoWorkspace.current
            VideoAddMediaSurface(
                state = state,
                actions = actions,
                insertion = workspace?.insertion ?: VideoInsertion.End,
                onInsertionChange = { workspace?.onSelectInsertion?.invoke(it) }
            )
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoArrange,
                title = Res.string.editor_video_arrange,
                icon = EditorSymbols.Reorder,
                group = EditorToolGroup.Edit
            ),
            tag = TAG_VIDEO_TAB_CLIPS,
            available = { it.sequence != null }
        ) { state, actions ->
            val workspace = LocalVideoWorkspace.current
            VideoArrangeSurface(
                state = state,
                actions = actions,
                viewport = workspace?.viewport,
                showCommands = workspace?.arrangeCommands ?: true
            )
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoTrim,
                title = Res.string.editor_video_trim_title,
                icon = EditorSymbols.ContentCut,
                group = EditorToolGroup.Edit
            ),
            tag = TAG_VIDEO_TAB_TRIM,
            available = { state ->
                state.sequence?.let { s ->
                    state.selectedClipId != null ||
                        s.tracks.flatMap { t -> t.clips }.isNotEmpty()
                } == true
            }
        ) { state, actions -> VideoTrimInspector(state = state, actions = actions) },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoTransform,
                title = Res.string.editor_video_transform,
                icon = EditorSymbols.Transform,
                group = EditorToolGroup.Picture
            ),
            tag = TAG_VIDEO_TOOL_TRANSFORM,
            available = { it.selectedClipId != null }
        ) { state, actions ->
            VideoTransformSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoColour,
                title = Res.string.editor_video_colour,
                icon = EditorSymbols.ColorLens,
                group = EditorToolGroup.Picture
            ),
            tag = TAG_VIDEO_TOOL_COLOUR,
            available = { it.selectedClipId != null }
        ) { state, actions ->
            VideoColourSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoLayers,
                title = Res.string.editor_layers,
                icon = EditorSymbols.Layers,
                group = EditorToolGroup.Edit
            ),
            tag = TAG_VIDEO_TOOL_LAYERS,
            available = { it.sequence != null }
        ) { state, actions ->
            val workspace = LocalVideoWorkspace.current
            VideoLayersSurface(
                state = state,
                actions = actions,
                onAddOverlay = {
                    if (workspace != null) {
                        workspace.openMedia(VideoInsertion.Overlay)
                    } else {
                        actions.requestAddMedia(VideoInsertion.Overlay)
                    }
                }
            )
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoAddAudio,
                title = Res.string.editor_audio_add,
                icon = EditorSymbols.MusicNote,
                group = EditorToolGroup.Audio
            ),
            tag = TAG_VIDEO_TOOL_ADD_AUDIO,
            available = { it.sequence != null }
        ) { state, actions ->
            VideoAddAudioSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoClipAudio,
                title = Res.string.editor_audio_clip,
                icon = EditorSymbols.VolumeUp,
                group = EditorToolGroup.Audio
            ),
            tag = TAG_VIDEO_TOOL_CLIP_AUDIO,
            available = { it.sequence != null }
        ) { state, actions ->
            VideoClipAudioSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoMixer,
                title = Res.string.editor_video_mixer,
                icon = EditorSymbols.GraphicEq,
                group = EditorToolGroup.Audio
            ),
            tag = TAG_VIDEO_TOOL_MIXER,
            available = { it.sequence != null }
        ) { state, actions ->
            VideoMixerSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoRouting,
                title = Res.string.editor_video_routing,
                icon = EditorSymbols.CallSplit,
                group = EditorToolGroup.Audio
            ),
            tag = TAG_VIDEO_TOOL_ROUTING,
            available = { state ->
                state.sequence?.audioTracks?.isNotEmpty() == true
            }
        ) { state, actions ->
            VideoRoutingSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoDucking,
                title = Res.string.editor_audio_ducking,
                icon = EditorSymbols.VolumeOff,
                group = EditorToolGroup.Audio
            ),
            tag = TAG_VIDEO_TOOL_DUCKING,
            // A sidechain needs a lane to duck and a lane to listen to.
            available = { state ->
                (state.sequence?.audioTracks?.size ?: 0) >= 2
            }
        ) { state, actions ->
            VideoDuckingSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoEq,
                title = Res.string.editor_audio_eq,
                icon = EditorSymbols.Tune,
                group = EditorToolGroup.Audio
            ),
            tag = TAG_VIDEO_TOOL_EQ,
            available = { state ->
                state.sequence?.audioTracks?.isNotEmpty() == true
            }
        ) { state, actions ->
            VideoEqSurface(state = state, actions = actions)
        },
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoVoice,
                title = Res.string.editor_voice,
                icon = EditorSymbols.Mic,
                group = EditorToolGroup.Audio
            ),
            tag = TAG_VIDEO_TOOL_VOICE,
            available = { it.sequence != null }
        ) { state, actions ->
            VideoVoiceSurface(state = state, actions = actions)
        },
        // Deliver › Export — selecting it opens the export sheet; the
        // content is the same options face for hosts that embed it.
        EditorToolEntry(
            descriptor = EditorToolDescriptor(
                id = EditorToolIds.VideoExport,
                title = Res.string.editor_video_export_video,
                icon = EditorSymbols.Download,
                group = EditorToolGroup.Deliver
            ),
            tag = TAG_VIDEO_TOOL_EXPORT,
            available = { (it.sequence?.duration?.ticks ?: 0L) > 0L }
        ) { state, actions ->
            VideoExportBody(
                state = state,
                actions = actions,
                stage = VideoExportStage.Options,
                onExport = actions.saveCopy,
                onHide = {},
                showTitle = false
            )
        }
    )

internal fun videoToolDescriptor(
    id: com.dot.gallery.editor.ui.EditorToolId
): EditorToolDescriptor =
    videoToolEntries().first { it.descriptor.id == id }.descriptor

/**
 * A registered tool's panel content, resolved through the registry — the
 * context header/dock dispatch by id, so a new tool needs no bespoke
 * panel wiring of its own.
 */
@Composable
internal fun VideoToolContent(
    id: com.dot.gallery.editor.ui.EditorToolId,
    state: VideoEditorState,
    actions: VideoEditorActions
) {
    videoToolEntries().first { it.descriptor.id == id }
        .content(state, actions)
}
