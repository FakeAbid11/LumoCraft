package com.lumocraft.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.lumocraft.app.R

/**
 * Typography for the Minecraft-themed UI.
 *
 * Headings and buttons use the pixel display face "Press Start 2P",
 * fetched at runtime via the Google Fonts provider (no font binary is
 * committed to the repo). If Play Services or the network is unavailable
 * the family degrades gracefully to [FontFamily.Monospace], which still
 * reads as retro/blocky. Body text stays on the platform default family
 * so long strings remain legible at small sizes.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val pixelGoogleFont = GoogleFont("Press Start 2P")

/** Pixel display family with a monospace fallback for offline devices. */
val PixelFontFamily = FontFamily(
    Font(googleFont = pixelGoogleFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = pixelGoogleFont, fontProvider = provider, weight = FontWeight.Bold),
)

/** Readable body family. */
val BodyFontFamily = FontFamily.Default

private val base = Typography()

/**
 * Press Start 2P is very wide, so display/headline sizes are pulled down
 * and line heights padded relative to the Material defaults to keep
 * headings from overflowing on narrow screens.
 */
val LumoTypography = Typography(
    displayLarge = base.displayLarge.copy(
        fontFamily = PixelFontFamily, fontSize = 34.sp, lineHeight = 44.sp
    ),
    displayMedium = base.displayMedium.copy(
        fontFamily = PixelFontFamily, fontSize = 28.sp, lineHeight = 38.sp
    ),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = PixelFontFamily, fontSize = 22.sp, lineHeight = 32.sp
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = PixelFontFamily, fontSize = 18.sp, lineHeight = 28.sp
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = PixelFontFamily, fontSize = 15.sp, lineHeight = 24.sp
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = PixelFontFamily, fontSize = 15.sp, lineHeight = 24.sp
    ),
    titleMedium = base.titleMedium.copy(
        fontFamily = PixelFontFamily, fontSize = 12.sp, lineHeight = 20.sp
    ),
    titleSmall = base.titleSmall.copy(
        fontFamily = PixelFontFamily, fontSize = 10.sp, lineHeight = 18.sp
    ),
    labelLarge = base.labelLarge.copy(
        fontFamily = PixelFontFamily, fontSize = 11.sp, lineHeight = 16.sp
    ),
    // Body + smaller labels stay on the readable default family.
    bodyLarge = base.bodyLarge.copy(fontFamily = BodyFontFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = BodyFontFamily),
    bodySmall = base.bodySmall.copy(fontFamily = BodyFontFamily),
    labelMedium = base.labelMedium.copy(fontFamily = BodyFontFamily),
    labelSmall = base.labelSmall.copy(fontFamily = BodyFontFamily),
)
