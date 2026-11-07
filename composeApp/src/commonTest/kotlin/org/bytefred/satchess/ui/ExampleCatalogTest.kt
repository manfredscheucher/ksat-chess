package org.bytefred.satchess.ui

import org.bytefred.satchess.puzzle.EXAMPLE_PUZZLE_SOURCES
import org.bytefred.satchess.ui.model.puzzleProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the Examples dialog relies on, for every bundled example. */
class ExampleCatalogTest {
    private val examples = loadExamples()

    @Test
    fun everyExampleDecodes() = assertEquals(EXAMPLE_PUZZLE_SOURCES.size, examples.size)

    @Test
    fun everyExampleIsValidAndDescribed() {
        for ((_, p) in examples) {
            assertNull(puzzleProblem(p), p.id)
            assertTrue(p.title.isNotBlank(), p.id)
            assertTrue(!p.description.isNullOrBlank(), p.id)
        }
    }

    @Test
    fun titlesAreUniqueWithinACategory() {
        for ((category, list) in examples.groupBy { it.category }) {
            assertEquals(list.size, list.distinctBy { it.puzzle.title }.size, category)
        }
    }

    @Test
    fun thirdPartyPuzzlesAreCredited() {
        for ((category, p) in examples.map { it.category to it.puzzle }) {
            if (category != "Demos") assertTrue(p.source?.name != null, p.id)
        }
    }
}
