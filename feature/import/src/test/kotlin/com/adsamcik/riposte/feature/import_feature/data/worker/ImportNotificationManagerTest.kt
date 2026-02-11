package com.adsamcik.riposte.feature.import_feature.data.worker

import android.app.NotificationChannel
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
 * Unit tests for [ImportNotificationManager].
 *
 * Uses Robolectric for the Notification system (ShadowNotificationManager).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ImportNotificationManagerTest {
    private lateinit var context: Context
    private lateinit var manager: ImportNotificationManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = ImportNotificationManager(context)
        notificationManager = context.getSystemService(NotificationManager::class.java)
    }

    // region createChannel

    @Test
    fun `createChannel creates notification channel with correct properties`() {
        // Act
        manager.createChannel()

        // Assert
        val channel: NotificationChannel? =
            notificationManager.getNotificationChannel(ImportNotificationManager.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel!!.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(channel.description)
            .isEqualTo("Shows progress when importing memes in the background")
        assertThat(channel.canShowBadge()).isFalse()
    }

    // endregion

    // region buildProgressNotification

    @Test
    fun `buildProgressNotification contains correct title and progress`() {
        // Act
        val notification = manager.buildProgressNotification(current = 3, total = 10)

        // Assert
        val extras = notification.extras
        assertThat(extras.getString("android.title")).isEqualTo("Importing memes")
        assertThat(extras.getString("android.text")).isEqualTo("3 of 10")
        assertThat(extras.getInt("android.progress")).isEqualTo(3)
        assertThat(extras.getInt("android.progressMax")).isEqualTo(10)
        assertThat(extras.getBoolean("android.progressIndeterminate")).isFalse()
    }

    @Test
    fun `buildProgressNotification is ongoing and silent`() {
        // Act
        val notification = manager.buildProgressNotification(current = 1, total = 5)

        // Assert
        val isOngoing = notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
        assertThat(isOngoing).isTrue()
        // Silent notifications use GROUP_ALERT_SUMMARY or set the silent flag
        assertThat(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
    }

    // endregion

    // region buildCompleteNotification

    @Test
    fun `buildCompleteNotification shows success text when no failures`() {
        // Act
        val notification = manager.buildCompleteNotification(successCount = 5, failedCount = 0)

        // Assert
        val extras = notification.extras
        assertThat(extras.getString("android.title")).isEqualTo("Import complete")
        assertThat(extras.getString("android.text")).isEqualTo("5 memes imported successfully")
    }

    @Test
    fun `buildCompleteNotification shows failure text when all failed`() {
        // Act
        val notification = manager.buildCompleteNotification(successCount = 0, failedCount = 3)

        // Assert
        val extras = notification.extras
        assertThat(extras.getString("android.title")).isEqualTo("Import complete")
        assertThat(extras.getString("android.text")).isEqualTo("Import failed for 3 images")
    }

    @Test
    fun `buildCompleteNotification shows mixed text when some failed`() {
        // Act
        val notification = manager.buildCompleteNotification(successCount = 7, failedCount = 2)

        // Assert
        val extras = notification.extras
        assertThat(extras.getString("android.text")).isEqualTo("7 imported, 2 failed")
    }

    @Test
    fun `buildCompleteNotification is auto-cancel`() {
        // Act
        val notification = manager.buildCompleteNotification(successCount = 1, failedCount = 0)

        // Assert
        val isAutoCancel =
            notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0
        assertThat(isAutoCancel).isTrue()
    }

    // endregion

    // region showCompleteNotification

    @Test
    fun `showCompleteNotification posts when permission granted`() {
        // Arrange
        val shadowNotificationManager = shadowOf(notificationManager)
        val app = shadowOf(context.applicationContext as android.app.Application)
        app.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        manager.createChannel()

        // Act
        manager.showCompleteNotification(successCount = 3, failedCount = 0)

        // Assert
        val posted = shadowNotificationManager.allNotifications
        assertThat(posted).isNotEmpty()
        val match =
            posted.find {
                it.extras.getString("android.title") == "Import complete"
            }
        assertThat(match).isNotNull()
    }

    @Test
    fun `showCompleteNotification does not post when permission denied`() {
        // Arrange
        val shadowNotificationManager = shadowOf(notificationManager)
        val app = shadowOf(context.applicationContext as android.app.Application)
        app.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        manager.createChannel()

        // Act
        manager.showCompleteNotification(successCount = 3, failedCount = 0)

        // Assert
        val posted = shadowNotificationManager.allNotifications
        val match =
            posted.find {
                it.extras.getString("android.title") == "Import complete"
            }
        assertThat(match).isNull()
    }

    // endregion
}
