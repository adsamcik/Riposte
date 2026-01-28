# Meme My Mood - XMP Metadata Format Specification

## Version 1.1

This document specifies the XMP metadata format used by Meme My Mood to embed meme information directly into image files.

## Overview

Meme My Mood uses XMP (Extensible Metadata Platform) to store emoji associations and other metadata directly within image files. This makes memes self-describing and allows them to be shared while preserving their metadata.

### Why XMP?

| Feature | XMP | EXIF |
|---------|-----|------|
| Unicode Support | ✅ Full | ❌ Limited |
| Text Length | ✅ Unlimited | ❌ 64KB max |
| Complex Structures | ✅ Yes | ❌ No |
| Image Format Support | ✅ JPEG, PNG, WebP, GIF | ⚠️ JPEG, TIFF only |
| Standard Compliance | ✅ ISO 16684-1 | ✅ JEITA CP-3451 |

## Namespace

```
Namespace URI: http://meme-my-mood.app/1.1/
Namespace Prefix: mmm
```

We also use Dublin Core for standard fields:
```
Namespace URI: http://purl.org/dc/elements/1.1/
Namespace Prefix: dc
```

## Schema Fields

### Required Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `mmm:schemaVersion` | String | Schema version for compatibility | `"1.1"` |
| `mmm:emojis` | Bag (Array) | Unicode emoji characters | `["😂", "🔥", "💯"]` |

### Optional Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `dc:title` | String | Meme title (primary language) | `"When the code works"` |
| `dc:description` | String | Meme description (primary language) | `"That feeling when..."` |
| `mmm:createdAt` | String (ISO 8601) | Creation timestamp | `"2026-01-11T12:00:00Z"` |
| `mmm:appVersion` | String | App version that created metadata | `"1.0.0"` |
| `mmm:source` | String | Original source URL | `"https://example.com/meme.jpg"` |
| `mmm:tags` | Bag (Array) | Additional search keywords | `["funny", "programming"]` |
| `mmm:primaryLanguage` | String | BCP 47 language code of primary content | `"en"` |
| `mmm:localizations` | Object | Localized content by language code | See below |
| `mmm:contentHash` | String | SHA-256 hash of image content for deduplication | `"a1b2c3..."` |

## JSON Representation

When storing or transmitting metadata outside of XMP, use this JSON format:

```json
{
  "schemaVersion": "1.1",
  "emojis": ["😂", "🔥"],
  "title": "When the code finally works",
  "description": "The moment of joy after hours of debugging",
  "createdAt": "2026-01-11T12:00:00Z",
  "appVersion": "1.0.0",
  "source": null,
  "tags": ["programming", "developer", "coding"],
  "contentHash": "a1b2c3d4e5f6...",
  "primaryLanguage": "en",
  "localizations": {
    "cs": {
      "title": "Když kód konečně funguje",
      "description": "Ten okamžik radosti po hodinách ladění",
      "tags": ["programování", "vývojář", "kódování"]
    },
    "de": {
      "title": "Wenn der Code endlich funktioniert",
      "description": "Der Moment der Freude nach stundenlangem Debugging",
      "tags": ["programmierung", "entwickler", "codierung"]
    }
  }
}
```

### Localization Object

Each localization entry can contain:

| Field | Type | Description |
|-------|------|-------------|
| `title` | String | Localized meme title |
| `description` | String | Localized description |
| `textContent` | String | Localized text content (if applicable) |
| `tags` | Array | Localized search keywords |

### Language Codes

Use BCP 47 language tags:
- `en` - English
- `cs` - Czech
- `de` - German
- `es` - Spanish
- `fr` - French
- `ja` - Japanese
- `ko` - Korean
- `zh` - Chinese (Simplified)
- `zh-TW` - Chinese (Traditional)

### JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "MemeMetadata",
  "type": "object",
  "required": ["schemaVersion", "emojis"],
  "properties": {
    "schemaVersion": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+$"
    },
    "emojis": {
      "type": "array",
      "items": { "type": "string" },
      "minItems": 1,
      "description": "Array of Unicode emoji characters"
    },
    "title": {
      "type": "string",
      "maxLength": 200
    },
    "description": {
      "type": "string",
      "maxLength": 2000
    },
    "createdAt": {
      "type": "string",
      "format": "date-time"
    },
    "appVersion": {
      "type": "string"
    },
    "source": {
      "type": "string",
      "format": "uri"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" }
    },
    "textContent": {
      "type": "string",
      "description": "Pre-extracted text content from the image"
    },
    "primaryLanguage": {
      "type": "string",
      "pattern": "^[a-z]{2}(-[A-Z]{2})?$",
      "description": "BCP 47 language code of the primary content"
    },
    "contentHash": {
      "type": "string",
      "pattern": "^[a-f0-9]{64}$",
      "description": "SHA-256 hash of image content for deduplication"
    },
    "localizations": {
      "type": "object",
      "description": "Localized content by BCP 47 language code",
      "additionalProperties": {
        "$ref": "#/$defs/Localization"
      }
    }
  },
  "$defs": {
    "Localization": {
      "type": "object",
      "properties": {
        "title": {
          "type": "string",
          "maxLength": 200
        },
        "description": {
          "type": "string",
          "maxLength": 2000
        },
        "textContent": {
          "type": "string"
        },
        "tags": {
          "type": "array",
          "items": { "type": "string" }
        }
      }
    }
  }
}
```

## XMP Packet Structure

### Example XMP Packet

```xml
<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description 
      rdf:about=""
      xmlns:mmm="http://meme-my-mood.app/1.0/"
      xmlns:dc="http://purl.org/dc/elements/1.1/">
      
      <!-- Schema Version -->
      <mmm:schemaVersion>1.0</mmm:schemaVersion>
      
      <!-- Emojis (Bag/Array) -->
      <mmm:emojis>
        <rdf:Bag>
          <rdf:li>😂</rdf:li>
          <rdf:li>🔥</rdf:li>
          <rdf:li>💯</rdf:li>
        </rdf:Bag>
      </mmm:emojis>
      
      <!-- Title (Dublin Core) -->
      <dc:title>
        <rdf:Alt>
          <rdf:li xml:lang="x-default">When the code works</rdf:li>
        </rdf:Alt>
      </dc:title>
      
      <!-- Description (Dublin Core) -->
      <dc:description>
        <rdf:Alt>
          <rdf:li xml:lang="x-default">That feeling after debugging for hours</rdf:li>
        </rdf:Alt>
      </dc:description>
      
      <!-- Keywords for search (Dublin Core) -->
      <dc:subject>
        <rdf:Bag>
          <rdf:li>face_with_tears_of_joy</rdf:li>
          <rdf:li>fire</rdf:li>
          <rdf:li>hundred_points</rdf:li>
          <rdf:li>programming</rdf:li>
        </rdf:Bag>
      </dc:subject>
      
      <!-- Creation timestamp -->
      <mmm:createdAt>2026-01-11T12:00:00Z</mmm:createdAt>
      
      <!-- App version -->
      <mmm:appVersion>1.0.0</mmm:appVersion>
      
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>
```

## Emoji Storage Format

### Recommendation: Store Actual Unicode Characters

We recommend storing actual Unicode emoji characters (e.g., `😂`) rather than shortcodes (e.g., `:joy:`).

**Reasons:**
1. **Universal Compatibility**: All systems can display Unicode emojis
2. **No Mapping Required**: No need for shortcode-to-emoji mapping tables
3. **Future Proof**: New emojis work automatically
4. **Standard Compliance**: Follows Unicode Consortium recommendations

### Emoji Name Generation

For search indexing, emoji names are auto-generated from the Unicode CLDR data:

| Emoji | Generated Name | Keywords |
|-------|---------------|----------|
| 😂 | `face_with_tears_of_joy` | laughing, funny, lol |
| 🔥 | `fire` | hot, lit, trending |
| 💯 | `hundred_points` | perfect, 100, score |
| 🤔 | `thinking_face` | hmm, wondering, curious |
| 💀 | `skull` | dead, dying, hilarious |

## Reading & Writing Metadata

### Android (Kotlin)

```kotlin
// Using ExifInterface for basic XMP access
val exif = ExifInterface(filePath)
val xmpData = exif.getAttribute(ExifInterface.TAG_XMP)

// Parse XMP and extract our namespace
// (Requires XML parsing library)
```

### Cross-Platform (ExifTool)

```bash
# Read metadata
exiftool -XMP-mmm:all image.jpg

# Write metadata
exiftool -XMP-mmm:emojis="😂" -XMP-mmm:emojis="🔥" image.jpg
```

## Fallback Behavior

When metadata is missing or unreadable:

| Missing Data | Fallback Behavior |
|--------------|-------------------|
| No emojis | Show ❓ placeholder, prompt user to add emojis |
| No title | Use filename (without extension) |
| No description | Show "No description" or leave empty |
| Invalid schema version | Attempt to parse as latest version |
| Corrupted XMP | Treat as new image without metadata |

## Sidecar Files

For formats that don't support embedded XMP (rare), use sidecar files:

- **Filename**: Same as image with `.xmp` extension
- **Example**: `meme.jpg` → `meme.xmp`
- **Priority**: Embedded XMP takes precedence over sidecar

## Version History

| Version | Date    | Changes                                              |
|---------|---------|------------------------------------------------------|
| 1.1     | 2026-01 | Added multilingual support with `localizations` field, `primaryLanguage`, `textContent` in schema |
| 1.0     | 2026-01 | Initial specification                                |

## References

- [XMP Specification (ISO 16684-1)](https://www.iso.org/standard/75163.html)
- [Dublin Core Metadata](https://www.dublincore.org/specifications/dublin-core/)
- [Unicode Emoji Data](https://unicode.org/Public/emoji/)
- [CLDR Emoji Annotations](https://cldr.unicode.org/index/downloads)
