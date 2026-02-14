"""Duplicate detection and cleanup command."""

import sys
from pathlib import Path

import click
from rich.console import Console
from rich.table import Table

from riposte_cli.commands.annotate import get_images_in_folder
from riposte_cli.hashing import (
    deduplicate_images,
    load_hash_manifest,
    save_hash_manifest,
)

console = Console()


def _delete_image_and_sidecar(image_path: Path, output_dir: Path) -> list[Path]:
    """Delete an image and its sidecar file if present.

    Args:
        image_path: Path to the image to delete.
        output_dir: Directory where sidecar files are stored.

    Returns:
        List of paths that were deleted.
    """
    deleted = []
    sidecar_path = output_dir / f"{image_path.name}.json"

    if image_path.exists():
        image_path.unlink()
        deleted.append(image_path)
    if sidecar_path.exists():
        sidecar_path.unlink()
        deleted.append(sidecar_path)

    return deleted


@click.command()
@click.argument(
    "folder",
    type=click.Path(exists=True, file_okay=False, path_type=Path),
)
@click.option(
    "--output",
    "-o",
    type=click.Path(path_type=Path),
    help="Directory where sidecar files are stored (default: same as input)",
)
@click.option(
    "--similarity-threshold",
    type=int,
    default=10,
    help="Max Hamming distance for near-duplicate detection (0-256, default: 10)",
)
@click.option(
    "--no-near",
    is_flag=True,
    help="Only remove exact duplicates (skip perceptual/near-duplicate detection)",
)
@click.option(
    "--dry-run",
    is_flag=True,
    help="Show duplicates that would be removed without deleting anything",
)
@click.option("--yes", "-y", is_flag=True, help="Skip confirmation prompt")
@click.option("--verbose", "-v", is_flag=True, help="Show detailed output")
def dedupe(
    folder: Path,
    output: Path | None,
    similarity_threshold: int,
    no_near: bool,
    dry_run: bool,
    yes: bool,
    verbose: bool,
) -> None:
    """Find and remove duplicate images in a folder.

    FOLDER is the path to a directory containing images to deduplicate.

    Detects exact duplicates (identical SHA-256 hash) and near-duplicates
    (similar perceptual hash). For each set of duplicates, the first image
    found (alphabetically) is kept and the rest are deleted along with
    their sidecar JSON files.

    \b
    Examples:
      meme-cli dedupe ./my-memes              # Interactive with confirmation
      meme-cli dedupe ./my-memes --dry-run    # Preview only
      meme-cli dedupe ./my-memes -y           # No confirmation prompt
      meme-cli dedupe ./my-memes --no-near    # Exact duplicates only
    """
    output_dir = output or folder
    detect_near = not no_near

    images = get_images_in_folder(folder)
    if not images:
        console.print(f"[yellow]No supported images found in {folder}[/yellow]")
        return

    console.print(f"[dim]Scanning {len(images)} image(s) for duplicates...[/dim]")

    manifest = load_hash_manifest(output_dir)
    result = deduplicate_images(
        images,
        manifest,
        output_dir,
        detect_near_duplicates=detect_near,
        similarity_threshold=similarity_threshold,
        verbose=verbose,
    )
    save_hash_manifest(output_dir, manifest)

    exact_dupes = result.exact_duplicates
    near_dupes = result.near_duplicates

    if not exact_dupes and not near_dupes:
        console.print("[green]✓ No duplicates found![/green]")
        return

    # Display results
    if exact_dupes:
        table = Table(title="Exact Duplicates", show_lines=True)
        table.add_column("Duplicate (will be removed)", style="red")
        table.add_column("Original (will be kept)", style="green")
        for dupe, original in exact_dupes:
            table.add_row(dupe.name, original.name)
        console.print(table)
        console.print()

    if near_dupes:
        table = Table(title="Near Duplicates", show_lines=True)
        table.add_column("Duplicate (will be removed)", style="red")
        table.add_column("Original (will be kept)", style="green")
        table.add_column("Distance", style="yellow", justify="right")
        for dupe, original, distance in near_dupes:
            table.add_row(dupe.name, original.name, str(distance))
        console.print(table)
        console.print()

    total = len(exact_dupes) + len(near_dupes)
    console.print(
        f"[bold]Found {total} duplicate(s)[/bold]: "
        f"{len(exact_dupes)} exact, {len(near_dupes)} near"
    )

    if dry_run:
        console.print("[dim]Dry run — no files were deleted[/dim]")
        return

    # Confirm before deleting
    if not yes:
        click.confirm(
            f"Delete {total} duplicate image(s) and their sidecars?",
            abort=True,
        )

    # Delete duplicates
    deleted_count = 0
    for dupe, _original in exact_dupes:
        deleted = _delete_image_and_sidecar(dupe, output_dir)
        deleted_count += len(deleted)
        if verbose:
            for p in deleted:
                console.print(f"  [dim]Deleted {p.name}[/dim]")

    for dupe, _original, _distance in near_dupes:
        deleted = _delete_image_and_sidecar(dupe, output_dir)
        deleted_count += len(deleted)
        if verbose:
            for p in deleted:
                console.print(f"  [dim]Deleted {p.name}[/dim]")

    # Clean up manifest entries for deleted files
    for dupe, _ in exact_dupes:
        manifest.pop(dupe.name, None)
    for dupe, _, _ in near_dupes:
        manifest.pop(dupe.name, None)
    save_hash_manifest(output_dir, manifest)

    console.print(
        f"\n[green]✓ Removed {total} duplicate(s) "
        f"({deleted_count} file(s) deleted)[/green]"
    )
