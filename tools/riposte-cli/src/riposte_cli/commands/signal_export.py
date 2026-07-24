"""Signal sticker pack preparation without Signal account access."""

import json
import tempfile
from dataclasses import dataclass
from pathlib import Path

import click
from PIL import Image
from rich.console import Console

from riposte_cli.commands.optimize import encode_image_as_webp

console = Console()

SIGNAL_STICKER_SIZE = 512
SIGNAL_STICKER_MAX_BYTES = 300_000
SIGNAL_MIN_STICKERS = 3
SIGNAL_MAX_STICKERS = 200
VISUALLY_LOSSLESS_QUALITIES = (95, 92, 89, 86, 83, 80)


@dataclass(frozen=True)
class SignalSticker:
    """A prepared Signal sticker and its suggested emoji."""

    content: bytes
    emoji: str


def _create_signal_canvas(image: Image.Image) -> Image.Image:
    """Center an image on Signal's transparent 512px sticker canvas."""
    if getattr(image, "is_animated", False):
        raise ValueError("animated images are not supported for Signal WebP export")

    artwork = image.convert("RGBA")
    artwork.thumbnail(
        (SIGNAL_STICKER_SIZE, SIGNAL_STICKER_SIZE),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new(
        "RGBA",
        (SIGNAL_STICKER_SIZE, SIGNAL_STICKER_SIZE),
        (0, 0, 0, 0),
    )
    position = (
        (SIGNAL_STICKER_SIZE - artwork.width) // 2,
        (SIGNAL_STICKER_SIZE - artwork.height) // 2,
    )
    canvas.alpha_composite(artwork, position)
    return canvas


def prepare_signal_sticker(image_path: Path) -> bytes:
    """Create a 512px WebP sticker that meets Signal's 300 KB limit."""
    with Image.open(image_path) as image:
        canvas = _create_signal_canvas(image)

    lossless = encode_image_as_webp(canvas, quality=100, lossless=True)
    if len(lossless) <= SIGNAL_STICKER_MAX_BYTES:
        return lossless

    for quality in VISUALLY_LOSSLESS_QUALITIES:
        encoded = encode_image_as_webp(canvas, quality=quality, lossless=False)
        if len(encoded) <= SIGNAL_STICKER_MAX_BYTES:
            return encoded

    raise ValueError(
        f"could not meet Signal's {SIGNAL_STICKER_MAX_BYTES // 1_000} KB limit "
        f"without dropping below quality {VISUALLY_LOSSLESS_QUALITIES[-1]}"
    )


def _load_sticker_emoji(image_path: Path) -> str:
    """Get the primary Signal emoji from a Riposte image sidecar."""
    sidecar_path = image_path.with_name(f"{image_path.name}.json")
    if not sidecar_path.exists():
        raise ValueError(f"missing sidecar: {sidecar_path.name}")

    with open(sidecar_path, encoding="utf-8") as sidecar_file:
        metadata = json.load(sidecar_file)
    if not isinstance(metadata, dict):
        raise ValueError(f"sidecar must contain a JSON object: {sidecar_path.name}")

    emojis = metadata.get("emojis")
    if not isinstance(emojis, list) or not emojis or not isinstance(emojis[0], str):
        raise ValueError(f"sidecar must have at least one emoji: {sidecar_path.name}")
    return emojis[0]


def _write_signal_manifest(
    output_dir: Path,
    title: str,
    author: str,
    stickers: list[SignalSticker],
) -> Path:
    """Write a Signal-sticker-tool-compatible manifest without credentials."""
    lines = [
        "meta:",
        f"  title: {json.dumps(title, ensure_ascii=False)}",
        f"  author: {json.dumps(author, ensure_ascii=False)}",
        '  cover: "sticker_001.webp"',
        "",
        "stickers:",
    ]
    for index, sticker in enumerate(stickers, start=1):
        lines.extend(
            [
                f'  - file: "sticker_{index:03}.webp"',
                f"    chr: {json.dumps(sticker.emoji, ensure_ascii=False)}",
            ]
        )

    manifest_path = output_dir / "stickers.yaml"
    manifest_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return manifest_path


@click.command()
@click.argument(
    "folder",
    type=click.Path(exists=True, file_okay=False, path_type=Path),
)
@click.option("--title", required=True, help="Sticker pack title for the manifest")
@click.option("--author", required=True, help="Sticker pack author for the manifest")
@click.option(
    "--output",
    "-o",
    type=click.Path(path_type=Path),
    help="Export directory (default: FOLDER/signal-stickers)",
)
def signal_export(
    folder: Path,
    title: str,
    author: str,
    output: Path | None,
) -> None:
    """Prepare a Signal sticker pack from annotated images in FOLDER.

    The command creates local sticker files and a manifest only. Upload the
    result through Signal Desktop to keep Signal credentials out of this CLI.
    """
    from riposte_cli.commands.annotate import get_images_in_folder

    images = get_images_in_folder(folder)
    if not SIGNAL_MIN_STICKERS <= len(images) <= SIGNAL_MAX_STICKERS:
        raise click.UsageError(
            f"Signal packs require {SIGNAL_MIN_STICKERS}-{SIGNAL_MAX_STICKERS} images; "
            f"found {len(images)}"
        )

    output_dir = output or folder / "signal-stickers"
    if output_dir.exists():
        raise click.UsageError(
            f"Output directory already exists: {output_dir}. Choose a new --output path."
        )
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    temporary_dir = Path(
        tempfile.mkdtemp(
            dir=output_dir.parent,
            prefix=f".{output_dir.name}-",
        )
    )

    stickers: list[SignalSticker] = []
    try:
        for index, image_path in enumerate(images, start=1):
            sticker = SignalSticker(
                content=prepare_signal_sticker(image_path),
                emoji=_load_sticker_emoji(image_path),
            )
            (temporary_dir / f"sticker_{index:03}.webp").write_bytes(sticker.content)
            stickers.append(sticker)
        _write_signal_manifest(temporary_dir, title, author, stickers)
        temporary_dir.replace(output_dir)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        for file_path in temporary_dir.iterdir():
            file_path.unlink()
        temporary_dir.rmdir()
        raise click.ClickException(f"Could not export Signal stickers: {error}") from error

    console.print(
        f"[green]Prepared {len(stickers)} Signal stickers in {output_dir}[/green]"
    )
    console.print(
        "[dim]Upload the WebP files through Signal Desktop. "
        "stickers.yaml maps each file to its suggested emoji.[/dim]"
    )
