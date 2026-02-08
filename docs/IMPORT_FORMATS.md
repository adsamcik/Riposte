# Riposte - Import Formats Guide

This document describes the image formats supported by the import feature and how emoji/metadata information can be embedded in each format.

## Supported Image Formats

| Format | Extension | MIME Type | Metadata Support | Notes |
|--------|-----------|-----------|------------------|-------|
| **JPEG** | `.jpg`, `.jpeg` | `image/jpeg` | ✅ Full | Most common format for memes |
| **PNG** | `.png` | `image/png` | ✅ Full | Supports transparency |
| **WebP** | `.webp` | `image/webp` | ✅ Full | Modern, efficient format |
| **GIF** | `.gif` | `image/gif` | ✅ Full | Animated image support |

All formats are processed and converted to JPEG for internal storage to ensure consistency and optimal performance.

---

## How Emoji Information is Embedded

Riposte uses **XMP (Extensible Metadata Platform)** to embed emoji associations and metadata directly within image files. This makes memes self-describing and portable.

### Embedding Methods

#### 1. XMP Sidecar Files (Current Implementation)

The app stores metadata in a sidecar file alongside each image:

```
meme.jpg           # The actual image
meme.jpg.xmp       # Metadata sidecar file
```

**Benefits:**
- Works with all image formats
- Doesn't modify original image data
- Easy to read/write with standard XML tools

**Example sidecar content:**
```xml
<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
      xmlns:mmm="http://riposte.app/1.0/"
      xmlns:dc="http://purl.org/dc/elements/1.1/">
      
      <mmm:schemaVersion>1.0</mmm:schemaVersion>
      
      <mmm:emojis>
        <rdf:Bag>
          <rdf:li>😂</rdf:li>
          <rdf:li>🔥</rdf:li>
          <rdf:li>💯</rdf:li>
        </rdf:Bag>
      </mmm:emojis>
      
      <dc:title>
        <rdf:Alt>
          <rdf:li xml:lang="x-default">When the code works</rdf:li>
        </rdf:Alt>
      </dc:title>
      
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>
```

#### 2. Embedded XMP (For Sharing)

When exporting/sharing memes (without stripping metadata), XMP is embedded directly in the image file's metadata section, making the file fully portable.

---

## Metadata Schema

### Namespace

```
URI:    http://riposte.app/1.0/
Prefix: mmm
```

### Required Fields

| Field | XMP Tag | Type | Description |
|-------|---------|------|-------------|
| Schema Version | `mmm:schemaVersion` | String | Version for compatibility (e.g., `"1.0"`) |
| Emojis | `mmm:emojis` | Bag (Array) | Unicode emoji characters |

### Optional Fields

| Field | XMP Tag | Type | Description |
|-------|---------|------|-------------|
| Title | `dc:title` | Alt String | Meme title |
| Description | `dc:description` | Alt String | Meme description |
| Created At | `mmm:createdAt` | ISO 8601 | Creation timestamp |
| App Version | `mmm:appVersion` | String | App version that created metadata |
| Source | `mmm:source` | URI | Original source URL |
| Tags | `mmm:tags` | Bag (Array) | Additional search keywords |

---

## Importing Memes with Metadata

### Automatic Metadata Extraction

When importing an image, the app automatically:

1. **Checks for XMP sidecar file** (`.xmp` alongside the image)
2. **Reads embedded XMP metadata** from the image file
3. **Extracts text using OCR** (if enabled in settings)
4. **Suggests emojis** based on image content using ML

### Manual Metadata Entry

During import, users can:

- Add/remove emoji tags
- Set a custom title
- Add a description
- Apply AI-suggested emojis

---

## Preparing Images for Import

### Method 1: Using Command Line Tools (ExifTool)

You can pre-tag images with emoji metadata before importing:

```bash
# Add emojis to an image
exiftool -XMP-mmm:schemaVersion="1.0" \
         -XMP-mmm:emojis="😂" \
         -XMP-mmm:emojis="🔥" \
         image.jpg

# Read existing metadata
exiftool -XMP-mmm:all image.jpg
```

### Method 2: Create XMP Sidecar File Manually

Create a `.xmp` file with the same base name as your image:

**`my_meme.jpg.xmp`:**
```xml
<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
      xmlns:mmm="http://riposte.app/1.0/"
      xmlns:dc="http://purl.org/dc/elements/1.1/">
      <mmm:schemaVersion>1.0</mmm:schemaVersion>
      <mmm:emojis>
        <rdf:Bag>
          <rdf:li>🤣</rdf:li>
          <rdf:li>👍</rdf:li>
        </rdf:Bag>
      </mmm:emojis>
      <dc:title>
        <rdf:Alt>
          <rdf:li xml:lang="x-default">My Awesome Meme</rdf:li>
        </rdf:Alt>
      </dc:title>
      <dc:description>
        <rdf:Alt>
          <rdf:li xml:lang="x-default">A funny meme about coding</rdf:li>
        </rdf:Alt>
      </dc:description>
      <mmm:tags>
        <rdf:Bag>
          <rdf:li>programming</rdf:li>
          <rdf:li>developer</rdf:li>
        </rdf:Bag>
      </mmm:tags>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>
```

### Method 3: JSON Metadata File

For batch imports or external tools, you can use JSON format:

**`my_meme.json`:**
```json
{
  "schemaVersion": "1.0",
  "emojis": ["🤣", "👍", "💻"],
  "title": "My Awesome Meme",
  "description": "A funny meme about coding",
  "tags": ["programming", "developer", "funny"]
}
```

---

## Emoji Storage Best Practices

### ✅ Recommended: Use Actual Unicode Characters

Store emojis as actual Unicode characters:
```xml
<rdf:li>😂</rdf:li>
<rdf:li>🔥</rdf:li>
```

**Benefits:**
- Universal compatibility
- No mapping tables needed
- Future-proof for new emojis

### ❌ Not Recommended: Shortcodes

Avoid using platform-specific shortcodes:
```xml
<!-- Don't do this -->
<rdf:li>:joy:</rdf:li>
<rdf:li>:fire:</rdf:li>
```

**Why:**
- Requires mapping tables
- Platform-dependent
- May become outdated

---

## Format-Specific Notes

### JPEG

- Full XMP support
- Can embed metadata in APP1 marker segment
- Most widely supported format

### PNG

- XMP stored in iTXt chunk
- Maintains transparency during processing
- Lossless compression

### WebP

- Modern format with excellent compression
- Full XMP metadata support
- May require conversion for older apps

### GIF

- Animated GIFs are imported as static frames (first frame)
- XMP stored in Application Extension block
- Transparency preserved

---

## Fallback Behavior

When importing images with missing or corrupted metadata:

| Scenario | App Behavior |
|----------|--------------|
| No emojis found | Prompts user to add emojis (required) |
| No title | Uses filename as title |
| No description | Leaves empty |
| Invalid schema version | Parses as latest version |
| Corrupted XMP | Treats as new image without metadata |
| Missing sidecar | Checks for embedded XMP |

---

## Related Documentation

- [METADATA_FORMAT.md](./METADATA_FORMAT.md) - Complete XMP schema specification
- [README.md](../README.md) - Project overview

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01 | Initial documentation |
