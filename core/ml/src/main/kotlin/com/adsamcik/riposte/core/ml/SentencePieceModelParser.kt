package com.adsamcik.riposte.core.ml

import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal parser for SentencePiece `.model` protobuf files.
 *
 * Reads the binary protobuf wire format directly, avoiding both the `protobuf-java`
 * dependency (which conflicts with Android's `protobuf-javalite`) and the `sentencepiece4j`
 * library (whose trie causes OOM with large vocabularies on Android).
 *
 * Extracts vocabulary pieces (token string, score, type) and builds a
 * [SentencePieceTokenizer] for memory-efficient tokenization.
 *
 * Supports the subset of the SentencePiece ModelProto schema needed for tokenization:
 * - ModelProto.pieces (field 1, repeated SentencePiece)
 * - SentencePiece.piece (field 1, string)
 * - SentencePiece.score (field 2, float)
 * - SentencePiece.type (field 3, enum)
 */
internal object SentencePieceModelParser {

    // Protobuf wire types
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    // ModelProto field numbers
    private const val FIELD_PIECES = 1

    // SentencePiece field numbers
    private const val FIELD_PIECE = 1
    private const val FIELD_SCORE = 2
    private const val FIELD_TYPE = 3

    private const val FIXED64_SIZE = 8
    private const val FIXED32_SIZE = 4
    private const val VARINT_MASK = 0x7F
    private const val VARINT_CONTINUE = 0x80
    private const val BYTE_MASK = 0xFF
    private const val TAG_FIELD_SHIFT = 3
    private const val TAG_WIRE_MASK = 0x7
    private const val VARINT_SHIFT = 7
    private const val DEFAULT_TYPE = 1 // NORMAL

    /**
     * Parses a SentencePiece `.model` file and returns a [SentencePieceTokenizer].
     */
    fun parse(file: File): SentencePieceTokenizer = parse(file.readBytes())

    /**
     * Parses a SentencePiece `.model` from an [InputStream].
     */
    fun parse(inputStream: InputStream): SentencePieceTokenizer = parse(inputStream.readBytes())

    /**
     * Parses raw bytes of a SentencePiece `.model` protobuf.
     *
     * @throws IllegalArgumentException if the data is empty or produces no vocabulary pieces.
     * @throws IllegalStateException if the protobuf data is malformed.
     */
    fun parse(data: ByteArray): SentencePieceTokenizer {
        require(data.isNotEmpty()) { "SentencePiece model data is empty" }

        val pieces = mutableListOf<SentencePieceTokenizer.ParsedPiece>()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var pieceId = 0

        try {
            while (buffer.hasRemaining()) {
                val tag = readVarint(buffer).toInt()
                val fieldNumber = tag ushr TAG_FIELD_SHIFT
                val wireType = tag and TAG_WIRE_MASK

                when {
                    fieldNumber == FIELD_PIECES && wireType == WIRE_LENGTH_DELIMITED -> {
                        val length = readVarint(buffer).toInt()
                        check(length >= 0 && length <= buffer.remaining()) {
                            "Invalid piece length: $length (remaining: ${buffer.remaining()})"
                        }
                        val pieceBytes = ByteArray(length)
                        buffer.get(pieceBytes)
                        val (token, score, type) = parseSentencePiece(pieceBytes)
                        pieces.add(SentencePieceTokenizer.ParsedPiece(token, pieceId++, score, type))
                    }
                    else -> skipField(buffer, wireType)
                }
            }
        } catch (e: BufferUnderflowException) {
            Timber.w(
                e,
                "Truncated protobuf data at offset %d — parsed %d pieces before error",
                buffer.position(),
                pieces.size,
            )
            // Use whatever pieces we successfully parsed
        }

        check(pieces.isNotEmpty()) {
            "SentencePiece model contains no vocabulary pieces (parsed ${data.size} bytes)"
        }

        return SentencePieceTokenizer.build(pieces)
    }

    private data class PieceData(
        val token: String,
        val score: Float,
        val type: Int,
    )

    private fun parseSentencePiece(data: ByteArray): PieceData {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var token = ""
        var score = 0f
        var typeValue = DEFAULT_TYPE

        while (buffer.hasRemaining()) {
            val tag = readVarint(buffer).toInt()
            val fieldNumber = tag ushr TAG_FIELD_SHIFT
            val wireType = tag and TAG_WIRE_MASK

            when {
                fieldNumber == FIELD_PIECE && wireType == WIRE_LENGTH_DELIMITED -> {
                    val length = readVarint(buffer).toInt()
                    val bytes = ByteArray(length)
                    buffer.get(bytes)
                    token = String(bytes, Charsets.UTF_8)
                }
                fieldNumber == FIELD_SCORE && wireType == WIRE_FIXED32 -> {
                    score = Float.fromBits(buffer.int)
                }
                fieldNumber == FIELD_TYPE && wireType == WIRE_VARINT -> {
                    typeValue = readVarint(buffer).toInt()
                }
                else -> skipField(buffer, wireType)
            }
        }

        return PieceData(token, score, typeValue)
    }

    private fun readVarint(buffer: ByteBuffer): Long {
        var result = 0L
        var shift = 0
        while (buffer.hasRemaining()) {
            val b = buffer.get().toInt() and BYTE_MASK
            result = result or ((b and VARINT_MASK).toLong() shl shift)
            if (b and VARINT_CONTINUE == 0) break
            shift += VARINT_SHIFT
        }
        return result
    }

    private fun skipField(
        buffer: ByteBuffer,
        wireType: Int,
    ) {
        when (wireType) {
            WIRE_VARINT -> readVarint(buffer)
            WIRE_FIXED64 -> buffer.position(buffer.position() + FIXED64_SIZE)
            WIRE_LENGTH_DELIMITED -> {
                val length = readVarint(buffer).toInt()
                buffer.position(buffer.position() + length)
            }
            WIRE_FIXED32 -> buffer.position(buffer.position() + FIXED32_SIZE)
        }
    }
}
