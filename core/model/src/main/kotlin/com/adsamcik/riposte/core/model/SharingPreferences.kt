package com.adsamcik.riposte.core.model

import kotlinx.serialization.Serializable

/**
 * User preferences for sharing memes.
 */
@Serializable
data class SharingPreferences(
    /**
     * Default image format for sharing.
     */
    val defaultFormat: ImageFormat = ImageFormat.WEBP,
    /**
     * Default compression quality (0-100).
     */
    val defaultQuality: Int = 85,
    /**
     * Default maximum width in pixels.
     */
    val maxWidth: Int = 1080,
    /**
     * Default maximum height in pixels.
     */
    val maxHeight: Int = 1080,
    /**
     * Default maximum dimension (for square constraint).
     */
    val defaultMaxDimension: Int = 1080,
    /**
     * Whether to strip metadata by default.
     */
    val stripMetadata: Boolean = true,
    /**
     * Recently used apps for sharing (package names).
     */
    val recentShareTargets: List<String> = emptyList(),
    /**
     * Favorite apps for sharing (package names).
     */
    val favoriteShareTargets: List<String> = emptyList(),
    val useNativeShareDialog: Boolean = false,
) {
    /**
     * Converts preferences to a ShareConfig.
     */
    fun toShareConfig(): ShareConfig =
        ShareConfig(
            format = defaultFormat,
            quality = defaultQuality,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            stripMetadata = stripMetadata,
        )

    companion object {
        val DEFAULT = SharingPreferences()
    }
}
