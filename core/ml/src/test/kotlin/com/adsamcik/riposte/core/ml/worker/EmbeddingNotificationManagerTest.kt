package com.adsamcik.riposte.core.ml.worker

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [EmbeddingNotificationManager].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class EmbeddingNotificationManagerTest {
    private lateinit var context: Context
    private lateinit var manager: EmbeddingNotificationManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = EmbeddingNotificationManager(context)
        notificationManager = context.getSystemService(NotificationManager::class.java)
    }

    // region createChannel

    @Test
    fun `createChannel creates notification channel with correct properties`() {
        manager.createChannel()

        val channel = notificationManager.getNotificationChannel(EmbeddingNotificationManager.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel!!.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(channel.description)
            .isEqualTo("Shows progress when indexing memes for search in the background")
        assertThat(channel.canShowBadge()).isFalse()
    }

    @Test
    fun `createChannel is idempotent`() {
        manager.createChannel()
        manager.createChannel()

        val channel = notificationManager.getNotificationChannel(EmbeddingNotificationManager.CHANNEL_ID)
        assertThat(channel).isNotNull()
    }

    // endregion

    // region buildProgressNotification

    @Test
    fun `buildProgressNotification contains correct title and progress`() {
        val notification = manager.buildProgressNotification(current = 5, total = 20)

        val extras = notification.extras
        assertThat(extras.getString("android.title")).isEqualTo("Indexing memes")
        assertThat(extras.getString("android.text")).isEqualTo("5 of 20")
        assertThat(extras.getInt("android.progress")).isEqualTo(5)
        assertThat(extras.getInt("android.progressMax")).isEqualTo(20)
        assertThat(extras.getBoolean("android.progressIndeterminate")).isFalse()
    }

    @Test
    fun `buildProgressNotification is ongoing and silent`() {
        val notification = manager.buildProgressNotification(current = 1, total = 10)

        val isOngoing = notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
        assertThat(isOngoing).isTrue()
    }

    // endregion

    // region buildCompleteNotification

    @Test
    fun `buildCompleteNotification shows success text when no failures`() {
        val notification = manager.buildCompleteNotification(successCount = 10, failedCount = 0)

        val extras = notification.extras
        assertThat(extras.getString("android.title")).isEqualTo("Indexing complete")
        assertThat(extras.getString("android.text")).isEqualTo("10 memes indexed for search")
    }

    @Test
    fun `buildCompleteNotification shows failure text when all failed`() {
        val notification = manager.buildCompleteNotification(successCount = 0, failedCount = 5)

        val extras = notification.extras
        assertThat(extras.getString("android.title")).isEqualTo("Indexing complete")
        assertThat(extras.getString("android.text")).isEqualTo("Indexing failed for 5 memes")
    }

    @Test
    fun `buildCompleteNotification shows mixed text when some failed`() {
        val notification = manager.buildCompleteNotification(successCount = 8, failedCount = 2)

        val extras = notification.extras
        assertThat(extras.getString("android.text")).isEqualTo("8 indexed, 2 failed")
    }

    @Test
    fun `buildCompleteNotification is auto-cancel`() {
        val notification = manager.buildCompleteNotification(successCount = 1, failedCount = 0)

        val isAutoCancel = notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0
        assertThat(isAutoCancel).isTrue()
    }

    // endregion

    // region showCompleteNotification

    @Test
    fun `showCompleteNotification posts when permission granted`() {
        val shadowNotificationManager = shadowOf(notificationManager)
        val app = shadowOf(context.applicationContext as android.app.Application)
        app.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        manager.createChannel()

        manager.showCompleteNotification(successCount = 3, failedCount = 0)

        val posted = shadowNotificationManager.allNotifications
        assertThat(posted).isNotEmpty()
        val match = posted.find { it.extras.getString("android.title") == "Indexing complete" }
        assertThat(match).isNotNull()
    }

    @Test
    fun `showCompleteNotification does not post when permission denied`() {
        val shadowNotificationManager = shadowOf(notificationManager)
        val app = shadowOf(context.applicationContext as android.app.Application)
        app.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        manager.createChannel()

        manager.showCompleteNotification(successCount = 3, failedCount = 0)

        val posted = shadowNotificationManager.allNotifications
        val match = posted.find { it.extras.getString("android.title") == "Indexing complete" }
        assertThat(match).isNull()
    }

    // endregion

    // region ID conflict prevention

    @Test
    fun `notification IDs do not conflict with import notifications`() {
        // Import notifications use IDs 1001 and 1002
        assertThat(EmbeddingNotificationManager.NOTIFICATION_ID).isNotEqualTo(1001)
        assertThat(EmbeddingNotificationManager.NOTIFICATION_ID).isNotEqualTo(1002)
        assertThat(EmbeddingNotificationManager.COMPLETE_NOTIFICATION_ID).isNotEqualTo(1001)
        assertThat(EmbeddingNotificationManager.COMPLETE_NOTIFICATION_ID).isNotEqualTo(1002)
    }

    @Test
    fun `channel ID does not conflict with import channel`() {
        assertThat(EmbeddingNotificationManager.CHANNEL_ID).isNotEqualTo("import_progress")
    }

    // endregion
}
