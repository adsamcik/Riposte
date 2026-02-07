"""Smoke tests for meme-my-mood CLI."""

from pathlib import Path


def test_hashing_module_imports() -> None:
    """Verify the hashing module can be imported without errors."""
    from meme_my_mood_cli.hashing import (
        DeduplicationResult,
        deduplicate_images,
        get_image_hash,
        load_hash_manifest,
        save_hash_manifest,
    )

    assert callable(deduplicate_images)
    assert callable(get_image_hash)
    assert callable(load_hash_manifest)
    assert callable(save_hash_manifest)
    assert DeduplicationResult is not None


def test_cli_entry_point_imports() -> None:
    """Verify the CLI entry point can be imported without errors."""
    from meme_my_mood_cli.main import cli

    assert callable(cli)


def test_get_image_hash(tmp_path: Path) -> None:
    """Test SHA-256 hashing of a small test file."""
    from meme_my_mood_cli.hashing import get_image_hash

    test_file = tmp_path / "test.bin"
    test_file.write_bytes(b"hello world")
    result = get_image_hash(test_file)
    assert isinstance(result, str)
    assert len(result) == 64  # SHA-256 hex length


def test_load_hash_manifest_empty(tmp_path: Path) -> None:
    """Test loading manifest from directory with no manifest file."""
    from meme_my_mood_cli.hashing import load_hash_manifest

    manifest = load_hash_manifest(tmp_path)
    assert manifest == {}


def test_save_and_load_hash_manifest(tmp_path: Path) -> None:
    """Test round-trip save and load of hash manifest."""
    from meme_my_mood_cli.hashing import (
        load_hash_manifest,
        save_hash_manifest,
    )

    data = {"image.jpg": {"content_hash": "abc123", "phash": "def456"}}
    save_hash_manifest(tmp_path, data)
    loaded = load_hash_manifest(tmp_path)
    assert loaded == data
