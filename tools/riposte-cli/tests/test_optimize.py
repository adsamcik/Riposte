"""Tests for WebP image optimization."""

import hashlib
import io
import json
import os
import zipfile
from pathlib import Path

from click.testing import CliRunner
from PIL import Image

from riposte_cli.commands import annotate
from riposte_cli.commands.annotate import BundleImage, create_optimized_bundle, select_bundle_image
from riposte_cli.commands.optimize import convert_to_webp, optimize
from riposte_cli.hashing import get_image_hash


def test_convert_to_webp_preserves_dimensions_and_transparency(tmp_path: Path) -> None:
    source = tmp_path / "transparent.png"
    destination = tmp_path / "transparent.webp"
    Image.new("RGBA", (12, 8), (20, 30, 40, 0)).save(source)

    convert_to_webp(source, destination, quality=85, lossless=False)

    with Image.open(destination) as optimized:
        assert optimized.format == "WEBP"
        assert optimized.size == (12, 8)
        assert optimized.mode == "RGBA"


def test_convert_to_webp_preserves_animation(tmp_path: Path) -> None:
    source = tmp_path / "animated.gif"
    destination = tmp_path / "animated.webp"
    first_frame = Image.new("RGB", (6, 6), "red")
    second_frame = Image.new("RGB", (6, 6), "blue")
    first_frame.save(
        source,
        save_all=True,
        append_images=[second_frame],
        duration=[20, 30],
        loop=1,
    )

    convert_to_webp(source, destination, quality=85, lossless=False)

    with Image.open(destination) as optimized:
        assert optimized.is_animated
        assert optimized.n_frames == 2
        assert optimized.info["loop"] == 1


def test_optimize_creates_webp_and_updates_sidecar_hash(tmp_path: Path) -> None:
    source_dir = tmp_path / "memes"
    source_dir.mkdir()
    source = source_dir / "meme.png"
    Image.new("RGB", (16, 10), (10, 20, 30)).save(source)
    source.with_name("meme.png.json").write_text(
        json.dumps({"schemaVersion": "1.3", "emojis": ["😂"], "contentHash": "old-hash"}),
        encoding="utf-8",
    )

    result = CliRunner().invoke(optimize, [str(source_dir)])

    destination = source_dir / "webp" / "meme.webp"
    sidecar = destination.with_name("meme.webp.json")
    assert result.exit_code == 0, result.output
    assert source.exists()
    assert destination.exists()
    assert json.loads(sidecar.read_text(encoding="utf-8"))["contentHash"] == get_image_hash(
        destination
    )


def test_optimize_skips_existing_output_without_force(tmp_path: Path) -> None:
    source_dir = tmp_path / "memes"
    source_dir.mkdir()
    Image.new("RGB", (8, 8), "white").save(source_dir / "meme.jpg")
    output_dir = source_dir / "webp"
    output_dir.mkdir()
    destination = output_dir / "meme.webp"
    destination.write_bytes(b"existing output")

    result = CliRunner().invoke(optimize, [str(source_dir)])

    assert result.exit_code == 0, result.output
    assert destination.read_bytes() == b"existing output"
    assert "All WebP outputs already exist" in result.output


def test_optimize_rejects_output_that_overwrites_input(tmp_path: Path) -> None:
    Image.new("RGB", (8, 8), "white").save(tmp_path / "meme.webp")

    result = CliRunner().invoke(optimize, [str(tmp_path), "--output", str(tmp_path)])

    assert result.exit_code != 0
    assert "would overwrite an input image" in result.output


def test_create_optimized_bundle_uses_lossless_webp_and_rehashes_sidecars(tmp_path: Path) -> None:
    source = tmp_path / "meme.png"
    original_content = Image.new("RGB", (16, 10), (10, 20, 30))
    original_content.save(source)
    metadata_dir = tmp_path / "metadata"
    metadata_dir.mkdir()
    (metadata_dir / "meme.png.json").write_text(
        json.dumps({"schemaVersion": "1.3", "emojis": ["😂"], "contentHash": "old-hash"}),
        encoding="utf-8",
    )
    bundle_path = tmp_path / "memes.meme.zip"

    result = create_optimized_bundle(bundle_path, [source], metadata_dir)

    assert result.image_count == 1
    assert result.optimized_archive_size < result.source_archive_size
    with zipfile.ZipFile(bundle_path) as bundle:
        assert bundle.namelist() == ["meme.webp", "meme.webp.json"]
        optimized_content = bundle.read("meme.webp")
        metadata = json.loads(bundle.read("meme.webp.json"))
    with Image.open(io.BytesIO(optimized_content)) as optimized:
        assert optimized.format == "WEBP"
        assert optimized.size == (16, 10)
    assert metadata["contentHash"] == hashlib.sha256(optimized_content).hexdigest()


def test_select_bundle_image_retains_source_without_material_savings(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source = tmp_path / "meme.jpg"
    source_content = os.urandom(1_000)
    source.write_bytes(source_content)

    def fake_optimize(_source: Path, *, quality: int, lossless: bool) -> bytes:
        return os.urandom(1_100 if lossless else 960)

    monkeypatch.setattr(annotate, "optimize_image_to_bytes", fake_optimize)

    original, selected = select_bundle_image(source)

    assert selected == original
    assert selected.content == source_content
    assert selected.suffix == ".jpg"


def test_select_bundle_image_uses_visually_lossless_webp_for_material_savings(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source = tmp_path / "meme.jpg"
    source.write_bytes(os.urandom(1_000))

    def fake_optimize(_source: Path, *, quality: int, lossless: bool) -> bytes:
        return os.urandom(1_100 if lossless else 900)

    monkeypatch.setattr(annotate, "optimize_image_to_bytes", fake_optimize)

    _original, selected = select_bundle_image(source)

    assert selected.suffix == ".webp"
    assert len(selected.content) == 900


def test_create_optimized_bundle_disambiguates_same_stem_images(
    tmp_path: Path,
    monkeypatch,
) -> None:
    jpg = tmp_path / "meme.jpg"
    png = tmp_path / "meme.png"
    webp = tmp_path / "meme.png.webp"
    Image.new("RGB", (8, 8), "white").save(jpg)
    Image.new("RGB", (8, 8), "black").save(png)
    Image.new("RGB", (8, 8), "red").save(webp)
    for source in (jpg, png, webp):
        source.with_name(f"{source.name}.json").write_text(
            json.dumps({"schemaVersion": "1.3", "emojis": ["😂"]}),
            encoding="utf-8",
        )
    bundle_path = tmp_path / "memes.meme.zip"

    def fake_select(source: Path) -> tuple[BundleImage, BundleImage]:
        original = BundleImage(
            content=source.read_bytes(),
            suffix=source.suffix,
            compression_type=zipfile.ZIP_STORED,
            archive_size=source.stat().st_size,
        )
        selected = BundleImage(
            content=b"optimized",
            suffix=".webp",
            compression_type=zipfile.ZIP_STORED,
            archive_size=len(b"optimized"),
        )
        return original, selected

    monkeypatch.setattr(annotate, "select_bundle_image", fake_select)

    create_optimized_bundle(bundle_path, [jpg, png, webp], tmp_path)

    with zipfile.ZipFile(bundle_path) as bundle:
        assert bundle.namelist() == [
            "meme.webp",
            "meme.webp.json",
            "meme.png.webp",
            "meme.png.webp.json",
            "meme.png.webp.webp",
            "meme.png.webp.webp.json",
        ]
