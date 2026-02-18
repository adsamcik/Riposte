package com.adsamcik.riposte.core.common.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the app.
 * Uses Kotlin serialization for compile-time safety.
 */

@Serializable
object GalleryRoute

@Serializable
data class MemeDetailRoute(val memeId: Long)

// Import Feature Routes
@Serializable
object ImportRoute

// Settings Feature Routes
@Serializable
object SettingsRoute

@Serializable
object LicensesRoute
