"""Tests for the hashing module."""

import hashlib
import tempfile
from pathlib import Path

import pytest
from PIL import Image

from meme_my_mood_cli.hashing import (
    HashIndex,
    add_to_manifest,
    are_similar_images,
    compute_phash,
    compute_sha256,
    deduplicate_images,
    get_image_hash,
    hamming_distance,
    HASH_MANIFEST_FILE,
    load_hash_manifest,
    save_hash_manifest,
)


@pytest.fixture
def temp_dir():
    """Create a temporary directory for tests."""
    with tempfile.TemporaryDirectory() as tmpdir:
        yield Path(tmpdir)


@pytest.fixture
def sample_image(temp_dir):
    """Create a sample test image."""
    img_path = temp_dir / "test_image.png"
    img = Image.new("RGB", (100, 100), color="red")
    img.save(img_path)
    return img_path


@pytest.fixture
def sample_image_copy(temp_dir, sample_image):
    """Create an exact copy of the sample image."""
    copy_path = temp_dir / "test_image_copy.png"
    with open(sample_image, "rb") as src, open(copy_path, "wb") as dst:
        dst.write(src.read())
    return copy_path


@pytest.fixture
def similar_image(temp_dir):
    """Create a similar but not identical image."""
    img_path = temp_dir / "similar_image.png"
    # Almost the same as sample_image but with a small difference
    img = Image.new("RGB", (100, 100), color="red")
    # Add a small variation
    img.putpixel((50, 50), (255, 0, 1))
    img.save(img_path)
    return img_path


@pytest.fixture
def different_image(temp_dir):
    """Create a completely different image."""
    img_path = temp_dir / "different_image.png"
    img = Image.new("RGB", (100, 100), color="blue")
    img.save(img_path)
    return img_path


class TestComputeSha256:
    """Tests for SHA-256 hash computation."""
    
    def test_compute_sha256_consistent(self, sample_image):
        """Same file should produce same hash."""
        hash1 = compute_sha256(sample_image)
        hash2 = compute_sha256(sample_image)
        assert hash1 == hash2
    
    def test_compute_sha256_format(self, sample_image):
        """Hash should be a 64-character hex string."""
        hash_val = compute_sha256(sample_image)
        assert len(hash_val) == 64
        assert all(c in "0123456789abcdef" for c in hash_val)
    
    def test_compute_sha256_matches_hashlib(self, sample_image):
        """Should match hashlib's SHA-256 output."""
        our_hash = compute_sha256(sample_image)
        
        sha256 = hashlib.sha256()
        with open(sample_image, "rb") as f:
            sha256.update(f.read())
        expected = sha256.hexdigest()
        
        assert our_hash == expected
    
    def test_compute_sha256_identical_files(self, sample_image, sample_image_copy):
        """Identical files should produce identical hashes."""
        hash1 = compute_sha256(sample_image)
        hash2 = compute_sha256(sample_image_copy)
        assert hash1 == hash2
    
    def test_compute_sha256_different_files(self, sample_image, different_image):
        """Different files should produce different hashes."""
        hash1 = compute_sha256(sample_image)
        hash2 = compute_sha256(different_image)
        assert hash1 != hash2


class TestComputePhash:
    """Tests for perceptual hash computation."""
    
    def test_compute_phash_consistent(self, sample_image):
        """Same image should produce same phash."""
        phash1 = compute_phash(sample_image)
        phash2 = compute_phash(sample_image)
        assert phash1 == phash2
    
    def test_compute_phash_format(self, sample_image):
        """Phash should be a hex string."""
        phash = compute_phash(sample_image)
        assert len(phash) > 0
        assert all(c in "0123456789abcdef" for c in phash)
    
    def test_compute_phash_similar_images(self, sample_image, similar_image):
        """Similar images should have similar phashes."""
        phash1 = compute_phash(sample_image)
        phash2 = compute_phash(similar_image)
        distance = hamming_distance(phash1, phash2)
        # Similar images should have low distance
        assert distance >= 0
        assert distance < 20  # Fairly similar
    
    def test_compute_phash_different_images(self, sample_image, different_image):
        """Very different images should have different phashes."""
        phash1 = compute_phash(sample_image)
        phash2 = compute_phash(different_image)
        distance = hamming_distance(phash1, phash2)
        # Different images should have higher distance
        assert distance > 0


class TestHammingDistance:
    """Tests for Hamming distance calculation."""
    
    def test_hamming_distance_identical(self):
        """Identical hashes should have distance 0."""
        assert hamming_distance("aabbccdd", "aabbccdd") == 0
    
    def test_hamming_distance_different(self):
        """Different hashes should have positive distance."""
        # ff vs 00 = 8 bits different per byte
        assert hamming_distance("ff", "00") == 8
    
    def test_hamming_distance_empty(self):
        """Empty hashes should return -1."""
        assert hamming_distance("", "abcd") == -1
        assert hamming_distance("abcd", "") == -1
    
    def test_hamming_distance_different_lengths(self):
        """Different length hashes should return -1."""
        assert hamming_distance("aabb", "aabbcc") == -1


class TestAreSimilarImages:
    """Tests for similarity detection."""
    
    def test_identical_similar(self):
        """Identical hashes should be similar."""
        assert are_similar_images("aabbccdd", "aabbccdd", threshold=10)
    
    def test_within_threshold(self):
        """Hashes within threshold should be similar."""
        # These differ by 8 bits (one byte)
        assert are_similar_images("ff", "00", threshold=10)
    
    def test_beyond_threshold(self):
        """Hashes beyond threshold should not be similar."""
        # 8 bits difference, threshold 5
        assert not are_similar_images("ff", "00", threshold=5)
    
    def test_empty_not_similar(self):
        """Empty hashes should not be similar."""
        assert not are_similar_images("", "aabbccdd", threshold=10)


class TestHashIndex:
    """Tests for HashIndex class."""
    
    def test_add_and_find_exact(self, sample_image):
        """Should find exact duplicates."""
        index = HashIndex()
        index.add(sample_image, "abcd1234", "phash123")
        
        result = index.has_exact_duplicate("abcd1234")
        assert result == sample_image
        
        result = index.has_exact_duplicate("different")
        assert result is None
    
    def test_find_similar(self, sample_image, similar_image):
        """Should find similar images."""
        index = HashIndex()
        index.add(sample_image, "sha1", "00000000")  # All zeros phash
        
        # Add another image with slightly different phash
        result = index.find_similar("00000001", threshold=10)
        assert len(result) > 0


class TestManifest:
    """Tests for manifest operations."""
    
    def test_load_nonexistent(self, temp_dir):
        """Should return empty manifest for nonexistent file."""
        manifest = load_hash_manifest(temp_dir)
        assert manifest["version"] == "1.0"
        assert manifest["hashes"] == {}
    
    def test_save_and_load(self, temp_dir):
        """Should save and load manifest correctly."""
        manifest = load_hash_manifest(temp_dir)
        manifest["hashes"]["abc123"] = {
            "sha256": "abc123",
            "phash": "def456",
            "filename": "test.png",
            "size": 1000,
            "created_at": "2026-01-28T00:00:00Z",
        }
        
        save_hash_manifest(temp_dir, manifest)
        
        # Verify file exists
        manifest_path = temp_dir / HASH_MANIFEST_FILE
        assert manifest_path.exists()
        
        # Load and verify
        loaded = load_hash_manifest(temp_dir)
        assert "abc123" in loaded["hashes"]
        assert loaded["hashes"]["abc123"]["filename"] == "test.png"
    
    def test_add_to_manifest(self, temp_dir, sample_image):
        """Should add entry to manifest."""
        manifest = load_hash_manifest(temp_dir)
        add_to_manifest(manifest, sample_image, "sha123", "phash456")
        
        assert "sha123" in manifest["hashes"]
        entry = manifest["hashes"]["sha123"]
        assert entry["sha256"] == "sha123"
        assert entry["phash"] == "phash456"
        assert entry["filename"] == sample_image.name


class TestDeduplicate:
    """Tests for deduplication functionality."""
    
    def test_unique_images(self, temp_dir, sample_image, different_image):
        """Should identify unique images."""
        manifest = load_hash_manifest(temp_dir)
        result = deduplicate_images(
            [sample_image, different_image],
            manifest,
            temp_dir,
        )
        
        assert len(result.unique_images) == 2
        assert len(result.exact_duplicates) == 0
        assert result.total_analyzed == 2
    
    def test_exact_duplicates(self, temp_dir, sample_image, sample_image_copy):
        """Should detect exact duplicates."""
        manifest = load_hash_manifest(temp_dir)
        result = deduplicate_images(
            [sample_image, sample_image_copy],
            manifest,
            temp_dir,
        )
        
        assert len(result.unique_images) == 1
        assert len(result.exact_duplicates) == 1
        assert result.exact_duplicates[0][0] == sample_image_copy
    
    def test_manifest_updated(self, temp_dir, sample_image):
        """Should update manifest with new hashes."""
        manifest = load_hash_manifest(temp_dir)
        assert len(manifest["hashes"]) == 0
        
        deduplicate_images(
            [sample_image],
            manifest,
            temp_dir,
        )
        
        # Manifest should now contain the hash
        assert len(manifest["hashes"]) == 1


class TestGetImageHash:
    """Tests for get_image_hash convenience function."""
    
    def test_returns_sha256(self, sample_image):
        """Should return SHA-256 hash."""
        hash_val = get_image_hash(sample_image)
        expected = compute_sha256(sample_image)
        assert hash_val == expected
