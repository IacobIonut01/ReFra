package com.dot.gallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.dot.gallery.R

val GoogleSansFontFamily = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.google_sans_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_bold, FontWeight.Bold),
    Font(R.font.google_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = GoogleSansFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = GoogleSansFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = GoogleSansFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = GoogleSansFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = GoogleSansFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = GoogleSansFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = GoogleSansFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = GoogleSansFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = GoogleSansFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = GoogleSansFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = GoogleSansFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = GoogleSansFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = GoogleSansFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = GoogleSansFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = GoogleSansFontFamily),
)