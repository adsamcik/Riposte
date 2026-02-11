package com.adsamcik.riposte.review

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.adsamcik.riposte.core.common.review.UserActionTracker
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Play In-App Review prompts.
 *
 * Triggers a review request after the user has opened the app at least
 * [MIN_SESSIONS] times and performed at least [MIN_POSITIVE_ACTIONS]
 * positive actions (import, favorite, share). The Play API itself
 * rate-limits actual dialog display.
 */
@Singleton
class InAppReviewManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : UserActionTracker {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** Call once per app launch (e.g., in onCreate). */
        fun trackAppOpen() {
            val count = prefs.getInt(KEY_SESSION_COUNT, 0) + 1
            prefs.edit().putInt(KEY_SESSION_COUNT, count).apply()
        }

        /** Call after a positive user action (import, favorite, share). */
        override fun trackPositiveAction() {
            val count = prefs.getInt(KEY_POSITIVE_ACTIONS, 0) + 1
            prefs.edit().putInt(KEY_POSITIVE_ACTIONS, count).apply()
        }

        /**
         * Requests the in-app review flow if conditions are met.
         * The Play API decides whether to actually show the dialog.
         */
        fun requestReviewIfReady(activity: Activity) {
            if (!shouldRequestReview()) return

            val manager = ReviewManagerFactory.create(context)
            manager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
                manager.launchReviewFlow(activity, reviewInfo)
                prefs.edit()
                    .putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
                    .apply()
            }
            // Silently ignore failures — review prompts are optional
        }

        private fun shouldRequestReview(): Boolean {
            val sessions = prefs.getInt(KEY_SESSION_COUNT, 0)
            val actions = prefs.getInt(KEY_POSITIVE_ACTIONS, 0)
            val lastRequest = prefs.getLong(KEY_LAST_REVIEW_REQUEST, 0L)
            val daysSinceLastRequest =
                (System.currentTimeMillis() - lastRequest) / (1000 * 60 * 60 * 24)

            return sessions >= MIN_SESSIONS &&
                actions >= MIN_POSITIVE_ACTIONS &&
                (lastRequest == 0L || daysSinceLastRequest >= MIN_DAYS_BETWEEN_REQUESTS)
        }

        companion object {
            private const val PREFS_NAME = "in_app_review"
            private const val KEY_SESSION_COUNT = "session_count"
            private const val KEY_POSITIVE_ACTIONS = "positive_actions"
            private const val KEY_LAST_REVIEW_REQUEST = "last_review_request"
            private const val MIN_SESSIONS = 3
            private const val MIN_POSITIVE_ACTIONS = 2
            private const val MIN_DAYS_BETWEEN_REQUESTS = 60
        }
    }
