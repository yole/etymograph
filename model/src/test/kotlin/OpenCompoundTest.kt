package page.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class OpenCompoundTest : QBaseTest() {
    @Test
    fun repeatedComponentsAreRejectedOnCreation() {
        val head = q.word("fara", "hunt")
        val modifier = q.word("mir", "jewel")
        for (components in listOf(listOf(head, head), listOf(head, modifier, modifier))) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                graph.createOpenCompound(components, 0)
            }
            assertEquals("An open compound cannot contain the same word more than once", error.message)
        }
        assertTrue(graph.allOpenCompounds().isEmpty())
    }

    @Test
    fun repeatedComponentsAreRejectedWithoutChangingCompound() {
        val head = q.word("fara", "hunt")
        val modifier = q.word("mir", "jewel")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1)

        assertThrows(IllegalArgumentException::class.java) {
            compound.updateComponents(listOf(head, modifier, modifier), 0)
        }
        assertEquals(listOf(modifier.id, head.id), compound.components.map { it.id })
        assertEquals(1, compound.headIndex)
    }

    @Test
    fun distinctWordEntriesWithSameTextAndGlossAreAllowed() {
        val first = graph.addWord("mir", q, "jewel")
        val second = graph.addWord("mir", q, "jewel")
        assertNotEquals(first.id, second.id)

        val compound = graph.createOpenCompound(listOf(first, second), 1)
        assertEquals(listOf(first.id, second.id), compound.components.map { it.id })
    }

    @Test
    fun requiredHead() {
        val word = q.word("mir", "jewel")
        for (headIndex in listOf(-1, 0, 1)) {
            assertThrows(IllegalArgumentException::class.java) {
                graph.createOpenCompound(emptyList(), headIndex)
            }
        }
        for (headIndex in listOf(-1, 1)) {
            assertThrows(IllegalArgumentException::class.java) {
                graph.createOpenCompound(listOf(word), headIndex)
            }
        }
        assertTrue(graph.allOpenCompounds().isEmpty())
    }

    @Test
    fun updateComponentsPreservesHeadInvariant() {
        val fara = q.word("fara", "hunt")
        val mir = q.word("mir", "jewel")
        val components = mutableListOf(fara, mir)
        val compound = graph.createOpenCompound(components, 1)
        components.clear()
        assertEquals(listOf(fara, mir), compound.components)
        assertSame(mir, compound.headComponent())

        assertThrows(IllegalArgumentException::class.java) {
            compound.updateComponents(listOf(fara), 1)
        }
        assertEquals(listOf(fara, mir), compound.components)
        assertSame(mir, compound.headComponent())

        compound.updateComponents(listOf(mir, fara), 0)
        assertEquals(listOf(mir, fara), compound.components)
        assertSame(mir, compound.headComponent())
    }

    @Test
    fun deleteCompoundCleansUpLinks() {
        val mir = q.word("mir", "jewel")
        val fara = q.word("fara", "hunt")
        val compound = graph.createOpenCompound(listOf(mir), 0)
        graph.addLink(compound, mir, Link.Related)
        graph.addLink(fara, compound, Link.Related)

        graph.deleteOpenCompound(compound)

        assertNull(graph.langEntityById(compound.id))
        assertTrue(graph.allOpenCompounds().isEmpty())
        assertTrue(graph.getLinksFrom(fara).none())
        assertTrue(graph.getLinksTo(mir).none())
        assertSame(mir, graph.wordById(mir.id))
    }

    @Test
    fun deleteComponentWordDeletesReferencingCompounds() {
        val fara = q.word("fara", "hunt")
        val mir = q.word("mir", "jewel")
        val withHead = graph.createOpenCompound(listOf(fara, mir), 0)
        val withModifier = graph.createOpenCompound(listOf(fara, mir), 1)
        val unrelated = graph.createOpenCompound(listOf(mir), 0)

        graph.deleteWord(fara)

        assertNull(graph.langEntityById(withHead.id))
        assertNull(graph.langEntityById(withModifier.id))
        assertEquals(listOf(unrelated), graph.allOpenCompounds())
    }
}
