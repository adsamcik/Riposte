"""Tests to verify CLI output is compatible with the Kotlin Android app.

These tests ensure the JSON sidecar files produced by the CLI can be
parsed by the Kotlin MemeMetadata class in the Android app.
"""

import json
from datetime import datetime, timezone
from pathlib import Path

import pytest

from meme_my_mood_cli import __version__
from meme_my_mood_cli.commands.annotate import create_sidecar_metadata, write_sidecar


class TestKotlinMemeMetadataCompatibility:
    """Tests that verify output matches Kotlin MemeMetadata class.
    
    The Kotlin class expects:
    - schemaVersion: String (required, default "1.0")
    - emojis: List<String> (required, must not be empty)
    - title: String? (optional)
    - description: String? (optional)
    - createdAt: String? (optional, ISO 8601)
    - appVersion: String? (optional)
    - source: String? (optional)
    - tags: List<String> (optional, default empty list)
    - textContent: String? (optional)
    """

    def test_schema_version_matches(self) -> None:
        """Verify schemaVersion matches Kotlin CURRENT_SCHEMA_VERSION."""
        metadata = create_sidecar_metadata(emojis=["😂"])
        assert metadata["schemaVersion"] == "1.0"

    def test_emojis_is_list_of_strings(self) -> None:
        """Verify emojis is a list of strings (required by Kotlin)."""
        metadata = create_sidecar_metadata(emojis=["😂", "🔥", "💯"])
        
        assert isinstance(metadata["emojis"], list)
        assert len(metadata["emojis"]) > 0  # Kotlin requires non-empty
        assert all(isinstance(e, str) for e in metadata["emojis"])

    def test_created_at_is_iso8601(self) -> None:
        """Verify createdAt is valid ISO 8601 format."""
        metadata = create_sidecar_metadata(emojis=["😂"])
        
        # Should parse without error
        created_at = metadata["createdAt"]
        parsed = datetime.fromisoformat(created_at.replace("Z", "+00:00"))
        assert parsed is not None

    def test_app_version_format(self) -> None:
        """Verify appVersion follows expected format."""
        metadata = create_sidecar_metadata(emojis=["😂"])
        
        assert "appVersion" in metadata
        assert metadata["appVersion"].startswith("cli-")
        assert __version__ in metadata["appVersion"]

    def test_optional_fields_omitted_when_none(self) -> None:
        """Verify optional fields are omitted, not null."""
        metadata = create_sidecar_metadata(emojis=["😂"])
        
        # Kotlin uses default values, so omitting is preferred over null
        assert "title" not in metadata
        assert "description" not in metadata
        assert "textContent" not in metadata
        assert "tags" not in metadata
        assert "source" not in metadata

    def test_optional_fields_present_when_provided(self) -> None:
        """Verify optional fields are included when provided."""
        metadata = create_sidecar_metadata(
            emojis=["😂"],
            title="Test Title",
            description="Test Description",
            text_content="Extracted Text",
            tags=["tag1", "tag2"],
        )
        
        assert metadata["title"] == "Test Title"
        assert metadata["description"] == "Test Description"
        assert metadata["textContent"] == "Extracted Text"
        assert metadata["tags"] == ["tag1", "tag2"]

    def test_tags_is_list_of_strings(self) -> None:
        """Verify tags is a list of strings."""
        metadata = create_sidecar_metadata(
            emojis=["😂"],
            tags=["funny", "meme", "programming"],
        )
        
        assert isinstance(metadata["tags"], list)
        assert all(isinstance(t, str) for t in metadata["tags"])

    def test_unicode_emoji_support(self) -> None:
        """Verify Unicode emojis are preserved correctly."""
        test_emojis = [
            "😂",      # Basic emoji
            "🔥",      # Fire
            "👾",      # Space invader
            "❤️",     # Heart with variation selector
            "🏳️‍🌈",  # Rainbow flag (ZWJ sequence)
            "👨‍💻",   # Man technologist (ZWJ sequence)
        ]
        metadata = create_sidecar_metadata(emojis=test_emojis)
        
        assert metadata["emojis"] == test_emojis

    def test_unicode_text_support(self) -> None:
        """Verify Unicode text in title/description is preserved."""
        metadata = create_sidecar_metadata(
            emojis=["😂"],
            title="日本語タイトル",
            description="Описание на русском 中文描述",
            text_content="مرحبا بالعالم",
        )
        
        assert metadata["title"] == "日本語タイトル"
        assert metadata["description"] == "Описание на русском 中文描述"
        assert metadata["textContent"] == "مرحبا بالعالم"


class TestJsonSerializationCompatibility:
    """Tests for JSON serialization compatible with Kotlin."""

    def test_json_serialization_valid(self, tmp_path: Path) -> None:
        """Verify output is valid JSON."""
        image_path = tmp_path / "test.jpg"
        image_path.touch()
        
        metadata = create_sidecar_metadata(
            emojis=["😂", "🔥"],
            title="Test Meme",
            tags=["test"],
        )
        sidecar_path = write_sidecar(image_path, metadata)
        
        # Should parse without error
        content = json.loads(sidecar_path.read_text(encoding="utf-8"))
        assert content is not None

    def test_json_uses_utf8_encoding(self, tmp_path: Path) -> None:
        """Verify JSON file uses UTF-8 encoding for Unicode."""
        image_path = tmp_path / "test.jpg"
        image_path.touch()
        
        metadata = create_sidecar_metadata(
            emojis=["😂", "🎮", "👾"],
            title="Émoji Tëst 🎉",
        )
        sidecar_path = write_sidecar(image_path, metadata)
        
        # Read as bytes and verify UTF-8
        raw_bytes = sidecar_path.read_bytes()
        decoded = raw_bytes.decode("utf-8")
        assert "😂" in decoded
        assert "🎮" in decoded
        assert "Émoji Tëst 🎉" in decoded

    def test_json_not_ascii_escaped(self, tmp_path: Path) -> None:
        """Verify emojis are not ASCII-escaped in output."""
        image_path = tmp_path / "test.jpg"
        image_path.touch()
        
        metadata = create_sidecar_metadata(emojis=["😂"])
        sidecar_path = write_sidecar(image_path, metadata)
        
        raw_content = sidecar_path.read_text(encoding="utf-8")
        # Should contain actual emoji, not \\uXXXX escape
        assert "😂" in raw_content
        assert "\\u" not in raw_content

    def test_sidecar_filename_format(self, tmp_path: Path) -> None:
        """Verify sidecar filename matches expected pattern."""
        image_path = tmp_path / "my_meme.jpg"
        image_path.touch()
        
        metadata = create_sidecar_metadata(emojis=["😂"])
        sidecar_path = write_sidecar(image_path, metadata)
        
        # Kotlin ZipImporter expects: image.jpg -> image.jpg.json
        assert sidecar_path.name == "my_meme.jpg.json"


class TestZipBundleCompatibility:
    """Tests for ZIP bundle format compatibility with Kotlin ZipImporter."""

    def test_sidecar_naming_for_zip(self, tmp_path: Path) -> None:
        """Verify sidecar naming works with ZipImporter expectations.
        
        ZipImporter does:
            entryName.endsWith(".json") -> imageFileName = entryName.removeSuffix(".json")
        """
        test_cases = [
            ("image.jpg", "image.jpg.json"),
            ("meme.png", "meme.png.json"),
            ("photo.webp", "photo.webp.json"),
            ("funny meme.jpg", "funny meme.jpg.json"),
            ("test.image.jpg", "test.image.jpg.json"),
        ]
        
        for image_name, expected_sidecar in test_cases:
            image_path = tmp_path / image_name
            image_path.touch()
            
            metadata = create_sidecar_metadata(emojis=["😂"])
            sidecar_path = write_sidecar(image_path, metadata)
            
            assert sidecar_path.name == expected_sidecar
            
            # Verify reverse lookup works (what ZipImporter does)
            recovered_image_name = sidecar_path.name.removesuffix(".json")
            assert recovered_image_name == image_name

    def test_full_metadata_round_trip(self, tmp_path: Path) -> None:
        """Test creating metadata that would fully populate MemeMetadata."""
        image_path = tmp_path / "meme.jpg"
        image_path.touch()
        
        # Create metadata with all fields
        metadata = create_sidecar_metadata(
            emojis=["😂", "🔥", "💯"],
            title="Ultimate Meme",
            description="The best meme ever created",
            text_content="When you finally fix the bug",
            tags=["programming", "debugging", "success"],
        )
        sidecar_path = write_sidecar(image_path, metadata)
        
        # Parse and verify all fields
        content = json.loads(sidecar_path.read_text(encoding="utf-8"))
        
        assert content["schemaVersion"] == "1.0"
        assert content["emojis"] == ["😂", "🔥", "💯"]
        assert content["title"] == "Ultimate Meme"
        assert content["description"] == "The best meme ever created"
        assert content["textContent"] == "When you finally fix the bug"
        assert content["tags"] == ["programming", "debugging", "success"]
        assert "createdAt" in content
        assert "appVersion" in content
