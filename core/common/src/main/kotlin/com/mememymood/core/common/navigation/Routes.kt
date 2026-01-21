package com.mememymood.core.common.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the app.
 * Uses Kotlin serialization for compile-time safety.
 */

// Gallery Feature Routes
@Serializable
object GalleryRoute

@Serializable
data class MemeDetailRoute(val memeId: Long)

// Import Feature Routes
@Serializable
object ImportRoute

// Search Feature Routes
@Serializable
object SearchRoute

// Settings Feature Routes
@Serializable
object SettingsRoute

// Share Feature Routes
@Serializable
data class ShareRoute(val memeId: Long)
