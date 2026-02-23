package com.adsamcik.riposte.core.ml

import timber.log.Timber

/**
 * Memory-efficient SentencePiece BPE tokenizer for Android.
 *
 * Uses a HashMap for vocabulary lookup instead of a trie, reducing memory
 * from ~200MB (sentencepiece4j) to ~40MB for EmbeddingGemma's 262K vocabulary.
 *
 * Implements Viterbi best-path segmentation with byte fallback.
 * Preprocessing follows Gemma's tokenizer config: only space→▁ replacement,
 * no NFKC normalization, no lowercasing.
 */
internal class SentencePieceTokenizer private constructor(
    private val vocab: HashMap<String, VocabEntry>,
    private val bytePieceIds: IntArray,
    private val maxPieceLength: Int,
    private val unkId: Int,
    private val unkScore: Float,
    private val maxScore: Float,
    private val hasByteFallback: Boolean,
) {
    /**
     * A vocabulary entry with token ID, log-probability score, and piece type.
     */
    data class VocabEntry(val id: Int, val score: Float, val type: Int)

    /**
     * Tokenizes text using Gemma-compatible preprocessing and Viterbi segmentation.
     *
     * Preprocessing: replace ASCII spaces with ▁ (U+2581). No other normalization.
     * Segmentation: Viterbi best-path with byte fallback for unknown characters.
     *
     * @return List of token IDs. Does NOT include BOS/EOS — caller must add them.
     */
    fun encode(text: String): List<Int> {
        if (text.isEmpty()) return emptyList()

        // Gemma preprocessing: only replace ASCII spaces with ▁
        val normalized = text.replace(' ', SPACE_REPLACEMENT)

        return viterbiEncode(normalized)
    }

    /**
     * Viterbi best-path segmentation.
     *
     * For each position i, tries all substrings text[i..j] up to [maxPieceLength].
     * Finds the segmentation that maximizes total score. Uses single-character UNK
     * fallback for unreachable positions, with post-processing to convert UNK
     * segments to byte-fallback tokens.
     */
    private fun viterbiEncode(text: String): List<Int> {
        val n = text.length
        if (n == 0) return emptyList()

        val bestScore = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val bestId = IntArray(n + 1) { -1 }
        val bestStart = IntArray(n + 1) { -1 }
        val bestIsUnk = BooleanArray(n + 1)
        bestScore[0] = 0f

        for (i in 0 until n) {
            if (bestScore[i] == Float.NEGATIVE_INFINITY) continue

            val maxJ = minOf(i + maxPieceLength, n)
            for (j in i + 1..maxJ) {
                val substr = text.substring(i, j)
                val entry = vocab[substr] ?: continue

                // Skip CONTROL and UNUSED tokens — not for segmentation
                if (entry.type == TYPE_CONTROL || entry.type == TYPE_UNUSED) continue

                // USER_DEFINED tokens get boosted score to ensure selection
                val effectiveScore =
                    if (entry.type == TYPE_USER_DEFINED) {
                        (j - i).toFloat() * maxScore - USER_DEFINED_SCORE_BIAS
                    } else {
                        entry.score
                    }

                val newScore = bestScore[i] + effectiveScore
                if (newScore > bestScore[j]) {
                    bestScore[j] = newScore
                    bestId[j] = entry.id
                    bestStart[j] = i
                    bestIsUnk[j] = false
                }
            }

            // Single-character UNK fallback — always competes for position i+1.
            // unkScore is very negative so it loses to any real vocab match.
            val unkCandidate = bestScore[i] + unkScore
            if (unkCandidate > bestScore[i + 1]) {
                bestScore[i + 1] = unkCandidate
                bestId[i + 1] = unkId
                bestStart[i + 1] = i
                bestIsUnk[i + 1] = true
            }
        }

        // Backtrack to reconstruct segmentation
        val segments = mutableListOf<Segment>()
        var pos = n
        while (pos > 0) {
            segments.add(0, Segment(bestStart[pos], pos, bestId[pos], bestIsUnk[pos]))
            pos = bestStart[pos]
        }

        // Post-process: byte fallback for UNK segments
        return applyByteFallback(text, segments)
    }

    /**
     * Converts UNK segments to byte-fallback tokens when available.
     * Each unknown character is split into its UTF-8 bytes, and each byte
     * is mapped to its corresponding `<0xNN>` token.
     */
    private fun applyByteFallback(
        text: String,
        segments: List<Segment>,
    ): List<Int> {
        val result = mutableListOf<Int>()
        for (seg in segments) {
            if (seg.isUnk && hasByteFallback) {
                val surface = text.substring(seg.start, seg.end)
                val bytes = surface.toByteArray(Charsets.UTF_8)
                for (b in bytes) {
                    val byteIdx = b.toInt() and BYTE_MASK
                    val byteId = bytePieceIds[byteIdx]
                    result.add(if (byteId >= 0) byteId else unkId)
                }
            } else {
                result.add(seg.id)
            }
        }
        return result
    }

    private data class Segment(
        val start: Int,
        val end: Int,
        val id: Int,
        val isUnk: Boolean,
    )

    /**
     * A parsed piece from the SentencePiece model protobuf.
     */
    data class ParsedPiece(
        val token: String,
        val id: Int,
        val score: Float,
        val type: Int,
    )

    companion object {
        /** SentencePiece word boundary marker (replaces spaces). */
        private const val SPACE_REPLACEMENT = '\u2581' // ▁

        // Protobuf piece type values
        const val TYPE_NORMAL = 1
        const val TYPE_UNKNOWN = 2
        const val TYPE_CONTROL = 3
        const val TYPE_USER_DEFINED = 4
        const val TYPE_UNUSED = 5
        const val TYPE_BYTE = 6

        /** UNK score penalty relative to minimum vocab score. */
        private const val UNK_SCORE_OFFSET = 10f

        /** Small bias subtracted from USER_DEFINED token boosted score. */
        private const val USER_DEFINED_SCORE_BIAS = 0.1f

        private const val DEFAULT_UNK_ID = 3
        private const val BYTE_TOKEN_COUNT = 256
        private const val BYTE_MASK = 0xFF

        /**
         * Builds a tokenizer from parsed vocabulary pieces.
         *
         * @param pieces The vocabulary extracted from a SentencePiece `.model` file.
         * @return A ready-to-use tokenizer instance.
         */
        fun build(pieces: List<ParsedPiece>): SentencePieceTokenizer {
            val vocab = HashMap<String, VocabEntry>(pieces.size * 2)
            val bytePieceIds = IntArray(BYTE_TOKEN_COUNT) { -1 }
            var maxLen = 0
            var minScore = 0f
            var maxScoreVal = Float.NEGATIVE_INFINITY
            var unkId = DEFAULT_UNK_ID

            for (piece in pieces) {
                val entry = VocabEntry(piece.id, piece.score, piece.type)

                when (piece.type) {
                    TYPE_UNKNOWN -> unkId = piece.id
                    TYPE_BYTE -> {
                        val byteVal = parseBytePieceValue(piece.token)
                        if (byteVal >= 0) {
                            bytePieceIds[byteVal] = piece.id
                        }
                    }
                    TYPE_NORMAL, TYPE_USER_DEFINED -> {
                        if (piece.score < minScore) minScore = piece.score
                        if (piece.score > maxScoreVal) maxScoreVal = piece.score
                    }
                }

                vocab[piece.token] = entry
                if (piece.token.length > maxLen) {
                    maxLen = piece.token.length
                }
            }

            val hasByteFallback = bytePieceIds.any { it >= 0 }

            Timber.i(
                "SentencePieceTokenizer: %d pieces, maxLen=%d, unkId=%d, byteFallback=%s",
                pieces.size,
                maxLen,
                unkId,
                hasByteFallback,
            )

            return SentencePieceTokenizer(
                vocab = vocab,
                bytePieceIds = bytePieceIds,
                maxPieceLength = maxLen,
                unkId = unkId,
                unkScore = minScore - UNK_SCORE_OFFSET,
                maxScore = if (maxScoreVal > Float.NEGATIVE_INFINITY) maxScoreVal else 0f,
                hasByteFallback = hasByteFallback,
            )
        }

        /**
         * Parses the byte value from a byte piece token like `<0x41>`.
         * @return The byte value (0-255) or -1 if not a valid byte token.
         */
        private fun parseBytePieceValue(token: String): Int {
            if (!token.startsWith("<0x") || !token.endsWith(">")) return -1
            val hex = token.substring(3, token.length - 1)
            return try {
                hex.toInt(HEX_RADIX)
            } catch (_: NumberFormatException) {
                -1
            }
        }

        private const val HEX_RADIX = 16
    }
}
