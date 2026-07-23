"""Tests for WebP image optimization."""

import json
from pathlib import Path

from click.testing import CliRunner
from PIL import Image

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
