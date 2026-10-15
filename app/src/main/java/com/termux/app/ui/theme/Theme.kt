package com.termux.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val CodyColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = Cyan,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00131A),
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = Danger,
)

/**
 * Type scale.
 *
 * Every size here was raised by roughly 1–2sp from the Material defaults the theme replaced. The
 * earlier scale matched the spec on paper but read small on the phone this actually runs on — a
 * 360dp-wide screen at 480dpi, where 12sp body text is comfortable on a tablet and cramped on a
 * handset. `bodyMedium` and `bodyLarge` carry the conversation, so they got the largest bump;
 * `labelSmall` stayed small because it only ever holds meta text like timestamps and "· free".
 */
private val CodyTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp),
)

@Composable
fun CodyTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = CodyColorScheme,
        typography = CodyTypography,
        // The screens paint their own backgrounds with `Modifier.background` rather than wrapping
        // everything in a Material `Surface`, so nothing ever overrode `LocalContentColor` and it
        // stayed at its default of `Color.Black`. Every Text/ClickableText that did not name an
        // explicit colour therefore rendered black-on-black — which is exactly how the model's
        // replies became invisible. Pin it once, here, so the whole app inherits a light default.
        content = {
            CompositionLocalProvider(LocalContentColor provides TextPrimary, content = content)
        },
    )
}
