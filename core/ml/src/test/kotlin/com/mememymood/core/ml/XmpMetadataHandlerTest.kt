package com.mememymood.core.ml

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mememymood.core.model.MemeMetadata
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class XmpMetadataHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockContentResolver: ContentResolver

    private lateinit var handler: XmpMetadataHandler

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver
        handler = XmpMetadataHandler(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Write Metadata Tests ====================

    @Test
    fun `writeMetadata creates sidecar file`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂", "🔥"),
            title = "Test Meme"
        )
        
        val result = handler.writeMetadata(imageFile.absolutePath, metadata)
        
        assertThat(result).isTrue()
        assertThat(File(imageFile.absolutePath + ".xmp").exists()).isTrue()
    }

    @Test
    fun `writeMetadata includes schema version`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            schemaVersion = "1.0",
            emojis = listOf("😂")
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<mmm:schemaVersion>1.0</mmm:schemaVersion>")
    }

    @Test
    fun `writeMetadata includes all emojis in rdf bag`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂", "🔥", "💯")
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<mmm:emojis>")
        assertThat(xmpContent).contains("<rdf:Bag>")
        assertThat(xmpContent).contains("<rdf:li>😂</rdf:li>")
        assertThat(xmpContent).contains("<rdf:li>🔥</rdf:li>")
        assertThat(xmpContent).contains("<rdf:li>💯</rdf:li>")
    }

    @Test
    fun `writeMetadata includes title using Dublin Core format`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            title = "My Awesome Meme"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<dc:title>")
        assertThat(xmpContent).contains("My Awesome Meme")
    }

    @Test
    fun `writeMetadata includes description using Dublin Core format`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            description = "This is a funny meme"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<dc:description>")
        assertThat(xmpContent).contains("This is a funny meme")
    }

    @Test
    fun `writeMetadata includes createdAt timestamp`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            createdAt = "2024-01-15T10:30:00Z"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<mmm:createdAt>2024-01-15T10:30:00Z</mmm:createdAt>")
    }

    @Test
    fun `writeMetadata includes appVersion`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            appVersion = "1.0.0"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<mmm:appVersion>1.0.0</mmm:appVersion>")
    }

    @Test
    fun `writeMetadata escapes special characters in source`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            source = "https://example.com/meme?id=1&name=test<>"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("&amp;")
        assertThat(xmpContent).contains("&lt;")
        assertThat(xmpContent).contains("&gt;")
    }

    @Test
    fun `writeMetadata includes tags in rdf bag`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            tags = listOf("funny", "viral", "trending")
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<mmm:tags>")
        assertThat(xmpContent).contains("<rdf:li>funny</rdf:li>")
        assertThat(xmpContent).contains("<rdf:li>viral</rdf:li>")
        assertThat(xmpContent).contains("<rdf:li>trending</rdf:li>")
    }

    @Test
    fun `writeMetadata escapes special characters in tags`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            tags = listOf("tag<with>special&chars")
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("tag&lt;with&gt;special&amp;chars")
    }

    @Test
    fun `writeMetadata omits optional fields when null`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            title = null,
            description = null,
            createdAt = null,
            appVersion = null,
            source = null
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).doesNotContain("<dc:title>")
        assertThat(xmpContent).doesNotContain("<dc:description>")
        assertThat(xmpContent).doesNotContain("<mmm:createdAt>")
        assertThat(xmpContent).doesNotContain("<mmm:appVersion>")
        assertThat(xmpContent).doesNotContain("<mmm:source>")
    }

    @Test
    fun `writeMetadata omits tags section when empty`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            tags = emptyList()
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).doesNotContain("<mmm:tags>")
    }

    @Test
    fun `writeMetadata includes proper XMP header and footer`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(emojis = listOf("😂"))
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val xmpContent = File(imageFile.absolutePath + ".xmp").readText()
        assertThat(xmpContent).contains("<?xpacket begin")
        assertThat(xmpContent).contains("<?xpacket end")
        assertThat(xmpContent).contains("<x:xmpmeta")
        assertThat(xmpContent).contains("</x:xmpmeta>")
        assertThat(xmpContent).contains("<rdf:RDF")
    }

    @Test
    fun `writeMetadata returns false for invalid path`() {
        val result = handler.writeMetadata("/nonexistent/path/image.jpg", MemeMetadata(emojis = listOf("😂")))
        
        assertThat(result).isFalse()
    }

    // ==================== Read Metadata Tests ====================

    @Test
    fun `readMetadata returns null when sidecar file does not exist`() {
        val imageFile = tempFolder.newFile("test.jpg")
        
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result).isNull()
    }

    @Test
    fun `readMetadata parses valid XMP sidecar file`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂", "🔥"),
            title = "Test Meme",
            description = "A funny meme"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result).isNotNull()
        assertThat(result?.emojis).containsExactly("😂", "🔥")
        assertThat(result?.title).isEqualTo("Test Meme")
        assertThat(result?.description).isEqualTo("A funny meme")
    }

    @Test
    fun `readMetadata parses schema version`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            schemaVersion = "1.0",
            emojis = listOf("😂")
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result?.schemaVersion).isEqualTo("1.0")
    }

    @Test
    fun `readMetadata parses createdAt timestamp`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            createdAt = "2024-01-15T10:30:00Z"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result?.createdAt).isEqualTo("2024-01-15T10:30:00Z")
    }

    @Test
    fun `readMetadata parses appVersion`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            appVersion = "2.1.0"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result?.appVersion).isEqualTo("2.1.0")
    }

    @Test
    fun `readMetadata parses source`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            source = "https://example.com/meme.jpg"
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result?.source).isEqualTo("https://example.com/meme.jpg")
    }

    @Test
    fun `readMetadata parses tags`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(
            emojis = listOf("😂"),
            tags = listOf("funny", "viral", "trending")
        )
        
        handler.writeMetadata(imageFile.absolutePath, metadata)
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result?.tags).containsExactly("funny", "viral", "trending")
    }

    @Test
    fun `readMetadata returns null for XMP without mmm namespace`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val sidecarFile = File(imageFile.absolutePath + ".xmp")
        sidecarFile.writeText("""
            <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about="">
                  <dc:title>Some title</dc:title>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent())
        
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result).isNull()
    }

    @Test
    fun `readMetadata returns null for XMP without emojis`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val sidecarFile = File(imageFile.absolutePath + ".xmp")
        sidecarFile.writeText("""
            <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                  xmlns:mmm="http://meme-my-mood.app/1.0/">
                  <mmm:schemaVersion>1.0</mmm:schemaVersion>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent())
        
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result).isNull()
    }

    @Test
    fun `readMetadata handles empty sidecar file`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val sidecarFile = File(imageFile.absolutePath + ".xmp")
        sidecarFile.writeText("")
        
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result).isNull()
    }

    @Test
    fun `readMetadata handles malformed XML gracefully`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val sidecarFile = File(imageFile.absolutePath + ".xmp")
        sidecarFile.writeText("<invalid><xml content")
        
        val result = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(result).isNull()
    }

    // ==================== Read Metadata from URI Tests ====================

    @Test
    fun `readMetadata with URI returns null when sidecar does not exist`() {
        val mockUri = mockk<Uri>()
        every { mockUri.path } returns "/test/image.jpg"
        every { mockContentResolver.openInputStream(mockUri) } returns ByteArrayInputStream(ByteArray(0))
        
        val result = handler.readMetadata(mockUri)
        
        assertThat(result).isNull()
    }

    @Test
    fun `readMetadata with URI returns null when path is null`() {
        val mockUri = mockk<Uri>()
        every { mockUri.path } returns null
        every { mockContentResolver.openInputStream(mockUri) } returns ByteArrayInputStream(ByteArray(0))
        
        val result = handler.readMetadata(mockUri)
        
        assertThat(result).isNull()
    }

    @Test
    fun `readMetadata with URI handles ContentResolver errors gracefully`() {
        val mockUri = mockk<Uri>()
        every { mockContentResolver.openInputStream(mockUri) } throws SecurityException("Access denied")
        
        val result = handler.readMetadata(mockUri)
        
        assertThat(result).isNull()
    }

    // ==================== Delete Metadata Tests ====================

    @Test
    fun `deleteMetadata removes sidecar file`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(emojis = listOf("😂"))
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        assertThat(File(imageFile.absolutePath + ".xmp").exists()).isTrue()
        
        val result = handler.deleteMetadata(imageFile.absolutePath)
        
        assertThat(result).isTrue()
        assertThat(File(imageFile.absolutePath + ".xmp").exists()).isFalse()
    }

    @Test
    fun `deleteMetadata returns true when sidecar does not exist`() {
        val imageFile = tempFolder.newFile("test.jpg")
        
        val result = handler.deleteMetadata(imageFile.absolutePath)
        
        assertThat(result).isTrue()
    }

    @Test
    fun `deleteMetadata handles errors gracefully`() {
        // Create a read-only directory scenario by trying to delete from non-existent path
        val result = handler.deleteMetadata("/nonexistent/path/image.jpg")
        
        // Should return true since the file doesn't exist
        assertThat(result).isTrue()
    }

    // ==================== Has Metadata Tests ====================

    @Test
    fun `hasMetadata returns true when sidecar exists`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val metadata = MemeMetadata(emojis = listOf("😂"))
        handler.writeMetadata(imageFile.absolutePath, metadata)
        
        val result = handler.hasMetadata(imageFile.absolutePath)
        
        assertThat(result).isTrue()
    }

    @Test
    fun `hasMetadata returns false when sidecar does not exist`() {
        val imageFile = tempFolder.newFile("test.jpg")
        
        val result = handler.hasMetadata(imageFile.absolutePath)
        
        assertThat(result).isFalse()
    }

    @Test
    fun `hasMetadata returns false for non-existent image`() {
        val result = handler.hasMetadata("/nonexistent/image.jpg")
        
        assertThat(result).isFalse()
    }

    // ==================== Round-Trip Tests ====================

    @Test
    fun `metadata survives write and read round-trip`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val originalMetadata = MemeMetadata(
            schemaVersion = "1.0",
            emojis = listOf("😂", "🔥", "💯"),
            title = "Epic Meme",
            description = "This is the most epic meme ever",
            createdAt = "2024-01-15T10:30:00Z",
            appVersion = "1.0.0",
            source = "https://memes.example.com/epic",
            tags = listOf("epic", "viral", "funny")
        )
        
        handler.writeMetadata(imageFile.absolutePath, originalMetadata)
        val readMetadata = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(readMetadata).isNotNull()
        assertThat(readMetadata?.schemaVersion).isEqualTo(originalMetadata.schemaVersion)
        assertThat(readMetadata?.emojis).containsExactlyElementsIn(originalMetadata.emojis)
        assertThat(readMetadata?.title).isEqualTo(originalMetadata.title)
        assertThat(readMetadata?.description).isEqualTo(originalMetadata.description)
        assertThat(readMetadata?.createdAt).isEqualTo(originalMetadata.createdAt)
        assertThat(readMetadata?.appVersion).isEqualTo(originalMetadata.appVersion)
        assertThat(readMetadata?.source).isEqualTo(originalMetadata.source)
        assertThat(readMetadata?.tags).containsExactlyElementsIn(originalMetadata.tags)
    }

    @Test
    fun `overwriting metadata replaces previous data`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val originalMetadata = MemeMetadata(
            emojis = listOf("😂"),
            title = "Original Title"
        )
        val updatedMetadata = MemeMetadata(
            emojis = listOf("🔥", "💯"),
            title = "Updated Title"
        )
        
        handler.writeMetadata(imageFile.absolutePath, originalMetadata)
        handler.writeMetadata(imageFile.absolutePath, updatedMetadata)
        
        val readMetadata = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(readMetadata?.emojis).containsExactly("🔥", "💯")
        assertThat(readMetadata?.title).isEqualTo("Updated Title")
    }

    @Test
    fun `metadata with unicode characters survives round-trip`() {
        val imageFile = tempFolder.newFile("test.jpg")
        val originalMetadata = MemeMetadata(
            emojis = listOf("😂", "🇺🇸", "👨‍👩‍👧‍👦"),  // Complex emoji including flag and family
            title = "Título en español con ñ y ü",
            description = "日本語のテスト 中文测试"
        )
        
        handler.writeMetadata(imageFile.absolutePath, originalMetadata)
        val readMetadata = handler.readMetadata(imageFile.absolutePath)
        
        assertThat(readMetadata?.emojis).containsExactlyElementsIn(originalMetadata.emojis)
        assertThat(readMetadata?.title).isEqualTo(originalMetadata.title)
        assertThat(readMetadata?.description).isEqualTo(originalMetadata.description)
    }
}
