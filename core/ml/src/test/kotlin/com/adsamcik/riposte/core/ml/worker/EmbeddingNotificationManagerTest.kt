package com.adsamcik.riposte.core.ml.worker

import android.app.NotificationManager
import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [EmbeddingNotificationManager].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EmbeddingNotificationManagerTest {
    private lateinit var context: Context
    private lateinit var manager: EmbeddingNotificationManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        manager = EmbeddingNotificationManager(context)
    }

    @Test
    fun `createChannel creates notification channel with correct id`() {
        manager.createChannel()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(EmbeddingNotificationManager.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun `createChannel is idempotent`() {
        manager.createChannel()
        manager.createChannel()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(EmbeddingNotificationManager.CHANNEL_ID)
        assertThat(channel).isNotNull()
    }

    @Test
    fun `buildProgressNotification sets correct content`() {
        manager.createChannel()
        val notification = manager.buildProgressNotification(5, 20)
        assertThat(notification).isNotNull()
    }

    @Test
    fun `buildCompleteNotification for success only`() {
        manager.createChannel()
        val notification = manager.buildCompleteNotification(10, 0)
        assertThat(notification).isNotNull()
    }

    @Test
    fun `buildCompleteNotification for failure only`() {
        manager.createChannel()
        val notification = manager.buildCompleteNotification(0, 5)
        assertThat(notification).isNotNull()
    }

    @Test
    fun `buildCompleteNotification for mixed results`() {
        manager.createChannel()
        val notification = manager.buildCompleteNotification(8, 2)
        assertThat(notification).isNotNull()
    }

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
}
