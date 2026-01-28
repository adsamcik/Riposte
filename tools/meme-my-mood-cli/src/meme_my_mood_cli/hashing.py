"""Image hashing for deduplication.

Provides content-based hashing using SHA-256 for exact duplicates
and perceptual hashing (pHash) for near-duplicate detection.
"""

import hashlib
import json
from collections.abc import Iterable
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import TypedDict

from PIL import Image


class HashEntry(TypedDict):
    """Hash manifest entry for a single image."""
    
    sha256: str
    phash: str
    filename: str
    size: int
    created_at: str


class HashManifest(TypedDict):
    """Hash manifest file structure."""
    
    version: str
    created_at: str
    updated_at: str
    hashes: dict[str, HashEntry]


# Hash manifest filename
HASH_MANIFEST_FILE = ".meme-hashes.json"


def compute_sha256(file_path: Path) -> str:
    """Compute SHA-256 hash of a file.
    
    Args:
        file_path: Path to the file.
        
    Returns:
        Hex-encoded SHA-256 hash string.
    """
    sha256 = hashlib.sha256()
    with open(file_path, "rb") as f:
        # Read in 64KB chunks for memory efficiency
        for chunk in iter(lambda: f.read(65536), b""):
            sha256.update(chunk)
    return sha256.hexdigest()


def compute_phash(file_path: Path, hash_size: int = 16) -> str:
    """Compute perceptual hash (pHash) of an image.
    
    Uses a simplified DCT-based perceptual hash algorithm.
    Similar images will have similar hashes.
    
    Args:
        file_path: Path to the image file.
        hash_size: Size of the hash grid (hash_size x hash_size pixels).
                   Larger = more precision, smaller = more tolerant.
                   Default 16 produces a 256-bit hash.
        
    Returns:
        Hex-encoded perceptual hash string.
    """
    try:
        with Image.open(file_path) as img:
            # Convert to grayscale
            img = img.convert("L")
            
            # Resize to hash_size+1 for difference calculation
            # Using LANCZOS for high-quality downsampling
            img = img.resize((hash_size + 1, hash_size), Image.Resampling.LANCZOS)
            
            # Convert to list of pixel values
            pixels = list(img.getdata())
            
            # Compute difference hash (dHash variant)
            # Compare adjacent pixels horizontally
            diff = []
            for row in range(hash_size):
                row_offset = row * (hash_size + 1)
                for col in range(hash_size):
                    left = pixels[row_offset + col]
                    right = pixels[row_offset + col + 1]
                    diff.append(1 if left > right else 0)
            
            # Convert bit array to hex string
            # Process in groups of 8 bits
            hash_bytes = []
            for i in range(0, len(diff), 8):
                byte_val = 0
                for bit in diff[i:i+8]:
                    byte_val = (byte_val << 1) | bit
                hash_bytes.append(byte_val)
            
            return bytes(hash_bytes).hex()
            
    except Exception as e:
        # If image can't be processed, return empty hash
        # The SHA-256 will still be used for exact matching
        return ""


def compute_hashes(file_path: Path) -> tuple[str, str]:
    """Compute both SHA-256 and perceptual hash for a file.
    
    Args:
        file_path: Path to the image file.
        
    Returns:
        Tuple of (sha256_hash, perceptual_hash).
    """
    sha256 = compute_sha256(file_path)
    phash = compute_phash(file_path)
    return sha256, phash


def hamming_distance(hash1: str, hash2: str) -> int:
    """Compute Hamming distance between two hex hashes.
    
    Args:
        hash1: First hex hash string.
        hash2: Second hex hash string.
        
    Returns:
        Number of differing bits.
    """
    if not hash1 or not hash2:
        return -1  # Invalid comparison
    
    if len(hash1) != len(hash2):
        return -1  # Can't compare different length hashes
    
    # Convert hex to bytes and count differing bits
    b1 = bytes.fromhex(hash1)
    b2 = bytes.fromhex(hash2)
    
    distance = 0
    for byte1, byte2 in zip(b1, b2):
        xor = byte1 ^ byte2
        distance += bin(xor).count("1")
    
    return distance


def are_similar_images(
    phash1: str,
    phash2: str,
    threshold: int = 10,
) -> bool:
    """Check if two images are perceptually similar.
    
    Args:
        phash1: First perceptual hash.
        phash2: Second perceptual hash.
        threshold: Maximum Hamming distance to consider similar.
                   Lower = stricter matching. Default 10 is ~96% similar.
        
    Returns:
        True if images are similar within threshold.
    """
    distance = hamming_distance(phash1, phash2)
    if distance < 0:
        return False  # Invalid comparison
    return distance <= threshold


@dataclass
class HashIndex:
    """In-memory index for fast hash lookups during processing.
    
    Allows O(1) lookup of exact duplicates by SHA-256 and
    efficient near-duplicate detection by perceptual hash.
    """
    
    sha256_to_path: dict[str, Path] = field(default_factory=dict)
    phash_to_paths: dict[str, list[Path]] = field(default_factory=dict)
    path_to_hashes: dict[Path, tuple[str, str]] = field(default_factory=dict)
    
    def add(self, path: Path, sha256: str, phash: str) -> None:
        """Add an image to the index.
        
        Args:
            path: Path to the image file.
            sha256: SHA-256 hash.
            phash: Perceptual hash.
        """
        self.sha256_to_path[sha256] = path
        self.path_to_hashes[path] = (sha256, phash)
        
        if phash:
            if phash not in self.phash_to_paths:
                self.phash_to_paths[phash] = []
            self.phash_to_paths[phash].append(path)
    
    def has_exact_duplicate(self, sha256: str) -> Path | None:
        """Check if an exact duplicate exists.
        
        Args:
            sha256: SHA-256 hash to check.
            
        Returns:
            Path to the duplicate if found, None otherwise.
        """
        return self.sha256_to_path.get(sha256)
    
    def find_similar(
        self,
        phash: str,
        threshold: int = 10,
    ) -> list[Path]:
        """Find images similar to the given perceptual hash.
        
        Args:
            phash: Perceptual hash to compare.
            threshold: Maximum Hamming distance.
            
        Returns:
            List of paths to similar images.
        """
        if not phash:
            return []
        
        similar = []
        for existing_phash, paths in self.phash_to_paths.items():
            if are_similar_images(phash, existing_phash, threshold):
                similar.extend(paths)
        
        return similar


def load_hash_manifest(folder: Path) -> HashManifest:
    """Load hash manifest from a folder.
    
    Args:
        folder: Folder containing the manifest.
        
    Returns:
        HashManifest dict, or empty manifest if not found.
    """
    manifest_path = folder / HASH_MANIFEST_FILE
    
    if not manifest_path.exists():
        return {
            "version": "1.0",
            "created_at": datetime.now(timezone.utc).isoformat(),
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "hashes": {},
        }
    
    try:
        with open(manifest_path, encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        # Corrupted or unreadable, start fresh
        return {
            "version": "1.0",
            "created_at": datetime.now(timezone.utc).isoformat(),
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "hashes": {},
        }


def save_hash_manifest(folder: Path, manifest: HashManifest) -> Path:
    """Save hash manifest to a folder.
    
    Args:
        folder: Folder to save the manifest in.
        manifest: The manifest to save.
        
    Returns:
        Path to the saved manifest file.
    """
    manifest_path = folder / HASH_MANIFEST_FILE
    manifest["updated_at"] = datetime.now(timezone.utc).isoformat()
    
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    
    return manifest_path


def add_to_manifest(
    manifest: HashManifest,
    file_path: Path,
    sha256: str,
    phash: str,
) -> None:
    """Add a file's hashes to the manifest.
    
    Args:
        manifest: The manifest to update.
        file_path: Path to the image file.
        sha256: SHA-256 hash.
        phash: Perceptual hash.
    """
    manifest["hashes"][sha256] = {
        "sha256": sha256,
        "phash": phash,
        "filename": file_path.name,
        "size": file_path.stat().st_size,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }


def build_index_from_manifest(manifest: HashManifest, folder: Path) -> HashIndex:
    """Build a HashIndex from a manifest.
    
    Args:
        manifest: The hash manifest.
        folder: The folder containing the images.
        
    Returns:
        HashIndex populated from the manifest.
    """
    index = HashIndex()
    
    for sha256, entry in manifest["hashes"].items():
        path = folder / entry["filename"]
        if path.exists():
            index.add(path, sha256, entry.get("phash", ""))
    
    return index


@dataclass
class DeduplicationResult:
    """Result of deduplication analysis."""
    
    # Images to process (unique)
    unique_images: list[Path]
    
    # Images skipped as exact duplicates (sha256 match)
    exact_duplicates: list[tuple[Path, Path]]  # (duplicate, original)
    
    # Images skipped as near-duplicates (phash similar)
    near_duplicates: list[tuple[Path, Path, int]]  # (duplicate, original, distance)
    
    # Total images analyzed
    total_analyzed: int


def deduplicate_images(
    images: Iterable[Path],
    manifest: HashManifest,
    output_folder: Path,
    *,
    detect_near_duplicates: bool = True,
    similarity_threshold: int = 10,
    verbose: bool = False,
) -> DeduplicationResult:
    """Analyze images and identify duplicates.
    
    Args:
        images: Iterable of image paths to analyze.
        manifest: Existing hash manifest (will be updated in place).
        output_folder: Folder for output (used to check existing sidecars).
        detect_near_duplicates: Whether to check perceptual hashes.
        similarity_threshold: Max Hamming distance for near-duplicates.
        verbose: Print verbose output.
        
    Returns:
        DeduplicationResult with categorized images.
    """
    index = build_index_from_manifest(manifest, output_folder)
    
    unique_images: list[Path] = []
    exact_duplicates: list[tuple[Path, Path]] = []
    near_duplicates: list[tuple[Path, Path, int]] = []
    
    image_list = list(images)
    
    for image_path in image_list:
        sha256, phash = compute_hashes(image_path)
        
        # Check for exact duplicate
        existing_exact = index.has_exact_duplicate(sha256)
        if existing_exact:
            exact_duplicates.append((image_path, existing_exact))
            continue
        
        # Check for near duplicates if enabled
        if detect_near_duplicates and phash:
            similar = index.find_similar(phash, similarity_threshold)
            if similar:
                # Find the most similar one
                best_match = None
                best_distance = similarity_threshold + 1
                for similar_path in similar:
                    similar_hashes = index.path_to_hashes.get(similar_path)
                    if similar_hashes:
                        dist = hamming_distance(phash, similar_hashes[1])
                        if 0 <= dist < best_distance:
                            best_distance = dist
                            best_match = similar_path
                
                if best_match:
                    near_duplicates.append((image_path, best_match, best_distance))
                    continue
        
        # Not a duplicate - add to index and manifest
        index.add(image_path, sha256, phash)
        add_to_manifest(manifest, image_path, sha256, phash)
        unique_images.append(image_path)
    
    return DeduplicationResult(
        unique_images=unique_images,
        exact_duplicates=exact_duplicates,
        near_duplicates=near_duplicates,
        total_analyzed=len(image_list),
    )


def get_image_hash(file_path: Path) -> str:
    """Get the SHA-256 hash for an image file.
    
    Convenience function for adding hash to sidecar metadata.
    
    Args:
        file_path: Path to the image file.
        
    Returns:
        SHA-256 hex string.
    """
    return compute_sha256(file_path)
