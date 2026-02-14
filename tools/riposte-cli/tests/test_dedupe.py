"""Tests for the dedupe command."""

import json
import shutil
from pathlib import Path

import pytest
from click.testing import CliRunner

from riposte_cli.commands.dedupe import _delete_image_and_sidecar, dedupe
from riposte_cli.hashing import HASH_MANIFEST_FILENAME


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def image_dir(tmp_path: Path) -> Path:
    """Create a temp directory with test image-like files."""
    d = tmp_path / "memes"
    d.mkdir()
    return d


def _write_fake_image(path: Path, content: bytes = b"fake image data") -> None:
    """Write a file with given content (not a real image, but enough for hash tests)."""
    path.write_bytes(content)


def test_delete_image_and_sidecar(tmp_path: Path) -> None:
    """Test that both image and sidecar are deleted."""
    img = tmp_path / "test.jpg"
    sidecar = tmp_path / "test.jpg.json"
    img.write_bytes(b"image data")
    sidecar.write_text('{"emojis": []}')

    deleted = _delete_image_and_sidecar(img, tmp_path)

    assert len(deleted) == 2
    assert not img.exists()
    assert not sidecar.exists()


def test_delete_image_without_sidecar(tmp_path: Path) -> None:
    """Test deletion when no sidecar exists."""
    img = tmp_path / "test.jpg"
    img.write_bytes(b"image data")

    deleted = _delete_image_and_sidecar(img, tmp_path)

    assert len(deleted) == 1
    assert not img.exists()


def test_delete_nonexistent_image(tmp_path: Path) -> None:
    """Test deletion of already-removed file is safe."""
    img = tmp_path / "gone.jpg"
    deleted = _delete_image_and_sidecar(img, tmp_path)
    assert len(deleted) == 0


def test_dedupe_no_images(runner, tmp_path: Path) -> None:
    """Test dedupe with an empty directory."""
    d = tmp_path / "empty"
    d.mkdir()

    result = runner.invoke(dedupe, [str(d)])

    assert result.exit_code == 0
    assert "No supported images" in result.output


def test_dedupe_command_imports() -> None:
    """Verify the dedupe command can be imported."""
    from riposte_cli.commands.dedupe import dedupe as cmd

    assert callable(cmd)


def test_dedupe_registered_in_cli() -> None:
    """Verify dedupe is registered in the main CLI group."""
    from riposte_cli.main import cli

    assert "dedupe" in [c.name for c in cli.commands.values()]
