//! Integration tests that verify cross-module functionality.

use riposte_core::proto;
use riposte_core::tokenizer::SentencePieceTokenizer;
use riposte_core::vector_index::VectorIndex;

fn encode_varint(mut value: u64, buf: &mut Vec<u8>) {
    loop {
        let mut byte = (value & 0x7F) as u8;
        value >>= 7;
        if value != 0 {
            byte |= 0x80;
        }
        buf.push(byte);
        if value == 0 {
            break;
        }
    }
}

fn build_piece_bytes(token: &str, score: f32, piece_type: u32) -> Vec<u8> {
    let mut buf = Vec::new();
    // field 1 (piece), wire type 2 → tag 0x0A
    buf.push(0x0A);
    let token_bytes = token.as_bytes();
    encode_varint(token_bytes.len() as u64, &mut buf);
    buf.extend_from_slice(token_bytes);
    // field 2 (score), wire type 5 (fixed32) → tag 0x15
    buf.push(0x15);
    buf.extend_from_slice(&score.to_le_bytes());
    // field 3 (type), wire type 0 (varint) → tag 0x18
    buf.push(0x18);
    encode_varint(piece_type as u64, &mut buf);
    buf
}

fn build_model_bytes(pieces: &[(&str, f32, u32)]) -> Vec<u8> {
    let mut model = Vec::new();
    for &(token, score, piece_type) in pieces {
        let piece = build_piece_bytes(token, score, piece_type);
        model.push(0x0A); // field 1, wire type 2
        encode_varint(piece.len() as u64, &mut model);
        model.extend_from_slice(&piece);
    }
    model
}

fn normalized(v: &[f32]) -> Vec<f32> {
    let norm = v.iter().map(|x| x * x).sum::<f32>().sqrt();
    if norm == 0.0 {
        v.to_vec()
    } else {
        v.iter().map(|x| x / norm).collect()
    }
}

#[test]
fn test_proto_to_tokenizer_roundtrip() {
    // Build a small vocab: unk, bos, eos, byte tokens, and normal tokens
    let vocab: Vec<(&str, f32, u32)> = vec![
        ("<unk>", 0.0, 2),    // UNKNOWN
        ("<s>", 0.0, 3),      // CONTROL (bos)
        ("</s>", 0.0, 3),     // CONTROL (eos)
        ("<0x61>", 0.0, 6),   // BYTE 'a'
        ("<0x62>", 0.0, 6),   // BYTE 'b'
        ("\u{2581}", -1.0, 1),          // NORMAL (space replacement ▁)
        ("\u{2581}hello", -1.5, 1),     // NORMAL
        ("world", -2.0, 1),            // NORMAL
        ("he", -3.0, 1),               // NORMAL
        ("lo", -3.0, 1),               // NORMAL
    ];

    let model_data = build_model_bytes(&vocab);

    // Parse with proto
    let parsed = proto::parse(&model_data).unwrap();
    assert_eq!(parsed.len(), 10);
    assert_eq!(parsed[0].token, "<unk>");
    assert_eq!(parsed[0].piece_type, 2);
    assert_eq!(parsed[6].token, "\u{2581}hello");

    // Build tokenizer from parsed pieces
    let tokenizer = SentencePieceTokenizer::build(&parsed).unwrap();
    assert_eq!(tokenizer.vocab_size(), 10);

    // " hello" → "▁hello" → should match piece id 6
    let ids = tokenizer.encode(" hello");
    assert_eq!(ids, vec![6]);

    // "world" → matches piece id 7
    let ids = tokenizer.encode("world");
    assert_eq!(ids, vec![7]);
}

#[test]
fn test_tokenizer_and_vector_index_pipeline() {
    let vocab: Vec<(&str, f32, u32)> = vec![
        ("<unk>", 0.0, 2),
        ("\u{2581}", -1.0, 1),
        ("\u{2581}cat", -1.5, 1),
        ("\u{2581}dog", -1.5, 1),
        ("\u{2581}fish", -2.0, 1),
        ("s", -3.0, 1),
    ];
    let model_data = build_model_bytes(&vocab);
    let parsed = proto::parse(&model_data).unwrap();
    let tokenizer = SentencePieceTokenizer::build(&parsed).unwrap();

    // Tokenize texts with different expected token counts
    let texts = [" cat", " cats", " cat cats"];
    let token_counts: Vec<usize> = texts.iter().map(|t| tokenizer.encode(t).len()).collect();

    for (i, count) in token_counts.iter().enumerate() {
        assert!(*count > 0, "text '{}' produced no tokens", texts[i]);
    }

    // Create vector index with fake embeddings keyed by token count
    let dims = 4;
    let index = VectorIndex::new(dims).unwrap();
    index.reserve(texts.len()).unwrap();

    for (i, &count) in token_counts.iter().enumerate() {
        let mut v = vec![0.1f32; dims];
        v[0] = count as f32;
        let v = normalized(&v);
        index.add(i as u64, &v).unwrap();
    }

    // Search with query matching the first embedding
    let mut query = vec![0.1f32; dims];
    query[0] = token_counts[0] as f32;
    let query = normalized(&query);

    let (keys, distances) = index.search(&query, texts.len()).unwrap();
    assert_eq!(keys.len(), texts.len());
    assert_eq!(keys[0], 0);
    for w in distances.windows(2) {
        assert!(w[0] <= w[1] + 1e-6);
    }
}

#[test]
fn test_vector_index_persistence_roundtrip() {
    let dims = 8;
    let dir = std::env::temp_dir();
    let path = dir.join("integration_test_vector_index.usearch");
    let path_str = path.to_str().unwrap();

    let vectors: Vec<Vec<f32>> = (0..5u64)
        .map(|i| {
            let mut v = vec![0.1f32; dims];
            v[i as usize % dims] += 1.0;
            normalized(&v)
        })
        .collect();

    // Create index, add vectors, save
    {
        let index = VectorIndex::new(dims).unwrap();
        index.reserve(5).unwrap();
        for (i, v) in vectors.iter().enumerate() {
            index.add(i as u64, v).unwrap();
        }
        assert_eq!(index.len(), 5);
        index.save(path_str).unwrap();
    }

    // Load into new index and verify same results
    {
        let index = VectorIndex::new(dims).unwrap();
        index.load(path_str).unwrap();
        assert_eq!(index.len(), 5);

        let (keys, distances) = index.search(&vectors[0], 3).unwrap();
        assert_eq!(keys.len(), 3);
        assert_eq!(keys[0], 0);
        assert!(distances[0] < 0.01);
    }

    let _ = std::fs::remove_file(path);
}
