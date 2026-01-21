package com.mememymood.core.ml

import android.content.Context
import android.net.Uri
import com.mememymood.core.model.MemeMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles reading and writing XMP metadata for meme images.
 * Uses the mmm: namespace as defined in the metadata specification.
 * 
 * Note: This is a simplified implementation that stores metadata in a sidecar file.
 * For production, consider using a proper XMP library like Apache XMP Toolkit.
 */
@Singleton
class XmpMetadataHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val XMP_NAMESPACE = "http://meme-my-mood.app/1.0/"
        private const val XMP_PREFIX = "mmm"
        private const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
        private const val SIDECAR_EXTENSION = ".xmp"
    }

    /**
     * Read metadata from a URI.
     */
    fun readMetadata(uri: Uri): MemeMetadata? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // Try to read from sidecar file first
                val path = uri.path ?: return null
                readMetadataFromSidecar(path)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read metadata from a file path.
     */
    fun readMetadata(filePath: String): MemeMetadata? {
        return try {
            readMetadataFromSidecar(filePath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read metadata from XMP sidecar file.
     */
    private fun readMetadataFromSidecar(imagePath: String): MemeMetadata? {
        val sidecarFile = File(imagePath + SIDECAR_EXTENSION)
        if (!sidecarFile.exists()) return null

        return try {
            val xmpContent = sidecarFile.readText()
            parseXmpPacket(xmpContent)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse XMP packet and extract metadata.
     */
    private fun parseXmpPacket(xmpData: String): MemeMetadata? {
        if (!xmpData.contains(XMP_NAMESPACE) && !xmpData.contains("$XMP_PREFIX:")) {
            return null
        }

        val schemaVersion = extractValue(xmpData, "$XMP_PREFIX:schemaVersion") ?: "1.0"
        val emojis = extractEmojis(xmpData)
        
        if (emojis.isEmpty()) {
            return null
        }

        val title = extractDcValue(xmpData, "title")
        val description = extractDcValue(xmpData, "description")
        val createdAtStr = extractValue(xmpData, "$XMP_PREFIX:createdAt")
        val appVersion = extractValue(xmpData, "$XMP_PREFIX:appVersion")
        val source = extractValue(xmpData, "$XMP_PREFIX:source")
        val tags = extractTags(xmpData)

        return MemeMetadata(
            schemaVersion = schemaVersion,
            emojis = emojis,
            title = title,
            description = description,
            createdAt = createdAtStr,
            appVersion = appVersion,
            source = source,
            tags = tags,
        )
    }

    private fun extractValue(xmpData: String, tag: String): String? {
        val regex = Regex("""<$tag>([^<]*)</$tag>""")
        return regex.find(xmpData)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun extractDcValue(xmpData: String, property: String): String? {
        val regex = Regex("""<dc:$property>\s*<rdf:Alt>\s*<rdf:li[^>]*>([^<]+)</rdf:li>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xmpData)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun extractEmojis(xmpData: String): List<String> {
        val blockRegex = Regex("""<$XMP_PREFIX:emojis>\s*<rdf:Bag>(.*?)</rdf:Bag>\s*</$XMP_PREFIX:emojis>""", RegexOption.DOT_MATCHES_ALL)
        val blockMatch = blockRegex.find(xmpData) ?: return emptyList()
        
        val emojiRegex = Regex("""<rdf:li>([^<]+)</rdf:li>""")
        return emojiRegex.findAll(blockMatch.groupValues[1])
            .map { it.groupValues[1] }
            .toList()
    }

    private fun extractTags(xmpData: String): List<String> {
        val blockRegex = Regex("""<$XMP_PREFIX:tags>\s*<rdf:Bag>(.*?)</rdf:Bag>\s*</$XMP_PREFIX:tags>""", RegexOption.DOT_MATCHES_ALL)
        val blockMatch = blockRegex.find(xmpData) ?: return emptyList()
        
        val tagRegex = Regex("""<rdf:li>([^<]+)</rdf:li>""")
        return tagRegex.findAll(blockMatch.groupValues[1])
            .map { it.groupValues[1] }
            .toList()
    }

    /**
     * Write metadata to an XMP sidecar file.
     */
    fun writeMetadata(imagePath: String, metadata: MemeMetadata): Boolean {
        return try {
            val xmpPacket = generateXmpPacket(metadata)
            val sidecarFile = File(imagePath + SIDECAR_EXTENSION)
            sidecarFile.writeText(xmpPacket)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate a complete XMP packet from metadata.
     */
    private fun generateXmpPacket(metadata: MemeMetadata): String {
        return buildString {
            appendLine("""<?xpacket begin="${'\uFEFF'}" id="W5M0MpCehiHzreSzNTczkc9d"?>""")
            appendLine("""<x:xmpmeta xmlns:x="adobe:ns:meta/">""")
            appendLine("""  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""")
            appendLine("""    <rdf:Description rdf:about="""")
            appendLine("""      xmlns:$XMP_PREFIX="$XMP_NAMESPACE"""")
            appendLine("""      xmlns:dc="$DC_NAMESPACE">""")
            
            // Schema version
            appendLine("""      <$XMP_PREFIX:schemaVersion>${metadata.schemaVersion}</$XMP_PREFIX:schemaVersion>""")
            
            // Emojis
            appendLine("""      <$XMP_PREFIX:emojis>""")
            appendLine("""        <rdf:Bag>""")
            metadata.emojis.forEach { emoji ->
                appendLine("""          <rdf:li>$emoji</rdf:li>""")
            }
            appendLine("""        </rdf:Bag>""")
            appendLine("""      </$XMP_PREFIX:emojis>""")
            
            // Title
            metadata.title?.let { title ->
                appendLine("""      <dc:title>""")
                appendLine("""        <rdf:Alt>""")
                appendLine("""          <rdf:li xml:lang="x-default">$title</rdf:li>""")
                appendLine("""        </rdf:Alt>""")
                appendLine("""      </dc:title>""")
            }
            
            // Description
            metadata.description?.let { description ->
                appendLine("""      <dc:description>""")
                appendLine("""        <rdf:Alt>""")
                appendLine("""          <rdf:li xml:lang="x-default">$description</rdf:li>""")
                appendLine("""        </rdf:Alt>""")
                appendLine("""      </dc:description>""")
            }
            
            // Timestamps
            metadata.createdAt?.let { createdAt ->
                appendLine("""      <$XMP_PREFIX:createdAt>$createdAt</$XMP_PREFIX:createdAt>""")
            }
            
            // App version
            metadata.appVersion?.let { version ->
                appendLine("""      <$XMP_PREFIX:appVersion>$version</$XMP_PREFIX:appVersion>""")
            }
            
            // Source
            metadata.source?.let { source ->
                val escapedSource = source
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                appendLine("""      <$XMP_PREFIX:source>$escapedSource</$XMP_PREFIX:source>""")
            }
            
            // Tags
            if (metadata.tags.isNotEmpty()) {
                appendLine("""      <$XMP_PREFIX:tags>""")
                appendLine("""        <rdf:Bag>""")
                metadata.tags.forEach { tag ->
                    val escapedTag = tag
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    appendLine("""          <rdf:li>$escapedTag</rdf:li>""")
                }
                appendLine("""        </rdf:Bag>""")
                appendLine("""      </$XMP_PREFIX:tags>""")
            }
            
            appendLine("""    </rdf:Description>""")
            appendLine("""  </rdf:RDF>""")
            appendLine("""</x:xmpmeta>""")
            appendLine("""<?xpacket end="w"?>""")
        }
    }

    /**
     * Delete XMP sidecar file for an image.
     */
    fun deleteMetadata(imagePath: String): Boolean {
        return try {
            val sidecarFile = File(imagePath + SIDECAR_EXTENSION)
            if (sidecarFile.exists()) {
                sidecarFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if an image has associated metadata.
     */
    fun hasMetadata(imagePath: String): Boolean {
        return File(imagePath + SIDECAR_EXTENSION).exists()
    }
}
