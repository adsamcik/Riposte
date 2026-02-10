package com.adsamcik.riposte.feature.import_feature.data.worker

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * Unit tests for [ImportStagingManager].
 *
 * Uses Robolectric for Android ContentResolver/Uri and the real
 * Robolectric cache directory for file system operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ImportStagingManagerTest {

    private lateinit var context: Context
    private lateinit var manager: ImportStagingManager

    private val stagingRootName = "import_staging"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = ImportStagingManager(context)
    }

    @After
    fun teardown() {
        File(context.cacheDir, stagingRootName).deleteRecursively()
    }

    @Test
    fun `stageImages creates staging directory and copies files`() = runTest {
        // Arrange
        val content1 = "image-data-1".toByteArray()
        val content2 = "image-data-2".toByteArray()
        val uri1 = Uri.parse("content://test/image1")
        val uri2 = Uri.parse("content://test/image2")

        val shadowResolver = shadowOf(context.contentResolver)
        shadowResolver.registerInputStream(uri1, ByteArrayInputStream(content1))
        shadowResolver.registerInputStream(uri2, ByteArrayInputStream(content2))

        val inputs = listOf(
            ImportStagingManager.StagingInput(id = "img1.jpg", uri = uri1),
            ImportStagingManager.StagingInput(id = "img2.jpg", uri = uri2),
        )

        // Act
        val stagingDir = manager.stageImages(inputs)

        // Assert
        assertThat(stagingDir.exists()).isTrue()
        assertThat(stagingDir.isDirectory).isTrue()

        val file1 = File(stagingDir, "img1.jpg")
        val file2 = File(stagingDir, "img2.jpg")
        assertThat(file1.exists()).isTrue()
        assertThat(file2.exists()).isTrue()
        assertThat(file1.readBytes()).isEqualTo(content1)
        assertThat(file2.readBytes()).isEqualTo(content2)
    }

    @Test(expected = IOException::class)
    fun `stageImages throws when directory creation fails`() = runTest {
        // Arrange - place a regular file at the staging root path so mkdirs fails
        val blockingFile = File(context.cacheDir, stagingRootName)
        blockingFile.parentFile?.mkdirs()
        blockingFile.createNewFile()
        blockingFile.setReadOnly()

        val uri = Uri.parse("content://test/blocked")
        val inputs = listOf(
            ImportStagingManager.StagingInput(id = "img.jpg", uri = uri),
        )

        // Act - should throw IOException
        manager.stageImages(inputs)
    }

    @Test
    fun `stageImages throws when input stream is null`() = runTest {
        // Arrange - don't register any input stream for this URI
        val uri = Uri.parse("content://test/missing")

        val inputs = listOf(
            ImportStagingManager.StagingInput(id = "img.jpg", uri = uri),
        )

        // Act - should throw (IOException from production code or
        // UnsupportedOperationException from Robolectric's ShadowContentResolver)
        val result = runCatching { manager.stageImages(inputs) }

        // Assert
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `cleanupStagingDir deletes directory and contents`() {
        // Arrange
        val dir = File(context.cacheDir, "to_cleanup")
        dir.mkdirs()
        File(dir, "file1.jpg").writeText("data1")
        File(dir, "file2.jpg").writeText("data2")
        assertThat(dir.exists()).isTrue()

        // Act
        manager.cleanupStagingDir(dir)

        // Assert
        assertThat(dir.exists()).isFalse()
    }

    @Test
    fun `cleanupStagingDir does nothing for nonexistent directory`() {
        // Arrange
        val dir = File(context.cacheDir, "nonexistent_dir")
        assertThat(dir.exists()).isFalse()

        // Act - should not throw
        manager.cleanupStagingDir(dir)

        // Assert
        assertThat(dir.exists()).isFalse()
    }

    @Test
    fun `cleanupAll deletes entire staging root`() {
        // Arrange
        val stagingRoot = File(context.cacheDir, stagingRootName)
        stagingRoot.mkdirs()
        File(stagingRoot, "batch1").mkdirs()
        File(File(stagingRoot, "batch1"), "img.jpg").writeText("data")
        File(stagingRoot, "batch2").mkdirs()
        assertThat(stagingRoot.exists()).isTrue()

        // Act
        manager.cleanupAll()

        // Assert
        assertThat(stagingRoot.exists()).isFalse()
    }
}
