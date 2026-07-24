"""WebP image optimization command."""

import io
import json
import tempfile
from pathlib import Path
from typing import BinaryIO

import click
from PIL import Image, ImageSequence
from rich.console import Console

from riposte_cli.hashing import get_image_hash

console = Console()


def _webp_mode(image: Image.Image) -> str:
    """Choose a WebP-compatible mode without losing transparency."""
    if "A" in image.getbands() or "transparency" in image.info:
        return "RGBA"
    return "RGB"


def _save_as_webp(
    image: Image.Image,
    destination: Path | BinaryIO,
    *,
    quality: int,
    lossless: bool,
) -> None:
    """Encode an open image as WebP while preserving animated frames."""
    save_options = {
        "format": "WEBP",
        "quality": quality,
        "method": 6,
        "lossless": lossless,
    }
    if icc_profile := image.info.get("icc_profile"):
        save_options["icc_profile"] = icc_profile
    if exif := image.info.get("exif"):
        save_options["exif"] = exif
    if getattr(image, "is_animated", False):
        frames = [frame.convert(_webp_mode(frame)) for frame in ImageSequence.Iterator(image)]
        durations = [
            frame.info.get("duration", image.info.get("duration", 0))
            for frame in ImageSequence.Iterator(image)
        ]
        frames[0].save(
            destination,
            save_all=True,
            append_images=frames[1:],
            duration=durations,
            loop=image.info.get("loop", 0),
            **save_options,
        )
        return
    image.convert(_webp_mode(image)).save(destination, **save_options)


def encode_image_as_webp(image: Image.Image, *, quality: int, lossless: bool) -> bytes:
    """Encode an already-prepared image as optimized WebP bytes."""
    destination = io.BytesIO()
    _save_as_webp(image, destination, quality=quality, lossless=lossless)
    return destination.getvalue()


def optimize_image_to_bytes(source: Path, *, quality: int, lossless: bool) -> bytes:
    """Encode an image as optimized WebP bytes without creating a file."""
    with Image.open(source) as image:
        return encode_image_as_webp(image, quality=quality, lossless=lossless)


def convert_to_webp(
    source: Path,
    destination: Path,
    *,
    quality: int,
    lossless: bool,
) -> None:
    """Convert an image to an optimized WebP file atomically.

    Animated images retain their frames, frame durations, and loop count.
    """
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary_file = tempfile.NamedTemporaryFile(
        dir=destination.parent,
        prefix=f".{destination.stem}-",
        suffix=".webp",
        delete=False,
    )
    temporary_path = Path(temporary_file.name)
    temporary_file.close()

    try:
        with Image.open(source) as image:
            _save_as_webp(image, temporary_path, quality=quality, lossless=lossless)
        temporary_path.replace(destination)
    except (OSError, ValueError):
        temporary_path.unlink(missing_ok=True)
        raise


def _load_sidecar_metadata(image_path: Path) -> dict | None:
    """Load the metadata sidecar paired with an image, if one exists."""
    sidecar_path = image_path.with_name(f"{image_path.name}.json")
    if not sidecar_path.exists():
        return None

    with open(sidecar_path, encoding="utf-8") as sidecar_file:
        metadata = json.load(sidecar_file)
    if not isinstance(metadata, dict):
        raise ValueError(f"Sidecar must contain a JSON object: {sidecar_path}")
    return metadata


def write_optimized_sidecar(destination: Path, metadata: dict) -> Path:
    """Write a copied sidecar whose hash reflects the optimized image."""
    optimized_metadata = {**metadata, "contentHash": get_image_hash(destination)}
    sidecar_path = destination.with_name(f"{destination.name}.json")
    with open(sidecar_path, "w", encoding="utf-8") as sidecar_file:
        json.dump(optimized_metadata, sidecar_file, indent=2, ensure_ascii=False)
    return sidecar_path


def _get_destination_paths(images: list[Path], output_dir: Path) -> dict[Path, Path]:
    """Map source images to collision-free output paths."""
    destinations = {image: output_dir / f"{image.stem}.webp" for image in images}
    output_names = [destination.name.casefold() for destination in destinations.values()]
    if len(output_names) != len(set(output_names)):
        raise click.UsageError(
            "Multiple input images map to the same WebP filename. "
            "Use separate folders or rename the conflicting files."
        )

    source_paths = {image.resolve() for image in images}
    if any(destination.resolve() in source_paths for destination in destinations.values()):
        raise click.UsageError(
            "Output would overwrite an input image. Choose a different --output directory."
        )
    return destinations


@click.command()
@click.argument(
    "folder",
    type=click.Path(exists=True, file_okay=False, path_type=Path),
)
@click.option(
    "--output",
    "-o",
    type=click.Path(path_type=Path),
    help="Directory for optimized WebP files (default: FOLDER/webp)",
)
@click.option(
    "--quality",
    type=click.IntRange(0, 100),
    default=85,
    show_default=True,
    help="Lossy WebP quality; higher values retain more detail",
)
@click.option(
    "--lossless",
    is_flag=True,
    help="Use lossless WebP compression instead of lossy compression",
)
@click.option(
    "--force",
    "-f",
    is_flag=True,
    help="Overwrite optimized WebP files that already exist",
)
@click.option("--verbose", "-v", is_flag=True, help="Show each converted file")
def optimize(
    folder: Path,
    output: Path | None,
    quality: int,
    lossless: bool,
    force: bool,
    verbose: bool,
) -> None:
    """Create optimized WebP copies of images in FOLDER.

    Source images are never modified. Existing metadata sidecars are copied
    beside their optimized WebP image with an updated content hash.
    """
    from riposte_cli.commands.annotate import get_images_in_folder

    images = get_images_in_folder(folder)
    if not images:
        console.print(f"[yellow]No supported images found in {folder}[/yellow]")
        return

    output_dir = output or folder / "webp"
    destinations = _get_destination_paths(images, output_dir)
    to_convert = [
        image for image, destination in destinations.items() if force or not destination.exists()
    ]
    skipped = len(images) - len(to_convert)

    if not to_convert:
        console.print("[green]All WebP outputs already exist.[/green]")
        return

    output_dir.mkdir(parents=True, exist_ok=True)
    converted = 0
    sidecars = 0
    failures = 0
    for image in to_convert:
        destination = destinations[image]
        try:
            metadata = _load_sidecar_metadata(image)
            convert_to_webp(
                image,
                destination,
                quality=quality,
                lossless=lossless,
            )
            if metadata is not None:
                write_optimized_sidecar(destination, metadata)
                sidecars += 1
            converted += 1
            if verbose:
                console.print(f"  [green]✓[/green] {image.name} → {destination.name}")
        except (OSError, ValueError, json.JSONDecodeError) as error:
            failures += 1
            console.print(f"  [red]✗[/red] {image.name}: {error}")

    console.print(
        f"[green]Optimized {converted} image(s) to WebP in {output_dir}[/green]"
    )
    if sidecars:
        console.print(f"[dim]Copied and updated {sidecars} sidecar file(s)[/dim]")
    if skipped:
        console.print(f"[dim]Skipped {skipped} existing WebP output(s)[/dim]")
    if failures:
        raise click.ClickException(f"Failed to optimize {failures} image(s)")
