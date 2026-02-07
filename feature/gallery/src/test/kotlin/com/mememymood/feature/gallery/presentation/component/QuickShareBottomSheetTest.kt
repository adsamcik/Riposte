package com.mememymood.feature.gallery.presentation.component

import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.ShareTarget
import org.junit.Test

class QuickShareBottomSheetTest {

    // region MESSAGING_PACKAGES Tests

    @Test
    fun `MESSAGING_PACKAGES contains well-known messaging apps`() {
        assertThat(MESSAGING_PACKAGES).contains("com.whatsapp")
        assertThat(MESSAGING_PACKAGES).contains("org.telegram.messenger")
        assertThat(MESSAGING_PACKAGES).contains("com.discord")
        assertThat(MESSAGING_PACKAGES).contains("com.facebook.orca")
        assertThat(MESSAGING_PACKAGES).contains("com.google.android.apps.messaging")
        assertThat(MESSAGING_PACKAGES).contains("org.thoughtcrime.securesms")
    }

    // endregion

    // region prioritizeShareTargets Tests

    @Test
    fun `when no usage data then messaging apps are sorted first`() {
        val targets = listOf(
            createTarget("com.example.gallery", shareCount = 0),
            createTarget("com.whatsapp", shareCount = 0),
            createTarget("com.example.notes", shareCount = 0),
            createTarget("org.telegram.messenger", shareCount = 0),
        )

        val result = prioritizeShareTargets(targets)

        // Messaging apps should come before non-messaging apps
        val messagingIndices = result.mapIndexedNotNull { index, target ->
            if (target.packageName in MESSAGING_PACKAGES) index else null
        }
        val nonMessagingIndices = result.mapIndexedNotNull { index, target ->
            if (target.packageName !in MESSAGING_PACKAGES) index else null
        }

        assertThat(messagingIndices.max()).isLessThan(nonMessagingIndices.min())
    }

    @Test
    fun `when usage data exists then original order is preserved`() {
        val targets = listOf(
            createTarget("com.example.gallery", shareCount = 5),
            createTarget("com.whatsapp", shareCount = 0),
            createTarget("com.example.notes", shareCount = 3),
            createTarget("org.telegram.messenger", shareCount = 0),
        )

        val result = prioritizeShareTargets(targets)

        assertThat(result).isEqualTo(targets)
    }

    @Test
    fun `when all targets are messaging apps then order is preserved`() {
        val targets = listOf(
            createTarget("com.whatsapp", shareCount = 0),
            createTarget("org.telegram.messenger", shareCount = 0),
            createTarget("com.discord", shareCount = 0),
        )

        val result = prioritizeShareTargets(targets)

        // All are messaging apps, so all should be in the result
        assertThat(result).hasSize(3)
        result.forEach { target ->
            assertThat(target.packageName).isIn(MESSAGING_PACKAGES)
        }
    }

    @Test
    fun `when no targets then empty list is returned`() {
        val result = prioritizeShareTargets(emptyList())

        assertThat(result).isEmpty()
    }

    @Test
    fun `when single non-messaging target with no usage then returns it`() {
        val targets = listOf(createTarget("com.example.app", shareCount = 0))

        val result = prioritizeShareTargets(targets)

        assertThat(result).hasSize(1)
        assertThat(result[0].packageName).isEqualTo("com.example.app")
    }

    @Test
    fun `when mixed targets with only zero share counts then messaging first`() {
        val targets = listOf(
            createTarget("com.example.browser"),
            createTarget("com.instagram.android"),
            createTarget("com.example.editor"),
            createTarget("com.snapchat.android"),
            createTarget("com.example.filemanager"),
        )

        val result = prioritizeShareTargets(targets)

        // First two results should be messaging apps
        assertThat(result[0].packageName).isIn(MESSAGING_PACKAGES)
        assertThat(result[1].packageName).isIn(MESSAGING_PACKAGES)
        // Last three should be non-messaging apps
        assertThat(result[2].packageName).isNotIn(MESSAGING_PACKAGES)
        assertThat(result[3].packageName).isNotIn(MESSAGING_PACKAGES)
        assertThat(result[4].packageName).isNotIn(MESSAGING_PACKAGES)
    }

    // endregion

    // region Helper Functions

    private fun createTarget(
        packageName: String,
        shareCount: Int = 0,
    ): ShareTarget = ShareTarget(
        packageName = packageName,
        activityName = "$packageName.ShareActivity",
        displayLabel = packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() },
        shareCount = shareCount,
    )

    // endregion
}
