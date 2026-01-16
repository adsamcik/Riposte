package com.mememymood.core.model

import kotlinx.serialization.Serializable

/**
 * Configuration for sharing a meme.
 */
@Serializable
data class ShareConfig(
    /**
     * Target image format for sharing.
     */
    val format: ImageFormat = ImageFormat.WEBP,
    
    /**
     * Compression quality (0-100). Only applies to lossy formats.
     */
    val quality: Int = 85,
    
    /**
     * Maximum width in pixels. Null means no scaling.
     */
    val maxWidth: Int? = 1080,
    
    /**
     * Maximum height in pixels. Null means no scaling.
     */
    val maxHeight: Int? = 1080,
    
    /**
     * Whether to strip metadata from the shared image.
     */
    val stripMetadata: Boolean = true,
    
    /**
     * Whether to add a watermark to the shared image.
     */
    val addWatermark: Boolean = false
) {
    companion object {
        val DEFAULT = ShareConfig()
        
        val HIGH_QUALITY = ShareConfig(
            format = ImageFormat.PNG,
            quality = 100,
            maxWidth = null,
            maxHeight = null,
            stripMetadata = false
        )
        
        val COMPACT = ShareConfig(
            format = ImageFormat.WEBP,
            quality = 70,
            maxWidth = 800,
            maxHeight = 800,
            stripMetadata = true
        )
    }
}

/**
 * Supported image formats for sharing.
 */
@Serializable
enum class ImageFormat(
    val mimeType: String,
    val extension: String,
    val supportsTransparency: Boolean,
    val isLossy: Boolean
) {
    JPEG(
        mimeType = "image/jpeg",
        extension = "jpg",
        supportsTransparency = false,
        isLossy = true
    ),
    PNG(
        mimeType = "image/png",
        extension = "png",
        supportsTransparency = true,
        isLossy = false
    ),
    WEBP(
        mimeType = "image/webp",
        extension = "webp",
        supportsTransparency = true,
        isLossy = true
    ),
    GIF(
        mimeType = "image/gif",
        extension = "gif",
        supportsTransparency = true,
        isLossy = false
    );

    companion object {
        fun fromMimeType(mimeType: String): ImageFormat? {
            return entries.find { it.mimeType == mimeType }
        }
        
        fun fromExtension(extension: String): ImageFormat? {
            return entries.find { it.extension.equals(extension, ignoreCase = true) }
        }
    }
}
