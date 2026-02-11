package com.adsamcik.riposte.core.common.crash

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class CrashReportWriterTest {
    private lateinit var crashDir: File

    @Before
    fun setup() {
        crashDir = File(System.getProperty("java.io.tmpdir"), "crash_test_${System.nanoTime()}")
        crashDir.mkdirs()
    }

    @After
    fun tearDown() {
        crashDir.deleteRecursively()
    }

    @Test
    fun `writeCrashReport creates file with stack trace`() {
        val writer = CrashReportWriter(crashDir, "1.0.0-test")
        val exception = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        // Call the handler directly (don't install globally in tests)
        // We need to invoke uncaughtException but prevent the previous handler from killing the process.
        // Instead, test the output by simulating the write via reflection or by checking the dir.
        // Since uncaughtException chains to the previous handler which would terminate,
        // we test by creating a writer with no previous handler.

        // The writer installs itself; we can verify the file is created by triggering
        // the handler on a throwaway thread that we catch.
        val latch = java.util.concurrent.CountDownLatch(1)
        val testThread =
            Thread {
                throw exception
            }

        // Install our writer
        writer.install()
        testThread.uncaughtExceptionHandler =
            Thread.UncaughtExceptionHandler { t, e ->
                writer.uncaughtException(t, e)
                latch.countDown()
            }
        testThread.start()
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

        val files = crashDir.listFiles()
        assertThat(files).isNotNull()
        assertThat(files!!.size).isEqualTo(1)

        val content = files[0].readText()
        assertThat(content).contains("=== Riposte Crash Report ===")
        assertThat(content).contains("App:        1.0.0-test")
        assertThat(content).contains("RuntimeException")
        assertThat(content).contains("Test crash")
        assertThat(content).contains("--- Stack Trace ---")
    }

    @Test
    fun `writeCrashReport creates crash dir if it does not exist`() {
        val nonExistentDir = File(crashDir, "nested")
        assertThat(nonExistentDir.exists()).isFalse()

        val writer = CrashReportWriter(nonExistentDir, "1.0.0")

        val latch = java.util.concurrent.CountDownLatch(1)
        val exception = IllegalStateException("dir test")
        val testThread = Thread { throw exception }
        testThread.uncaughtExceptionHandler =
            Thread.UncaughtExceptionHandler { t, e ->
                writer.uncaughtException(t, e)
                latch.countDown()
            }
        testThread.start()
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

        assertThat(nonExistentDir.exists()).isTrue()
        assertThat(nonExistentDir.listFiles()!!.size).isEqualTo(1)
    }

    @Test
    fun `crash report file name starts with crash_ prefix`() {
        val writer = CrashReportWriter(crashDir, "1.0.0")

        val latch = java.util.concurrent.CountDownLatch(1)
        val testThread = Thread { throw RuntimeException("prefix test") }
        testThread.uncaughtExceptionHandler =
            Thread.UncaughtExceptionHandler { t, e ->
                writer.uncaughtException(t, e)
                latch.countDown()
            }
        testThread.start()
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

        val file = crashDir.listFiles()!!.first()
        assertThat(file.name).startsWith("crash_")
        assertThat(file.name).endsWith(".txt")
    }
}
