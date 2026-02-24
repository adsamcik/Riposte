//! Memory-efficient SentencePiece BPE tokenizer.
//!
//! Uses FxHashMap for vocabulary lookup. Implements Viterbi best-path segmentation
//! with byte fallback. Faithful port of the Kotlin `SentencePieceTokenizer`.
//!
//! Preprocessing follows Gemma's tokenizer config: only space→▁ replacement,
//! no NFKC normalization, no lowercasing.

use crate::proto::{self, ParsedPiece};
use rustc_hash::FxHashMap;

/// SentencePiece word boundary marker (replaces ASCII spaces).
const SPACE_REPLACEMENT: char = '\u{2581}'; // ▁

// Protobuf piece type values
const TYPE_NORMAL: u32 = 1;
const TYPE_UNKNOWN: u32 = 2;
const TYPE_CONTROL: u32 = 3;
const TYPE_USER_DEFINED: u32 = 4;
const TYPE_UNUSED: u32 = 5;
const TYPE_BYTE: u32 = 6;

/// UNK score penalty relative to minimum vocab score.
const UNK_SCORE_OFFSET: f32 = 10.0;

/// Small bias subtracted from USER_DEFINED token boosted score.
const USER_DEFINED_SCORE_BIAS: f32 = 0.1;

const DEFAULT_UNK_ID: u32 = 3;
const BYTE_TOKEN_COUNT: usize = 256;

#[derive(Debug, Clone, Copy)]
struct VocabEntry {
    id: u32,
    score: f32,
    piece_type: u32,
}

/// A SentencePiece BPE tokenizer with Viterbi best-path segmentation.
///
/// Thread-safe: all state is immutable after construction.
pub struct SentencePieceTokenizer {
    vocab: FxHashMap<String, VocabEntry>,
    byte_piece_ids: [i32; BYTE_TOKEN_COUNT],
    max_piece_length: usize,
    unk_id: u32,
    unk_score: f32,
    max_score: f32,
    has_byte_fallback: bool,
}

impl SentencePieceTokenizer {
    /// Parses a SentencePiece `.model` file and builds a tokenizer.
    pub fn from_model_data(data: &[u8]) -> Result<Self, String> {
        let pieces = proto::parse(data)?;
        Self::build(&pieces)
    }

    /// Builds a tokenizer from parsed vocabulary pieces.
    pub fn build(pieces: &[ParsedPiece]) -> Result<Self, String> {
        if pieces.is_empty() {
            return Err("No vocabulary pieces".into());
        }

        let mut vocab = FxHashMap::with_capacity_and_hasher(pieces.len() * 2, Default::default());
        let mut byte_piece_ids = [-1i32; BYTE_TOKEN_COUNT];
        let mut max_len: usize = 0;
        let mut min_score: f32 = 0.0;
        let mut max_score_val: f32 = f32::NEG_INFINITY;
        let mut unk_id: u32 = DEFAULT_UNK_ID;

        for piece in pieces {
            let entry = VocabEntry {
                id: piece.id,
                score: piece.score,
                piece_type: piece.piece_type,
            };

            match piece.piece_type {
                TYPE_UNKNOWN => unk_id = piece.id,
                TYPE_BYTE => {
                    if let Some(byte_val) = parse_byte_piece_value(&piece.token) {
                        byte_piece_ids[byte_val as usize] = piece.id as i32;
                    }
                }
                TYPE_NORMAL | TYPE_USER_DEFINED => {
                    if piece.score < min_score {
                        min_score = piece.score;
                    }
                    if piece.score > max_score_val {
                        max_score_val = piece.score;
                    }
                }
                _ => {}
            }

            if piece.token.len() > max_len {
                max_len = piece.token.len();
            }
            vocab.insert(piece.token.clone(), entry);
        }

        let has_byte_fallback = byte_piece_ids.iter().any(|&id| id >= 0);

        Ok(SentencePieceTokenizer {
            vocab,
            byte_piece_ids,
            max_piece_length: max_len,
            unk_id,
            unk_score: min_score - UNK_SCORE_OFFSET,
            max_score: if max_score_val > f32::NEG_INFINITY {
                max_score_val
            } else {
                0.0
            },
            has_byte_fallback,
        })
    }

    /// Tokenizes text using Gemma-compatible preprocessing and Viterbi segmentation.
    ///
    /// Preprocessing: replace ASCII spaces with ▁ (U+2581). No other normalization.
    /// Segmentation: Viterbi best-path with byte fallback for unknown characters.
    ///
    /// Returns token IDs. Does NOT include BOS/EOS — caller must add them.
    pub fn encode(&self, text: &str) -> Vec<u32> {
        if text.is_empty() {
            return Vec::new();
        }

        // Gemma preprocessing: only replace ASCII spaces with ▁
        let normalized: String = text.chars().map(|c| if c == ' ' { SPACE_REPLACEMENT } else { c }).collect();

        self.viterbi_encode(&normalized)
    }

    /// Returns the vocab size.
    pub fn vocab_size(&self) -> usize {
        self.vocab.len()
    }

    /// Viterbi best-path segmentation.
    ///
    /// For each position i, tries all substrings text[i..j] up to max_piece_length.
    /// Finds the segmentation that maximizes total score.
    fn viterbi_encode(&self, text: &str) -> Vec<u32> {
        // We work on char indices since the vocab contains multi-byte Unicode tokens
        let chars: Vec<char> = text.chars().collect();
        let n = chars.len();
        if n == 0 {
            return Vec::new();
        }

        // Map from char index to byte offset for substring extraction
        let mut char_to_byte: Vec<usize> = Vec::with_capacity(n + 1);
        let mut byte_offset = 0;
        for &ch in &chars {
            char_to_byte.push(byte_offset);
            byte_offset += ch.len_utf8();
        }
        char_to_byte.push(byte_offset);

        let mut best_score = vec![f32::NEG_INFINITY; n + 1];
        let mut best_id = vec![0u32; n + 1];
        let mut best_start = vec![0usize; n + 1];
        let mut best_is_unk = vec![false; n + 1];
        best_score[0] = 0.0;

        for i in 0..n {
            if best_score[i] == f32::NEG_INFINITY {
                continue;
            }

            // Convert max_piece_length (bytes) to an upper bound on chars
            let max_j = n.min(i + self.max_piece_length);
            for j in (i + 1)..=max_j {
                let byte_start = char_to_byte[i];
                let byte_end = char_to_byte[j];

                // Skip if the byte span exceeds max_piece_length
                if byte_end - byte_start > self.max_piece_length {
                    break;
                }

                let substr = &text[byte_start..byte_end];
                let entry = match self.vocab.get(substr) {
                    Some(e) => e,
                    None => continue,
                };

                // Skip CONTROL and UNUSED tokens
                if entry.piece_type == TYPE_CONTROL || entry.piece_type == TYPE_UNUSED {
                    continue;
                }

                // USER_DEFINED tokens get boosted score
                let effective_score = if entry.piece_type == TYPE_USER_DEFINED {
                    (j - i) as f32 * self.max_score - USER_DEFINED_SCORE_BIAS
                } else {
                    entry.score
                };

                let new_score = best_score[i] + effective_score;
                if new_score > best_score[j] {
                    best_score[j] = new_score;
                    best_id[j] = entry.id;
                    best_start[j] = i;
                    best_is_unk[j] = false;
                }
            }

            // Single-character UNK fallback
            let unk_candidate = best_score[i] + self.unk_score;
            if unk_candidate > best_score[i + 1] {
                best_score[i + 1] = unk_candidate;
                best_id[i + 1] = self.unk_id;
                best_start[i + 1] = i;
                best_is_unk[i + 1] = true;
            }
        }

        // Backtrack to reconstruct segmentation
        let mut segments: Vec<(usize, usize, u32, bool)> = Vec::new();
        let mut pos = n;
        while pos > 0 {
            segments.push((best_start[pos], pos, best_id[pos], best_is_unk[pos]));
            pos = best_start[pos];
        }
        segments.reverse();

        // Post-process: byte fallback for UNK segments
        self.apply_byte_fallback(&chars, &char_to_byte, &segments)
    }

    /// Converts UNK segments to byte-fallback tokens when available.
    fn apply_byte_fallback(
        &self,
        chars: &[char],
        char_to_byte: &[usize],
        segments: &[(usize, usize, u32, bool)],
    ) -> Vec<u32> {
        let mut result = Vec::new();
        let text_bytes: Vec<u8> = chars.iter().collect::<String>().into_bytes();

        for &(start, end, id, is_unk) in segments {
            if is_unk && self.has_byte_fallback {
                let byte_start = char_to_byte[start];
                let byte_end = char_to_byte[end];
                for &b in &text_bytes[byte_start..byte_end] {
                    let byte_id = self.byte_piece_ids[b as usize];
                    result.push(if byte_id >= 0 { byte_id as u32 } else { self.unk_id });
                }
            } else {
                result.push(id);
            }
        }
        result
    }
}

/// Parses the byte value from a byte piece token like `<0x41>`.
fn parse_byte_piece_value(token: &str) -> Option<u8> {
    if !token.starts_with("<0x") || !token.ends_with('>') {
        return None;
    }
    let hex = &token[3..token.len() - 1];
    u8::from_str_radix(hex, 16).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn build_test_tokenizer() -> SentencePieceTokenizer {
        let pieces = vec![
            ParsedPiece { token: "<pad>".into(), id: 0, score: 0.0, piece_type: TYPE_CONTROL },
            ParsedPiece { token: "<unk>".into(), id: 1, score: 0.0, piece_type: TYPE_UNKNOWN },
            ParsedPiece { token: "<s>".into(), id: 2, score: 0.0, piece_type: TYPE_CONTROL },
            ParsedPiece { token: "</s>".into(), id: 3, score: 0.0, piece_type: TYPE_CONTROL },
            ParsedPiece { token: "▁".into(), id: 4, score: -1.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "▁the".into(), id: 5, score: -2.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "▁a".into(), id: 6, score: -2.5, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "h".into(), id: 7, score: -3.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "e".into(), id: 8, score: -3.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "l".into(), id: 9, score: -3.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "o".into(), id: 10, score: -3.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "he".into(), id: 11, score: -2.8, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "ll".into(), id: 12, score: -2.9, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "lo".into(), id: 13, score: -2.85, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "hel".into(), id: 14, score: -2.5, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "hello".into(), id: 15, score: -2.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "▁hello".into(), id: 16, score: -1.5, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "world".into(), id: 17, score: -2.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "▁world".into(), id: 18, score: -1.5, piece_type: TYPE_NORMAL },
        ];

        // Add byte fallback tokens
        let mut all_pieces = pieces;
        for b in 0u8..=255 {
            all_pieces.push(ParsedPiece {
                token: format!("<0x{b:02X}>"),
                id: 100 + b as u32,
                score: 0.0,
                piece_type: TYPE_BYTE,
            });
        }

        SentencePieceTokenizer::build(&all_pieces).unwrap()
    }

    #[test]
    fn test_empty_input() {
        let tok = build_test_tokenizer();
        assert!(tok.encode("").is_empty());
    }

    #[test]
    fn test_single_word() {
        let tok = build_test_tokenizer();
        // "hello" should match the full "hello" token (id=15), score -2.0
        // which is better than "hel" + "lo" (score -2.5 + -2.85 = -5.35)
        let ids = tok.encode("hello");
        assert_eq!(ids, vec![15]); // "hello"
    }

    #[test]
    fn test_space_replacement() {
        let tok = build_test_tokenizer();
        // "hello world" → "▁hello▁world" after space replacement
        // But since we prepend ▁ only to spaces, it becomes "hello▁world"
        // Actually: spaces become ▁. So "hello world" → "hello▁world"
        // "hello" (id=15) + "▁world" (id=18)
        let ids = tok.encode("hello world");
        assert_eq!(ids, vec![15, 18]); // "hello" + "▁world"
    }

    #[test]
    fn test_space_at_start() {
        let tok = build_test_tokenizer();
        // " hello" → "▁hello" → id=16
        let ids = tok.encode(" hello");
        assert_eq!(ids, vec![16]); // "▁hello"
    }

    #[test]
    fn test_unknown_chars_use_byte_fallback() {
        let tok = build_test_tokenizer();
        // 'z' not in vocab → byte fallback → <0x7A> → id = 100+0x7A = 222
        let ids = tok.encode("z");
        assert_eq!(ids, vec![100 + 0x7A]);
    }

    #[test]
    fn test_multibyte_unknown_char_byte_fallback() {
        let tok = build_test_tokenizer();
        // '€' is U+20AC, UTF-8: [0xE2, 0x82, 0xAC]
        let ids = tok.encode("€");
        assert_eq!(ids, vec![
            100 + 0xE2,
            100 + 0x82,
            100 + 0xAC,
        ]);
    }

    #[test]
    fn test_mixed_known_and_unknown() {
        let tok = build_test_tokenizer();
        // "helloz" → "hello" (id=15) + "z" (byte fallback 0x7A → 222)
        let ids = tok.encode("helloz");
        assert_eq!(ids, vec![15, 100 + 0x7A]);
    }

    #[test]
    fn test_parse_byte_piece_value() {
        assert_eq!(parse_byte_piece_value("<0x00>"), Some(0));
        assert_eq!(parse_byte_piece_value("<0xFF>"), Some(255));
        assert_eq!(parse_byte_piece_value("<0x41>"), Some(65));
        assert_eq!(parse_byte_piece_value("hello"), None);
        assert_eq!(parse_byte_piece_value("<0xZZ>"), None);
    }

    #[test]
    fn test_vocab_size() {
        let tok = build_test_tokenizer();
        // 19 regular + 256 byte tokens
        assert_eq!(tok.vocab_size(), 19 + 256);
    }

    #[test]
    fn test_control_tokens_not_emitted() {
        let tok = build_test_tokenizer();
        // <pad>, <s>, </s> are CONTROL type — should never appear in output
        let ids = tok.encode("hello world");
        for &id in &ids {
            assert!(id != 0 && id != 2 && id != 3, "Control token {id} should not appear in output");
        }
    }

    #[test]
    fn test_consecutive_spaces() {
        let tok = build_test_tokenizer();
        // "  " → "▁▁" → "▁" (id=4) + "▁" (id=4)
        let ids = tok.encode("  ");
        assert_eq!(ids, vec![4, 4]);
    }

    #[test]
    fn test_user_defined_tokens() {
        let pieces = vec![
            ParsedPiece { token: "<unk>".into(), id: 0, score: 0.0, piece_type: TYPE_UNKNOWN },
            ParsedPiece { token: "x".into(), id: 1, score: 1.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "a".into(), id: 2, score: -5.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "b".into(), id: 3, score: -5.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "ab".into(), id: 4, score: -100.0, piece_type: TYPE_USER_DEFINED },
        ];
        let tok = SentencePieceTokenizer::build(&pieces).unwrap();
        let ids = tok.encode("ab");
        // USER_DEFINED boost: 2 * max_score(1.0) - 0.1 = 1.9 > "a"+"b" = -10.0
        assert_eq!(ids, vec![4]);
    }

    #[test]
    fn test_all_unknown_text() {
        let tok = build_test_tokenizer();
        // 'z' is not in vocab → byte fallback for each character
        let ids = tok.encode("zzz");
        assert_eq!(ids.len(), 3);
        for &id in &ids {
            assert_eq!(id, 100 + 0x7A);
        }
    }

    #[test]
    fn test_long_text() {
        let tok = build_test_tokenizer();
        let text = "hello ".repeat(167); // ~1002 chars
        let ids = tok.encode(&text);
        assert!(!ids.is_empty());
        assert!(ids.len() > 1);
    }

    #[test]
    fn test_max_piece_length_boundary() {
        let long_token = "a".repeat(10);
        let pieces = vec![
            ParsedPiece { token: "<unk>".into(), id: 0, score: 0.0, piece_type: TYPE_UNKNOWN },
            ParsedPiece { token: long_token.clone(), id: 1, score: -1.0, piece_type: TYPE_NORMAL },
            ParsedPiece { token: "a".into(), id: 2, score: -5.0, piece_type: TYPE_NORMAL },
        ];
        let tok = SentencePieceTokenizer::build(&pieces).unwrap();

        // Text exactly matching the longest token
        let ids = tok.encode(&long_token);
        assert_eq!(ids, vec![1]);

        // Text one char longer must split into two tokens
        let longer = "a".repeat(11);
        let ids = tok.encode(&longer);
        assert_eq!(ids.len(), 2);
        assert!(ids.contains(&1) && ids.contains(&2));
    }

    #[test]
    fn test_build_empty_pieces_fails() {
        let result = SentencePieceTokenizer::build(&[]);
        assert!(result.is_err());
    }

    #[test]
    fn test_build_minimal_vocab() {
        let pieces = vec![
            ParsedPiece { token: "a".into(), id: 0, score: 0.0, piece_type: TYPE_NORMAL },
        ];
        let tok = SentencePieceTokenizer::build(&pieces).unwrap();
        assert_eq!(tok.vocab_size(), 1);
        let ids = tok.encode("a");
        assert_eq!(ids, vec![0]);
    }

    #[test]
    fn test_from_model_data_invalid() {
        let garbage = vec![0xFF, 0xFF, 0xFF, 0xFF, 0xFF];
        let result = SentencePieceTokenizer::from_model_data(&garbage);
        assert!(result.is_err());
    }
}
