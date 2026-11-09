package org.bytefred.satchess.ui.model

import org.bytefred.satchess.sat.Solution
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultsTest {
    private fun solution(key: String) = Solution(fens = emptyList(), moves = emptyList(), projectedKey = key, verified = true)

    @Test
    fun countSummaries() {
        assertEquals("unique", countSummary(1, complete = true, expected = null))
        assertEquals("unique, expected 1", countSummary(1, complete = true, expected = 1))
        assertEquals("3 solutions, expected 3", countSummary(3, complete = true, expected = 3))
        assertEquals("no solution", countSummary(0, complete = true, expected = null))
        assertEquals("at least 1 solution (stopped at the limit)", countSummary(1, complete = false, expected = null))
    }

    @Test
    fun unverifiedSolutionsAreNeverUnique() {
        assertEquals("1 solution (NOT verified)", countSummary(1, complete = true, expected = null, unverified = 1))
        assertEquals("3 solutions (1 NOT verified), expected 3", countSummary(3, complete = true, expected = 3, unverified = 1))
        assertEquals(
            "at least 2 solutions (stopped at the limit) (2 NOT verified)",
            countSummary(2, complete = false, expected = null, unverified = 2),
        )
    }

    @Test
    fun answersAreGroupedByProjectedKeyMostFrequentFirst() {
        val groups = groupByProjection(listOf(solution("h4 = Q"), solution("h4 = R"), solution("h4 = R")))
        assertEquals(listOf("h4 = R", "h4 = Q"), groups.map { it.key })
        assertEquals("h4 = R (2 lines), h4 = Q (1 line)", answerSummary(groups))
    }

    @Test
    fun sanLinesAreNumbered() {
        assertEquals("1. f3 e5 2. g4 Qh4#", sanLine(listOf("f3", "e5", "g4", "Qh4#"), whiteStarts = true))
        assertEquals("1... e5 2. Nf3 Nc6", sanLine(listOf("e5", "Nf3", "Nc6"), whiteStarts = false))
        assertEquals("", sanLine(emptyList(), whiteStarts = true))
    }

    @Test
    fun staticGridColumnsAreCeilSqrtOfTheBoardCount() {
        assertEquals(listOf(1, 2, 2, 2, 3, 3, 3, 3, 3, 4), (0..9).map(::staticColumns))
    }
}
