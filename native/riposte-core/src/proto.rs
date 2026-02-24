//! Minimal protobuf parser for SentencePiece `.model` files.
//!
//! Reads the binary protobuf wire format directly, extracting vocabulary pieces
//! (token string, score, type) needed by the tokenizer. Mirrors the Kotlin
//! `SentencePieceModelParser` implementation.

/// A parsed piece from the SentencePiece model protobuf.
#[derive(Debug, Clone)]
pub struct ParsedPiece {
    pub token: String,
    pub id: u32,
    pub score: f32,
    pub piece_type: u32,
}

// Protobuf wire types
const WIRE_VARINT: u32 = 0;
const WIRE_FIXED64: u32 = 1;
const WIRE_LENGTH_DELIMITED: u32 = 2;
const WIRE_FIXED32: u32 = 5;

// ModelProto field numbers
const FIELD_PIECES: u32 = 1;

// SentencePiece field numbers
const FIELD_PIECE: u32 = 1;
const FIELD_SCORE: u32 = 2;
const FIELD_TYPE: u32 = 3;

const DEFAULT_TYPE: u32 = 1; // NORMAL

/// Parses a SentencePiece `.model` protobuf and returns the vocabulary pieces.
///
/// # Errors
/// Returns an error if the data is empty or produces no vocabulary pieces.
pub fn parse(data: &[u8]) -> Result<Vec<ParsedPiece>, String> {
    if data.is_empty() {
        return Err("SentencePiece model data is empty".into());
    }

    let mut pieces = Vec::new();
    let mut pos = 0;
    let mut piece_id: u32 = 0;

    while pos < data.len() {
        let (tag, new_pos) = read_varint(data, pos)?;
        pos = new_pos;

        let field_number = (tag >> 3) as u32;
        let wire_type = (tag & 0x7) as u32;

        if field_number == FIELD_PIECES && wire_type == WIRE_LENGTH_DELIMITED {
            let (length, new_pos) = read_varint(data, pos)?;
            pos = new_pos;
            let length = length as usize;

            if pos + length > data.len() {
                // Truncated data — use whatever we parsed
                break;
            }

            let piece_data = &data[pos..pos + length];
            pos += length;

            let (token, score, piece_type) = parse_sentence_piece(piece_data)?;
            pieces.push(ParsedPiece {
                token,
                id: piece_id,
                score,
                piece_type,
            });
            piece_id += 1;
        } else {
            pos = skip_field(data, pos, wire_type)?;
        }
    }

    if pieces.is_empty() {
        return Err(format!(
            "SentencePiece model contains no vocabulary pieces (parsed {} bytes)",
            data.len()
        ));
    }

    Ok(pieces)
}

fn parse_sentence_piece(data: &[u8]) -> Result<(String, f32, u32), String> {
    let mut pos = 0;
    let mut token = String::new();
    let mut score: f32 = 0.0;
    let mut type_value: u32 = DEFAULT_TYPE;

    while pos < data.len() {
        let (tag, new_pos) = read_varint(data, pos)?;
        pos = new_pos;

        let field_number = (tag >> 3) as u32;
        let wire_type = (tag & 0x7) as u32;

        match (field_number, wire_type) {
            (FIELD_PIECE, WIRE_LENGTH_DELIMITED) => {
                let (length, new_pos) = read_varint(data, pos)?;
                pos = new_pos;
                let length = length as usize;
                if pos + length > data.len() {
                    return Err("Truncated piece token".into());
                }
                token = String::from_utf8_lossy(&data[pos..pos + length]).into_owned();
                pos += length;
            }
            (FIELD_SCORE, WIRE_FIXED32) => {
                if pos + 4 > data.len() {
                    return Err("Truncated score field".into());
                }
                score = f32::from_le_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]);
                pos += 4;
            }
            (FIELD_TYPE, WIRE_VARINT) => {
                let (val, new_pos) = read_varint(data, pos)?;
                type_value = val as u32;
                pos = new_pos;
            }
            _ => {
                pos = skip_field(data, pos, wire_type)?;
            }
        }
    }

    Ok((token, score, type_value))
}

fn read_varint(data: &[u8], mut pos: usize) -> Result<(u64, usize), String> {
    let mut result: u64 = 0;
    let mut shift = 0;

    loop {
        if pos >= data.len() {
            return Err("Unexpected end of data reading varint".into());
        }
        let b = data[pos] as u64;
        pos += 1;
        result |= (b & 0x7F) << shift;
        if b & 0x80 == 0 {
            break;
        }
        shift += 7;
    }

    Ok((result, pos))
}

fn skip_field(data: &[u8], mut pos: usize, wire_type: u32) -> Result<usize, String> {
    match wire_type {
        WIRE_VARINT => {
            let (_, new_pos) = read_varint(data, pos)?;
            Ok(new_pos)
        }
        WIRE_FIXED64 => {
            if pos + 8 > data.len() {
                return Err("Truncated fixed64".into());
            }
            Ok(pos + 8)
        }
        WIRE_LENGTH_DELIMITED => {
            let (length, new_pos) = read_varint(data, pos)?;
            pos = new_pos + length as usize;
            if pos > data.len() {
                return Err("Truncated length-delimited field".into());
            }
            Ok(pos)
        }
        WIRE_FIXED32 => {
            if pos + 4 > data.len() {
                return Err("Truncated fixed32".into());
            }
            Ok(pos + 4)
        }
        _ => Err(format!("Unknown wire type: {wire_type}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_empty_data_returns_error() {
        assert!(parse(&[]).is_err());
    }

    #[test]
    fn test_read_varint_single_byte() {
        let data = [0x05];
        let (val, pos) = read_varint(&data, 0).unwrap();
        assert_eq!(val, 5);
        assert_eq!(pos, 1);
    }

    #[test]
    fn test_read_varint_multi_byte() {
        // 300 = 0b100101100 → [0xAC, 0x02]
        let data = [0xAC, 0x02];
        let (val, pos) = read_varint(&data, 0).unwrap();
        assert_eq!(val, 300);
        assert_eq!(pos, 2);
    }

    /// Build a minimal protobuf with one SentencePiece containing token "▁hello", score -1.5, type NORMAL(1)
    #[test]
    fn test_parse_single_piece() {
        let piece_bytes = build_piece_bytes("▁hello", -1.5f32, 1);
        let mut model_bytes = Vec::new();
        // field 1, wire type 2 (length-delimited) → tag = (1 << 3) | 2 = 0x0A
        model_bytes.push(0x0A);
        encode_varint(piece_bytes.len() as u64, &mut model_bytes);
        model_bytes.extend_from_slice(&piece_bytes);

        let pieces = parse(&model_bytes).unwrap();
        assert_eq!(pieces.len(), 1);
        assert_eq!(pieces[0].token, "▁hello");
        assert!((pieces[0].score - (-1.5f32)).abs() < 1e-6);
        assert_eq!(pieces[0].piece_type, 1);
        assert_eq!(pieces[0].id, 0);
    }

    #[test]
    fn test_parse_multiple_pieces() {
        let mut model_bytes = Vec::new();
        for (i, token) in ["<unk>", "<s>", "</s>", "▁the"].iter().enumerate() {
            let piece = build_piece_bytes(token, -(i as f32), if i < 3 { i as u32 + 1 } else { 1 });
            model_bytes.push(0x0A);
            encode_varint(piece.len() as u64, &mut model_bytes);
            model_bytes.extend_from_slice(&piece);
        }

        let pieces = parse(&model_bytes).unwrap();
        assert_eq!(pieces.len(), 4);
        assert_eq!(pieces[0].token, "<unk>");
        assert_eq!(pieces[3].token, "▁the");
        assert_eq!(pieces[3].id, 3);
    }

    // --- helpers ---

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
}
