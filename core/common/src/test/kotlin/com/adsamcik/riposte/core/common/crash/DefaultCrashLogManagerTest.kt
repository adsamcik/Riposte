package com.adsamcik.riposte.core.common.crash

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class DefaultCrashLogManagerTest {

    private lateinit var crashDir: File
    private lateinit var manager: DefaultCrashLogManager

    @Before
    fun setup() {
        crashDir = File(System.getProperty("java.io.tmpdir"), "crash_mgr_test_${System.nanoTime()}")
        crashDir.mkdirs()
        manager = DefaultCrashLogManager(crashDir)
    }

    @After
    fun tearDown() {
        crashDir.deleteRecursively()
    }

    @Test
    fun `getCrashLogCount returns zero when directory is empty`() {
        assertThat(manager.getCrashLogCount()).isEqualTo(0)
    }

    @Test
    fun `getCrashLogCount returns correct count`() {
        File(crashDir, "crash_1.txt").writeText("report 1")
        File(crashDir, "crash_2.txt").writeText("report 2")

        assertThat(manager.getCrashLogCount()).isEqualTo(2)
    }

    @Test
    fun `getCrashLogCount returns zero when directory does not exist`() {
        crashDir.deleteRecursively()

        assertThat(manager.getCrashLogCount()).isEqualTo(0)
    }

    @Test
    fun `getShareableReport returns empty string when no logs`() {
        assertThat(manager.getShareableReport()).isEmpty()
    }

    @Test
    fun `getShareableReport combines reports newest first`() {
        File(crashDir, "crash_100.txt").writeText("older report")
        File(crashDir, "crash_200.txt").writeText("newer report")

        val report = manager.getShareableReport()

        assertThat(report).contains("newer report")
        assertThat(report).contains("older report")
        // Newest should appear first
        assertThat(report.indexOf("newer report")).isLessThan(report.indexOf("older report"))
    }

    @Test
    fun `getShareableReport separates reports with blank line`() {
        File(crashDir, "crash_1.txt").writeText("report A")
        File(crashDir, "crash_2.txt").writeText("report B")

        val report = manager.getShareableReport()

        assertThat(report).contains("\n\n")
    }

    @Test
    fun `clearAll deletes all crash files`() {
        File(crashDir, "crash_1.txt").writeText("report 1")
        File(crashDir, "crash_2.txt").writeText("report 2")
        assertThat(manager.getCrashLogCount()).isEqualTo(2)

        manager.clearAll()

        assertThat(manager.getCrashLogCount()).isEqualTo(0)
        assertThat(crashDir.exists()).isTrue() // Directory itself should remain
    }

    @Test
    fun `clearAll is safe when directory is empty`() {
        manager.clearAll()

        assertThat(manager.getCrashLogCount()).isEqualTo(0)
    }
}
