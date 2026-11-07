package org.bytefred.satchess.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitPaneTest {
    private fun assertClose(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 0.001f) }
    }

    @Test
    fun dragMovesOnlyTheTwoNeighbours() = assertClose(
        listOf(1.5f, 0.5f, 1f),
        dragSplit(listOf(1f, 1f, 1f), 0, 50f, 300f, 10f),
    )

    @Test
    fun aPanelStopsAtTheMinimum() = assertClose(
        listOf(1.9f, 0.1f),
        dragSplit(listOf(1f, 1f), 0, 500f, 200f, 10f),
    )

    @Test
    fun noPlaceToDragChangesNothing() = assertClose(listOf(1f, 1f), dragSplit(listOf(1f, 1f), 5, 50f, 200f, 10f))
}
