package com.adsamcik.riposte.core.common.review

/**
 * Tracks positive user actions for in-app review triggering.
 * Implemented in the app module with Google Play In-App Review API.
 */
interface UserActionTracker {
    /** Call after a positive user action (import, favorite, share). */
    fun trackPositiveAction()
}
