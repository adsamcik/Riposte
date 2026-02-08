package com.adsamcik.riposte.core.common.util

/**
 * Normalizes an emoji string by stripping Unicode variation selectors and
 * trailing zero-width joiners that don't change the base emoji identity.
 *
 * This ensures that "❤️" (U+2764 U+FE0F) matches "❤" (U+2764) and vice versa.
 */
fun normalizeEmoji(emoji: String): String {
    return emoji
        .replace("\uFE0E", "") // text presentation selector
        .replace("\uFE0F", "") // emoji presentation selector
        .trimEnd('\u200D')     // trailing zero-width joiner (if alone)
}
