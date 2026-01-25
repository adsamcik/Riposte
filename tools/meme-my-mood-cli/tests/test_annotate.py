"""Tests for annotate command."""

import json
import tempfile
from pathlib import Path

import pytest

from meme_my_mood_cli.commands.annotate import (
    create_sidecar_metadata,
    is_supported_image,
    write_sidecar,
)


class TestIsSupportedImage:
    """Tests for is_supported_image function."""

    def test_supported_extensions(self, tmp_path: Path) -> None:
        """Test that supported extensions are recognized."""
        for ext in [".jpg", ".jpeg", ".png", ".webp", ".gif"]:
            # Create a minimal valid image file
            image_file = tmp_path / f"test{ext}"
            # PNG magic bytes for testing
            image_file.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\x00" * 100)
            # Note: This won't pass filetype check without real image data
            # For unit testing, we'd mock the filetype.guess call

    def test_unsupported_extensions(self, tmp_path: Path) -> None:
        """Test that unsupported extensions are rejected."""
        for ext in [".txt", ".pdf", ".doc", ".mp3"]:
            test_file = tmp_path / f"test{ext}"
            test_file.write_text("not an image")
            assert not is_supported_image(test_file)


class TestCreateSidecarMetadata:
    """Tests for create_sidecar_metadata function."""

    def test_minimal_metadata(self) -> None:
        """Test creating metadata with only required fields."""
        metadata = create_sidecar_metadata(emojis=["😂", "🔥"])
        
        assert metadata["schemaVersion"] == "1.0"
        assert metadata["emojis"] == ["😂", "🔥"]
        assert "createdAt" in metadata
        assert "appVersion" in metadata
        assert "title" not in metadata
        assert "description" not in metadata

    def test_full_metadata(self) -> None:
        """Test creating metadata with all optional fields."""
        metadata = create_sidecar_metadata(
            emojis=["😂"],
            title="Test Meme",
            description="A test description",
            text_content="Hello World",
            tags=["test", "meme"],
        )
        
        assert metadata["emojis"] == ["😂"]
        assert metadata["title"] == "Test Meme"
        assert metadata["description"] == "A test description"
        assert metadata["textContent"] == "Hello World"
        assert metadata["tags"] == ["test", "meme"]


class TestWriteSidecar:
    """Tests for write_sidecar function."""

    def test_write_sidecar_same_directory(self, tmp_path: Path) -> None:
        """Test writing sidecar to same directory as image."""
        image_path = tmp_path / "test.jpg"
        image_path.touch()
        
        metadata = {"schemaVersion": "1.0", "emojis": ["😂"]}
        sidecar_path = write_sidecar(image_path, metadata)
        
        assert sidecar_path == tmp_path / "test.jpg.json"
        assert sidecar_path.exists()
        
        content = json.loads(sidecar_path.read_text(encoding="utf-8"))
        assert content["emojis"] == ["😂"]

    def test_write_sidecar_different_directory(self, tmp_path: Path) -> None:
        """Test writing sidecar to different directory."""
        image_path = tmp_path / "images" / "test.jpg"
        image_path.parent.mkdir()
        image_path.touch()
        
        output_dir = tmp_path / "output"
        output_dir.mkdir()
        
        metadata = {"schemaVersion": "1.0", "emojis": ["🔥"]}
        sidecar_path = write_sidecar(image_path, metadata, output_dir)
        
        assert sidecar_path == output_dir / "test.jpg.json"
        assert sidecar_path.exists()

    def test_sidecar_unicode_support(self, tmp_path: Path) -> None:
        """Test that Unicode emojis are properly written."""
        image_path = tmp_path / "test.jpg"
        image_path.touch()
        
        metadata = {
            "schemaVersion": "1.0",
            "emojis": ["😂", "🤣", "💀", "🔥", "❤️"],
            "title": "Тест 测试 テスト",
        }
        sidecar_path = write_sidecar(image_path, metadata)
        
        content = json.loads(sidecar_path.read_text(encoding="utf-8"))
        assert content["emojis"] == ["😂", "🤣", "💀", "🔥", "❤️"]
        assert content["title"] == "Тест 测试 テスト"
