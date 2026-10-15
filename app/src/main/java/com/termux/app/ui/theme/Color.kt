package com.termux.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * Every colour in the app resolves through one of these tokens — nothing hard-codes a hex at a call
 * site — so changing the palette means editing this file alone.
 *
 * The accent is a slate-blue rather than a saturated violet. Violet on a near-black background is
 * the single most recognisable "generated app" colour, and a pure red danger tint reads as an alarm
 * rather than as an error. Both were replaced with calmer equivalents: a blue that sits close to
 * the surface it lives on, and a red desaturated enough to be legible without shouting.
 *
 * On glass: the elevated surfaces are slightly translucent so whatever sits behind them — the
 * background wash, a scrim — shows through faintly. Kept low-alpha on purpose. Full frosted glass
 * everywhere is its own cliché now, and at this contrast ratio it costs legibility for a look that
 * does not survive contact with a real transcript.
 */
val Bg = Color(0xFF0D1117)
val BgElevated = Color(0xF21A212B)
val Surface = Color(0xF01E242E)
val SurfaceHigh = Color(0xF5272F3B)
val Border = Color(0x3D9FB4CC)

/** Primary action colour: a slate-blue, muted enough to sit quietly on the dark surfaces. */
val Accent = Color(0xFF3D7EFF)
/** Lighter sibling of [Accent], for text and the variant chip where contrast matters more. */
val AccentSoft = Color(0xFF79A8FF)

val Cyan = Color(0xFF2D9CDB)
val TextPrimary = Color(0xFFE6EDF3)
val TextMuted = Color(0xFF8B949E)

/** The user's own turns. Cool blue against the agent's near-white so the two read apart. */
val UserText = Color(0xFF79B8FF)

val Success = Color(0xFF3FB950)
val Warning = Color(0xFFD29922)

/**
 * Errors. Desaturated from a true red on purpose: at full saturation this reads as an emergency
 * rather than as a failed tool call, and the app raises it for ordinary things like a denied edit.
 */
val Danger = Color(0xFFE5534B)

/**
 * Brand gradient — Cody's blue.
 *
 * Used for the mark and the launcher icon. Low-contrast on purpose: a saturated multi-hue gradient
 * at icon size reads as decoration, and the mark is small enough that it does not need to shout.
 */
val BrandColors = listOf(
    Color(0xFF4A8CFF),
    Color(0xFF7FB4FF),
    Color(0xFFBFE0FF),
)