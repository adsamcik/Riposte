//! HNSW vector index backed by USearch for cosine-similarity search.

use usearch::{Index, IndexOptions, MetricKind, ScalarKind};

/// A cosine-similarity HNSW vector index wrapping [`usearch::Index`].
pub struct VectorIndex {
    index: Index,
    dimensions: usize,
}

impl VectorIndex {
    /// Creates a new HNSW index for cosine similarity with default F32 quantization.
    pub fn new(dimensions: usize) -> Result<Self, String> {
        Self::with_quantization(dimensions, false)
    }

    /// Creates an index with custom quantization (`F32` by default, `F16` when `use_f16` is true).
    pub fn with_quantization(dimensions: usize, use_f16: bool) -> Result<Self, String> {
        let quantization = if use_f16 {
            ScalarKind::F16
        } else {
            ScalarKind::F32
        };

        let options = IndexOptions {
            dimensions,
            metric: MetricKind::Cos,
            quantization,
            connectivity: 16,
            expansion_add: 128,
            expansion_search: 64,
            multi: false,
        };

        let index = Index::new(&options).map_err(|e| format!("Failed to create index: {e}"))?;

        Ok(Self { index, dimensions })
    }

    /// Reserves capacity for `capacity` vectors.
    pub fn reserve(&self, capacity: usize) -> Result<(), String> {
        self.index
            .reserve(capacity)
            .map_err(|e| format!("Failed to reserve capacity: {e}"))
    }

    /// Adds a vector with the given key.
    pub fn add(&self, key: u64, vector: &[f32]) -> Result<(), String> {
        if vector.len() != self.dimensions {
            return Err(format!(
                "Vector dimension mismatch: expected {}, got {}",
                self.dimensions,
                vector.len()
            ));
        }
        self.index
            .add(key, vector)
            .map_err(|e| format!("Failed to add vector: {e}"))
    }

    /// Searches for `count` nearest neighbors. Returns `(keys, distances)`.
    pub fn search(&self, query: &[f32], count: usize) -> Result<(Vec<u64>, Vec<f32>), String> {
        if query.len() != self.dimensions {
            return Err(format!(
                "Query dimension mismatch: expected {}, got {}",
                self.dimensions,
                query.len()
            ));
        }
        let results = self
            .index
            .search(query, count)
            .map_err(|e| format!("Search failed: {e}"))?;

        Ok((results.keys.to_vec(), results.distances.to_vec()))
    }

    /// Removes a vector by key. Returns `true` if the key existed.
    pub fn remove(&self, key: u64) -> Result<bool, String> {
        let found = self.index.contains(key);
        if found {
            self.index
                .remove(key)
                .map_err(|e| format!("Failed to remove key {key}: {e}"))?;
        }
        Ok(found)
    }

    /// Returns the number of vectors in the index.
    pub fn len(&self) -> usize {
        self.index.size()
    }

    /// Returns `true` if the index contains no vectors.
    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Saves the index to a file at `path`.
    pub fn save(&self, path: &str) -> Result<(), String> {
        self.index
            .save(path)
            .map_err(|e| format!("Failed to save index: {e}"))
    }

    /// Loads an index from a file at `path`.
    pub fn load(&self, path: &str) -> Result<(), String> {
        self.index
            .load(path)
            .map_err(|e| format!("Failed to load index: {e}"))
    }

    /// Checks if a key exists in the index.
    pub fn contains(&self, key: u64) -> bool {
        self.index.contains(key)
    }

    /// Returns the number of dimensions for vectors in this index.
    pub fn dimensions(&self) -> usize {
        self.dimensions
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn normalized(v: &[f32]) -> Vec<f32> {
        let norm = v.iter().map(|x| x * x).sum::<f32>().sqrt();
        if norm == 0.0 {
            v.to_vec()
        } else {
            v.iter().map(|x| x / norm).collect()
        }
    }

    #[test]
    fn create_index_with_dimensions() {
        let index = VectorIndex::new(128).unwrap();
        assert_eq!(index.dimensions(), 128);
        assert!(index.is_empty());
        assert_eq!(index.len(), 0);
    }

    #[test]
    fn add_single_vector_and_search() {
        let index = VectorIndex::new(4).unwrap();
        index.reserve(1).unwrap();
        let v = normalized(&[1.0, 0.0, 0.0, 0.0]);
        index.add(1, &v).unwrap();

        let (keys, distances) = index.search(&v, 1).unwrap();
        assert_eq!(keys.len(), 1);
        assert_eq!(keys[0], 1);
        assert!(distances[0] < 1e-5, "distance should be ~0 for identical vector");
    }

    #[test]
    fn add_multiple_vectors_search_returns_correct_order() {
        let index = VectorIndex::new(4).unwrap();
        index.reserve(3).unwrap();

        let query = normalized(&[1.0, 0.0, 0.0, 0.0]);
        let close = normalized(&[0.9, 0.1, 0.0, 0.0]);
        let far = normalized(&[0.0, 0.0, 0.0, 1.0]);

        index.add(1, &query).unwrap();
        index.add(2, &close).unwrap();
        index.add(3, &far).unwrap();

        let (keys, distances) = index.search(&query, 3).unwrap();
        assert_eq!(keys.len(), 3);
        // The exact match should be first (smallest distance)
        assert_eq!(keys[0], 1);
        // Distances should be non-decreasing
        assert!(distances[0] <= distances[1]);
        assert!(distances[1] <= distances[2]);
    }

    #[test]
    fn search_returns_correct_cosine_distances() {
        let index = VectorIndex::new(3).unwrap();
        index.reserve(2).unwrap();

        let a = normalized(&[1.0, 0.0, 0.0]);
        let b = normalized(&[0.0, 1.0, 0.0]);

        index.add(1, &a).unwrap();
        index.add(2, &b).unwrap();

        let (keys, distances) = index.search(&a, 2).unwrap();
        assert_eq!(keys[0], 1);
        assert!(distances[0] < 0.01);
        // Cosine distance between orthogonal vectors ≈ 1.0
        assert!((distances[1] - 1.0).abs() < 0.1);
    }

    #[test]
    fn search_with_k_greater_than_num_vectors() {
        let index = VectorIndex::new(3).unwrap();
        index.reserve(2).unwrap();

        let a = normalized(&[1.0, 0.0, 0.0]);
        let b = normalized(&[0.0, 1.0, 0.0]);
        index.add(1, &a).unwrap();
        index.add(2, &b).unwrap();

        let (keys, _) = index.search(&a, 100).unwrap();
        assert_eq!(keys.len(), 2);
    }

    #[test]
    fn remove_vector_then_search() {
        let index = VectorIndex::new(3).unwrap();
        index.reserve(2).unwrap();

        let a = normalized(&[1.0, 0.0, 0.0]);
        let b = normalized(&[0.0, 1.0, 0.0]);
        index.add(1, &a).unwrap();
        index.add(2, &b).unwrap();

        assert!(index.remove(1).unwrap());
        assert!(!index.contains(1));

        let (keys, _) = index.search(&a, 2).unwrap();
        assert!(!keys.contains(&1));
    }

    #[test]
    fn contains_len_is_empty() {
        let index = VectorIndex::new(3).unwrap();
        assert!(index.is_empty());
        assert_eq!(index.len(), 0);
        assert!(!index.contains(1));

        index.reserve(1).unwrap();
        let v = normalized(&[1.0, 0.0, 0.0]);
        index.add(1, &v).unwrap();

        assert!(!index.is_empty());
        assert_eq!(index.len(), 1);
        assert!(index.contains(1));
    }

    #[test]
    fn save_and_load_round_trip() {
        let dir = std::env::temp_dir();
        let path = dir.join("test_vector_index_roundtrip.usearch");
        let path_str = path.to_str().unwrap();

        // Build and save
        {
            let index = VectorIndex::new(4).unwrap();
            index.reserve(2).unwrap();
            let a = normalized(&[1.0, 0.0, 0.0, 0.0]);
            let b = normalized(&[0.0, 1.0, 0.0, 0.0]);
            index.add(10, &a).unwrap();
            index.add(20, &b).unwrap();
            index.save(path_str).unwrap();
        }

        // Load into fresh index and verify
        {
            let index = VectorIndex::new(4).unwrap();
            index.load(path_str).unwrap();
            assert_eq!(index.len(), 2);
            assert!(index.contains(10));
            assert!(index.contains(20));

            let query = normalized(&[1.0, 0.0, 0.0, 0.0]);
            let (keys, _) = index.search(&query, 1).unwrap();
            assert_eq!(keys[0], 10);
        }

        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn search_empty_index_returns_empty() {
        let index = VectorIndex::new(3).unwrap();
        let q = normalized(&[1.0, 0.0, 0.0]);
        let (keys, distances) = index.search(&q, 5).unwrap();
        assert!(keys.is_empty());
        assert!(distances.is_empty());
    }

    #[test]
    fn add_duplicate_key_returns_error() {
        let index = VectorIndex::new(3).unwrap();
        index.reserve(2).unwrap();

        let v1 = normalized(&[1.0, 0.0, 0.0]);
        let v2 = normalized(&[0.0, 1.0, 0.0]);

        index.add(1, &v1).unwrap();
        let result = index.add(1, &v2);
        assert!(result.is_err(), "Duplicate keys should be rejected");
    }

    #[test]
    fn f16_quantization_variant() {
        let index = VectorIndex::with_quantization(4, true).unwrap();
        index.reserve(2).unwrap();

        let a = normalized(&[1.0, 0.0, 0.0, 0.0]);
        let b = normalized(&[0.0, 1.0, 0.0, 0.0]);
        index.add(1, &a).unwrap();
        index.add(2, &b).unwrap();

        let (keys, _) = index.search(&a, 1).unwrap();
        assert_eq!(keys[0], 1);
    }

    #[test]
    fn large_dimension_256d() {
        let dims = 256;
        let index = VectorIndex::new(dims).unwrap();
        index.reserve(5).unwrap();

        for i in 0..5u64 {
            let mut v = vec![0.0f32; dims];
            v[i as usize % dims] = 1.0;
            let v = normalized(&v);
            index.add(i, &v).unwrap();
        }

        assert_eq!(index.len(), 5);

        let mut q = vec![0.0f32; dims];
        q[0] = 1.0;
        let q = normalized(&q);
        let (keys, _) = index.search(&q, 1).unwrap();
        assert_eq!(keys[0], 0);
    }

    #[test]
    fn large_dimension_768d() {
        let dims = 768;
        let index = VectorIndex::new(dims).unwrap();
        index.reserve(3).unwrap();

        for i in 0..3u64 {
            let mut v = vec![0.0f32; dims];
            v[i as usize] = 1.0;
            let v = normalized(&v);
            index.add(i, &v).unwrap();
        }

        assert_eq!(index.len(), 3);

        let mut q = vec![0.0f32; dims];
        q[2] = 1.0;
        let q = normalized(&q);
        let (keys, _) = index.search(&q, 1).unwrap();
        assert_eq!(keys[0], 2);
    }

    #[test]
    fn reserve_then_add_many() {
        let index = VectorIndex::new(8).unwrap();
        let count = 100;
        index.reserve(count).unwrap();

        for i in 0..count as u64 {
            let mut v = vec![0.1f32; 8];
            v[(i as usize) % 8] += 1.0;
            let v = normalized(&v);
            index.add(i, &v).unwrap();
        }

        assert_eq!(index.len(), count);
    }

    #[test]
    fn search_exact_k1() {
        let index = VectorIndex::new(3).unwrap();
        index.reserve(10).unwrap();

        for i in 0..10u64 {
            let mut v = vec![0.1f32; 3];
            v[(i as usize) % 3] += (i as f32) * 0.1;
            let v = normalized(&v);
            index.add(i, &v).unwrap();
        }

        let q = normalized(&[1.0, 0.0, 0.0]);
        let (keys, distances) = index.search(&q, 1).unwrap();
        assert_eq!(keys.len(), 1);
        assert_eq!(distances.len(), 1);
    }

    #[test]
    fn dimension_mismatch_add_returns_error() {
        let index = VectorIndex::new(4).unwrap();
        let wrong = vec![1.0f32; 3];
        let result = index.add(1, &wrong);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("dimension mismatch"));
    }

    #[test]
    fn dimension_mismatch_search_returns_error() {
        let index = VectorIndex::new(4).unwrap();
        let wrong = vec![1.0f32; 5];
        let result = index.search(&wrong, 1);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("dimension mismatch"));
    }

    #[test]
    fn remove_nonexistent_key_returns_false() {
        let index = VectorIndex::new(3).unwrap();
        assert!(!index.remove(999).unwrap());
    }

    #[test]
    fn save_to_invalid_path_returns_error() {
        let index = VectorIndex::new(3).unwrap();
        // Try writing to a temp file to ensure the index can save something,
        // then try an invalid path.
        let result = index.save("");
        assert!(result.is_err());
    }

    #[test]
    fn test_zero_vector() {
        let index = VectorIndex::new(4).unwrap();
        index.reserve(1).unwrap();
        let zero = vec![0.0f32; 4];
        index.add(1, &zero).unwrap();

        let (keys, _) = index.search(&zero, 1).unwrap();
        assert_eq!(keys.len(), 1);
        assert_eq!(keys[0], 1);
    }

    #[test]
    fn test_search_after_remove_all() {
        let index = VectorIndex::new(3).unwrap();
        index.reserve(3).unwrap();

        let v1 = normalized(&[1.0, 0.0, 0.0]);
        let v2 = normalized(&[0.0, 1.0, 0.0]);
        let v3 = normalized(&[0.0, 0.0, 1.0]);
        index.add(1, &v1).unwrap();
        index.add(2, &v2).unwrap();
        index.add(3, &v3).unwrap();

        index.remove(1).unwrap();
        index.remove(2).unwrap();
        index.remove(3).unwrap();

        let (keys, _) = index.search(&v1, 5).unwrap();
        assert!(keys.is_empty());
    }

    #[test]
    fn test_large_batch_100_vectors() {
        let dims = 256;
        let count = 100usize;
        let index = VectorIndex::new(dims).unwrap();
        index.reserve(count).unwrap();

        for i in 0..count {
            let mut v = vec![0.01f32; dims];
            v[i % dims] += 1.0;
            let v = normalized(&v);
            index.add(i as u64, &v).unwrap();
        }

        assert_eq!(index.len(), count);

        let mut q = vec![0.01f32; dims];
        q[0] += 1.0;
        let q = normalized(&q);
        let (keys, _) = index.search(&q, 10).unwrap();
        assert_eq!(keys.len(), 10);
    }

    #[test]
    fn test_load_nonexistent_file() {
        let index = VectorIndex::new(3).unwrap();
        let result = index.load("nonexistent_path_12345.usearch");
        assert!(result.is_err());
    }

    #[test]
    fn test_reserve_zero() {
        let index = VectorIndex::new(3).unwrap();
        index.reserve(0).unwrap();
        assert!(index.is_empty());
    }
}
