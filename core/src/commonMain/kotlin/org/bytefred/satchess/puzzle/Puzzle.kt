package org.bytefred.satchess.puzzle

import kotlinx.serialization.Serializable

/**
 * A puzzle: a window of [plies] half-moves (t = 0..plies) over one game, plus constraints.
 * Format spec: doc/spec.md section 4. All answers are existential (cooperative play).
 */
@Serializable
data class Puzzle(
    val id: String = "",
    val title: String = "",
    val composer: String? = null,
    val source: Source? = null,
    /** Our own short description (never copied text from the source). */
    val description: String? = null,
    val question: Question = Question.CUSTOM,
    val plies: Int = 0,
    val start: Start = Start(),
    /** Board constraints at plies other than 0, keyed by ply ("8" -> FEN placement with '?'). */
    val positions: Map<String, String> = emptyMap(),
    /** Square domains per ply: "0" -> { "h4" -> "QRBNqrbn" } ('.' = empty allowed). */
    val squares: Map<String, Map<String, String>> = emptyMap(),
    /** Move patterns per ply index (move from ply t to t+1): "0" -> SAN or object. */
    val moves: Map<String, MovePattern> = emptyMap(),
    val goal: Goal = Goal.NONE,
    /** Also prove that plies - 2 are not enough. */
    val shortest: Boolean = false,
    /** Projection items: "moves", "board@t", "square@t:h4", "piece@t:K", "sideToMove@0". */
    val project: List<String> = listOf("moves", "board@0"),
    val rules: RuleFlags = RuleFlags(),
    /** Known number of solutions under [project], or null. */
    val expected: Int? = null,
)

@Serializable
data class Source(val name: String, val url: String? = null, val year: Int? = null)

@Serializable
data class Start(
    /** FEN placement with '?' for unknown squares; "startpos" = initial position; all '?' = open. */
    val board: String = "startpos",
    /** "w", "b" or "?" (free: solved once per side). */
    val sideToMove: String = "w",
    /** "auto", "-", a subset of "KQkq", or "?" (each right free). */
    val castling: String = "auto",
    /** "-" or an e.p. target square like "e3". */
    val enPassant: String = "-",
)

/** A move pattern: SAN (written in JSON as a plain string) and/or individual fields. */
@Serializable(with = MovePatternSerializer::class)
data class MovePattern(
    val san: String? = null,
    /** FEN letter of the moving piece, e.g. "N" or "p". */
    val piece: String? = null,
    val from: String? = null,
    val to: String? = null,
    val capture: Boolean? = null,
    /** FEN letter of the promotion piece. */
    val promo: String? = null,
    /** "K", "Q", "k", "q" (FEN castling letter) or "any". */
    val castle: String? = null,
    val check: Boolean? = null,
)

@Serializable
enum class Goal {
    @kotlinx.serialization.SerialName("none") NONE,
    @kotlinx.serialization.SerialName("check") CHECK,
    @kotlinx.serialization.SerialName("checkmate") CHECKMATE,
    @kotlinx.serialization.SerialName("stalemate") STALEMATE,
}

@Serializable
enum class Question {
    @kotlinx.serialization.SerialName("proofGame") PROOF_GAME,
    @kotlinx.serialization.SerialName("lastMoves") LAST_MOVES,
    @kotlinx.serialization.SerialName("helpmate") HELPMATE,
    @kotlinx.serialization.SerialName("unknownPiece") UNKNOWN_PIECE,
    @kotlinx.serialization.SerialName("custom") CUSTOM,
}

@Serializable
data class RuleFlags(
    val oneKingEach: Boolean = true,
    val checkRules: Boolean = true,
    /** null = on whenever some board has '?'. */
    val materialSane: Boolean? = null,
)
