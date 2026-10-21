package org.bytefred.satchess.puzzle

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** JSON (de)serialization of puzzles; tolerant on input, pretty on output. */
object PuzzleJson {
    val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }
    fun decode(text: String): Puzzle = json.decodeFromString(Puzzle.serializer(), text)
    fun encode(puzzle: Puzzle): String = json.encodeToString(Puzzle.serializer(), puzzle)
}

/** The object form of a [MovePattern]; same fields, plain generated serializer. */
@Serializable
private data class MovePatternFields(
    val san: String? = null,
    val piece: String? = null,
    val from: String? = null,
    val to: String? = null,
    val capture: Boolean? = null,
    val promo: String? = null,
    val castle: String? = null,
    val check: Boolean? = null,
)

/**
 * Accepts a plain JSON string (SAN) or an object for [MovePattern]. A pattern that only has
 * [MovePattern.san] is written back as a plain string, everything else as an object.
 */
object MovePatternSerializer : KSerializer<MovePattern> {
    override val descriptor: SerialDescriptor = MovePatternFields.serializer().descriptor

    override fun deserialize(decoder: Decoder): MovePattern {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("MovePattern can only be read from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (!element.isString) throw SerializationException("move pattern must be a SAN string or an object")
                MovePattern(san = element.content)
            }
            is JsonObject -> input.json.decodeFromJsonElement<MovePatternFields>(element).let {
                MovePattern(it.san, it.piece, it.from, it.to, it.capture, it.promo, it.castle, it.check)
            }
            else -> throw SerializationException("move pattern must be a SAN string or an object")
        }
    }

    override fun serialize(encoder: Encoder, value: MovePattern) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("MovePattern can only be written as JSON")
        if (value.san != null && value == MovePattern(san = value.san)) {
            output.encodeJsonElement(JsonPrimitive(value.san))
        } else {
            val fields = with(value) { MovePatternFields(san, piece, from, to, capture, promo, castle, check) }
            output.encodeJsonElement(output.json.encodeToJsonElement(fields))
        }
    }
}
