@file:Suppress("MagicNumber")

package com.adsamcik.riposte.core.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================================
// Riposte "Digital Joy" Color Identity
// M3 Expressive palette: Electric Orange / Rich Purple / Cyber Pink
// ==========================================================

// Legacy M3 defaults (kept for any residual references)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ── Brand Colors: Light Mode ──────────────────────────────
val MoodPrimary = Color(0xFFFF6B35) // Electric Orange
val MoodSecondary = Color(0xFF8B5CF6) // Rich Purple
val MoodTertiary = Color(0xFFF221A9) // Cyber Pink
val MoodAccent = Color(0xFF84CC16) // Electric Lime (celebration/success)

// ── Brand Colors: Dark Mode ───────────────────────────────
val MoodPrimaryDark = Color(0xFFFF8A5B) // Warm Orange
val MoodSecondaryDark = Color(0xFFA78BFA) // Amethyst
val MoodTertiaryDark = Color(0xFFFB7DC7) // Neon Pink
val MoodAccentDark = Color(0xFFA3E635) // Bright Lime

// ── On-Brand Colors ───────────────────────────────────────
val OnMoodPrimary = Color(0xFF000000)
val OnMoodPrimaryDark = Color(0xFF000000)
val OnMoodSecondary = Color(0xFFFFFFFF)
val OnMoodSecondaryDark = Color(0xFF000000)

// ── Surface Colors: Light ─────────────────────────────────
val SurfaceLight = Color(0xFFFFFBFE)
val SurfaceDark = Color(0xFF0A0A0A) // Near-true black for OLED
val SurfaceContainerLight = Color(0xFFF1F5F9)
val SurfaceContainerDark = Color(0xFF1A1A1A) // Warm dark
val SurfaceContainerHighLight = Color(0xFFE2E8F0)
val SurfaceContainerHighDark = Color(0xFF2A2A2A)

// ── Semantic Colors ───────────────────────────────────────
val Success = Color(0xFF059669)
val SuccessDark = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val WarningDark = Color(0xFFFBBF24)
val Error = Color(0xFFDC2626)
val ErrorDark = Color(0xFFF87171)
val Info = Color(0xFF0284C7)
val InfoDark = Color(0xFF38BDF8)

// ── Emoji Card Backgrounds (warm, expressive variety) ─────
val EmojiCardBackgroundsLight =
    listOf(
        // Amber 100
        Color(0xFFFEF3C7),
        // Green 100
        Color(0xFFDCFCE7),
        // Blue 100
        Color(0xFFDBEAFE),
        // Pink 100
        Color(0xFFFCE7F3),
        // Indigo 100
        Color(0xFFE0E7FF),
        // Purple 100
        Color(0xFFF3E8FF),
        // Orange 100
        Color(0xFFFFEDD5),
        // Cyan 100
        Color(0xFFCFFAFE),
    )

val EmojiCardBackgroundsDark =
    listOf(
        // Amber 900
        Color(0xFF78350F),
        // Green 900
        Color(0xFF14532D),
        // Blue 900
        Color(0xFF1E3A5F),
        // Pink 900
        Color(0xFF831843),
        // Indigo 900
        Color(0xFF312E81),
        // Purple 900
        Color(0xFF581C87),
        // Orange 900
        Color(0xFF7C2D12),
        // Cyan 900
        Color(0xFF164E63),
    )

val EmojiCardBackgrounds = EmojiCardBackgroundsLight

// ── Emoji Context Colors (for contextual UI tinting) ──────
object EmojiContextColors {
    val Joy = Color(0xFFFCD34D) // 😂 Warm Yellow
    val Fire = Color(0xFFF97316) // 🔥 Hot Red-Orange
    val Skull = Color(0xFF64748B) // 💀 Cool Gray
    val Heart = Color(0xFFF43F5E) // ❤️ Rose Pink
    val Gem = Color(0xFFEAB308) // 💎 Gold
    val Water = Color(0xFF0284C7) // 🌊 Ocean Blue
    val Plant = Color(0xFF059669) // 🌱 Forest Green
    val Electric = Color(0xFF06B6D4) // ⚡ Neon Cyan
}

/** Maps known emojis to their semantic [EmojiContextColors] value. */
private val emojiContextMap: Map<String, Color> =
    mapOf(
        "😂" to EmojiContextColors.Joy,
        "🤣" to EmojiContextColors.Joy,
        "😅" to EmojiContextColors.Joy,
        "😊" to EmojiContextColors.Joy,
        "😄" to EmojiContextColors.Joy,
        "🔥" to EmojiContextColors.Fire,
        "😤" to EmojiContextColors.Fire,
        "💀" to EmojiContextColors.Skull,
        "🙃" to EmojiContextColors.Skull,
        "😏" to EmojiContextColors.Skull,
        "❤️" to EmojiContextColors.Heart,
        "🥺" to EmojiContextColors.Heart,
        "🥹" to EmojiContextColors.Heart,
        "😭" to EmojiContextColors.Heart,
        "😩" to EmojiContextColors.Heart,
        "💎" to EmojiContextColors.Gem,
        "✨" to EmojiContextColors.Gem,
        "⭐" to EmojiContextColors.Gem,
        "🌊" to EmojiContextColors.Water,
        "💧" to EmojiContextColors.Water,
        "🌱" to EmojiContextColors.Plant,
        "🌿" to EmojiContextColors.Plant,
        "☕" to EmojiContextColors.Plant,
        "😌" to EmojiContextColors.Plant,
        "⚡" to EmojiContextColors.Electric,
        "🧑" to EmojiContextColors.Electric,
        "🌧️" to EmojiContextColors.Water,
    )

/**
 * Returns an expressive context color for the given emoji.
 *
 * Known emojis get their semantic color from [EmojiContextColors].
 * Unknown emojis get a deterministic color from [EmojiCardBackgrounds].
 */
fun getEmojiContextColor(emoji: String): Color =
    emojiContextMap[emoji]
        ?: EmojiCardBackgrounds[
            emoji.hashCode().mod(EmojiCardBackgrounds.size).let {
                if (it < 0) it + EmojiCardBackgrounds.size else it
            },
        ]
