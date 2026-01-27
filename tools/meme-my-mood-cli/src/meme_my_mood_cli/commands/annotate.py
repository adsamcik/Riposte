"""Image annotation command using GitHub Copilot SDK."""

import asyncio
import json
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

import click
import filetype
from rich.console import Console
from rich.progress import (
    Progress,
    SpinnerColumn,
    TextColumn,
    BarColumn,
    TaskProgressColumn,
)

from meme_my_mood_cli import __version__
from meme_my_mood_cli.copilot import (
    analyze_image_async,
    get_rate_limiter,
    reset_rate_limiter,
    CopilotError,
    CopilotNotAuthenticatedError,
    RateLimitError,
    ServerError,
)

console = Console()

# Supported image formats
SUPPORTED_EXTENSIONS = {
    ".jpg", ".jpeg", ".png", ".webp", ".gif",
    ".bmp", ".tiff", ".tif", ".heic", ".heif",
    ".avif", ".jxl",
}




def is_supported_image(path: Path) -> bool:
    """Check if a file is a supported image format.
    
    Args:
        path: Path to the file.
        
    Returns:
        True if the file is a supported image format.
    """
    # Check extension first
    if path.suffix.lower() not in SUPPORTED_EXTENSIONS:
        return False
    
    # Verify with filetype library
    try:
        kind = filetype.guess(str(path))
        if kind is None:
            return False
        return kind.mime.startswith("image/")
    except Exception:
        return False


def get_images_in_folder(folder: Path) -> list[Path]:
    """Get all supported images in a folder.
    
    Args:
        folder: Path to the folder.
        
    Returns:
        List of paths to supported images.
    """
    images = []
    for file in folder.iterdir():
        if file.is_file() and is_supported_image(file):
            images.append(file)
    return sorted(images)


def has_sidecar(image_path: Path, output_dir: Path | None = None) -> bool:
    """Check if an image already has a JSON sidecar file.
    
    Args:
        image_path: Path to the image file.
        output_dir: Optional output directory. If None, uses the image's directory.
        
    Returns:
        True if a sidecar file exists.
    """
    if output_dir is None:
        output_dir = image_path.parent
    
    sidecar_path = output_dir / f"{image_path.name}.json"
    return sidecar_path.exists()


def filter_images_by_mode(
    images: list[Path],
    output_dir: Path,
    *,
    force: bool = False,
    only_missing: bool = False,
) -> tuple[list[Path], int]:
    """Filter images based on processing mode.
    
    Args:
        images: List of all image paths.
        output_dir: Output directory for sidecar files.
        force: If True, process all images (regenerate all sidecars).
        only_missing: If True, only process images without existing sidecars.
        
    Returns:
        Tuple of (filtered images to process, count of skipped images).
    """
    if force:
        # Process all images, overwrite existing sidecars
        return images, 0
    
    if only_missing:
        # Only process images without sidecars
        to_process = []
        skipped = 0
        for img in images:
            if has_sidecar(img, output_dir):
                skipped += 1
            else:
                to_process.append(img)
        return to_process, skipped
    
    # Default behavior: same as only_missing for safety
    # (don't overwrite existing sidecars unless --force is used)
    to_process = []
    skipped = 0
    for img in images:
        if has_sidecar(img, output_dir):
            skipped += 1
        else:
            to_process.append(img)
    return to_process, skipped


def create_sidecar_metadata(
    emojis: list[str],
    title: str | None = None,
    description: str | None = None,
    text_content: str | None = None,
    tags: list[str] | None = None,
) -> dict:
    """Create a metadata sidecar dictionary.
    
    Args:
        emojis: List of emoji characters.
        title: Optional title.
        description: Optional description.
        text_content: Optional extracted text content.
        tags: Optional list of tags.
        
    Returns:
        Metadata dictionary matching the schema.
    """
    metadata = {
        "schemaVersion": "1.0",
        "emojis": emojis,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "appVersion": f"cli-{__version__}",
    }
    
    if title:
        metadata["title"] = title
    if description:
        metadata["description"] = description
    if text_content:
        metadata["textContent"] = text_content
    if tags:
        metadata["tags"] = tags
    
    return metadata


def write_sidecar(image_path: Path, metadata: dict, output_dir: Path | None = None) -> Path:
    """Write a JSON sidecar file for an image.
    
    Args:
        image_path: Path to the image file.
        metadata: Metadata dictionary.
        output_dir: Optional output directory. If None, uses the image's directory.
        
    Returns:
        Path to the created sidecar file.
    """
    if output_dir is None:
        output_dir = image_path.parent
    
    sidecar_path = output_dir / f"{image_path.name}.json"
    
    with open(sidecar_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2, ensure_ascii=False)
    
    return sidecar_path


@click.command()
@click.argument(
    "folder",
    type=click.Path(exists=True, file_okay=False, path_type=Path),
)
@click.option(
    "--zip", "create_zip",
    is_flag=True,
    help="Bundle images and sidecars into a .meme.zip file",
)
@click.option(
    "--output", "-o",
    type=click.Path(path_type=Path),
    help="Output directory for sidecar files",
)
@click.option(
    "--model", "-m",
    default="gpt-4.1",
    help="Model to use for analysis (default: gpt-4.1)",
)
@click.option(
    "--force", "-f",
    is_flag=True,
    help="Force regeneration of all sidecars, overwriting existing ones",
)
@click.option(
    "--continue", "continue_missing",
    is_flag=True,
    help="Only process images that don't have JSON sidecars yet",
)
@click.option(
    "--add-new",
    is_flag=True,
    help="Alias for --continue: only add new images without metadata",
)
@click.option("--dry-run", is_flag=True, help="Show what would be processed")
@click.option("--verbose", "-v", is_flag=True, help="Show detailed progress")
def annotate(
    folder: Path,
    create_zip: bool,
    output: Path | None,
    model: str,
    force: bool,
    continue_missing: bool,
    add_new: bool,
    dry_run: bool,
    verbose: bool,
) -> None:
    """Annotate images in a folder with AI-generated metadata.
    
    FOLDER is the path to a directory containing images to annotate.
    
    Uses GitHub Copilot SDK (requires Copilot CLI installed).
    
    \b
    Processing Modes:
      Default:     Skip images that already have sidecar files
      --force:     Regenerate all sidecars (overwrites existing)
      --continue:  Same as default, explicitly skip existing
      --add-new:   Alias for --continue
    """
    # Validate mutually exclusive options
    if force and (continue_missing or add_new):
        console.print(
            "[red]Error: --force cannot be used with "
            "--continue or --add-new[/red]"
        )
        sys.exit(1)
    
    # --add-new is an alias for --continue
    only_missing = continue_missing or add_new or not force
    
    # Set up output directory early for filtering
    output_dir = output or folder
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Find images
    all_images = get_images_in_folder(folder)
    if not all_images:
        console.print(
            f"[yellow]No supported images found in {folder}[/yellow]"
        )
        console.print(
            f"Supported formats: {', '.join(sorted(SUPPORTED_EXTENSIONS))}"
        )
        return
    
    # Filter images based on processing mode
    images, skipped = filter_images_by_mode(
        all_images, output_dir, force=force, only_missing=only_missing
    )
    
    # Show mode and counts
    if force:
        mode_desc = "[bold red]Force mode[/bold red] - regenerating all sidecars"
    else:
        mode_desc = "[bold]Incremental mode[/bold] - skipping existing sidecars"
    
    console.print(f"\n{mode_desc}")
    console.print(f"Total images: {len(all_images)}")
    if skipped > 0:
        console.print(f"[dim]Skipping {skipped} image(s) with existing sidecars[/dim]")
    console.print(f"[bold]Processing {len(images)} image(s)[/bold]\n")
    
    if not images:
        console.print("[green]✓ All images already have sidecars![/green]")
        return
    
    if dry_run:
        console.print("[dim]Dry run - no files will be created[/dim]\n")
        for img in images:
            sidecar_exists = has_sidecar(img, output_dir)
            status = "[yellow]overwrite[/yellow]" if sidecar_exists else "[green]new[/green]"
            console.print(f"  • {img.name} ({status})")
        return
    
    # Reset rate limiter for this batch
    reset_rate_limiter()
    
    # Process images with async
    async def process_images():
        processed = []
        errors = []
        rate_limiter = get_rate_limiter()
        
        with Progress(
            SpinnerColumn(),
            TextColumn("[progress.description]{task.description}"),
            BarColumn(),
            TaskProgressColumn(),
            console=console,
        ) as progress:
            task = progress.add_task("Annotating images...", total=len(images))
            
            for image_path in images:
                progress.update(
                    task, description=f"Processing {image_path.name}..."
                )
                
                # Retry loop for rate limiting
                max_retries = 5
                for attempt in range(max_retries):
                    try:
                        # Analyze image with Copilot SDK
                        result = await analyze_image_async(
                            image_path,
                            model=model,
                            verbose=verbose,
                        )
                        
                        # Create sidecar metadata
                        metadata = create_sidecar_metadata(
                            emojis=result["emojis"],
                            title=result.get("title"),
                            description=result.get("description"),
                            text_content=result.get("textContent"),
                            tags=result.get("tags"),
                        )
                        
                        # Write sidecar file
                        sidecar_path = write_sidecar(
                            image_path, metadata, output_dir
                        )
                        processed.append((image_path, sidecar_path))
                        
                        if verbose:
                            emojis_str = " ".join(result.get("emojis", []))
                            console.print(
                                f"  [green]✓[/green] "
                                f"{image_path.name} → {emojis_str}"
                            )
                        break  # Success, exit retry loop
                        
                    except CopilotNotAuthenticatedError as e:
                        console.print(f"\n[red]Error: {e}[/red]")
                        console.print(
                            "Install GitHub Copilot CLI: "
                            "https://github.com/github/copilot-cli"
                        )
                        sys.exit(1)
                    except RateLimitError as e:
                        wait_time = e.retry_after or rate_limiter.record_failure()
                        if rate_limiter.should_give_up():
                            console.print(
                                f"\n[red]Too many rate limits. "
                                f"Skipping {image_path.name}[/red]"
                            )
                            errors.append((image_path, str(e)))
                            break
                        console.print(
                            f"\n[yellow]Rate limit hit. "
                            f"Waiting {wait_time:.1f}s... "
                            f"(attempt {attempt + 1}/{max_retries})[/yellow]"
                        )
                        await asyncio.sleep(wait_time)
                    except ServerError as e:
                        # Server errors (500/502/504) are transient, retry
                        wait_time = rate_limiter.record_failure()
                        if rate_limiter.should_give_up():
                            console.print(
                                f"\n[red]Too many server errors. "
                                f"Skipping {image_path.name}[/red]"
                            )
                            errors.append((image_path, str(e)))
                            break
                        console.print(
                            f"\n[yellow]Server error. "
                            f"Waiting {wait_time:.1f}s... "
                            f"(attempt {attempt + 1}/{max_retries})[/yellow]"
                        )
                        await asyncio.sleep(wait_time)
                    except CopilotError as e:
                        errors.append((image_path, str(e)))
                        console.print(
                            f"  [red]✗[/red] {image_path.name}: {e}"
                        )
                        break
                    except Exception as e:
                        errors.append((image_path, str(e)))
                        console.print(
                            f"  [red]✗[/red] {image_path.name}: {e}"
                        )
                        break
                
                progress.advance(task)
        
        return processed, errors
    
    # Run async processing
    processed, errors = asyncio.run(process_images())
    
    # Summary
    console.print()
    if processed:
        console.print(
            f"[green]✓ Successfully annotated "
            f"{len(processed)} image(s)[/green]"
        )
    if errors:
        console.print(
            f"[red]✗ Failed to annotate {len(errors)} image(s)[/red]"
        )
    
    # Create ZIP bundle if requested
    if create_zip and processed:
        zip_path = folder.parent / f"{folder.name}.meme.zip"
        
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
            for image_path, sidecar_path in processed:
                # Add image
                zf.write(image_path, image_path.name)
                # Add sidecar
                zf.write(sidecar_path, sidecar_path.name)
        
        console.print(f"\n[bold blue]📦 Created bundle: {zip_path}[/bold blue]")
        console.print(
            "[dim]Transfer this file to your Android device "
            "and open with Meme My Mood[/dim]"
        )
