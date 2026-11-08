package com.dot.gallery.editor.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.google_sans_medium
import com.dot.gallery.editor.ui.generated.resources.google_sans_regular
import com.dot.gallery.editor.ui.generated.resources.google_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Standalone default theme for the editor library — the neutral V3 scheme
 * (shared with [EditorWorkspaceTheme] via [v3ColorScheme]) with the
 * Material shape scale and Google Sans typography. Gallery hosts instead
 * wrap the editor in `GalleryTheme { EditorWorkspaceTheme { … } }`; nothing
 * inside [PhotoEditorScreen] forces this theme, so callers supply their own.
 *
 * The Google Sans files under `composeResources/font` are byte copies of
 * `app/core/ui/src/commonMain/composeResources/font/google_sans_{regular,
 * medium,semibold}.ttf` — the editor library cannot depend on `:app:core:ui`
 * (it has no desktop target), so the resources are duplicated here with the
 * same provenance: Google Sans, licensed fonts bundled with the app.
 */
@Composable
fun EditorTheme(
    darkTheme: Boolean = true,
    typography: Typography = editorTypography(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = v3ColorScheme(darkTheme),
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        shapes = EditorShapes
    ) {
        EditorWorkspaceTheme(content = content)
    }
}

private val EditorShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun editorTypography(): Typography {
    val family = FontFamily(
        Font(Res.font.google_sans_regular, FontWeight.Normal),
        Font(Res.font.google_sans_medium, FontWeight.Medium),
        Font(Res.font.google_sans_semibold, FontWeight.SemiBold)
    )
    val base = Typography()
    fun TextStyle.withEditorFont() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.withEditorFont(),
        displayMedium = base.displayMedium.withEditorFont(),
        displaySmall = base.displaySmall.withEditorFont(),
        headlineLarge = base.headlineLarge.withEditorFont(),
        headlineMedium = base.headlineMedium.withEditorFont(),
        headlineSmall = base.headlineSmall.withEditorFont(),
        titleLarge = base.titleLarge.withEditorFont(),
        titleMedium = base.titleMedium.withEditorFont(),
        titleSmall = base.titleSmall.withEditorFont(),
        bodyLarge = base.bodyLarge.withEditorFont(),
        bodyMedium = base.bodyMedium.withEditorFont(),
        bodySmall = base.bodySmall.withEditorFont(),
        labelLarge = base.labelLarge.withEditorFont(),
        labelMedium = base.labelMedium.withEditorFont(),
        labelSmall = base.labelSmall.withEditorFont()
    )
}
