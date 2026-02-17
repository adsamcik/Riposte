"""Tests for metadata sidecar creation."""

from riposte_cli import __version__
from riposte_cli.commands.annotate import create_sidecar_metadata


def test_create_sidecar_metadata_includes_cli_version():
    metadata = create_sidecar_metadata(emojis=["😂"])

    assert metadata["cliVersion"] == __version__
    assert metadata["appVersion"] == f"cli-{__version__}"


def test_create_sidecar_metadata_required_fields():
    metadata = create_sidecar_metadata(emojis=["😂", "🔥"])

    assert metadata["schemaVersion"] == "1.3"
    assert metadata["emojis"] == ["😂", "🔥"]
    assert "createdAt" in metadata
    assert "cliVersion" in metadata
    assert "appVersion" in metadata


def test_create_sidecar_metadata_optional_fields():
    metadata = create_sidecar_metadata(
        emojis=["😂"],
        title="Test Meme",
        description="A test description",
        tags=["funny"],
        search_phrases=["when code works"],
        primary_language="en",
        content_hash="abc123",
        based_on="Drake Hotline Bling",
    )

    assert metadata["title"] == "Test Meme"
    assert metadata["description"] == "A test description"
    assert metadata["tags"] == ["funny"]
    assert metadata["searchPhrases"] == ["when code works"]
    assert metadata["primaryLanguage"] == "en"
    assert metadata["contentHash"] == "abc123"
    assert metadata["basedOn"] == "Drake Hotline Bling"
