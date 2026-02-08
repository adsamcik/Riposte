"""Image hashing and deduplication utilities.

Provides SHA-256 content hashing and DCT-based perceptual hashing for
detecting exact and near-duplicate images.
"""

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import imagehash
from PIL import Image

HASH_MANIFEST_FILENAME = ".meme-hashes.json"


@dataclass
class DeduplicationResult:
    """Result of image deduplication analysis."""

    unique_images: list[Path]
    """Images that are unique (not duplicates)."""

    exact_duplicates: list[tuple[Path, Path]]
    """Pairs of (duplicate, original) for exact content matches."""

    near_duplicates: list[tuple[Path, Path, int]]
    """Triples of (duplicate, original, distance) for perceptual matches."""


def get_image_hash(image_path: Path) -> str:
    """Compute SHA-256 content hash of an image file.

    Args:
        image_path: Path to the image file.

    Returns:
        Hex-encoded SHA-256 hash string.
    """
    sha256 = hashlib.sha256()
    with open(image_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            sha256.update(chunk)
    return sha256.hexdigest()


def _compute_phash(image_path: Path, hash_size: int = 16) -> str | None:
    """Compute a DCT-based perceptual hash (pHash) of an image.

    Uses the imagehash library's DCT-based pHash algorithm, which is robust
    against rescaling, recompression, and minor edits.

    Args:
        image_path: Path to the image file.
        hash_size: Size of the hash grid (hash_size x hash_size).

    Returns:
        Hex-encoded perceptual hash, or None if hashing fails.
    """
    try:
        with Image.open(image_path) as img:
            phash = imagehash.phash(img, hash_size=hash_size)
            return str(phash)
    except Exception:
        return None


def _hamming_distance(hash1: str, hash2: str) -> int:
    """Compute Hamming distance between two hex hash strings.

    Args:
        hash1: First hex hash.
        hash2: Second hex hash.

    Returns:
        Number of differing bits.
    """
    if len(hash1) != len(hash2):
        return len(hash1) * 4  # Max distance if lengths differ

    val1 = int(hash1, 16)
    val2 = int(hash2, 16)
    xor = val1 ^ val2
    return bin(xor).count("1")


def load_hash_manifest(directory: Path) -> dict[str, Any]:
    """Load a hash manifest from a directory.

    The manifest maps image filenames to their content and perceptual hashes,
    allowing faster deduplication on subsequent runs.

    Args:
        directory: Directory containing the manifest file.

    Returns:
        Dictionary with filename keys and hash info values.
    """
    manifest_path = directory / HASH_MANIFEST_FILENAME
    if manifest_path.exists():
        try:
            with open(manifest_path, encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            return {}
    return {}


def save_hash_manifest(directory: Path, manifest: dict[str, Any]) -> None:
    """Save a hash manifest to a directory.

    Args:
        directory: Directory to save the manifest in.
        manifest: The manifest dictionary to save.
    """
    manifest_path = directory / HASH_MANIFEST_FILENAME
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)


def deduplicate_images(
    images: list[Path],
    manifest: dict[str, Any],
    output_dir: Path,
    *,
    detect_near_duplicates: bool = True,
    similarity_threshold: int = 10,
    verbose: bool = False,
) -> DeduplicationResult:
    """Deduplicate a list of images using content and perceptual hashing.

    For each image, computes a SHA-256 content hash (exact duplicate detection)
    and optionally a perceptual hash (near-duplicate detection). Results are
    cached in the manifest for faster subsequent runs.

    Args:
        images: List of image paths to deduplicate.
        manifest: Hash manifest dict (modified in-place with new entries).
        output_dir: Output directory (used as context, not modified).
        detect_near_duplicates: Whether to use perceptual hashing.
        similarity_threshold: Max Hamming distance to consider near-duplicate
            (0 = exact perceptual match, higher = more lenient).
        verbose: Print detailed dedup info.

    Returns:
        DeduplicationResult with unique images and detected duplicates.
    """
    unique: list[Path] = []
    exact_duplicates: list[tuple[Path, Path]] = []
    near_duplicates: list[tuple[Path, Path, int]] = []

    # Track seen hashes: content_hash -> first image path
    seen_content: dict[str, Path] = {}
    # Track perceptual hashes: phash -> first image path
    seen_phash: dict[str, Path] = {}

    for image_path in images:
        filename = image_path.name

        # Check manifest cache first
        if filename in manifest:
            cached = manifest[filename]
            content_hash = cached.get("content_hash", "")
            phash = cached.get("phash")
        else:
            content_hash = get_image_hash(image_path)
            phash = (
                _compute_phash(image_path)
                if detect_near_duplicates
                else None
            )
            manifest[filename] = {
                "content_hash": content_hash,
                "phash": phash,
            }

        # Check for exact duplicates
        if content_hash in seen_content:
            exact_duplicates.append((image_path, seen_content[content_hash]))
            continue

        # Check for near-duplicates via perceptual hash
        if detect_near_duplicates and phash is not None:
            is_near_dupe = False
            for existing_phash, existing_path in seen_phash.items():
                distance = _hamming_distance(phash, existing_phash)
                if distance <= similarity_threshold:
                    near_duplicates.append(
                        (image_path, existing_path, distance)
                    )
                    is_near_dupe = True
                    break
            if is_near_dupe:
                continue
            seen_phash[phash] = image_path

        seen_content[content_hash] = image_path
        unique.append(image_path)

    return DeduplicationResult(
        unique_images=unique,
        exact_duplicates=exact_duplicates,
        near_duplicates=near_duplicates,
    )
