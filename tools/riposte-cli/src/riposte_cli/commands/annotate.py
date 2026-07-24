"""Image annotation command using GitHub Copilot SDK."""

import asyncio
import hashlib
import json
import sys
import tempfile
import time
import zipfile
import zlib
from dataclasses import dataclass
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
    TimeElapsedColumn,
    TimeRemainingColumn,
)

from riposte_cli import __version__
from riposte_cli.commands.optimize import optimize_image_to_bytes
from riposte_cli.copilot import (
    analyze_image_async,
    create_concurrency_limiter,
    get_rate_limiter,
    reset_rate_limiter,
    ConcurrencyLimiter,
    CopilotError,
    CopilotNotAuthenticatedError,
    RateLimitError,
    ServerError,
)
from copilot import CopilotClient
from riposte_cli.hashing import (
    deduplicate_images,
    get_image_hash,
    load_hash_manifest,
    save_hash_manifest,
)

console = Console()
LOSSLESS_WEBP_QUALITY = 100
VISUALLY_LOSSLESS_WEBP_QUALITY = 95
MIN_LOSSY_WEBP_SAVINGS_RATIO = 0.05

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
    tags: list[str] | None = None,
    search_phrases: list[str] | None = None,
    primary_language: str | None = None,
    localizations: dict[str, dict] | None = None,
    content_hash: str | None = None,
    based_on: str | None = None,
) -> dict:
    """Create a metadata sidecar dictionary.
    
    Args:
        emojis: List of emoji characters.
        title: Optional title.
        description: Optional description.
        tags: Optional list of tags.
        search_phrases: Optional list of natural language search phrases.
        primary_language: BCP 47 language code of the primary content.
        localizations: Optional dict of language code -> localized content.
        content_hash: SHA-256 hash of the image content for deduplication.
        
    Returns:
        Metadata dictionary matching the schema.
    """
    metadata = {
        "schemaVersion": "1.3",
        "emojis": emojis,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "appVersion": f"cli-{__version__}",
        "cliVersion": __version__,
    }
    
    if title:
        metadata["title"] = title
    if description:
        metadata["description"] = description
    if tags:
        metadata["tags"] = tags
    if search_phrases:
        metadata["searchPhrases"] = search_phrases
    if primary_language:
        metadata["primaryLanguage"] = primary_language
    if localizations:
        metadata["localizations"] = localizations
    if content_hash:
        metadata["contentHash"] = content_hash
    if based_on:
        metadata["basedOn"] = based_on
    
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


@dataclass(frozen=True)
class BundleImage:
    """A selected image representation and its optimized ZIP storage details."""

    content: bytes
    suffix: str
    compression_type: int
    archive_size: int


@dataclass(frozen=True)
class BundleResult:
    """Summary of image payload sizes written to a meme bundle."""

    image_count: int
    source_archive_size: int
    optimized_archive_size: int


def _get_zip_storage(content: bytes) -> tuple[int, int]:
    """Choose the smaller ZIP storage mode for already-compressed media."""
    compressor = zlib.compressobj(level=9, method=zlib.DEFLATED, wbits=-15)
    compressed_content = compressor.compress(content) + compressor.flush()
    if len(compressed_content) < len(content):
        return zipfile.ZIP_DEFLATED, len(compressed_content)
    return zipfile.ZIP_STORED, len(content)


def _create_bundle_image(content: bytes, suffix: str) -> BundleImage:
    """Describe the smallest ZIP representation of an image payload."""
    compression_type, archive_size = _get_zip_storage(content)
    return BundleImage(
        content=content,
        suffix=suffix,
        compression_type=compression_type,
        archive_size=archive_size,
    )


def select_bundle_image(image_path: Path) -> tuple[BundleImage, BundleImage]:
    """Select the smallest fidelity-safe image encoding for a ZIP bundle.

    Lossless WebP is selected whenever it is smaller. Quality-95 WebP is
    selected only when it cuts the estimated archive size by at least 5%.
    Otherwise the source encoding is retained.
    """
    source = _create_bundle_image(image_path.read_bytes(), image_path.suffix)
    lossless_webp = _create_bundle_image(
        optimize_image_to_bytes(
            image_path,
            quality=LOSSLESS_WEBP_QUALITY,
            lossless=True,
        ),
        ".webp",
    )
    visually_lossless_webp = _create_bundle_image(
        optimize_image_to_bytes(
            image_path,
            quality=VISUALLY_LOSSLESS_WEBP_QUALITY,
            lossless=False,
        ),
        ".webp",
    )
    candidates = [source]
    if lossless_webp.archive_size < source.archive_size:
        candidates.append(lossless_webp)
    if visually_lossless_webp.archive_size <= source.archive_size * (
        1 - MIN_LOSSY_WEBP_SAVINGS_RATIO
    ):
        candidates.append(visually_lossless_webp)
    return source, min(candidates, key=lambda candidate: candidate.archive_size)


def _get_bundle_image_name(
    image_path: Path,
    suffix: str,
    used_names: set[str],
) -> str:
    """Create a collision-safe filename for a bundle entry."""
    name = f"{image_path.stem}{suffix}"
    if name.casefold() in used_names:
        name = f"{image_path.name}{suffix}"
    duplicate_number = 2
    while name.casefold() in used_names:
        name = f"{image_path.name}.{duplicate_number}{suffix}"
        duplicate_number += 1
    used_names.add(name.casefold())
    return name


def create_optimized_bundle(
    zip_path: Path,
    images: list[Path],
    sidecar_dir: Path,
) -> BundleResult:
    """Write an atomic bundle using the smallest fidelity-safe image formats."""
    temporary_file = tempfile.NamedTemporaryFile(
        dir=zip_path.parent,
        prefix=f".{zip_path.stem}-",
        suffix=".zip",
        delete=False,
    )
    temporary_path = Path(temporary_file.name)
    temporary_file.close()
    bundled = 0
    source_archive_size = 0
    optimized_archive_size = 0
    used_names: set[str] = set()

    try:
        with zipfile.ZipFile(
            temporary_path,
            "w",
            zipfile.ZIP_DEFLATED,
            compresslevel=9,
        ) as bundle:
            for image_path in images:
                sidecar_path = sidecar_dir / f"{image_path.name}.json"
                if not sidecar_path.exists():
                    continue

                try:
                    with open(sidecar_path, encoding="utf-8") as sidecar_file:
                        metadata = json.load(sidecar_file)
                    if not isinstance(metadata, dict):
                        raise ValueError("sidecar must contain a JSON object")
                    source_image, selected_image = select_bundle_image(image_path)
                except (OSError, ValueError, json.JSONDecodeError) as error:
                    temporary_path.unlink(missing_ok=True)
                    raise click.ClickException(
                        f"Could not optimize {image_path.name} for the bundle: {error}"
                    ) from error

                bundle_name = _get_bundle_image_name(
                    image_path,
                    selected_image.suffix,
                    used_names,
                )
                metadata["contentHash"] = hashlib.sha256(selected_image.content).hexdigest()
                metadata_content = json.dumps(
                    metadata,
                    indent=2,
                    ensure_ascii=False,
                ).encode("utf-8")
                bundle.writestr(
                    bundle_name,
                    selected_image.content,
                    compress_type=selected_image.compression_type,
                )
                bundle.writestr(
                    f"{bundle_name}.json",
                    metadata_content,
                    compress_type=zipfile.ZIP_DEFLATED,
                )
                bundled += 1
                source_archive_size += source_image.archive_size
                optimized_archive_size += selected_image.archive_size
        temporary_path.replace(zip_path)
    except OSError:
        temporary_path.unlink(missing_ok=True)
        raise

    return BundleResult(
        image_count=bundled,
        source_archive_size=source_archive_size,
        optimized_archive_size=optimized_archive_size,
    )


@click.command()
@click.argument(
    "folder",
    type=click.Path(exists=True, file_okay=False, path_type=Path),
)
@click.option(
    "--zip", "create_zip",
    is_flag=True,
    help="Bundle optimized WebP images and sidecars into a .meme.zip file",
)
@click.option(
    "--output", "-o",
    type=click.Path(path_type=Path),
    help="Output directory for sidecar files",
)
@click.option(
    "--model", "-m",
    default="gpt-5-mini",
    help="Model to use for analysis (default: gpt-5-mini)",
)
@click.option(
    "--languages", "-l",
    default="en",
    help="Comma-separated BCP 47 language codes (e.g., 'en,cs,de'). "
         "First language is primary. Default: en",
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
@click.option(
    "--no-dedup",
    is_flag=True,
    help="Disable duplicate detection (skip hash-based deduplication)",
)
@click.option(
    "--similarity-threshold",
    type=int,
    default=10,
    help="Max Hamming distance for near-duplicate detection (0-256, default: 10)",
)
@click.option("--dry-run", is_flag=True, help="Show what would be processed")
@click.option("--verbose", "-v", is_flag=True, help="Show detailed progress")
@click.option(
    "--concurrency", "-j",
    type=click.IntRange(1, 10),
    default=4,
    help="Max parallel API requests (1-10, default: 4)",
)
def annotate(
    folder: Path,
    create_zip: bool,
    output: Path | None,
    model: str,
    languages: str,
    force: bool,
    continue_missing: bool,
    add_new: bool,
    no_dedup: bool,
    similarity_threshold: int,
    dry_run: bool,
    verbose: bool,
    concurrency: int,
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
    
    \b
    Deduplication:
      By default, images are hashed (SHA-256 + perceptual hash) and
      duplicates are skipped. Hash manifest is saved to .meme-hashes.json
      for faster subsequent runs.
      
      Use --no-dedup to disable deduplication.
      Use --similarity-threshold to adjust near-duplicate sensitivity.
    
    \b
    Multilingual Support:
      Use --languages to specify output languages (comma-separated).
      First language is the primary language; additional languages
      are stored in the "localizations" field.
      
      Examples:
        --languages en           (English only, default)
        --languages cs           (Czech only)
        --languages en,cs,de     (English primary, with Czech and German)
    """
    # Parse languages
    language_list = [lang.strip() for lang in languages.split(",") if lang.strip()]
    if not language_list:
        language_list = ["en"]
    
    primary_language = language_list[0]
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
    
    # Load hash manifest and perform deduplication
    manifest = load_hash_manifest(output_dir)
    dedup_result = None
    exact_dupes = 0
    near_dupes = 0
    
    if not no_dedup and images:
        console.print("[dim]Checking for duplicates...[/dim]")
        dedup_result = deduplicate_images(
            images,
            manifest,
            output_dir,
            detect_near_duplicates=True,
            similarity_threshold=similarity_threshold,
            verbose=verbose,
        )
        
        exact_dupes = len(dedup_result.exact_duplicates)
        near_dupes = len(dedup_result.near_duplicates)
        images = dedup_result.unique_images
        
        # Save manifest with new hashes
        save_hash_manifest(output_dir, manifest)
    
    # Show mode and counts
    if force:
        mode_desc = (
            "[bold red]Force mode[/bold red] - regenerating all sidecars"
        )
    else:
        mode_desc = (
            "[bold]Incremental mode[/bold] - skipping existing sidecars"
        )
    
    console.print(f"\n{mode_desc}")
    console.print(f"Total images: {len(all_images)}")
    
    # Show language configuration
    if len(language_list) == 1:
        console.print(f"Language: {primary_language}")
    else:
        additional = ", ".join(language_list[1:])
        console.print(
            f"Languages: {primary_language} (primary), {additional}"
        )
    
    if skipped > 0:
        remaining = len(images)
        total = remaining + skipped
        console.print(
            f"[dim]Skipping {skipped} image(s) with existing sidecars[/dim]"
        )
        if remaining > 0:
            console.print(
                f"[dim]Resuming: {remaining} remaining of {total} total[/dim]"
            )
    if exact_dupes > 0:
        console.print(
            f"[dim]Skipping {exact_dupes} exact duplicate(s) "
            "(identical content hash)[/dim]"
        )
    if near_dupes > 0:
        console.print(
            f"[dim]Skipping {near_dupes} near-duplicate(s) "
            "(similar perceptual hash)[/dim]"
        )
    console.print(f"[bold]Processing {len(images)} image(s)[/bold]")
    if images:
        console.print(f"[dim]Concurrency: {concurrency} parallel workers[/dim]")
    console.print()
    
    if not images:
        console.print("[green]✓ All images already have sidecars![/green]")
        if not create_zip:
            return
    
    if dry_run:
        console.print("[dim]Dry run - no files will be created[/dim]\n")
        for img in images:
            sidecar_exists = has_sidecar(img, output_dir)
            status = "[yellow]overwrite[/yellow]" if sidecar_exists else "[green]new[/green]"
            console.print(f"  • {img.name} ({status})")
        return
    
    processed = []
    errors = []
    
    if images:
        # Create concurrency limiter for this batch
        limiter = create_concurrency_limiter(max_concurrency=concurrency)

        async def process_single_image(
            image_path: Path,
            shared_client: CopilotClient,
            progress: Progress,
            task_id: int,
        ) -> tuple[Path, Path] | tuple[Path, str] | None:
            """Process a single image with retries, respecting the concurrency limiter."""
            max_retries = 5
            img_start = time.monotonic()
            
            for attempt in range(max_retries):
                acquired = False
                try:
                    await limiter.acquire()
                    acquired = True
                    try:
                        result = await analyze_image_async(
                            image_path,
                            model=model,
                            verbose=verbose,
                            languages=language_list,
                            client=shared_client,
                        )
                    finally:
                        await limiter.release()
                        acquired = False
                    
                    content_hash = get_image_hash(image_path)
                    metadata = create_sidecar_metadata(
                        emojis=result["emojis"],
                        title=result.get("title"),
                        description=result.get("description"),
                        tags=result.get("tags"),
                        search_phrases=result.get("searchPhrases"),
                        primary_language=primary_language,
                        localizations=result.get("localizations"),
                        content_hash=content_hash,
                        based_on=result.get("basedOn"),
                    )
                    sidecar_path = write_sidecar(image_path, metadata, output_dir)
                    
                    await limiter.record_success()
                    img_elapsed = time.monotonic() - img_start
                    emojis_str = " ".join(result.get("emojis", []))
                    console.print(
                        f"  [green]✓[/green] "
                        f"{image_path.name} → {emojis_str} "
                        f"[dim]({img_elapsed:.1f}s)[/dim]"
                    )
                    progress.advance(task_id)
                    return (image_path, sidecar_path)

                except CopilotNotAuthenticatedError as e:
                    console.print(f"\n[red]Error: {e}[/red]")
                    console.print(
                        "Install GitHub Copilot CLI: "
                        "https://github.com/github/copilot-cli"
                    )
                    sys.exit(1)
                except asyncio.CancelledError:
                    if acquired:
                        await limiter.release()
                    raise
                except RateLimitError as e:
                    wait_time = await limiter.record_rate_limit(
                        retry_after=e.retry_after,
                    )
                    if attempt + 1 >= max_retries:
                        console.print(
                            f"\n[red]Too many rate limits. "
                            f"Skipping {image_path.name}[/red]"
                        )
                        progress.advance(task_id)
                        return (image_path, str(e))
                    console.print(
                        f"\n[yellow]Rate limit hit — all workers paused "
                        f"{wait_time:.1f}s "
                        f"(attempt {attempt + 1}/{max_retries}, "
                        f"concurrency → {limiter.current_concurrency})[/yellow]"
                    )
                except ServerError as e:
                    wait_time = await limiter.record_server_error()
                    if attempt + 1 >= max_retries:
                        console.print(
                            f"\n[red]Too many server errors. "
                            f"Skipping {image_path.name}[/red]"
                        )
                        progress.advance(task_id)
                        return (image_path, str(e))
                    console.print(
                        f"\n[yellow]Server error. "
                        f"Waiting {wait_time:.1f}s... "
                        f"(attempt {attempt + 1}/{max_retries})[/yellow]"
                    )
                    await asyncio.sleep(wait_time)
                except CopilotError as e:
                    img_elapsed = time.monotonic() - img_start
                    console.print(
                        f"  [red]✗[/red] {image_path.name}: "
                        f"{e} [dim]({img_elapsed:.1f}s)[/dim]"
                    )
                    progress.advance(task_id)
                    return (image_path, str(e))
                except Exception as e:
                    img_elapsed = time.monotonic() - img_start
                    console.print(
                        f"  [red]✗[/red] {image_path.name}: "
                        f"{e} [dim]({img_elapsed:.1f}s)[/dim]"
                    )
                    progress.advance(task_id)
                    return (image_path, str(e))
            
            # Exhausted retries
            progress.advance(task_id)
            return (image_path, "Exhausted all retries")

        async def process_images():
            shared_client = CopilotClient()
            interrupted = False
            try:
                await shared_client.start()
                
                with Progress(
                    SpinnerColumn(),
                    TextColumn("[progress.description]{task.description}"),
                    BarColumn(),
                    TaskProgressColumn(),
                    TimeElapsedColumn(),
                    TimeRemainingColumn(),
                    console=console,
                ) as progress:
                    task_id = progress.add_task(
                        f"Annotating ({concurrency} workers)...",
                        total=len(images),
                    )
                    
                    pending_tasks = [
                        asyncio.create_task(
                            process_single_image(img, shared_client, progress, task_id),
                            name=img.name,
                        )
                        for img in images
                    ]
                    
                    try:
                        results = await asyncio.gather(*pending_tasks)
                    except (KeyboardInterrupt, asyncio.CancelledError):
                        interrupted = True
                        console.print(
                            "\n[yellow]Interrupted — finishing in-flight "
                            "requests...[/yellow]"
                        )
                        # Cancel tasks that haven't started yet
                        for t in pending_tasks:
                            if not t.done():
                                t.cancel()
                        # Wait for all tasks to finish (cancelled or not)
                        results = await asyncio.gather(
                            *pending_tasks, return_exceptions=True
                        )
                
                _processed = []
                _errors = []
                for result in results:
                    if result is None or isinstance(result, BaseException):
                        continue
                    path, second = result
                    if isinstance(second, Path):
                        _processed.append((path, second))
                    else:
                        _errors.append((path, second))
                return _processed, _errors, interrupted
            finally:
                await shared_client.stop()
        
        # Run async processing
        was_interrupted = False
        try:
            processed, errors, was_interrupted = asyncio.run(process_images())
        except KeyboardInterrupt:
            was_interrupted = True
            console.print(
                "\n[yellow]Interrupted before processing started[/yellow]"
            )
        
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
        if was_interrupted:
            skipped_count = len(images) - len(processed) - len(errors)
            if skipped_count > 0:
                console.print(
                    f"[yellow]⚠ {skipped_count} image(s) not started "
                    f"(interrupted)[/yellow]"
                )
            console.print(
                "[dim]Run again with --continue to process "
                "remaining images[/dim]"
            )
    
    # Create a smallest-format ZIP bundle from every image with a sidecar.
    if create_zip:
        zip_path = folder.parent / f"{folder.name}.meme.zip"
        bundle_result = create_optimized_bundle(zip_path, all_images, output_dir)
        
        if bundle_result.image_count > 0:
            console.print(
                f"\n[bold blue]📦 Created bundle: {zip_path}[/bold blue]"
            )
            saved_bytes = (
                bundle_result.source_archive_size - bundle_result.optimized_archive_size
            )
            saved_percent = (
                saved_bytes / bundle_result.source_archive_size * 100
                if bundle_result.source_archive_size
                else 0
            )
            console.print(
                f"[dim]{bundle_result.image_count} image(s) bundled; image payload "
                f"{bundle_result.source_archive_size / 1024:.1f} KiB → "
                f"{bundle_result.optimized_archive_size / 1024:.1f} KiB "
                f"({saved_percent:.1f}% smaller). "
                "Transfer this file to your Android device "
                "and open with Riposte[/dim]"
            )
        else:
            console.print(
                "\n[yellow]No images with sidecars to bundle.[/yellow]"
            )
