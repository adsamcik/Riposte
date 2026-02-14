@file:Suppress("MagicNumber")

package com.adsamcik.riposte.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

// ── M3 Standard Shape Scale ───────────────────────────────
val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

// ── Legacy Shapes (backward compatibility) ────────────────
object MoodShapes {
    val MemeCard = RoundedCornerShape(16.dp)
    val EmojiChip = RoundedCornerShape(20.dp)
    val SearchBar = RoundedCornerShape(28.dp)
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Dialog = RoundedCornerShape(28.dp)
}

// ── Expressive Signature Shapes ───────────────────────────

/**
 * Soft heptagonal shape — Riposte's signature card shape.
 * Uses the full rectangle width/height with gentle scalloped corners,
 * preserving ~95% of image content while adding personality.
 * Each corner gets a subtle inward curve for the "cookie bite" feel.
 */
val CookieShape: Shape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val path = Path()
            val w = size.width
            val h = size.height
            // Corner indent depth — subtle enough to preserve content
            val indent = min(w, h) * 0.035f
            val cornerRadius = min(w, h) * 0.08f

            // Top edge: flat top with indented corners
            path.moveTo(cornerRadius, 0f)
            path.lineTo(w - cornerRadius, 0f)
            // Top-right corner: scalloped
            path.quadraticTo(w, 0f, w, cornerRadius)
            // Right edge
            path.lineTo(w, h * 0.35f)
            path.quadraticTo(w - indent, h * 0.5f, w, h * 0.65f)
            path.lineTo(w, h - cornerRadius)
            // Bottom-right corner: scalloped
            path.quadraticTo(w, h, w - cornerRadius, h)
            // Bottom edge
            path.lineTo(w * 0.65f, h)
            path.quadraticTo(w * 0.5f, h - indent, w * 0.35f, h)
            path.lineTo(cornerRadius, h)
            // Bottom-left corner: scalloped
            path.quadraticTo(0f, h, 0f, h - cornerRadius)
            // Left edge
            path.lineTo(0f, h * 0.65f)
            path.quadraticTo(indent, h * 0.5f, 0f, h * 0.35f)
            path.lineTo(0f, cornerRadius)
            // Top-left corner: scalloped
            path.quadraticTo(0f, 0f, cornerRadius, 0f)
            path.close()
            return Outline.Generic(path)
        }
    }

/**
 * Rounded soft-square with exaggerated corners.
 * Used for selected emoji chips and organization contexts.
 * Softer than Cookie, signals categorization/tagging.
 */
val CloverShape: Shape = RoundedCornerShape(35)

/**
 * Circle shape used for active/sharing states.
 * Signals action and celebration moments.
 */
val SunnyShape: Shape = CircleShape

// ── Riposte Shape System ──────────────────────────────────

/**
 * Expressive shape tokens for component assignment.
 *
 * Shape hierarchy: 30% expressive / 70% standard.
 * - [CookieShape]: Content browsing (meme cards)
 * - [CloverShape]: Organization (selected chips, share)
 * - [SunnyShape]: Action states (selected cards, FAB expanded)
 * - Standard [RoundedCornerShape]: System/utility (search, nav, sheets)
 */
object RiposteShapes {
    // ── High-Impact Expressive ─────────────────────
    val MemeCard = CookieShape
    val MemeCardSelected = SunnyShape
    val FABDefault = RoundedCornerShape(20.dp)
    val FABExpanded = RoundedCornerShape(28.dp)
    val ShareButton = CloverShape

    // ── Emoji Chips ────────────────────────────────
    val EmojiChipDefault = RoundedCornerShape(20.dp)
    val EmojiChipSelected = CloverShape

    // ── Functional Standard ────────────────────────
    val SearchBar = RoundedCornerShape(28.dp)
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val FloatingToolbar = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(16.dp)
    val SettingsItem = RoundedCornerShape(12.dp)
}

/**
 * Returns a shape for a meme card based on its grid position.
 * Creates visual variety: 80% Cookie, 20% Clover pattern.
 */
fun getMemeCardShape(
    gridIndex: Int,
    isSelected: Boolean = false,
): Shape =
    when {
        isSelected -> RiposteShapes.MemeCardSelected
        gridIndex % 5 == 0 -> CloverShape
        else -> RiposteShapes.MemeCard
    }
