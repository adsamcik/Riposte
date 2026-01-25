"""Image annotation command using GitHub Models API."""

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
from meme_my_mood_cli.config import get_access_token, get_default_model
from meme_my_mood_cli.copilot import (
    analyze_image,
    CopilotError,
    CopilotNotAuthenticatedError,
    RateLimitError,
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
    default="openai/gpt-4.1",
    help="Model to use for analysis (default: openai/gpt-4.1)",
)
@click.option("--dry-run", is_flag=True, help="Show what would be processed")
@click.option("--verbose", "-v", is_flag=True, help="Show detailed progress")
def annotate(
    folder: Path,
    create_zip: bool,
    output: Path | None,
    model: str,
    dry_run: bool,
    verbose: bool,
) -> None:
    """Annotate images in a folder with AI-generated metadata.
    
    FOLDER is the path to a directory containing images to annotate.
    
    Requires a GitHub PAT with 'models' scope.
    Run 'meme-cli auth login' to authenticate.
    """
    # Check for access token
    access_token = get_access_token()
    if not access_token:
        console.print(
            "\n[red]Error: Not authenticated.[/red]"
        )
        console.print(
            "Run [bold]meme-cli auth login[/bold] to authenticate,\n"
            "or set the GITHUB_TOKEN environment variable."
        )
        sys.exit(1)
    
    # Find images
    images = get_images_in_folder(folder)
    if not images:
        console.print(f"[yellow]No supported images found in {folder}[/yellow]")
        console.print(
            f"Supported formats: {', '.join(sorted(SUPPORTED_EXTENSIONS))}"
        )
        return
    
    console.print(f"\n[bold]Found {len(images)} image(s) to annotate[/bold]\n")
    
    if dry_run:
        console.print("[dim]Dry run - no files will be created[/dim]\n")
        for img in images:
            console.print(f"  • {img.name}")
        return
    
    # Set up output directory
    output_dir = output or folder
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Process images
    processed = []
    errors = []
    
    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        BarColumn(),
        TaskProgressColumn(),
        console=console,
    ) as progress:
        task = progress.add_task("Annotating images...", total=len(images))
        
        for image_path in images:
            progress.update(task, description=f"Processing {image_path.name}...")
            
            try:
                # Analyze image with GitHub Models API
                result = analyze_image(
                    image_path,
                    access_token=access_token,
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
                sidecar_path = write_sidecar(image_path, metadata, output_dir)
                processed.append((image_path, sidecar_path))
                
                if verbose:
                    emojis_str = " ".join(result.get("emojis", []))
                    console.print(
                        f"  [green]✓[/green] {image_path.name} → {emojis_str}"
                    )
                
            except CopilotNotAuthenticatedError as e:
                console.print(
                    f"\n[red]Error: {e}[/red]"
                )
                console.print(
                    "Run [bold]meme-cli auth login[/bold] to authenticate."
                )
                sys.exit(1)
            except RateLimitError as e:
                console.print(
                    "\n[yellow]Rate limit reached. Waiting before retry...[/yellow]"
                )
                errors.append((image_path, str(e)))
            except CopilotError as e:
                errors.append((image_path, str(e)))
                console.print(f"  [red]✗[/red] {image_path.name}: {e}")
            except Exception as e:
                errors.append((image_path, str(e)))
                console.print(f"  [red]✗[/red] {image_path.name}: {e}")
            
            progress.advance(task)
    
    # Summary
    console.print()
    if processed:
        console.print(
            f"[green]✓ Successfully annotated {len(processed)} image(s)[/green]"
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
